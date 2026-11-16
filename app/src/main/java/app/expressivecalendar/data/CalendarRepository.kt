package app.expressivecalendar.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

data class DeviceCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val accountType: String,
    val color: Int,
    val writable: Boolean,
    val visible: Boolean,
)

/** One occurrence of an event on the calendar (a recurring event yields many). */
data class EventInstance(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val location: String?,
    val description: String?,
    val allDay: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val color: Int,
    val recurring: Boolean,
    val beginMillis: Long,
) {
    /** First and last calendar day this instance touches (end-exclusive handling for midnight ends). */
    val firstDay: LocalDate get() = start.toLocalDate()
    val lastDay: LocalDate
        get() {
            val e = if (allDay || (end.toLocalTime() == java.time.LocalTime.MIDNIGHT && end > start)) end.minusNanos(1) else end
            return maxOf(e.toLocalDate(), firstDay)
        }

    fun occursOn(day: LocalDate) = !day.isBefore(firstDay) && !day.isAfter(lastDay)
}

enum class Repeat(val label: String, val rrule: String?) {
    NONE("Does not repeat", null),
    DAILY("Every day", "FREQ=DAILY"),
    WEEKLY("Every week", "FREQ=WEEKLY"),
    MONTHLY("Every month", "FREQ=MONTHLY"),
    YEARLY("Every year", "FREQ=YEARLY");

    companion object {
        fun fromRrule(r: String?): Repeat {
            if (r.isNullOrBlank()) return NONE
            val freq = r.split(';').firstOrNull { it.startsWith("FREQ=") } ?: return NONE
            return entries.firstOrNull { it.rrule == freq } ?: NONE
        }
    }
}

/** The editable form of an event series. */
data class EventDraft(
    val eventId: Long? = null,
    val calendarId: Long? = null,
    val title: String = "",
    val location: String = "",
    val description: String = "",
    val allDay: Boolean = false,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val repeat: Repeat = Repeat.NONE,
    /** Preserved verbatim when the user doesn't touch the repeat option. */
    val rawRrule: String? = null,
    val reminderMinutes: Int = 10,
)

class CalendarRepository(private val context: Context) {
    private val resolver get() = context.contentResolver

    /** Emits Unit whenever anything in the calendar provider changes (sync, other apps, us). */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    suspend fun calendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val out = mutableListOf<DeviceCalendar>()
        resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(
                Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
                Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_COLOR, Calendars.CALENDAR_ACCESS_LEVEL,
                Calendars.VISIBLE,
            ),
            null, null, "${Calendars.ACCOUNT_NAME} ASC, ${Calendars.CALENDAR_DISPLAY_NAME} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += DeviceCalendar(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "Calendar",
                    account = c.getString(2) ?: "",
                    accountType = c.getString(3) ?: "",
                    color = c.getInt(4),
                    writable = c.getInt(5) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                    visible = c.getInt(6) == 1,
                )
            }
        }
        out
    }

    suspend fun instances(from: LocalDate, toExclusive: LocalDate): List<EventInstance> =
        withContext(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            // Pad a day either side: all-day events are stored at UTC midnight, which can fall on
            // the neighbouring local day for zones far from UTC.
            val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = toExclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val uri = Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, begin)
                ContentUris.appendId(it, end)
            }.build()
            val out = mutableListOf<EventInstance>()
            resolver.query(
                uri,
                arrayOf(
                    Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE,
                    Instances.EVENT_LOCATION, Instances.DESCRIPTION, Instances.ALL_DAY,
                    Instances.BEGIN, Instances.END, Instances.DISPLAY_COLOR, Instances.RRULE,
                    Instances.VISIBLE,
                ),
                "${Instances.VISIBLE} = 1",
                null,
                "${Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val allDay = c.getInt(5) == 1
                    val tz = if (allDay) ZoneOffset.UTC else zone
                    val s = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(6)), tz)
                    val e = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(7)), tz)
                    val inst = EventInstance(
                        eventId = c.getLong(0),
                        calendarId = c.getLong(1),
                        title = c.getString(2)?.ifBlank { null } ?: "(No title)",
                        location = c.getString(3)?.ifBlank { null },
                        description = c.getString(4)?.ifBlank { null },
                        allDay = allDay,
                        start = s,
                        end = maxOf(e, s),
                        color = c.getInt(8),
                        recurring = !c.getString(9).isNullOrBlank(),
                        beginMillis = c.getLong(6),
                    )
                    if (!inst.lastDay.isBefore(from) && inst.firstDay.isBefore(toExclusive)) out += inst
                }
            }
            out.sortedWith(compareBy<EventInstance>({ !it.allDay }, { it.start }))
        }

    /** Loads the whole series for editing (not just one occurrence). */
    suspend fun loadDraft(eventId: Long): EventDraft? = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val draft = resolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
            arrayOf(
                Events.CALENDAR_ID, Events.TITLE, Events.EVENT_LOCATION, Events.DESCRIPTION,
                Events.ALL_DAY, Events.DTSTART, Events.DTEND, Events.DURATION, Events.RRULE,
            ),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val allDay = c.getInt(4) == 1
            val tz = if (allDay) ZoneOffset.UTC else zone
            val startMs = c.getLong(5)
            val endMs = if (!c.isNull(6) && c.getLong(6) > 0) c.getLong(6)
            else startMs + parseDuration(c.getString(7)).toMillis()
            val rrule = c.getString(8)
            EventDraft(
                eventId = eventId,
                calendarId = c.getLong(0),
                title = c.getString(1).orEmpty(),
                location = c.getString(2).orEmpty(),
                description = c.getString(3).orEmpty(),
                allDay = allDay,
                start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), tz),
                end = LocalDateTime.ofInstant(Instant.ofEpochMilli(maxOf(endMs, startMs)), tz),
                repeat = Repeat.fromRrule(rrule),
                rawRrule = rrule,
            )
        } ?: return@withContext null
        val reminder = resolver.query(
            Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES),
            "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()),
            "${Reminders.MINUTES} ASC",
        )?.use { c -> if (c.moveToFirst()) c.getInt(0) else -1 } ?: -1
        draft.copy(reminderMinutes = reminder)
    }

    suspend fun save(d: EventDraft): Long = withContext(Dispatchers.IO) {
        val calendarId = requireNotNull(d.calendarId) { "No calendar selected" }
        val zone = ZoneId.systemDefault()
        val (startMs, endMs, tzId) = if (d.allDay) {
            val s = d.start.toLocalDate()
            val e = maxOf(d.end.toLocalDate(), s).plusDays(1)
            Triple(
                s.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                e.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                "UTC",
            )
        } else {
            val s = d.start.atZone(zone).toInstant().toEpochMilli()
            Triple(s, maxOf(d.end.atZone(zone).toInstant().toEpochMilli(), s), TimeZone.getDefault().id)
        }
        val rrule = when {
            d.repeat == Repeat.fromRrule(d.rawRrule) && d.rawRrule != null -> d.rawRrule
            else -> d.repeat.rrule
        }
        val v = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, d.title.trim())
            put(Events.EVENT_LOCATION, d.location.trim())
            put(Events.DESCRIPTION, d.description.trim())
            put(Events.ALL_DAY, if (d.allDay) 1 else 0)
            put(Events.DTSTART, startMs)
            put(Events.EVENT_TIMEZONE, tzId)
            put(Events.HAS_ALARM, if (d.reminderMinutes >= 0) 1 else 0)
            if (rrule != null) {
                // Recurring events must carry DURATION and no DTEND, or the provider rejects them.
                put(Events.RRULE, rrule)
                putNull(Events.DTEND)
                val dur = endMs - startMs
                put(
                    Events.DURATION,
                    if (d.allDay) "P${(dur / 86_400_000L).coerceAtLeast(1)}D" else "P${dur / 1000}S",
                )
            } else {
                putNull(Events.RRULE)
                putNull(Events.DURATION)
                put(Events.DTEND, endMs)
            }
        }
        val id = if (d.eventId == null) {
            ContentUris.parseId(resolver.insert(Events.CONTENT_URI, v)!!)
        } else {
            resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, d.eventId), v, null, null)
            d.eventId
        }
        resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(id.toString()))
        if (d.reminderMinutes >= 0) {
            resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                put(Reminders.EVENT_ID, id)
                put(Reminders.MINUTES, d.reminderMinutes)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
            })
        }
        id
    }

    suspend fun delete(eventId: Long) = withContext(Dispatchers.IO) {
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
    }

    /**
     * Creates an on-device calendar for phones with no calendar account at all. Uses the
     * sync-adapter URI with ACCOUNT_TYPE_LOCAL, which is the documented way to own a calendar.
     */
    suspend fun createLocalCalendar(): Long = withContext(Dispatchers.IO) {
        val account = "Expressive Calendar"
        val uri: Uri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val v = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, account)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, "My calendar")
            put(Calendars.CALENDAR_DISPLAY_NAME, "My calendar")
            put(Calendars.CALENDAR_COLOR, 0xFF006874.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, account)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        ContentUris.parseId(resolver.insert(uri, v)!!)
    }

    private fun parseDuration(s: String?): Duration {
        if (s.isNullOrBlank()) return Duration.ofHours(1)
        // RFC 5545 durations: P1D, PT1H, P3600S, P1W …  java.time wants a T before S/M/H.
        val norm = s.trim().uppercase().let {
            when {
                it.contains('W') -> return Duration.ofDays(it.filter(Char::isDigit).toLong() * 7)
                it.contains('T') -> it
                it.endsWith('D') -> it
                else -> it.replaceFirst("P", "PT")
            }
        }
        return runCatching { Duration.parse(norm) }.getOrDefault(Duration.ofHours(1))
    }
}
