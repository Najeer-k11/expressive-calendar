package app.expressivecalendar.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expressivecalendar.data.Country
import app.expressivecalendar.data.countryName
import app.expressivecalendar.data.flagEmoji
import app.expressivecalendar.ui.elasticClickable
import app.expressivecalendar.ui.luminousElasticClickable
import java.util.Locale
import kotlinx.coroutines.launch

private val RowShape = RoundedCornerShape(20.dp)
private val BadgeTapShape = RoundedCornerShape(18.dp)

/** Badge shapes for chosen countries, cycled so a row of them reads as a playful set. */
@Composable
private fun badgeShape(index: Int): Shape = when (index % 5) {
    0 -> MaterialShapes.Cookie9Sided
    1 -> MaterialShapes.Sunny
    2 -> MaterialShapes.Cookie7Sided
    3 -> MaterialShapes.SoftBurst
    else -> MaterialShapes.Cookie12Sided
}.toShape()

/**
 * Settings → Holidays → Countries. Any number of countries; none means "follow the phone's
 * region". Like the rest of Settings, every change applies immediately.
 */
@Composable
fun HolidayCountriesScreen(
    countries: List<Country>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Local copy so rapid taps never race the async settings write.
    var chosen by remember { mutableStateOf(selected) }
    fun update(codes: Set<String>) { chosen = codes; onChange(codes) }

    // Countries chosen when the screen opened come first; fixed so rows don't jump while ticking.
    val ordered = remember(countries) { countries.sortedBy { it.code !in selected } }
    val filtered = remember(query, ordered) {
        if (query.isBlank()) ordered
        else ordered.filter { it.name.contains(query, ignoreCase = true) || it.code.equals(query, ignoreCase = true) }
    }
    val autoCode = remember { Locale.getDefault().country.ifBlank { "US" } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        // Same header as Settings: big emphasized title, quiet action on the right.
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Countries",
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.Check, "Done", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Live summary of what the calendar will show: one shape badge per country.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) {
            if (chosen.isEmpty()) {
                item(key = "auto") {
                    CountryBadge(autoCode, "Automatic", MaterialShapes.Sunny.toShape(), removable = false) {}
                }
            } else {
                val sorted = chosen.sortedBy { countryName(it) }
                items(sorted, key = { it }) { code ->
                    CountryBadge(code, countryName(code), badgeShape(sorted.indexOf(code)), removable = true) {
                        update(chosen - code)
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Section("All countries")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search countries", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search countries" },
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).elasticClickable { query = "" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, "Clear search", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (query.isBlank()) {
                item(key = "auto") {
                    SelectRow(
                        leading = {
                            Icon(Icons.Rounded.MyLocation, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        },
                        title = "Automatic",
                        subtitle = "Follow the phone's region · ${countryName(autoCode)}",
                        selected = chosen.isEmpty(),
                    ) { update(emptySet()) }
                }
            }
            items(filtered, key = { it.code }) { c ->
                SelectRow(
                    leading = { Text(flagEmoji(c.code), fontSize = 24.sp) },
                    title = c.name,
                    subtitle = null,
                    selected = c.code in chosen,
                ) { update(if (c.code in chosen) chosen - c.code else chosen + c.code) }
            }
            if (filtered.isEmpty()) {
                item(key = "none") {
                    Text(
                        "No country matches “$query”",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 28.dp, horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/** A chosen country: flag on an expressive shape that springs and spins in; tap to remove. */
@Composable
private fun CountryBadge(code: String, label: String, shape: Shape, removable: Boolean, onRemove: () -> Unit) {
    val scale = remember { Animatable(0f) }
    val turn = remember { Animatable(-60f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { scale.animateTo(1f, spring(dampingRatio = .45f, stiffness = 380f)) }
            launch { turn.animateTo(0f, spring(dampingRatio = .6f, stiffness = 220f)) }
        }
    }
    Column(
        Modifier
            .width(84.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (removable) "$label, tap to remove" else "$label holidays"
            }
            // Rounded rect, not the default circle: the press layer clips, and a circle would
            // cut off the remove mark and the label.
            .luminousElasticClickable(enabled = removable, shape = BadgeTapShape, onClick = onRemove)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(64.dp)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = turn.value }
                    .clip(shape)
                    .background(if (removable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer),
            )
            Text(flagEmoji(code), fontSize = 28.sp, modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value })
            if (removable) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** A selectable text row with a spring-in cookie check; shared by Countries and Backup. */
@Composable
internal fun SelectRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .09f) else Color.Transparent,
        tween(220), label = "rowBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(bg, RowShape)
            .luminousElasticClickable(shape = RowShape, maxStretchPercent = .02f, maxTranslateDp = 1.dp, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.Checkbox
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (selected) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SelectMark(selected)
    }
}

/** Unselected: a quiet ring. Selected: a cookie that springs up and spins, like the palette swatches. */
@Composable
private fun SelectMark(selected: Boolean) {
    val scale by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = .45f, stiffness = 380f), label = "markScale")
    val turn by animateFloatAsState(if (selected) 0f else -60f, spring(dampingRatio = .6f, stiffness = 220f), label = "markTurn")
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp).border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        Box(
            Modifier
                .size(32.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = turn }
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
