package app.expressivecalendar.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.expressivecalendar.data.EventInstance
import app.expressivecalendar.data.Holiday
import app.expressivecalendar.data.HolidayKind
import app.expressivecalendar.data.WeekStart
import app.expressivecalendar.ui.elasticClickable
import app.expressivecalendar.ui.formatTime
import app.expressivecalendar.ui.is24h
import app.expressivecalendar.ui.luminousElasticClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val DAY_PAGES = 20000
private const val DAY_BASE = DAY_PAGES / 2
private val HOUR_HEIGHT = 64.dp
private val GUTTER = 52.dp

@Composable
fun DayScreen(
    selected: LocalDate,
    weekStart: WeekStart,
    events: Map<LocalDate, List<EventInstance>>,
    holidays: Map<LocalDate, List<Holiday>>,
    onSelect: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onHolidayClick: (Holiday) -> Unit,
    onNewEventAt: (LocalDateTime) -> Unit,
    contentPadding: PaddingValues,
) {
    val base = remember { LocalDate.now() }
    fun dayOf(page: Int) = base.plusDays((page - DAY_BASE).toLong())
    fun pageOf(d: LocalDate) = DAY_BASE + ChronoUnit.DAYS.between(base, d).toInt()
    val pager = rememberPagerState(initialPage = pageOf(selected)) { DAY_PAGES }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { onSelect(dayOf(it)) }
    }
    LaunchedEffect(selected) {
        val target = pageOf(selected)
        if (pager.settledPage != target && !pager.isScrollInProgress) pager.animateScrollToPage(target)
    }

    Column(Modifier.fillMaxSize().padding(contentPadding.let {
        PaddingValues(top = it.calculateTopPadding())
    })) {
        // Header: month + year, with day stepping.
        val shown = dayOf(pager.currentPage)
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    shown.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "${shown.dayOfMonth} " + YearMonth.from(shown).month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " ${shown.year}",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            RoundNavButton(Icons.Rounded.ChevronLeft, "Previous day") { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
            Spacer(Modifier.width(8.dp))
            RoundNavButton(Icons.Rounded.ChevronRight, "Next day") { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
        }
        Spacer(Modifier.height(14.dp))
        WeekStrip(shown, weekStart, events, holidays, onSelect)
        Spacer(Modifier.height(10.dp))

        HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
            val day = dayOf(page)
            DayPage(
                day = day,
                events = events[day].orEmpty(),
                holidays = holidays[day].orEmpty(),
                onEventClick = onEventClick,
                onHolidayClick = onHolidayClick,
                onNewEventAt = onNewEventAt,
                bottomPadding = contentPadding.calculateBottomPadding(),
            )
        }
    }
}

@Composable
private fun WeekStrip(
    selected: LocalDate,
    weekStart: WeekStart,
    events: Map<LocalDate, List<EventInstance>>,
    holidays: Map<LocalDate, List<Holiday>>,
    onSelect: (LocalDate) -> Unit,
) {
    val first = weekStart.firstDay()
    val start = selected.minusDays(((selected.dayOfWeek.value - first.value + 7) % 7).toLong())
    val today = LocalDate.now()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (0 until 7).forEach { i ->
            val d = start.plusDays(i.toLong())
            val isSel = d == selected
            val holiday = holidays[d]?.any { it.kind == HolidayKind.PUBLIC } == true
            val cs = MaterialTheme.colorScheme
            val bg by animateColorAsState(
                when {
                    isSel -> cs.primary
                    holiday -> cs.tertiaryContainer
                    else -> cs.surfaceContainerHigh
                },
                label = "chipBg",
            )
            val fg = when {
                isSel -> cs.onPrimary
                d == today -> cs.primary
                holiday -> cs.onTertiaryContainer
                else -> cs.onSurface
            }
            // Selected chip grows taller and rounder, like the expressive day chips on alarms.
            val radius by animateDpAsState(if (isSel) 26.dp else 16.dp, spring(dampingRatio = .55f, stiffness = 500f), label = "r")
            Column(
                Modifier
                    .weight(1f)
                    .height(if (isSel) 72.dp else 64.dp)
                    .clip(RoundedCornerShape(radius))
                    .background(bg)
                    .elasticClickable { onSelect(d) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2),
                    style = MaterialTheme.typography.labelMedium,
                    color = fg.copy(alpha = .8f),
                )
                Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.titleMediumEmphasized, color = fg)
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (events[d].isNullOrEmpty()) Color.Transparent else fg),
                )
            }
        }
    }
}

@Composable
private fun DayPage(
    day: LocalDate,
    events: List<EventInstance>,
    holidays: List<Holiday>,
    onEventClick: (EventInstance) -> Unit,
    onHolidayClick: (Holiday) -> Unit,
    onNewEventAt: (LocalDateTime) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val allDay = events.filter { it.allDay || (it.start.toLocalDate() < day && it.end.toLocalDate() > day) }
    val timed = events - allDay.toSet()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val h24 = is24h()

    // Open on "now" for today, 8 AM otherwise.
    LaunchedEffect(day) {
        val hour = if (day == LocalDate.now()) (LocalTime.now().hour - 1).coerceAtLeast(0) else 8
        scroll.scrollTo(with(density) { (HOUR_HEIGHT * hour).roundToPx() })
    }

    Column(Modifier.fillMaxSize()) {
        if (holidays.isNotEmpty() || allDay.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                holidays.forEach { h ->
                    AllDayChip(
                        title = h.name,
                        container = if (h.kind == HolidayKind.PUBLIC) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = if (h.kind == HolidayKind.PUBLIC) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        holiday = true,
                        onClick = { onHolidayClick(h) },
                    )
                }
                allDay.forEach { e ->
                    AllDayChip(
                        title = e.title,
                        container = Color(e.color).copy(alpha = .22f),
                        content = MaterialTheme.colorScheme.onSurface,
                        holiday = false,
                        onClick = { onEventClick(e) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.weight(1f).verticalScroll(scroll)) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(HOUR_HEIGHT * 24 + bottomPadding)
                    .pointerInput(day) {
                        detectTapGestures(onLongPress = { off ->
                            val minutes = (off.y / HOUR_HEIGHT.toPx() * 60).toInt().coerceIn(0, 23 * 60 + 30)
                            onNewEventAt(day.atTime(minutes / 60, (minutes % 60) / 30 * 30))
                        })
                    },
            ) {
                // Hour grid
                for (h in 0 until 24) {
                    Row(Modifier.offset(y = HOUR_HEIGHT * h).fillMaxWidth().height(HOUR_HEIGHT)) {
                        Text(
                            if (h == 0) "" else formatTime(LocalTime.of(h, 0), h24).replace(":00", ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(GUTTER).offset(y = (-8).dp).padding(start = 12.dp),
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
                        )
                    }
                }

                // Events, laid out in columns where they overlap.
                val laneWidth = maxWidth - GUTTER - 20.dp
                layoutLanes(timed, day).forEach { (e, lane, lanes) ->
                    val startMin = if (e.start.toLocalDate() < day) 0 else e.start.hour * 60 + e.start.minute
                    val endMin = if (e.end.toLocalDate() > day) 24 * 60 else e.end.hour * 60 + e.end.minute
                    val top = HOUR_HEIGHT * (startMin / 60f)
                    val height = (HOUR_HEIGHT * ((endMin - startMin).coerceAtLeast(20) / 60f)) - 2.dp
                    val w = laneWidth / lanes
                    TimelineEvent(
                        e = e, h24 = h24,
                        modifier = Modifier
                            .offset(x = GUTTER + w * lane, y = top)
                            .width(w - 3.dp)
                            .height(height),
                        onClick = { onEventClick(e) },
                    )
                }

                // Now line
                if (day == LocalDate.now()) {
                    var now by remember { mutableStateOf(LocalTime.now()) }
                    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalTime.now() } }
                    val y = HOUR_HEIGHT * ((now.hour * 60 + now.minute) / 60f)
                    Box(Modifier.offset(x = GUTTER - 7.dp, y = y - 7.dp).size(14.dp).clip(MaterialShapes.Cookie4Sided.toShape()).background(MaterialTheme.colorScheme.primary))
                    Box(
                        Modifier
                            .offset(x = GUTTER, y = y - 1.dp)
                            .width(maxWidth - GUTTER - 16.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDayChip(title: String, container: Color, content: Color, holiday: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .luminousElasticClickable(shape = RoundedCornerShape(16.dp), maxStretchPercent = .02f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (holiday) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.Celebration, null,
                Modifier.padding(end = 8.dp).size(18.dp), tint = content,
            )
        }
        Text(title, style = MaterialTheme.typography.labelLargeEmphasized, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TimelineEvent(e: EventInstance, h24: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = Color(e.color)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(accent.copy(alpha = .22f))
            .border(1.dp, accent.copy(alpha = .5f), shape)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(e.title, style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            "${formatTime(e.start.toLocalTime(), h24)} – ${formatTime(e.end.toLocalTime(), h24)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Greedy column assignment for overlapping events. Returns (event, lane, laneCountInCluster). */
private fun layoutLanes(events: List<EventInstance>, day: LocalDate): List<Triple<EventInstance, Int, Int>> {
    fun s(e: EventInstance) = if (e.start.toLocalDate() < day) day.atStartOfDay() else e.start
    fun en(e: EventInstance) = maxOf(if (e.end.toLocalDate() > day) day.plusDays(1).atStartOfDay() else e.end, s(e).plusMinutes(20))
    val sorted = events.sortedWith(compareBy({ s(it) }, { en(it) }))
    val out = mutableListOf<Triple<EventInstance, Int, Int>>()
    var cluster = mutableListOf<Pair<EventInstance, Int>>()
    val laneEnds = mutableListOf<LocalDateTime>()
    var clusterEnd: LocalDateTime? = null
    fun flush() {
        val n = (cluster.maxOfOrNull { it.second } ?: -1) + 1
        cluster.forEach { out += Triple(it.first, it.second, n) }
        cluster = mutableListOf(); laneEnds.clear()
    }
    for (e in sorted) {
        if (clusterEnd != null && s(e) >= clusterEnd) flush()
        var lane = laneEnds.indexOfFirst { it <= s(e) }
        if (lane == -1) { laneEnds += en(e); lane = laneEnds.lastIndex } else laneEnds[lane] = en(e)
        cluster += e to lane
        clusterEnd = if (clusterEnd == null || en(e) > clusterEnd) en(e) else clusterEnd
    }
    flush()
    return out
}
