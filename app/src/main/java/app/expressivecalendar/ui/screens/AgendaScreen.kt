package app.expressivecalendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.expressivecalendar.data.EventInstance
import app.expressivecalendar.data.Holiday
import app.expressivecalendar.ui.EmptyDayCard
import app.expressivecalendar.ui.EventTile
import app.expressivecalendar.ui.HolidayTile
import app.expressivecalendar.ui.elasticClickable
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private const val AGENDA_DAYS = 180L

@Composable
fun AgendaScreen(
    events: Map<LocalDate, List<EventInstance>>,
    holidays: Map<LocalDate, List<Holiday>>,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onHolidayClick: (Holiday) -> Unit,
    onNewEvent: () -> Unit,
    contentPadding: PaddingValues,
) {
    val today = remember { LocalDate.now() }
    val days = remember(events, holidays) {
        (0 until AGENDA_DAYS).map { today.plusDays(it) }
            .filter { it == today || !events[it].isNullOrEmpty() || !holidays[it].isNullOrEmpty() }
    }
    val state = rememberLazyListState()

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title") {
            Column(Modifier.padding(start = 8.dp, bottom = 6.dp)) {
                Text("Agenda", style = MaterialTheme.typography.displayMediumEmphasized, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Next six months",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        var lastMonth: YearMonth? = YearMonth.from(today)
        days.forEach { day ->
            val m = YearMonth.from(day)
            if (m != lastMonth) {
                lastMonth = m
                item(key = "m_$m") {
                    Text(
                        m.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) +
                            if (m.year != today.year) " ${m.year}" else "",
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 2.dp).animateItem(),
                    )
                }
            }
            item(key = "d_$day") {
                AgendaDay(
                    day = day,
                    isToday = day == today,
                    events = events[day].orEmpty(),
                    holidays = holidays[day].orEmpty(),
                    onDayClick = { onDayClick(day) },
                    onEventClick = onEventClick,
                    onHolidayClick = onHolidayClick,
                    onNewEvent = onNewEvent,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun AgendaDay(
    day: LocalDate,
    isToday: Boolean,
    events: List<EventInstance>,
    holidays: List<Holiday>,
    onDayClick: () -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onHolidayClick: (Holiday) -> Unit,
    onNewEvent: () -> Unit,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // The date badge: a big numeral, with today set in a sunny shape.
        Column(
            Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .elasticClickable(onClick = onDayClick)
                .padding(top = 4.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = if (isToday) cs.primary else cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .size(52.dp)
                    .clip(if (isToday) MaterialShapes.Sunny.toShape() else MaterialShapes.Circle.toShape())
                    .background(if (isToday) cs.primary else androidx.compose.ui.graphics.Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = if (isToday) cs.onPrimary else cs.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            holidays.forEach { h -> HolidayTile(h, onClick = { onHolidayClick(h) }) }
            events.forEach { e -> EventTile(e, day, onClick = { onEventClick(e) }) }
            if (events.isEmpty() && holidays.isEmpty()) {
                EmptyDayCard("Nothing planned today", "Add event", onNewEvent)
            }
        }
    }
}
