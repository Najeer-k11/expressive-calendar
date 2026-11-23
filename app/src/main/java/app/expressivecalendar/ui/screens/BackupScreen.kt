package app.expressivecalendar.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.expressivecalendar.data.DeviceCalendar
import app.expressivecalendar.data.IcsEvent
import app.expressivecalendar.data.ImportResult
import app.expressivecalendar.ui.luminousElasticClickable
import kotlinx.coroutines.launch
import java.time.LocalDate

private val TileShape = RoundedCornerShape(24.dp)

/** A message under an action: what happened, good or bad. */
private data class Outcome(val text: String, val ok: Boolean)

/**
 * Settings → Backup. Export chosen calendars to an .ics file and import one back into any
 * writable calendar. Works with files from Google Calendar, Thunderbird, Etar and others.
 */
@Composable
fun BackupScreen(
    calendars: List<DeviceCalendar>,
    onExport: suspend (Set<Long>, Uri) -> Result<Int>,
    onRead: suspend (Uri) -> Result<List<IcsEvent>>,
    onImport: suspend (List<IcsEvent>, Long) -> Result<ImportResult>,
    onCreateLocalCalendar: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Export: every visible calendar is ticked to start with.
    var exportIds by remember { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(calendars) {
        if (exportIds == null && calendars.isNotEmpty()) exportIds = calendars.filter { it.visible }.map { it.id }.toSet()
    }
    val chosen = exportIds.orEmpty()
    var exportOutcome by remember { mutableStateOf<Outcome?>(null) }
    var busy by remember { mutableStateOf(false) }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            exportOutcome = onExport(chosen, uri).fold(
                { n -> Outcome("Saved $n ${if (n == 1) "event" else "events"} to ${fileName(context, uri)}", true) },
                { Outcome("Couldn't save the file: ${it.message ?: "unknown error"}", false) },
            )
            busy = false
        }
    }

    // Import: pick a file, preview it, choose a calendar, confirm.
    val writable = calendars.filter { it.writable }
    var file by remember { mutableStateOf<Pair<String, List<IcsEvent>>?>(null) }
    var target by remember { mutableStateOf<Long?>(null) }
    if (target == null || writable.none { it.id == target }) target = (writable.firstOrNull { it.visible } ?: writable.firstOrNull())?.id
    var importOutcome by remember { mutableStateOf<Outcome?>(null) }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        importOutcome = null
        scope.launch {
            onRead(uri).fold(
                { events ->
                    if (events.isEmpty()) {
                        file = null
                        importOutcome = Outcome("No events found in ${fileName(context, uri)}", false)
                    } else {
                        file = fileName(context, uri) to events
                    }
                },
                { file = null; importOutcome = Outcome("That file couldn't be read as a calendar (.ics)", false) },
            )
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Backup",
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.Check, "Done", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Section("Export")
            Blurb("Save your events to an .ics file. Keep it somewhere safe, or open it in any other calendar app.")
            Spacer(Modifier.height(6.dp))
            if (calendars.isEmpty()) Blurb("There are no calendars on this phone yet.")
            calendars.forEach { cal ->
                SelectRow(
                    leading = { CalendarDot(Color(cal.color)) },
                    title = cal.name,
                    subtitle = cal.account.ifBlank { null },
                    selected = cal.id in chosen,
                ) { exportIds = if (cal.id in chosen) chosen - cal.id else chosen + cal.id }
            }
            Spacer(Modifier.height(12.dp))
            PillButton(
                text = if (chosen.size == 1) "Export 1 calendar" else "Export ${chosen.size} calendars",
                icon = Icons.Outlined.FileDownload,
                primary = true,
                enabled = chosen.isNotEmpty() && !busy,
            ) {
                exportOutcome = null
                createDoc.launch("calendar-backup-${LocalDate.now()}.ics")
            }
            OutcomeLine(exportOutcome)

            Section("Import")
            Blurb("Add events from an .ics file. Importing the same file again won't create duplicates.")
            Spacer(Modifier.height(12.dp))
            PillButton("Choose a file", Icons.Outlined.FileUpload, primary = false, enabled = !busy) {
                openDoc.launch(arrayOf("text/calendar", "text/x-vcalendar", "application/ics", "application/octet-stream", "text/plain"))
            }

            AnimatedVisibility(
                visible = file != null,
                enter = expandVertically(spring(dampingRatio = .8f, stiffness = 380f)) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                val (name, events) = file ?: ("" to emptyList())
                Column(Modifier.padding(top = 14.dp)) {
                    FoundTile(name, events)
                    Spacer(Modifier.height(16.dp))
                    if (writable.isEmpty()) {
                        Blurb("There's no calendar to add them to yet.")
                        Spacer(Modifier.height(10.dp))
                        PillButton("Create phone calendar", null, primary = false, enabled = true, onClick = onCreateLocalCalendar)
                    } else {
                        Text(
                            "Add to",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            writable.forEach { cal ->
                                FilterChip(
                                    selected = target == cal.id,
                                    onClick = { target = cal.id },
                                    label = { Text(cal.name) },
                                    leadingIcon = {
                                        Box(Modifier.size(FilterChipDefaults.IconSize).clip(CircleShape).background(Color(cal.color)))
                                    },
                                    shape = CircleShape,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        val count = events.count { it.recurrenceId == null }
                        PillButton(
                            if (count == 1) "Import 1 event" else "Import $count events",
                            Icons.Rounded.Check, primary = true, enabled = target != null && !busy,
                        ) {
                            val cal = target ?: return@PillButton
                            busy = true
                            scope.launch {
                                importOutcome = onImport(events, cal).fold(
                                    { r -> Outcome(importSummary(r), r.failed == 0); },
                                    { Outcome("Import failed: ${it.message ?: "unknown error"}", false) },
                                )
                                if (importOutcome?.ok == true) file = null
                                busy = false
                            }
                        }
                    }
                }
            }
            OutcomeLine(importOutcome)

            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

private fun importSummary(r: ImportResult): String = buildList {
    add(if (r.imported == 1) "Imported 1 event" else "Imported ${r.imported} events")
    if (r.alreadyThere > 0) add("${r.alreadyThere} already there")
    if (r.failed > 0) add("${r.failed} couldn't be added")
}.joinToString(" · ")

private fun fileName(context: android.content.Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "the file"

@Composable
private fun Blurb(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The calendar's own colour, as an accent on a small cookie. */
@Composable
private fun CalendarDot(color: Color) {
    Box(Modifier.size(22.dp).clip(MaterialShapes.Cookie4Sided.toShape()).background(color))
}

/** What the chosen file holds, with a burst that pops in to mark the find. */
@Composable
private fun FoundTile(name: String, events: List<IcsEvent>) {
    val series = events.filter { it.recurrenceId == null }
    val repeating = series.count { it.rrule != null }
    val pop = remember(name) { Animatable(0f) }
    LaunchedEffect(name) { pop.animateTo(1f, spring(dampingRatio = .45f, stiffness = 380f)) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = (1 - pop.value) * -60f }
                .clip(MaterialShapes.SoftBurst.toShape())
                .background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (series.size > 999) "999+" else series.size.toString(),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.onSecondary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (series.size == 1) "1 event found" else "${series.size} events found",
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                listOfNotNull(name, if (repeating > 0) "$repeating repeating" else null).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .8f),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun PillButton(text: String, icon: ImageVector?, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else .45f)
            .clip(CircleShape)
            .background(bg, CircleShape)
            .luminousElasticClickable(enabled = enabled, shape = CircleShape, maxStretchPercent = .03f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = fg)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLargeEmphasized, color = fg)
    }
}

@Composable
private fun OutcomeLine(outcome: Outcome?) {
    AnimatedVisibility(outcome != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val o = outcome ?: return@AnimatedVisibility
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (o.ok) Icons.Rounded.Check else Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp),
                tint = if (o.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                o.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (o.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
    }
}
