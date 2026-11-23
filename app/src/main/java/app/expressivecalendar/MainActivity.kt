package app.expressivecalendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import app.expressivecalendar.notify.ReminderNotifications
import app.expressivecalendar.ui.CalendarApp
import app.expressivecalendar.ui.CalendarViewModel

class MainActivity : ComponentActivity() {
    private val vm: CalendarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge is enabled inside CalendarTheme, where the effective dark/light state is known.
        setContent { CalendarApp(vm) }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A tapped reminder notification opens its event. */
    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(ReminderNotifications.EXTRA_EVENT_ID, -1L) ?: -1L
        if (id < 0) return
        vm.openEvent(
            id,
            intent!!.getLongExtra(ReminderNotifications.EXTRA_BEGIN, 0L),
            intent.getBooleanExtra(ReminderNotifications.EXTRA_ALL_DAY, false),
        )
        intent.removeExtra(ReminderNotifications.EXTRA_EVENT_ID)
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted or revoked calendar access in system settings.
        vm.onPermissionResult()
    }
}
