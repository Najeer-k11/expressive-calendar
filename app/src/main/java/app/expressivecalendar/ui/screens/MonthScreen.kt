package app.expressivecalendar.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.expressivecalendar.data.EventInstance
import app.expressivecalendar.data.Holiday
import app.expressivecalendar.data.HolidayKind
import app.expressivecalendar.data.WeekStart
import app.expressivecalendar.ui.EmptyDayCard
import app.expressivecalendar.ui.EventTile
import app.expressivecalendar.ui.HolidayTile
import app.expressivecalendar.ui.elasticClickable
import app.expressivecalendar.ui.formatLongDate
import app.expressivecalendar.ui.relativeDayLabel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private const val PAGE_COUNT = 2400
private const val BASE_PAGE = PAGE_COUNT / 2
private val CELL_HEIGHT = 56.dp

@Composable
fun MonthScreen(
    visibleMonth: YearMonth,
    selected: LocalDate,
    weekStart: WeekStart,
    showWeekNumbers: Boolean,
    events: Map<LocalDate, List<EventInstance>>,
    holidays: Map<LocalDate, List<Holiday>>,
    onShowMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onNewEventOn: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onHolidayClick: (Holiday) -> Unit,
    contentPadding: PaddingValues,
) {
    val baseMonth = remember { YearMonth.now() }
    fun pageOf(m: YearMonth) = BASE_PAGE + (m.year - baseMonth.year) * 12 + (m.monthValue - baseMonth.monthValue)
    fun monthOf(page: Int) = baseMonth.plusMonths((page - BASE_PAGE).toLong())

    val pager = rememberPagerState(initialPage = pageOf(visibleMonth)) { PAGE_COUNT }
    val scope = rememberCoroutineScope()

    // Pager → VM
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { onShowMonth(monthOf(it)) }
    }
    // VM → pager (Today button, picking a date in another month)
    LaunchedEffect(visibleMonth) {
        val target = pageOf(visibleMonth)
        if (pager.settledPage != target && !pager.isScrollInProgress) pager.animateScrollToPage(target)
    }

    val order = weekStart.order()
    val dayEvents = events[selected].orEmpty()
    val dayHolidays = holidays[selected].orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            MonthHeader(
                month = monthOf(pager.currentPage),
                onPrev = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                onNext = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            )
        }
        item(key = "grid") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        if (app.expressivecalendar.ui.isAppDark()) MaterialTheme.colorScheme.surfaceContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                    .padding(horizontal = 6.dp, vertical = 12.dp),
            ) {
                WeekdayLabels(order, showWeekNumbers)
                Spacer(Modifier.height(4.dp))
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT * 6),
                    beyondViewportPageCount = 1,
                ) { page ->
                    MonthGrid(
                        month = monthOf(page),
                        selected = selected,
                        firstDay = order.first(),
                        showWeekNumbers = showWeekNumbers,
                        events = events,
                        holidays = holidays,
                        onTap = { d -> if (d == selected) onOpenDay(d) else onSelect(d) },
                        onLongPress = onNewEventOn,
                    )
                }
            }
        }
        item(key = "dayHeader") {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    val fwd = targetState > initialState
                    (slideInVertically { if (fwd) it / 2 else -it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { if (fwd) -it / 2 else it / 2 } + fadeOut())
                },
                label = "dayHeader",
            ) { d ->
                Column(Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp)) {
                    Text(
                        relativeDayLabel(d),
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(formatLongDate(d), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(dayHolidays, key = { "h_${it.date}_${it.name}" }) { h ->
            HolidayTile(h, onClick = { onHolidayClick(h) }, modifier = Modifier.animateItem())
        }
        items(dayEvents, key = { "e_${it.eventId}_${it.beginMillis}" }) { e ->
            EventTile(e, selected, onClick = { onEventClick(e) }, modifier = Modifier.animateItem())
        }
        if (dayEvents.isEmpty()) {
            item(key = "empty") {
                EmptyDayCard(
                    text = if (dayHolidays.isEmpty()) "Nothing planned" else "No events of your own",
                    actionLabel = "Add event",
                    onAction = { onNewEventOn(selected) },
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val fwd = targetState > initialState
                (slideInVertically(spring(dampingRatio = .7f, stiffness = 400f)) { if (fwd) it else -it } + fadeIn()) togetherWith
                    (slideOutVertically { if (fwd) -it else it } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "monthTitle",
        ) { m ->
            Column {
                Text(
                    m.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()),
                    style = MaterialTheme.typography.displayMediumEmphasized,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                Text(
                    m.year.toString(),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        RoundNavButton(Icons.Rounded.ChevronLeft, "Previous month", onPrev)
        Spacer(Modifier.width(8.dp))
        RoundNavButton(Icons.Rounded.ChevronRight, "Next month", onNext)
    }
}

@Composable
fun RoundNavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .elasticClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun WeekdayLabels(order: List<DayOfWeek>, weekNumbers: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        if (weekNumbers) Spacer(Modifier.width(WEEK_NUM_WIDTH))
        order.forEach { d ->
            val weekend = d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY
            Text(
                d.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = if (weekend) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val WEEK_NUM_WIDTH = 26.dp

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    firstDay: DayOfWeek,
    showWeekNumbers: Boolean,
    events: Map<LocalDate, List<EventInstance>>,
    holidays: Map<LocalDate, List<Holiday>>,
    onTap: (LocalDate) -> Unit,
    onLongPress: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val lead = ((first.dayOfWeek.value - firstDay.value) + 7) % 7
    val gridStart = first.minusDays(lead.toLong())
    val today = LocalDate.now()
    val weekFields = WeekFields.of(firstDay, 4)

    Column(Modifier.fillMaxSize()) {
        for (row in 0 until 6) {
            Row(Modifier.fillMaxWidth().height(CELL_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                if (showWeekNumbers) {
                    Text(
                        gridStart.plusDays(row * 7L).get(weekFields.weekOfWeekBasedYear()).toString(),
                        modifier = Modifier.width(WEEK_NUM_WIDTH),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                for (col in 0 until 7) {
                    val day = gridStart.plusDays((row * 7 + col).toLong())
                    DayCell(
                        day = day,
                        inMonth = day.month == month.month,
                        isToday = day == today,
                        isSelected = day == selected,
                        events = events[day].orEmpty(),
                        holiday = holidays[day]?.minByOrNull { it.kind.ordinal },
                        onTap = { onTap(day) },
                        onLongPress = { onLongPress(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<EventInstance>,
    holiday: Holiday?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val publicHoliday = holiday?.kind == HolidayKind.PUBLIC
    val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY

    val textColor by animateColorAsState(
        when {
            isSelected -> cs.onPrimary
            isToday -> cs.primary
            publicHoliday -> cs.onTertiaryContainer
            weekend -> cs.tertiary
            else -> cs.onSurface
        },
        label = "dayText",
    )
    // The selection is a nine-sided cookie that springs in and turns slightly, the calendar's
    // equivalent of the clock's under-damped hand movement.
    val selScale by animateFloatAsState(
        if (isSelected) 1f else 0f,
        spring(dampingRatio = .45f, stiffness = 380f),
        label = "selScale",
    )
    val selRotation by animateFloatAsState(
        if (isSelected) 0f else -60f,
        spring(dampingRatio = .6f, stiffness = 220f),
        label = "selRot",
    )
    val cookie = MaterialShapes.Cookie9Sided.toShape()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .graphicsLayer { alpha = if (inMonth) 1f else .38f }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (holiday != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (publicHoliday) cs.tertiaryContainer else cs.tertiaryContainer.copy(alpha = .35f)),
            )
        }
        if (isToday && !isSelected) {
            Box(Modifier.size(44.dp).border(2.dp, cs.primary, CircleShape))
        }
        Box(
            Modifier
                .size(46.dp)
                .graphicsLayer {
                    scaleX = selScale; scaleY = selScale; rotationZ = selRotation
                    alpha = selScale.coerceIn(0f, 1f)
                }
                .clip(cookie)
                .background(cs.primary),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.dayOfMonth.toString(),
                style = if (isSelected || isToday) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
                color = textColor,
            )
            EventDots(events, if (isSelected) cs.onPrimary else null)
        }
    }
}

@Composable
private fun EventDots(events: List<EventInstance>, override: Color?) {
    AnimatedVisibility(events.isNotEmpty(), enter = scaleIn(tween(160)), exit = scaleOut(tween(120))) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(6.dp)) {
            events.distinctBy { it.eventId }.take(3).forEach {
                Box(Modifier.size(5.dp).clip(CircleShape).background(override ?: Color(it.color)))
            }
        }
    }
}
