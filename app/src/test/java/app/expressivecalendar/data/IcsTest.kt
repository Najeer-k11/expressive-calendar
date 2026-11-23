package app.expressivecalendar.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IcsTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()
    private fun day(y: Int, mo: Int, d: Int) =
        LocalDate.of(y, mo, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun roundTripKeepsEverything() {
        val events = listOf(
            IcsEvent(
                uid = "a@x", title = "Team sync; weekly, with \"quotes\"\nline two",
                location = "Room 4, floor 2", description = "Bring\\notes",
                start = at(berlin, 2026, 3, 2, 10), end = at(berlin, 2026, 3, 2, 11),
                timeZone = "Europe/Berlin", rrule = "FREQ=WEEKLY;BYDAY=MO",
                exdates = listOf(at(berlin, 2026, 3, 16, 10)), reminders = listOf(10, 0),
            ),
            IcsEvent(
                uid = "b@x", title = "Holiday trip", allDay = true,
                start = day(2026, 7, 1), end = day(2026, 7, 4), reminders = listOf(1440),
            ),
            IcsEvent(
                uid = "a@x", title = "Team sync (moved)", start = at(berlin, 2026, 3, 10, 14),
                end = at(berlin, 2026, 3, 10, 15), timeZone = "Europe/Berlin",
                recurrenceId = at(berlin, 2026, 3, 9, 10),
            ),
        )
        val parsed = Ics.parse(Ics.write(events), ZoneId.of("Asia/Kolkata"))
        assertEquals(events.map { it.copy(reminders = it.reminders.sorted()) }, parsed.map { it.copy(reminders = it.reminders.sorted()) })
    }

    @Test fun repeatingEventsStayOnWallClockAcrossDst() {
        // 10:00 Berlin in winter; written as TZID + local time, not as a UTC instant.
        val ics = Ics.write(listOf(IcsEvent(uid = "w", title = "Standup", start = at(berlin, 2026, 1, 5, 10), end = at(berlin, 2026, 1, 5, 10, 15), timeZone = "Europe/Berlin", rrule = "FREQ=DAILY")))
        assertTrue("DTSTART;TZID=Europe/Berlin:20260105T100000" in ics, ics)
    }

    @Test fun readsGoogleExportWithVtimezoneAndFolding() {
        val ics = """
            BEGIN:VCALENDAR
            PRODID:-//Google Inc//Google Calendar 70.9054//EN
            VERSION:2.0
            BEGIN:VTIMEZONE
            TZID:Europe/Berlin
            BEGIN:STANDARD
            DTSTART:19701025T030000
            TZOFFSETFROM:+0200
            TZOFFSETTO:+0100
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            DTSTART;TZID=Europe/Berlin:20261201T090000
            DTEND;TZID=Europe/Berlin:20261201T093000
            RRULE:FREQ=WEEKLY;BYDAY=TU
            EXDATE;TZID=Europe/Berlin:20261208T090000,20261215T090000
            UID:abc123@google.com
            SUMMARY:A very long title that goes on and on so that Google has to fold th
             e line in two
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-P0DT0H30M0S
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")
        val e = Ics.parse(ics, ZoneOffset.UTC).single()
        assertEquals("A very long title that goes on and on so that Google has to fold the line in two", e.title)
        assertEquals(at(berlin, 2026, 12, 1, 9), e.start)
        assertEquals(at(berlin, 2026, 12, 1, 9, 30), e.end)
        assertEquals("Europe/Berlin", e.timeZone)
        assertEquals(listOf(at(berlin, 2026, 12, 8, 9), at(berlin, 2026, 12, 15, 9)), e.exdates)
        assertEquals(listOf(30), e.reminders)
    }

    @Test fun outlookZoneNamesAndDurationsFallBack() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:o1
            DTSTART;TZID="Pacific Standard Time":20261105T080000
            DURATION:PT1H30M
            SUMMARY:Call
            END:VEVENT
            BEGIN:VEVENT
            UID:o2
            DTSTART;VALUE=DATE:20261224
            SUMMARY:Eve
            END:VEVENT
            BEGIN:VTODO
            UID:t1
            DTSTART:20261105T080000Z
            SUMMARY:Not an event
            END:VTODO
            END:VCALENDAR
        """.trimIndent()
        val (call, eve) = Ics.parse(ics, berlin)
        assertEquals(at(berlin, 2026, 11, 5, 8), call.start) // unknown zone -> default zone
        assertEquals(call.start + 90 * 60_000, call.end)
        assertEquals(true, eve.allDay)
        assertEquals(day(2026, 12, 25), eve.end) // all-day with no end lasts one day
        assertEquals(2, Ics.parse(ics, berlin).size) // VTODO ignored
    }

    @Test fun cancelledOccurrencesBecomeExdates() {
        val series = IcsEvent(uid = "s", title = "Gym", start = 0, end = 3_600_000, rrule = "FREQ=DAILY")
        val gone = IcsEvent(uid = "s", title = "Gym", start = 86_400_000, end = 90_000_000, recurrenceId = 86_400_000, cancelled = true)
        val folded = Ics.foldCancellations(listOf(series, gone))
        assertEquals(listOf(series.copy(exdates = listOf(86_400_000L))), folded)
    }

    @Test fun editedOccurrencesDetachFromTheirSeries() {
        val series = IcsEvent(uid = "s", title = "Sync", start = 0, end = 3_600_000, rrule = "FREQ=DAILY")
        val moved = IcsEvent(uid = "s", title = "Sync (late)", start = 90_000_000, end = 93_600_000, recurrenceId = 86_400_000)
        val orphan = IcsEvent(uid = "o", title = "Lone", start = 5, end = 6, recurrenceId = 1)
        val out = Ics.detachOccurrences(listOf(series, moved, orphan))
        assertEquals(
            listOf(
                series.copy(exdates = listOf(86_400_000L)),
                moved.copy(uid = "s/86400000", recurrenceId = null),
                orphan.copy(uid = "o/1", recurrenceId = null),
            ),
            out,
        )
    }

    @Test fun providerDateLists() {
        val utc = Ics.parseProviderDates("20261201T100000Z,20261208T100000Z", berlin)
        assertEquals(listOf(at(ZoneOffset.UTC, 2026, 12, 1, 10), at(ZoneOffset.UTC, 2026, 12, 8, 10)), utc)
        assertEquals(listOf(at(berlin, 2026, 12, 1, 10)), Ics.parseProviderDates("Europe/Berlin;20261201T100000", ZoneOffset.UTC))
        assertEquals("20261201T100000Z,20261208T100000Z", Ics.formatProviderDates(utc.reversed()))
        assertNull(Ics.formatProviderDates(emptyList()))
    }

    @Test fun durations() {
        assertEquals(-900L, Ics.parseDuration("-PT15M"))
        assertEquals(86_400L * 8, Ics.parseDuration("P1W1D"))
        assertEquals(5_400L, Ics.parseDuration("PT1H30M"))
        assertNull(Ics.parseDuration("15 minutes"))
    }

    @Test fun longLinesFoldAt75Octets() {
        val ics = Ics.write(listOf(IcsEvent(uid = "f", title = "Ünïcödé ".repeat(30), start = 0, end = 0)))
        ics.split("\r\n").forEach { assertTrue(it.toByteArray().size <= 75, "too long: $it") }
        assertEquals("Ünïcödé ".repeat(30).trim(), Ics.parse(ics, ZoneOffset.UTC).single().title)
    }
}
