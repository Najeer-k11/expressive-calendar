package app.expressivecalendar.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

data class ImportResult(val imported: Int, val alreadyThere: Int, val failed: Int)

/**
 * Backup to and restore from .ics files, mapping between the calendar provider and [Ics].
 *
 * Re-importing is safe: each event keeps its iCalendar UID (Events.UID_2445), and events whose
 * UID is already in the target calendar are skipped.
 */
class CalendarBackup(private val context: Context) {
    private val resolver get() = context.contentResolver

    private data class Row(
        val id: Long,
        val originalId: Long?,
        val syncId: String?,
        val originalSyncId: String?,
        val uid: String?,
        val event: IcsEvent,
    )

    suspend fun export(calendarIds: Set<Long>, to: Uri): Int = withContext(Dispatchers.IO) {
        val events = readEvents(calendarIds)
        resolver.openOutputStream(to, "wt")!!.bufferedWriter(Charsets.UTF_8).use { it.write(Ics.write(events)) }
        events.count { it.recurrenceId == null }
    }

    private fun readEvents(calendarIds: Set<Long>): List<IcsEvent> {
        if (calendarIds.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        val rows = mutableListOf<Row>()
        resolver.query(
            Events.CONTENT_URI,
            arrayOf(
                Events._ID, Events.ORIGINAL_ID, Events.UID_2445, Events.TITLE, Events.EVENT_LOCATION,
                Events.DESCRIPTION, Events.ALL_DAY, Events.DTSTART, Events.DTEND, Events.DURATION,
                Events.EVENT_TIMEZONE, Events.RRULE, Events.EXDATE, Events.ORIGINAL_INSTANCE_TIME,
                Events.STATUS, Events._SYNC_ID, Events.ORIGINAL_SYNC_ID,
            ),
            "${Events.CALENDAR_ID} IN (${calendarIds.joinToString(",")}) AND ${Events.DELETED} = 0",
            null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val allDay = c.getInt(6) == 1
                val start = c.getLong(7)
                val end = if (!c.isNull(8) && c.getLong(8) > 0) c.getLong(8)
                else start + ((c.getString(9)?.let { Ics.parseDuration(it) } ?: if (allDay) 86_400L else 3_600L) * 1000)
                rows += Row(
                    id = c.getLong(0),
                    originalId = if (c.isNull(1)) null else c.getLong(1),
                    syncId = c.getString(15)?.ifBlank { null },
                    originalSyncId = c.getString(16)?.ifBlank { null },
                    uid = c.getString(2)?.ifBlank { null },
                    event = IcsEvent(
                        uid = "",
                        title = c.getString(3).orEmpty(),
                        location = c.getString(4),
                        description = c.getString(5),
                        allDay = allDay,
                        start = start,
                        end = maxOf(end, start),
                        timeZone = if (allDay) "UTC" else c.getString(10) ?: zone.id,
                        rrule = c.getString(11)?.ifBlank { null },
                        exdates = Ics.parseProviderDates(c.getString(12), zone),
                        recurrenceId = if (c.isNull(13)) null else c.getLong(13),
                        cancelled = !c.isNull(14) && c.getInt(14) == Events.STATUS_CANCELED,
                    ),
                )
            }
        }
        val reminders = readReminders(rows.map { it.id })
        val uidById = rows.associate { it.id to (it.uid ?: "${it.id}-${it.event.start}@expressive-calendar") }
        // Synced calendars may link an edited occurrence to its series only by sync id.
        val idBySyncId = rows.mapNotNull { r -> r.syncId?.let { it to r.id } }.toMap()
        val events = rows.mapNotNull { r ->
            val series = (r.originalId ?: r.originalSyncId?.let { idBySyncId[it] })?.takeIf { it in uidById }
            val isOccurrence = r.originalId != null || r.originalSyncId != null || r.event.recurrenceId != null
            // A deleted occurrence of a series we can't see: nothing to export.
            if (r.event.cancelled && isOccurrence && series == null) return@mapNotNull null
            // An edited occurrence whose series isn't in the export stands on its own.
            r.event.copy(
                uid = if (series != null) uidById.getValue(series) else uidById.getValue(r.id),
                recurrenceId = if (series != null) r.event.recurrenceId else null,
                cancelled = r.event.cancelled && series != null,
                reminders = reminders[r.id].orEmpty(),
            )
        }.filter { !it.cancelled || it.recurrenceId != null }
        return Ics.foldCancellations(events)
    }

    private fun readReminders(eventIds: List<Long>): Map<Long, List<Int>> {
        val out = HashMap<Long, MutableList<Int>>()
        eventIds.chunked(500).forEach { chunk ->
            resolver.query(
                Reminders.CONTENT_URI, arrayOf(Reminders.EVENT_ID, Reminders.MINUTES),
                "${Reminders.EVENT_ID} IN (${chunk.joinToString(",")})", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val m = c.getInt(1)
                    out.getOrPut(c.getLong(0)) { mutableListOf() } += if (m < 0) 10 else m // -1 = "default"
                }
            }
        }
        return out
    }

    /** Reads and parses a file without touching the calendar, for the "found N events" preview. */
    suspend fun read(from: Uri): List<IcsEvent> = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(from)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        Ics.foldCancellations(Ics.parse(text, ZoneId.systemDefault()))
    }

    suspend fun import(events: List<IcsEvent>, calendarId: Long): ImportResult = withContext(Dispatchers.IO) {
        val existing = HashSet<String>()
        resolver.query(
            Events.CONTENT_URI, arrayOf(Events.UID_2445),
            "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0 AND ${Events.ORIGINAL_ID} IS NULL",
            arrayOf(calendarId.toString()), null,
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { existing += it } }

        // Counts are per event in the file (series), not per row written.
        val seriesUids = events.filter { it.recurrenceId == null }.map { it.uid }.toSet()
        var imported = 0
        var skipped = 0
        var failed = 0
        for (e in Ics.detachOccurrences(events)) {
            val counts = e.uid in seriesUids
            if (e.uid in existing) { if (counts) skipped++; continue }
            val ok = runCatching { insert(e, calendarId) }
                .onFailure { android.util.Log.w("ExpressiveCalendar", "import failed: ${e.uid}", it) }
                .isSuccess
            if (counts) { if (ok) imported++ else failed++ }
        }
        ImportResult(imported, skipped, failed)
    }

    private fun insert(e: IcsEvent, calendarId: Long): Long {
        val v = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, e.title)
            put(Events.EVENT_LOCATION, e.location.orEmpty())
            put(Events.DESCRIPTION, e.description.orEmpty())
            put(Events.ALL_DAY, if (e.allDay) 1 else 0)
            put(Events.DTSTART, e.start)
            put(Events.EVENT_TIMEZONE, if (e.allDay) "UTC" else e.timeZone)
            put(Events.HAS_ALARM, if (e.reminders.isEmpty()) 0 else 1)
            put(Events.UID_2445, e.uid)
            if (e.rrule != null) {
                // Recurring events must carry DURATION and no DTEND, or the provider rejects them.
                put(Events.RRULE, e.rrule)
                put(Events.DURATION, Ics.providerDuration(e.start, e.end, e.allDay))
                Ics.formatProviderDates(e.exdates)?.let { put(Events.EXDATE, it) }
            } else {
                put(Events.DTEND, e.end)
            }
        }
        val id = ContentUris.parseId(resolver.insert(Events.CONTENT_URI, v)!!)
        for (m in e.reminders) {
            resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                put(Reminders.EVENT_ID, id)
                put(Reminders.MINUTES, m)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
            })
        }
        return id
    }
}
