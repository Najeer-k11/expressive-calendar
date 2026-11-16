package app.expressivecalendar.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.expressivecalendar.ui.luminousElasticClickable

@Composable
fun PermissionScreen(permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "spin")
    val rotation by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "r")
    val breathe by spin.animateFloat(.96f, 1.04f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "b")

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(220.dp)
                    .graphicsLayer { rotationZ = rotation; scaleX = breathe; scaleY = breathe }
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Box(
                Modifier
                    .size(120.dp)
                    .graphicsLayer { rotationZ = -rotation * 1.5f }
                    .clip(MaterialShapes.Sunny.toShape())
                    .background(MaterialTheme.colorScheme.primary),
            )
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.height(40.dp))
        Text(
            "Your days,\nexpressively",
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (permanentlyDenied) "Calendar access was turned off. Turn it on in system settings to see and add events."
            else "Allow calendar access to show your events from Google and other accounts, and to save new ones back to them.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.primary)
                .luminousElasticClickable(shape = RoundedCornerShape(32.dp), maxStretchPercent = .04f, onClick = if (permanentlyDenied) onOpenSettings else onRequest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (permanentlyDenied) "Open settings" else "Allow calendar access",
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
