package app.expressivecalendar.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.expressivecalendar.data.DeviceCalendar
import app.expressivecalendar.data.EventDraft
import app.expressivecalendar.data.REMINDER_CHOICES
import app.expressivecalendar.data.Repeat
import app.expressivecalendar.data.reminderLabel
import app.expressivecalendar.ui.ChoicePickerDialog
import app.expressivecalendar.ui.SettingIcon
import app.expressivecalendar.ui.SheetHeaderButton
import app.expressivecalendar.ui.elasticClickable
import app.expressivecalendar.ui.formatShortDate
import app.expressivecalendar.ui.formatTime
import app.expressivecalendar.ui.is24h
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

private enum class Picker { START_DATE, START_TIME, END_DATE, END_TIME, REPEAT, REMINDER, CALENDAR }

@Composable
fun EventEditorSheet(
    initial: EventDraft,
    calendars: List<DeviceCalendar>,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
    onCreateLocalCalendar: () -> Unit,
) {
    val writable = calendars.filter { it.writable }
    var d by remember(initial) {
        mutableStateOf(
            initial.copy(calendarId = initial.calendarId ?: writable.firstOrNull { it.visible }?.id ?: writable.firstOrNull()?.id),
        )
    }
    // Pick up a calendar once one appears (e.g. after creating a local one).
    if (d.calendarId == null && writable.isNotEmpty()) d = d.copy(calendarId = writable.first().id)

    var picker by remember { mutableStateOf<Picker?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val h24 = is24h()
    val canSave = d.title.isNotBlank() && d.calendarId != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, shape = SheetShape) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 22.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SheetHeaderButton("Cancel", primary = false, onClick = onDismiss)
                Text(
                    if (d.eventId == null) "New event" else "Edit event",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                SheetHeaderButton("Save", primary = true, icon = Icons.Rounded.Check, enabled = canSave, onClick = { onSave(d) })
            }
            Spacer(Modifier.height(14.dp))

            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Title: big, borderless, the one thing on the sheet that matters most.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .055f))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    if (d.title.isEmpty()) {
                        Text("Add title", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f))
                    }
                    BasicTextField(
                        value = d.title,
                        onValueChange = { d = d.copy(title = it) },
                        textStyle = MaterialTheme.typography.headlineMediumEmphasized.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (writable.isEmpty()) {
                    NoCalendarCard(onCreateLocalCalendar)
                }

                Group {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .elasticClickable { d = d.copy(allDay = !d.allDay) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingIcon(Icons.Outlined.WbSunny)
                        Spacer(Modifier.width(14.dp))
                        Text("All day", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(checked = d.allDay, onCheckedChange = { d = d.copy(allDay = it) })
                    }
                    DateTimeRow("Starts", d.start, d.allDay, h24, { picker = Picker.START_DATE }, { picker = Picker.START_TIME })
                    DateTimeRow("Ends", d.end, d.allDay, h24, { picker = Picker.END_DATE }, { picker = Picker.END_TIME })
                }

                Group {
                    NavRow(Icons.Outlined.Repeat, "Repeat", d.repeat.label) { picker = Picker.REPEAT }
                    NavRow(Icons.Outlined.NotificationsNone, "Reminder", reminderLabel(d.reminderMinutes)) { picker = Picker.REMINDER }
                    val cal = calendars.firstOrNull { it.id == d.calendarId }
                    NavRow(
                        Icons.Outlined.Event, "Calendar", cal?.let { it.name + if (it.account != it.name) " · ${it.account}" else "" } ?: "None",
                        tint = cal?.let { Color(it.color) },
                    ) { if (writable.isNotEmpty()) picker = Picker.CALENDAR }
                }

                Group {
                    FieldRow(Icons.Outlined.LocationOn, "Add location", d.location) { d = d.copy(location = it) }
                    FieldRow(Icons.AutoMirrored.Outlined.Notes, "Add notes", d.description, singleLine = false) { d = d.copy(description = it) }
                }
            }
        }
    }

    when (picker) {
        Picker.START_DATE, Picker.END_DATE -> {
            val isStart = picker == Picker.START_DATE
            val current = (if (isStart) d.start else d.end).toLocalDate()
            val ds = rememberDatePickerState(initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        ds.selectedDateMillis?.let { ms ->
                            val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            d = if (isStart) d.withStart(d.start.with(date)) else d.withEnd(d.end.with(date))
                        }
                        picker = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text("Cancel") } },
            ) { DatePicker(state = ds) }
        }
        Picker.START_TIME, Picker.END_TIME -> {
            val isStart = picker == Picker.START_TIME
            val current = (if (isStart) d.start else d.end).toLocalTime()
            val ts = rememberTimePickerState(current.hour, current.minute, h24)
            TimePickerDialog(
                onDismissRequest = { picker = null },
                title = { Text(if (isStart) "Start time" else "End time") },
                confirmButton = {
                    TextButton(onClick = {
                        val t = LocalTime.of(ts.hour, ts.minute)
                        d = if (isStart) d.withStart(d.start.with(t)) else d.withEnd(d.end.with(t))
                        picker = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text("Cancel") } },
            ) { TimePicker(state = ts) }
        }
        Picker.REPEAT -> ChoicePickerDialog("Repeat", d.repeat, Repeat.entries, { it.label }, { picker = null }) {
            d = d.copy(repeat = it); picker = null
        }
        Picker.REMINDER -> ChoicePickerDialog("Reminder", d.reminderMinutes, REMINDER_CHOICES, ::reminderLabel, { picker = null }) {
            d = d.copy(reminderMinutes = it); picker = null
        }
        Picker.CALENDAR -> ChoicePickerDialog(
            "Calendar",
            writable.firstOrNull { it.id == d.calendarId },
            writable,
            { c -> c?.let { it.name + if (it.account != it.name) " · ${it.account}" else "" }.orEmpty() },
            { picker = null },
        ) { d = d.copy(calendarId = it?.id); picker = null }
        null -> Unit
    }
}

/** Moving the start keeps the duration, like every calendar app does. */
private fun EventDraft.withStart(newStart: LocalDateTime): EventDraft {
    val dur = Duration.between(start, end)
    return copy(start = newStart, end = newStart.plus(dur))
}

private fun EventDraft.withEnd(newEnd: LocalDateTime): EventDraft =
    if (newEnd < start) copy(end = start.plusHours(if (allDay) 0 else 1)) else copy(end = newEnd)

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 6.dp),
    ) { content() }
}

@Composable
private fun DateTimeRow(
    label: String,
    value: LocalDateTime,
    allDay: Boolean,
    h24: Boolean,
    onDate: () -> Unit,
    onTime: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 74.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Pill(formatShortDate(value.toLocalDate()), onDate)
        AnimatedVisibility(
            !allDay,
            enter = expandHorizontally(spring(dampingRatio = .7f, stiffness = 500f)) + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Pill(formatTime(value.toLocalTime(), h24), onTime)
            }
        }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .elasticClickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, value: String, tint: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .elasticClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FieldRow(icon: ImageVector, hint: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        SettingIcon(icon)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f).padding(top = if (singleLine) 0.dp else 12.dp)) {
            val style: TextStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
            if (value.isEmpty()) Text(hint, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f)))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 2,
                textStyle = style,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NoCalendarCard(onCreate: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(18.dp),
    ) {
        Text("No calendar to save to", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.onErrorContainer)
        Text(
            "Add a Google account in system settings, or keep events on this phone only.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.height(10.dp))
        SheetHeaderButton("Create phone calendar", primary = true, onClick = onCreate)
    }
}

fun newDraftFor(date: LocalDate, reminder: Int, at: LocalTime? = null): EventDraft {
    val now = LocalTime.now()
    val startTime = at ?: LocalTime.of(((now.hour + 1).coerceAtMost(23)), 0)
    val start = date.atTime(startTime)
    return EventDraft(start = start, end = start.plusHours(1), reminderMinutes = reminder)
}
