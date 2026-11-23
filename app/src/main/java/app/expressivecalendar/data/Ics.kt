package app.expressivecalendar.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * One VEVENT, in the calendar provider's terms (epoch millis; all-day = UTC midnight).
 * Pure Kotlin so the iCalendar reading/writing can be unit-tested off-device.
 */
data class IcsEvent(
    val uid: String,
    val title: String,
    val location: String? = null,
    val description: String? = null,
    val allDay: Boolean = false,
    val start: Long,
    /** Exclusive end. */
    val end: Long,
    /** IANA zone the times are anchored to; "UTC" for all-day events. */
    val timeZone: String = "UTC",
    val rrule: String? = null,
    val exdates: List<Long> = emptyList(),
    /** Minutes before start. */
    val reminders: List<Int> = emptyList(),
    /** Set on an edited occurrence of a recurring event: the original start of that occurrence. */
    val recurrenceId: Long? = null,
    val cancelled: Boolean = false,
)

/** RFC 5545 iCalendar (.ics) export and import. */
object Ics {
    private val DATE = DateTimeFormatter.BASIC_ISO_DATE
    private val LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    // -----------------------------------------------------------------------------------------
    // Writing
    // -----------------------------------------------------------------------------------------

    fun write(events: List<IcsEvent>, now: Instant = Instant.now()): String {
        val out = StringBuilder()
        fun line(s: String) = out.append(fold(s)).append("\r\n")
        val stamp = LOCAL.format(LocalDateTime.ofInstant(now, ZoneOffset.UTC)) + "Z"
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:-//Expressive Calendar//EN")
        line("CALSCALE:GREGORIAN")
        for (e in events) {
            line("BEGIN:VEVENT")
            line("UID:${escape(e.uid)}")
            line("DTSTAMP:$stamp")
            if (e.recurrenceId != null) line("RECURRENCE-ID${dateProp(e.recurrenceId, e.allDay, e.timeZone)}")
            line("DTSTART${dateProp(e.start, e.allDay, e.timeZone)}")
            line("DTEND${dateProp(maxOf(e.end, e.start), e.allDay, e.timeZone)}")
            line("SUMMARY:${escape(e.title)}")
            e.location?.takeIf { it.isNotBlank() }?.let { line("LOCATION:${escape(it)}") }
            e.description?.takeIf { it.isNotBlank() }?.let { line("DESCRIPTION:${escape(it)}") }
            e.rrule?.takeIf { it.isNotBlank() }?.let { line("RRULE:$it") }
            if (e.exdates.isNotEmpty()) {
                // One EXDATE line per value keeps every line's parameters exact (VALUE/TZID).
                e.exdates.sorted().forEach { line("EXDATE${dateProp(it, e.allDay, e.timeZone)}") }
            }
            if (e.cancelled) line("STATUS:CANCELLED")
            for (m in e.reminders.distinct().sorted()) {
                line("BEGIN:VALARM")
                line("ACTION:DISPLAY")
                line("DESCRIPTION:${escape(e.title)}")
                line("TRIGGER:${if (m == 0) "PT0M" else "-PT${m}M"}")
                line("END:VALARM")
            }
            line("END:VEVENT")
        }
        line("END:VCALENDAR")
        return out.toString()
    }

    /** `;VALUE=DATE:20261225`, `:20261225T100000Z` or `;TZID=Europe/Berlin:20261225T100000`. */
    private fun dateProp(millis: Long, allDay: Boolean, zone: String): String {
        if (allDay) return ";VALUE=DATE:" + DATE.format(LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC))
        val z = zoneOrNull(zone)
        if (z == null || z.id == "UTC" || z.id == "Z" || z == ZoneOffset.UTC) {
            return ":" + LOCAL.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)) + "Z"
        }
        // Local time + zone keeps repeating events on the same wall-clock time across DST.
        return ";TZID=${z.id}:" + LOCAL.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), z))
    }

    private fun escape(s: String) = s
        .replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
        .replace("\r\n", "\\n").replace("\n", "\\n")

    /** Lines longer than 75 octets continue on the next line after a space (RFC 5545 §3.1). */
    private fun fold(s: String): String {
        if (s.toByteArray(Charsets.UTF_8).size <= 75) return s
        val sb = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val chunk = String(Character.toChars(cp))
            val n = chunk.toByteArray(Charsets.UTF_8).size
            if (bytes + n > 75) {
                sb.append("\r\n ")
                bytes = 1
            }
            sb.append(chunk)
            bytes += n
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    // -----------------------------------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------------------------------

    private class Prop(val name: String, val params: Map<String, String>, val value: String)

    /**
     * Parses every VEVENT in [text]. Times without a zone are read in [defaultZone]; unknown
     * TZIDs (e.g. Outlook's "Pacific Standard Time") fall back to it too.
     */
    fun parse(text: String, defaultZone: ZoneId = ZoneId.systemDefault()): List<IcsEvent> {
        val lines = text.replace("\r\n", "\n").replace("\r", "\n")
            .replace(Regex("\n[ \t]"), "")
            .split('\n').filter { it.isNotBlank() }
        val events = mutableListOf<IcsEvent>()
        var props: MutableList<Prop>? = null
        var alarms = mutableListOf<List<Prop>>()
        var alarm: MutableList<Prop>? = null
        var skipDepth = 0 // inside components we ignore (VTIMEZONE, VTODO…)
        for (raw in lines) {
            val p = parseLine(raw) ?: continue
            when {
                p.name == "BEGIN" && p.value.equals("VEVENT", true) && skipDepth == 0 -> {
                    props = mutableListOf(); alarms = mutableListOf()
                }
                p.name == "END" && p.value.equals("VEVENT", true) && props != null -> {
                    toEvent(props, alarms, defaultZone)?.let { events += it }
                    props = null
                }
                p.name == "BEGIN" && p.value.equals("VALARM", true) && props != null -> alarm = mutableListOf()
                p.name == "END" && p.value.equals("VALARM", true) && alarm != null -> {
                    alarms += alarm; alarm = null
                }
                p.name == "BEGIN" && props == null && !p.value.equals("VCALENDAR", true) -> skipDepth++
                p.name == "END" && props == null && !p.value.equals("VCALENDAR", true) -> skipDepth = maxOf(0, skipDepth - 1)
                alarm != null -> alarm.add(p)
                props != null -> props.add(p)
            }
        }
        return events
    }

    private fun parseLine(line: String): Prop? {
        // NAME;PARAM=a;PARAM="quoted:colon":value — the first colon outside quotes ends the head.
        var inQuote = false
        var colon = -1
        for ((i, c) in line.withIndex()) {
            if (c == '"') inQuote = !inQuote
            else if (c == ':' && !inQuote) { colon = i; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        val parts = splitOutsideQuotes(head, ';')
        val params = parts.drop(1).mapNotNull { kv ->
            val eq = kv.indexOf('=')
            if (eq <= 0) null else kv.substring(0, eq).uppercase() to kv.substring(eq + 1).trim('"')
        }.toMap()
        return Prop(parts[0].uppercase(), params, line.substring(colon + 1))
    }

    private fun splitOutsideQuotes(s: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        var inQuote = false
        val cur = StringBuilder()
        for (c in s) {
            when {
                c == '"' -> { inQuote = !inQuote; cur.append(c) }
                c == sep && !inQuote -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
        }
        out += cur.toString()
        return out
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    private class When(val millis: Long, val allDay: Boolean, val zone: ZoneId)

    private fun parseWhen(value: String, params: Map<String, String>, defaultZone: ZoneId): When? = runCatching {
        val v = value.trim()
        if (params["VALUE"].equals("DATE", true) || v.length == 8) {
            val d = LocalDate.parse(v.take(8), DATE)
            return When(d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), true, ZoneOffset.UTC)
        }
        val local = LocalDateTime.parse(v.take(15), LOCAL)
        val zone: ZoneId = when {
            v.endsWith("Z", ignoreCase = true) -> ZoneOffset.UTC
            params["TZID"] != null -> zoneOrNull(params["TZID"]!!) ?: defaultZone
            else -> defaultZone
        }
        When(local.atZone(zone).toInstant().toEpochMilli(), false, zone)
    }.getOrNull()

    /** IANA zone from a TZID, tolerating prefixes like "/mozilla.org/20050126_1/Europe/Berlin". */
    private fun zoneOrNull(tzid: String): ZoneId? {
        val t = tzid.trim()
        runCatching { return ZoneId.of(t) }
        val ids = ZoneId.getAvailableZoneIds()
        return ids.filter { t.endsWith(it) }.maxByOrNull { it.length }?.let { ZoneId.of(it) }
    }

    /** RFC 5545 duration (e.g. -PT15M, P1D, P1W, PT1H30M) in seconds; null if malformed. */
    fun parseDuration(s: String): Long? {
        val m = Regex("""^([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""")
            .matchEntire(s.trim().uppercase()) ?: return null
        val (sign, w, d, h, min, sec) = m.destructured
        val total = (w.toLongOrNull() ?: 0) * 604_800 + (d.toLongOrNull() ?: 0) * 86_400 +
            (h.toLongOrNull() ?: 0) * 3_600 + (min.toLongOrNull() ?: 0) * 60 + (sec.toLongOrNull() ?: 0)
        return if (sign == "-") -total else total
    }

    private fun toEvent(props: List<Prop>, alarms: List<List<Prop>>, defaultZone: ZoneId): IcsEvent? {
        fun first(name: String) = props.firstOrNull { it.name == name }
        val startProp = first("DTSTART") ?: return null
        val start = parseWhen(startProp.value, startProp.params, defaultZone) ?: return null
        val end: Long = first("DTEND")?.let { parseWhen(it.value, it.params, defaultZone)?.millis }
            ?: first("DURATION")?.let { parseDuration(it.value)?.let { s -> start.millis + s * 1000 } }
            ?: (if (start.allDay) start.millis + 86_400_000L else start.millis)
        val exdates = props.filter { it.name == "EXDATE" }.flatMap { p ->
            p.value.split(',').mapNotNull { parseWhen(it, p.params, defaultZone)?.millis }
        }
        val reminders = alarms.mapNotNull { a ->
            val trig = a.firstOrNull { it.name == "TRIGGER" } ?: return@mapNotNull null
            if (trig.params["VALUE"].equals("DATE-TIME", true)) {
                val at = parseWhen(trig.value, trig.params, defaultZone)?.millis ?: return@mapNotNull null
                ((start.millis - at) / 60_000).toInt().takeIf { it >= 0 }
            } else {
                val secs = parseDuration(trig.value) ?: return@mapNotNull null
                // Relative to the end is rare; treat it like start (closest sensible reminder).
                (-secs / 60).toInt().takeIf { it >= 0 }
            }
        }
        val recurrence = first("RECURRENCE-ID")?.let { parseWhen(it.value, it.params, defaultZone)?.millis }
        return IcsEvent(
            uid = first("UID")?.value?.let(::unescape)?.ifBlank { null }
                ?: "import-${start.millis}-${first("SUMMARY")?.value.hashCode()}",
            title = first("SUMMARY")?.value?.let(::unescape)?.trim().orEmpty(),
            location = first("LOCATION")?.value?.let(::unescape)?.trim()?.ifBlank { null },
            description = first("DESCRIPTION")?.value?.let(::unescape)?.trim()?.ifBlank { null },
            allDay = start.allDay,
            start = start.millis,
            end = maxOf(end, start.millis),
            timeZone = if (start.allDay) "UTC" else start.zone.id.let { if (it == "Z") "UTC" else it },
            rrule = first("RRULE")?.value?.trim()?.ifBlank { null },
            exdates = exdates.distinct(),
            reminders = reminders.distinct(),
            recurrenceId = recurrence,
            cancelled = first("STATUS")?.value.equals("CANCELLED", true),
        )
    }

    /**
     * Cancelled occurrences ("this event was deleted on 3 Dec") become EXDATEs on their series,
     * which is how the calendar provider stores them most reliably.
     */
    fun foldCancellations(events: List<IcsEvent>): List<IcsEvent> {
        val cancelled = events.filter { it.recurrenceId != null && it.cancelled }.groupBy { it.uid }
        if (cancelled.isEmpty()) return events
        return events.mapNotNull { e ->
            when {
                e.recurrenceId != null && e.cancelled -> null
                e.recurrenceId == null && e.rrule != null && cancelled[e.uid] != null ->
                    e.copy(exdates = (e.exdates + cancelled[e.uid]!!.mapNotNull { it.recurrenceId }).distinct())
                else -> e
            }
        }
    }

    /**
     * Turns each edited occurrence into a standalone event and excludes its original date from
     * the series. On phone-only (local) calendars the provider stops listing a series' instances
     * once it has a linked exception, so this is the shape that reliably shows up. The standalone
     * copy gets a derived UID ("<series uid>/<original start>") so re-imports still de-duplicate.
     * Occurrences whose series isn't in [events] are kept as standalone events too.
     */
    fun detachOccurrences(events: List<IcsEvent>): List<IcsEvent> {
        val occurrences = events.filter { it.recurrenceId != null }
        if (occurrences.isEmpty()) return events
        val byUid = occurrences.groupBy { it.uid }
        return events.filter { it.recurrenceId == null }.map { e ->
            val moved = byUid[e.uid]
            if (e.rrule == null || moved == null) e
            else e.copy(exdates = (e.exdates + moved.mapNotNull { it.recurrenceId }).distinct())
        } + occurrences.map { it.copy(uid = "${it.uid}/${it.recurrenceId}", recurrenceId = null) }
    }

    /** Provider EXDATE/RDATE strings: "20261201T100000Z,20261208T100000Z", optionally "TZID;…". */
    fun parseProviderDates(s: String?, defaultZone: ZoneId): List<Long> {
        if (s.isNullOrBlank()) return emptyList()
        val (params, values) = if (';' in s && s.indexOf(';') < (s.indexOf(',').takeIf { it >= 0 } ?: s.length)) {
            mapOf("TZID" to s.substringBefore(';')) to s.substringAfter(';')
        } else emptyMap<String, String>() to s
        return values.split(',').mapNotNull { parseWhen(it, params, defaultZone)?.millis }
    }

    /** The provider's EXDATE format: comma-separated UTC date-times. */
    fun formatProviderDates(millis: List<Long>): String? =
        millis.distinct().sorted().takeIf { it.isNotEmpty() }?.joinToString(",") {
            LOCAL.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC)) + "Z"
        }

    /** Duration string the provider expects on recurring events. */
    fun providerDuration(startMs: Long, endMs: Long, allDay: Boolean): String {
        val d = Duration.ofMillis(maxOf(0, endMs - startMs))
        return if (allDay) "P${d.toDays().coerceAtLeast(1)}D" else "P${d.seconds}S"
    }
}
