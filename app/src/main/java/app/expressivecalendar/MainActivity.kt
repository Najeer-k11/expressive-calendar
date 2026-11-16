package app.expressivecalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import app.expressivecalendar.ui.CalendarApp
import app.expressivecalendar.ui.CalendarViewModel

class MainActivity : ComponentActivity() {
    private val vm: CalendarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge is enabled inside CalendarTheme, where the effective dark/light state is known.
        setContent { CalendarApp(vm) }
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted or revoked calendar access in system settings.
        vm.onPermissionResult()
    }
}
