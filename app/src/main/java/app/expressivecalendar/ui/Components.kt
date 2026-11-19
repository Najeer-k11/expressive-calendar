package app.expressivecalendar.ui

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.expressivecalendar.data.EventInstance
import app.expressivecalendar.data.Holiday
import app.expressivecalendar.data.HolidayKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------------------------

@Composable
fun is24h(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

fun formatTime(t: LocalTime, h24: Boolean): String =
    t.format(DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm a", Locale.getDefault()))

fun formatShortDate(d: LocalDate): String =
    d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))

fun formatLongDate(d: LocalDate): String =
    d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))

fun relativeDayLabel(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
}

fun eventTimeLabel(e: EventInstance, day: LocalDate, h24: Boolean): String {
    if (e.allDay) {
        return if (e.firstDay == e.lastDay) "All day"
        else "All day · day ${day.toEpochDay() - e.firstDay.toEpochDay() + 1} of ${e.lastDay.toEpochDay() - e.firstDay.toEpochDay() + 1}"
    }
    val startsToday = e.start.toLocalDate() == day
    val endsToday = e.end.toLocalDate() == day || (e.end.toLocalTime() == LocalTime.MIDNIGHT && e.end.toLocalDate() == day.plusDays(1))
    return when {
        startsToday && endsToday -> "${formatTime(e.start.toLocalTime(), h24)} – ${formatTime(e.end.toLocalTime(), h24)}"
        startsToday -> "From ${formatTime(e.start.toLocalTime(), h24)}"
        endsToday -> "Until ${formatTime(e.end.toLocalTime(), h24)}"
        else -> "All day"
    }
}

fun formatRange(start: LocalDateTime, end: LocalDateTime, allDay: Boolean, h24: Boolean): String {
    return if (allDay) {
        val last = if (end > start) end.minusNanos(1).toLocalDate() else start.toLocalDate()
        if (last == start.toLocalDate()) formatLongDate(start.toLocalDate())
        else "${formatShortDate(start.toLocalDate())} – ${formatShortDate(last)}"
    } else if (start.toLocalDate() == end.toLocalDate()) {
        "${formatLongDate(start.toLocalDate())}\n${formatTime(start.toLocalTime(), h24)} – ${formatTime(end.toLocalTime(), h24)}"
    } else {
        "${formatShortDate(start.toLocalDate())}, ${formatTime(start.toLocalTime(), h24)} – " +
            "${formatShortDate(end.toLocalDate())}, ${formatTime(end.toLocalTime(), h24)}"
    }
}

// ---------------------------------------------------------------------------------------------
// Sheet header and settings-style rows (ported from Expressive Clock)
// ---------------------------------------------------------------------------------------------

@Composable
fun SheetHeaderButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .10f)
                    primary -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = .70f)
                },
            )
            .luminousElasticClickable(enabled = enabled, shape = RoundedCornerShape(18.dp), onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
            primary -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLargeEmphasized, color = fg)
    }
}

@Composable
fun SettingIcon(icon: ImageVector, tint: Color? = null) {
    val dark = MaterialTheme.colorScheme.surface.luminanceIsDark()
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                tint?.copy(alpha = if (dark) .9f else .22f)
                    ?: if (dark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                tint != null && dark -> Color.White
                tint != null -> tint
                dark -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
fun <T> ChoicePickerDialog(
    title: String,
    value: T,
    options: List<T>,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = { onSelect(option) })
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == value, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Calendar tiles
// ---------------------------------------------------------------------------------------------

private val TileShape = RoundedCornerShape(24.dp)

/** An event as a tonal tile, the calendar's equivalent of the clock's alarm tile. */
@Composable
fun EventTile(
    event: EventInstance,
    day: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val h24 = is24h()
    val now = LocalDateTime.now()
    val past = event.end < now && !event.allDay
    val container by animateColorAsState(
        if (MaterialTheme.colorScheme.surface.luminanceIsDark()) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "eventTile",
    )
    val accent = Color(event.color)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(container.copy(alpha = .94f), TileShape)
            .border(1.25.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f), TileShape)
            .luminousElasticClickable(shape = TileShape, maxStretchPercent = .03f, maxTranslateDp = 1.2.dp, onClick = onClick)
            .padding(start = 14.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)
            .then(if (past) Modifier.alphaFade(.6f) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(6.dp)
                .height(44.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                event.title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    eventTimeLabel(event, day, h24),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (event.recurring) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Repeat, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            event.location?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Holidays use the tertiary family so they read as "not yours" at a glance. */
@Composable
fun HolidayTile(holiday: Holiday, countryName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val public = holiday.kind == HolidayKind.PUBLIC
    val bg = if (public) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (public) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(bg, TileShape)
            .luminousElasticClickable(shape = TileShape, maxStretchPercent = .03f, maxTranslateDp = 1.2.dp, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(if (public) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Celebration, null, Modifier.size(22.dp),
                tint = if (public) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(holiday.name, style = MaterialTheme.typography.titleMediumEmphasized, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                (if (public) "Public holiday" else "Observance") + " · " + countryName,
                style = MaterialTheme.typography.bodyMedium,
                color = fg.copy(alpha = .75f),
            )
        }
    }
}

@Composable
fun EmptyDayCard(text: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .border(1.25.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f), TileShape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        SheetHeaderButton(text = actionLabel, primary = false, onClick = onAction)
    }
}

@Composable
fun AccentBar(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.width(4.dp).fillMaxHeight().clip(CircleShape).background(color))
}

fun Color.luminanceIsDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

fun Modifier.alphaFade(alpha: Float): Modifier = this.alpha(alpha)

@Composable
fun isAppDark(): Boolean = MaterialTheme.colorScheme.surface.luminanceIsDark()
