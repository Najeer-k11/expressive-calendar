package app.expressivecalendar.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.expressivecalendar.BuildConfig
import app.expressivecalendar.data.CalendarSettings
import app.expressivecalendar.data.CalendarView
import app.expressivecalendar.data.Country
import app.expressivecalendar.data.DarkMode
import app.expressivecalendar.data.DeviceCalendar
import app.expressivecalendar.data.IcsEvent
import app.expressivecalendar.data.ImportResult
import app.expressivecalendar.data.REMINDER_CHOICES
import app.expressivecalendar.data.WeekStart
import app.expressivecalendar.data.countryName
import app.expressivecalendar.data.flagEmoji
import app.expressivecalendar.data.reminderLabel
import app.expressivecalendar.ui.ChoicePickerDialog
import app.expressivecalendar.ui.elasticClickable
import app.expressivecalendar.ui.theme.Palette
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private enum class SettingsPicker { REMINDER, CALENDARS, ABOUT }

/**
 * Settings, laid out around a live preview: the mini month at the top re-renders with the chosen
 * colours, week start and week numbers, and most choices are made inline rather than in dialogs.
 */
@Composable
fun SettingsScreen(
    settings: CalendarSettings,
    calendars: List<DeviceCalendar>,
    countries: List<Country>,
    holidayStatus: String?,
    notificationsOn: Boolean,
    otherReminderApp: String?,
    onNotificationsChange: (Boolean) -> Unit,
    onChange: ((CalendarSettings) -> CalendarSettings) -> Unit,
    onRefreshHolidays: () -> Unit,
    onExport: suspend (Set<Long>, Uri) -> Result<Int>,
    onReadIcs: suspend (Uri) -> Result<List<IcsEvent>>,
    onImport: suspend (List<IcsEvent>, Long) -> Result<ImportResult>,
    onCreateLocalCalendar: () -> Unit,
    onBack: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.HOME) }
    BackHandler(enabled = page != SettingsPage.HOME) { page = SettingsPage.HOME }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (targetState != SettingsPage.HOME) {
                slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { it } togetherWith
                    slideOutHorizontally(tween(420, easing = FastOutSlowInEasing)) { -it / 8 } + fadeOut(tween(300))
            } else {
                slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 8 } + fadeIn(tween(200)) togetherWith
                    slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it }
            }
        },
        label = "settingsPage",
    ) { current ->
        when (current) {
            SettingsPage.COUNTRIES -> HolidayCountriesScreen(
                countries = countries,
                selected = settings.holidayCountries,
                onChange = { codes -> onChange { it.copy(holidayCountries = codes) } },
                onBack = { page = SettingsPage.HOME },
            )
            SettingsPage.BACKUP -> BackupScreen(
                calendars = calendars,
                onExport = onExport,
                onRead = onReadIcs,
                onImport = onImport,
                onCreateLocalCalendar = onCreateLocalCalendar,
                onBack = { page = SettingsPage.HOME },
            )
            SettingsPage.HOME -> SettingsHome(
                settings, calendars, holidayStatus, notificationsOn, otherReminderApp,
                onNotificationsChange, onChange, onRefreshHolidays,
                onOpenCountries = { page = SettingsPage.COUNTRIES },
                onOpenBackup = { page = SettingsPage.BACKUP },
                onBack = onBack,
            )
        }
    }
}

private enum class SettingsPage { HOME, COUNTRIES, BACKUP }

@Composable
private fun SettingsHome(
    settings: CalendarSettings,
    calendars: List<DeviceCalendar>,
    holidayStatus: String?,
    notificationsOn: Boolean,
    otherReminderApp: String?,
    onNotificationsChange: (Boolean) -> Unit,
    onChange: ((CalendarSettings) -> CalendarSettings) -> Unit,
    onRefreshHolidays: () -> Unit,
    onOpenCountries: () -> Unit,
    onOpenBackup: () -> Unit,
    onBack: () -> Unit,
) {
    var picker by remember { mutableStateOf<SettingsPicker?>(null) }
    val dark = when (settings.theme.darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.Close, "Close settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            MonthPreview(settings.weekStart, settings.showWeekNumbers)
            Spacer(Modifier.height(16.dp))
            PaletteShapes(
                dark = dark,
                dynamic = settings.theme.dynamicColor,
                selected = settings.theme.palette,
                onDynamic = { onChange { it.copy(theme = it.theme.copy(dynamicColor = true)) } },
                onSelect = { p -> onChange { it.copy(theme = it.theme.copy(palette = p, dynamicColor = false)) } },
            )

            Spacer(Modifier.height(20.dp))
            Label("Mode")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DarkMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = settings.theme.darkMode == mode,
                        onClick = { onChange { it.copy(theme = it.theme.copy(darkMode = mode)) } },
                        shape = SegmentedButtonDefaults.itemShape(i, DarkMode.entries.size),
                    ) { Text(if (mode == DarkMode.SYSTEM) "Auto" else mode.label) }
                }
            }
            ToggleLine("Pure black", "True black surfaces in dark mode", settings.theme.amoledBlack) { v ->
                onChange { it.copy(theme = it.theme.copy(amoledBlack = v)) }
            }

            Section("Calendar")
            Label("Week starts on")
            ChipLine(WeekStart.entries, settings.weekStart, { if (it == WeekStart.SYSTEM) "Auto" else it.label.take(3) }) { v ->
                onChange { it.copy(weekStart = v) }
            }
            Spacer(Modifier.height(8.dp))
            Label("Opens on")
            ChipLine(CalendarView.entries, settings.defaultView, { it.label }) { v -> onChange { it.copy(defaultView = v) } }
            ToggleLine("Week numbers", "ISO weeks beside the month", settings.showWeekNumbers) { v ->
                onChange { it.copy(showWeekNumbers = v) }
            }
            ValueLine("Default reminder", reminderLabel(settings.defaultReminderMinutes)) { picker = SettingsPicker.REMINDER }
            ToggleLine(
                "Reminder notifications",
                when {
                    notificationsOn -> "Alert me when a reminder is due"
                    settings.eventNotifications ?: (otherReminderApp == null) ->
                        "Notifications are blocked · tap to allow"
                    otherReminderApp != null -> "Off · another app ($otherReminderApp) shows them"
                    else -> "Reminders are saved but won't alert"
                },
                notificationsOn,
                onNotificationsChange,
            )
            val shown = calendars.count { it.id !in settings.hiddenCalendarIds }
            ValueLine("Calendars", "$shown of ${calendars.size}") { picker = SettingsPicker.CALENDARS }

            Section("Holidays")
            ToggleLine(
                "Public holidays",
                settings.effectiveCountries.joinToString("  ") { flagEmoji(it) } + "  " +
                    settings.effectiveCountries.joinToString { countryName(it) } +
                    if (settings.holidayCountries.isEmpty()) " · automatic" else "",
                settings.showHolidays,
            ) { v -> onChange { it.copy(showHolidays = v) } }
            AnimatedVisibility(
                settings.showHolidays,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    ValueLine(
                        if (settings.effectiveCountries.size > 1) "Countries" else "Country",
                        settings.effectiveCountries.singleOrNull()?.let { countryName(it) }
                            ?: "${settings.effectiveCountries.size} countries",
                        onClick = onOpenCountries,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onRefreshHolidays)
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Refresh holidays", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                holidayStatus ?: "Downloaded once a month, kept for offline use",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.Refresh, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Section("Backup")
            ValueLine("Import & export", ".ics file", onClick = onOpenBackup)

            Spacer(Modifier.height(28.dp))
            Text(
                "Expressive Calendar ${BuildConfig.VERSION_NAME}  ·  About & credits",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { picker = SettingsPicker.ABOUT }
                    .padding(vertical = 12.dp),
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(12.dp))
        }
    }

    when (picker) {
        SettingsPicker.REMINDER -> ChoicePickerDialog("Default reminder", settings.defaultReminderMinutes, REMINDER_CHOICES, ::reminderLabel, { picker = null }) { v ->
            onChange { it.copy(defaultReminderMinutes = v) }; picker = null
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

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

@Composable
internal fun Section(title: String) {
    Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f).height(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .18f)))
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun <T> ChipLine(options: List<T>, value: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = option == value
            FilterChip(
                selected = on,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                leadingIcon = if (on) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
                shape = CircleShape,
            )
        }
    }
}

@Composable
private fun ToggleLine(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ValueLine(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** A miniature of the month view in the current colours, reacting to week start and week numbers. */
@Composable
private fun MonthPreview(weekStart: WeekStart, weekNumbers: Boolean) {
    val today = remember { LocalDate.now() }
    val order = weekStart.order()
    val first = today.withDayOfMonth(1)
    val lead = order.indexOf(first.dayOfWeek)
    val start = first.minusDays(lead.toLong())
    val rows = (lead + today.lengthOfMonth() + 6) / 7
    val cookie = MaterialShapes.Cookie9Sided.toShape()
    val cs = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(cs.primaryContainer)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = cs.onPrimaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text("${today.year}", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer.copy(alpha = .6f))
            Spacer(Modifier.weight(1f))
            Text("Preview", style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer.copy(alpha = .6f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            if (weekNumbers) Spacer(Modifier.width(26.dp))
            order.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onPrimaryContainer.copy(alpha = .65f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        repeat(rows) { r ->
            val weekFirst = start.plusDays(r * 7L)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (weekNumbers) {
                    Text(
                        "${weekFirst.plusDays(3).get(WeekFields.ISO.weekOfWeekBasedYear())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.primary,
                        modifier = Modifier.width(26.dp),
                    )
                }
                repeat(7) { c ->
                    val day = weekFirst.plusDays(c.toLong())
                    val inMonth = day.month == today.month
                    val isToday = day == today
                    Box(Modifier.weight(1f).aspectRatio(1.25f), contentAlignment = Alignment.Center) {
                        if (isToday) Box(Modifier.size(30.dp).clip(cookie).background(cs.primary))
                        Text(
                            "${day.dayOfMonth}",
                            style = MaterialTheme.typography.labelLarge,
                            color = when {
                                isToday -> cs.onPrimary
                                inMonth -> cs.onPrimaryContainer
                                else -> cs.onPrimaryContainer.copy(alpha = .3f)
                            },
                        )
                    }
                }
            }
        }
    }
}

private val SwatchShapes = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Pentagon,
    MaterialShapes.Cookie7Sided, MaterialShapes.SoftBurst, MaterialShapes.Gem, MaterialShapes.Flower,
    MaterialShapes.Cookie12Sided, MaterialShapes.Puffy,
)

/** Colour themes as a strip of expressive shapes; the chosen one grows and spins into place. */
@Composable
private fun PaletteShapes(
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
    val shapes = SwatchShapes.map { it.toShape() }

    val name = if (dynamic && wallpaper != null) "Wallpaper" else selected.displayName
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label("Colour")
        Spacer(Modifier.weight(1f))
        Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var i = 0
        if (wallpaper != null) {
            ShapeSwatch("Wallpaper", wallpaper.primary, wallpaper.tertiaryContainer, shapes[i++ % shapes.size], dynamic, onDynamic)
        }
        Palette.entries.forEach { p ->
            val s = if (dark) p.dark else p.light
            ShapeSwatch(p.displayName, s.primary, s.primaryContainer, shapes[i++ % shapes.size], !dynamic && p == selected) { onSelect(p) }
        }
    }
}

@Composable
private fun ShapeSwatch(name: String, color: Color, ring: Color, shape: Shape, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.78f, spring(Spring.DampingRatioMediumBouncy), label = "swatchScale")
    val turn by animateFloatAsState(if (selected) 60f else 0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow), label = "swatchTurn")
    Box(
        Modifier
            .size(58.dp)
            .semantics {
                contentDescription = "$name colour theme"
                this.selected = selected
                role = Role.RadioButton
            }
            .elasticClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(58.dp).graphicsLayer { rotationZ = turn }.clip(shape).background(ring))
        Box(
            Modifier
                .size(46.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = turn }
                .clip(shape)
                .background(color),
        )
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

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

