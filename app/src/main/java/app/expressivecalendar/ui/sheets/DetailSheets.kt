package app.expressivecalendar.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.expressivecalendar.data.DeviceCalendar
import app.expressivecalendar.data.EventInstance
import app.expressivecalendar.data.Holiday
import app.expressivecalendar.data.HolidayKind
import app.expressivecalendar.data.countryName
import app.expressivecalendar.data.flagEmoji
import app.expressivecalendar.ui.SettingIcon
import app.expressivecalendar.ui.SheetHeaderButton
import app.expressivecalendar.ui.formatLongDate
import app.expressivecalendar.ui.formatRange
import app.expressivecalendar.ui.is24h
import app.expressivecalendar.ui.luminousElasticClickable

val SheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

@Composable
fun EventDetailSheet(
    event: EventInstance,
    calendar: DeviceCalendar?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }
    val h24 = is24h()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, shape = SheetShape) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(MaterialShapes.Cookie7Sided.toShape())
                        .background(Color(event.color)),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    event.title,
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(18.dp))
            DetailGroup {
                DetailRow(Icons.Outlined.Schedule, formatRange(event.start, event.end, event.allDay, h24))
                if (event.recurring) DetailRow(Icons.Outlined.Repeat, "Repeating event")
                event.location?.let { DetailRow(Icons.Outlined.LocationOn, it) }
                calendar?.let { DetailRow(Icons.Outlined.Event, it.name + if (it.account.isNotBlank() && it.account != it.name) "\n${it.account}" else "", Color(it.color)) }
                event.description?.let { DetailRow(Icons.AutoMirrored.Outlined.Notes, it) }
            }
            Spacer(Modifier.height(18.dp))
            if (calendar?.writable != false) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .luminousElasticClickable(shape = RoundedCornerShape(22.dp), onClick = { confirmDelete = true }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Row(
                        Modifier
                            .weight(1.4f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .luminousElasticClickable(shape = RoundedCornerShape(22.dp), onClick = onEdit),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Edit", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            } else {
                Text(
                    "This calendar is read-only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete event?") },
            text = {
                Text(
                    if (event.recurring) "This deletes every occurrence of “${event.title}”."
                    else "“${event.title}” will be removed from ${calendar?.name ?: "your calendar"}.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun HolidayDetailSheet(holiday: Holiday, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val public = holiday.kind == HolidayKind.PUBLIC
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, shape = SheetShape) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(MaterialShapes.SoftBurst.toShape())
                    .background(if (public) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Celebration, null, Modifier.size(42.dp),
                    tint = if (public) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(holiday.name, style = MaterialTheme.typography.headlineMediumEmphasized, color = MaterialTheme.colorScheme.onSurface)
            if (holiday.localName != holiday.name) {
                Text(holiday.localName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(18.dp))
            DetailGroup {
                DetailRow(Icons.Outlined.Schedule, formatLongDate(holiday.date))
                DetailRow(Icons.Outlined.Public, holiday.countries.joinToString("\n") { "${flagEmoji(it)}  ${countryName(it)}" })
                DetailRow(Icons.Outlined.Celebration, if (public) "Public holiday" else "Observance")
            }
            Spacer(Modifier.height(12.dp))
            SheetHeaderButton("Close", primary = false, onClick = onDismiss)
        }
    }
}

@Composable
private fun DetailGroup(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 6.dp),
    ) { content() }
}

@Composable
private fun DetailRow(icon: ImageVector, text: String, tint: Color? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon, tint)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
