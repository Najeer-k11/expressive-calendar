package app.expressivecalendar.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.expressivecalendar.data.CalendarSettings
import app.expressivecalendar.data.CalendarView
import app.expressivecalendar.data.Country
import app.expressivecalendar.data.DarkMode
import app.expressivecalendar.data.DeviceCalendar
import app.expressivecalendar.data.REMINDER_CHOICES
import app.expressivecalendar.data.WeekStart
import app.expressivecalendar.data.reminderLabel
import app.expressivecalendar.ui.ChoicePickerDialog
import app.expressivecalendar.ui.SettingIcon
import app.expressivecalendar.ui.SettingsCard
import app.expressivecalendar.ui.SettingsDivider
import app.expressivecalendar.ui.SettingsNavigateRow
import app.expressivecalendar.ui.SettingsSwitchRow
import app.expressivecalendar.ui.elasticClickable
import app.expressivecalendar.ui.theme.Palette

private enum class SettingsPicker { THEME, WEEK_START, VIEW, REMINDER, COUNTRY, CALENDARS, ABOUT }

@Composable
fun SettingsScreen(
    settings: CalendarSettings,
    calendars: List<DeviceCalendar>,
    countries: List<Country>,
    countryName: String,
    holidayStatus: String?,
    onChange: ((CalendarSettings) -> CalendarSettings) -> Unit,
    onRefreshHolidays: () -> Unit,
    onBack: () -> Unit,
) {
    var picker by remember { mutableStateOf<SettingsPicker?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .elasticClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(18.dp))
            Text("Settings", style = MaterialTheme.typography.headlineLargeEmphasized, color = MaterialTheme.colorScheme.onBackground)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsCard("Appearance") {
                SettingsNavigateRow(
                    Icons.Outlined.SettingsBrightness, "Theme", settings.theme.darkMode.label,
                    trailing = settings.theme.darkMode.label,
                ) { picker = SettingsPicker.THEME }
                SettingsDivider()
                SettingsSwitchRow(
                    Icons.Outlined.Contrast, "Pure black", "Use true black surfaces in dark mode",
                    settings.theme.amoledBlack,
                ) { v -> onChange { it.copy(theme = it.theme.copy(amoledBlack = v)) } }
                SettingsDivider()
                PalettePickerRow(
                    dark = when (settings.theme.darkMode) {
                        DarkMode.SYSTEM -> isSystemInDarkTheme()
                        DarkMode.LIGHT -> false
                        DarkMode.DARK -> true
                    },
                    dynamic = settings.theme.dynamicColor,
                    selected = settings.theme.palette,
                    onDynamic = { onChange { it.copy(theme = it.theme.copy(dynamicColor = true)) } },
                    onSelect = { p -> onChange { it.copy(theme = it.theme.copy(palette = p, dynamicColor = false)) } },
                )
            }

            SettingsCard("Calendar") {
                SettingsNavigateRow(Icons.Outlined.Dashboard, "Open on", settings.defaultView.label) { picker = SettingsPicker.VIEW }
                SettingsDivider()
                SettingsNavigateRow(Icons.Outlined.CalendarMonth, "Start week on", settings.weekStart.label) { picker = SettingsPicker.WEEK_START }
                SettingsDivider()
                SettingsSwitchRow(
                    Icons.Outlined.Numbers, "Week numbers", "Show ISO week numbers in month view",
                    settings.showWeekNumbers,
                ) { v -> onChange { it.copy(showWeekNumbers = v) } }
                SettingsDivider()
                SettingsNavigateRow(
                    Icons.Outlined.NotificationsNone, "Default reminder", reminderLabel(settings.defaultReminderMinutes),
                ) { picker = SettingsPicker.REMINDER }
                SettingsDivider()
                val shown = calendars.count { it.id !in settings.hiddenCalendarIds }
                SettingsNavigateRow(
                    Icons.Outlined.Event, "Calendars", "$shown of ${calendars.size} shown",
                ) { picker = SettingsPicker.CALENDARS }
            }

            SettingsCard("Holidays") {
                SettingsSwitchRow(
                    Icons.Outlined.Celebration, "Show holidays", "Public holidays and observances",
                    settings.showHolidays,
                ) { v -> onChange { it.copy(showHolidays = v) } }
                SettingsDivider()
                SettingsNavigateRow(
                    Icons.Outlined.Public, "Country", countryName + if (settings.holidayCountry == null) " (automatic)" else "",
                ) { picker = SettingsPicker.COUNTRY }
                SettingsDivider()
                SettingsNavigateRow(
                    Icons.Outlined.Refresh, "Refresh holidays",
                    holidayStatus ?: "Downloaded once a month, kept for offline use",
                ) { onRefreshHolidays() }
            }

            SettingsCard("About") {
                SettingsNavigateRow(Icons.Outlined.Info, "Expressive Calendar", "Design system from Expressive Clock · MIT") {
                    picker = SettingsPicker.ABOUT
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(12.dp))
        }
    }

    when (picker) {
        SettingsPicker.THEME -> ChoicePickerDialog("Theme", settings.theme.darkMode, DarkMode.entries, { it.label }, { picker = null }) { v ->
            onChange { it.copy(theme = it.theme.copy(darkMode = v)) }; picker = null
        }
        SettingsPicker.WEEK_START -> ChoicePickerDialog("Start week on", settings.weekStart, WeekStart.entries, { it.label }, { picker = null }) { v ->
            onChange { it.copy(weekStart = v) }; picker = null
        }
        SettingsPicker.VIEW -> ChoicePickerDialog("Open on", settings.defaultView, CalendarView.entries, { it.label }, { picker = null }) { v ->
            onChange { it.copy(defaultView = v) }; picker = null
        }
        SettingsPicker.REMINDER -> ChoicePickerDialog("Default reminder", settings.defaultReminderMinutes, REMINDER_CHOICES, ::reminderLabel, { picker = null }) { v ->
            onChange { it.copy(defaultReminderMinutes = v) }; picker = null
        }
        SettingsPicker.COUNTRY -> CountryPickerDialog(countries, settings.holidayCountry, { picker = null }) { code ->
            onChange { it.copy(holidayCountry = code) }; picker = null
        }
        SettingsPicker.CALENDARS -> CalendarsDialog(calendars, settings.hiddenCalendarIds, { picker = null }) { id, show ->
            onChange { s -> s.copy(hiddenCalendarIds = if (show) s.hiddenCalendarIds - id else s.hiddenCalendarIds + id) }
        }
        SettingsPicker.ABOUT -> AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text("Expressive Calendar") },
            text = {
                Text(
                    "A Material 3 Expressive calendar. Events are read from and saved to the calendars " +
                        "on your phone, so they sync with your accounts.\n\n" +
                        "Holidays: Nager.Date (date.nager.at), with Google public holiday calendars as a fallback.\n\n" +
                        "Visual design system adapted from Expressive Clock by Samuel Beckman, itself based on " +
                        "Material Clock by Rayane Bouafia (MIT). Google Sans Flex is licensed under the SIL OFL 1.1.",
                )
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text("Close") } },
        )
        null -> Unit
    }
}

@Composable
private fun CountryPickerDialog(
    countries: List<Country>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, countries) {
        if (query.isBlank()) countries
        else countries.filter { it.name.contains(query, ignoreCase = true) || it.code.equals(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Holiday country") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search countries", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (query.isBlank()) {
                        item { RadioLine("Automatic (phone region)", selected == null) { onSelect(null) } }
                    }
                    items(filtered, key = { it.code }) { c ->
                        RadioLine("${flag(c.code)}  ${c.name}", c.code == selected) { onSelect(c.code) }
                    }
                }
            }
        },
    )
}

@Composable
private fun RadioLine(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun flag(code: String): String =
    if (code.length != 2) "" else code.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')) }.joinToString("") { String(it) }

@Composable
private fun CalendarsDialog(
    calendars: List<DeviceCalendar>,
    hidden: Set<Long>,
    onDismiss: () -> Unit,
    onToggle: (Long, Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calendars") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        text = {
            if (calendars.isEmpty()) {
                Text("No calendars on this phone yet. Add an account in system settings, or create one when you add your first event.")
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    calendars.groupBy { it.account }.forEach { (account, list) ->
                        item(key = "a_$account") {
                            Text(
                                account.ifBlank { "Phone" },
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(list, key = { it.id }) { c ->
                            val shown = c.id !in hidden
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onToggle(c.id, !shown) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = shown, onCheckedChange = { onToggle(c.id, it) })
                                Box(Modifier.size(14.dp).clip(CircleShape).background(Color(c.color)))
                                Spacer(Modifier.width(12.dp))
                                Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun PalettePickerRow(
    dark: Boolean,
    dynamic: Boolean,
    selected: Palette,
    onDynamic: () -> Unit,
    onSelect: (Palette) -> Unit,
) {
    val context = LocalContext.current
    val wallpaper = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) androidx.compose.material3.dynamicDarkColorScheme(context)
        else androidx.compose.material3.dynamicLightColorScheme(context)
    } else null

    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingIcon(Icons.Outlined.Palette)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Colour theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("Personalise the look of the app", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 76.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (wallpaper != null) PaletteSwatch("Wallpaper", wallpaper.primary, dynamic, onDynamic)
            Palette.entries.forEach { p ->
                PaletteSwatch(p.displayName, if (dark) p.dark.primary else p.light.primary, !dynamic && p == selected) { onSelect(p) }
            }
        }
    }
}

@Composable
private fun PaletteSwatch(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp)) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color)
                .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                .elasticClickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, "Selected $name theme", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}
