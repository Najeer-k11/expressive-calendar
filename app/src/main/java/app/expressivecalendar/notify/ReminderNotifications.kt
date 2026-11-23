package app.expressivecalendar.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarAlerts
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.expressivecalendar.MainActivity
import app.expressivecalendar.R
import app.expressivecalendar.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What a reminder notification shows; also carried through snooze so it can be re-posted. */
private data class Alert(
    val alertId: Long,
    val eventId: Long,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val title: String,
    val location: String?,
    val color: Int,
) {
    val notificationId: Int get() = (eventId * 31 + begin).hashCode()
}

/**
 * Event reminder notifications.
 *
 * Reminders are stored in the system calendar provider, which schedules them and broadcasts
 * [CalendarContract.ACTION_EVENT_REMINDER] when one is due, but it never shows anything itself:
 * a calendar app has to. Phones without Google/Samsung Calendar (e.g. GrapheneOS) otherwise get
 * no reminders at all, so this app posts them.
 */
object ReminderNotifications {
    const val CHANNEL_ID = "event_reminders"
    const val EXTRA_EVENT_ID = "app.expressivecalendar.extra.EVENT_ID"
    const val EXTRA_BEGIN = "app.expressivecalendar.extra.BEGIN"
    const val EXTRA_ALL_DAY = "app.expressivecalendar.extra.ALL_DAY"

    internal const val ACTION_SNOOZE = "app.expressivecalendar.action.SNOOZE"
    internal const val ACTION_SNOOZE_FIRE = "app.expressivecalendar.action.SNOOZE_FIRE"
    internal const val ACTION_DISMISS = "app.expressivecalendar.action.DISMISS"
    private const val EXTRA_ALERT_ID = "alert_id"
    private const val EXTRA_END = "end"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_LOCATION = "location"
    private const val EXTRA_COLOR = "color"
    private const val SNOOZE_MS = 10 * 60_000L

    /** Alerts older than this when the broadcast arrives are considered stale, not shown. */
    private const val LOOKBACK_MS = 24 * 60 * 60_000L

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Event reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts for events with a reminder"
                enableVibration(true)
            },
        )
    }

    /** True when the app may post notifications (runtime permission and the app-level switch). */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Label of another installed calendar app (Google Calendar, Samsung Calendar, Etar…), or null.
     * Those show reminders themselves, so ours default to off to avoid every alert arriving twice.
     *
     * Google Calendar schedules its own alarms rather than listening for EVENT_REMINDER, so the
     * main signal is "can open a calendar event"; the broadcast check catches reminder-only apps.
     */
    fun otherReminderApp(context: Context): String? {
        val pm = context.packageManager
        val viewers = pm.queryIntentActivities(
            Intent(Intent.ACTION_VIEW).setDataAndType(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, 1), "vnd.android.cursor.item/event",
            ),
            0,
        ).map { it.activityInfo }
        // The calendar provider ("Calendar Storage") also receives the broadcast, but only
        // launchable apps post notifications.
        val receivers = pm.queryBroadcastReceivers(
            Intent(CalendarContract.ACTION_EVENT_REMINDER, Uri.parse("content://com.android.calendar/0")), 0,
        ).map { it.activityInfo }.filter { pm.getLaunchIntentForPackage(it.packageName) != null }
        return (viewers + receivers)
            .firstOrNull { it.packageName != context.packageName }
            ?.applicationInfo?.loadLabel(pm)?.toString()
    }

    /** Resolves the user's setting (null = automatic) to whether we should notify. */
    fun enabled(context: Context, setting: Boolean?): Boolean = setting ?: (otherReminderApp(context) == null)

    /** Posts every reminder that is due now and not yet shown, then marks it fired. */
    internal fun postDue(context: Context) {
        val now = System.currentTimeMillis()
        val alerts = mutableListOf<Alert>()
        context.contentResolver.query(
            CalendarAlerts.CONTENT_URI,
            arrayOf(
                CalendarAlerts._ID, CalendarAlerts.EVENT_ID, CalendarAlerts.BEGIN, CalendarAlerts.END,
                CalendarAlerts.ALL_DAY, CalendarAlerts.TITLE, CalendarAlerts.EVENT_LOCATION,
                CalendarAlerts.DISPLAY_COLOR,
            ),
            "${CalendarAlerts.STATE} = ? AND ${CalendarAlerts.ALARM_TIME} <= ? AND " +
                "${CalendarAlerts.ALARM_TIME} > ? AND ${CalendarAlerts.END} > ? AND ${CalendarAlerts.VISIBLE} = 1",
            arrayOf(
                CalendarAlerts.STATE_SCHEDULED.toString(), now.toString(),
                (now - LOOKBACK_MS).toString(), now.toString(),
            ),
            "${CalendarAlerts.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                alerts += Alert(
                    alertId = c.getLong(0),
                    eventId = c.getLong(1),
                    begin = c.getLong(2),
                    end = c.getLong(3),
                    allDay = c.getInt(4) == 1,
                    title = c.getString(5)?.ifBlank { null } ?: "(No title)",
                    location = c.getString(6)?.ifBlank { null },
                    color = c.getInt(7),
                )
            }
        }
        // Several reminders on one occurrence collapse into a single notification.
        alerts.distinctBy { it.notificationId }.forEach { post(context, it) }
        alerts.forEach { setState(context, it.alertId, CalendarAlerts.STATE_FIRED) }
    }

    private fun post(context: Context, a: Alert) {
        if (!canPost(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, a.notificationId,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_EVENT_ID, a.eventId)
                .putExtra(EXTRA_BEGIN, a.begin)
                .putExtra(EXTRA_ALL_DAY, a.allDay),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = actionIntent(context, ACTION_SNOOZE, a)
        val dismiss = actionIntent(context, ACTION_DISMISS, a)
        val whenText = timeText(context, a)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_event)
            .setColor(a.color)
            .setContentTitle(a.title)
            .setContentText(listOfNotNull(whenText, a.location).joinToString("  ·  "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(whenText, a.location).joinToString("\n")))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(if (a.allDay) System.currentTimeMillis() else a.begin)
            .setShowWhen(!a.allDay)
            .setContentIntent(open)
            .setDeleteIntent(dismiss)
            .setAutoCancel(true)
            .addAction(0, "Snooze 10 min", snooze)
            .build()
        @Suppress("MissingPermission") // checked in canPost
        NotificationManagerCompat.from(context).notify(a.notificationId, n)
    }

    private fun timeText(context: Context, a: Alert): String {
        if (a.allDay) {
            val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_UTC
            // All-day ends are exclusive midnights; show the last day, not the day after.
            return "All day · " + DateUtils.formatDateRange(context, a.begin, maxOf(a.begin, a.end - 1), flags)
        }
        var flags = DateUtils.FORMAT_SHOW_TIME
        if (!DateUtils.isToday(a.begin)) flags = flags or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
        return DateUtils.formatDateRange(context, a.begin, a.end, flags)
    }

    private fun actionIntent(context: Context, action: String, a: Alert): PendingIntent =
        PendingIntent.getBroadcast(
            context, a.notificationId,
            Intent(context, ReminderActionReceiver::class.java).setAction(action).putAlert(a),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun Intent.putAlert(a: Alert): Intent = this
        .putExtra(EXTRA_ALERT_ID, a.alertId)
        .putExtra(EXTRA_EVENT_ID, a.eventId)
        .putExtra(EXTRA_BEGIN, a.begin)
        .putExtra(EXTRA_END, a.end)
        .putExtra(EXTRA_ALL_DAY, a.allDay)
        .putExtra(EXTRA_TITLE, a.title)
        .putExtra(EXTRA_LOCATION, a.location)
        .putExtra(EXTRA_COLOR, a.color)

    private fun Intent.getAlert(): Alert = Alert(
        alertId = getLongExtra(EXTRA_ALERT_ID, -1),
        eventId = getLongExtra(EXTRA_EVENT_ID, -1),
        begin = getLongExtra(EXTRA_BEGIN, 0),
        end = getLongExtra(EXTRA_END, 0),
        allDay = getBooleanExtra(EXTRA_ALL_DAY, false),
        title = getStringExtra(EXTRA_TITLE) ?: "(No title)",
        location = getStringExtra(EXTRA_LOCATION),
        color = getIntExtra(EXTRA_COLOR, 0),
    )

    private fun setState(context: Context, alertId: Long, state: Int) {
        if (alertId < 0) return
        runCatching {
            context.contentResolver.update(
                ContentUris.withAppendedId(CalendarAlerts.CONTENT_URI, alertId),
                ContentValues().apply { put(CalendarAlerts.STATE, state) },
                null, null,
            )
        }
    }

    internal fun handleAction(context: Context, intent: Intent) {
        val a = intent.getAlert()
        when (intent.action) {
            ACTION_DISMISS -> setState(context, a.alertId, CalendarAlerts.STATE_DISMISSED)
            ACTION_SNOOZE -> {
                NotificationManagerCompat.from(context).cancel(a.notificationId)
                val fire = PendingIntent.getBroadcast(
                    context, a.notificationId,
                    Intent(context, ReminderActionReceiver::class.java).setAction(ACTION_SNOOZE_FIRE).putAlert(a),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                // Exact, or a "10 minute" snooze can drift to 17+ (calendar apps may use exact alarms).
                val am = context.getSystemService(AlarmManager::class.java)
                val at = System.currentTimeMillis() + SNOOZE_MS
                if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
                }
            }
            ACTION_SNOOZE_FIRE -> post(context, a)
        }
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Receives the calendar provider's "a reminder is due" broadcast. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CalendarContract.ACTION_EVENT_REMINDER) return
        val app = context.applicationContext
        val pending = goAsync()
        receiverScope.launch {
            try {
                val setting = SettingsStore(app).settings.first().eventNotifications
                if (ReminderNotifications.enabled(app, setting)) ReminderNotifications.postDue(app)
            } catch (t: Throwable) {
                android.util.Log.e("ExpressiveCalendar", "reminder failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Snooze / dismiss from our own notifications; not exported. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        receiverScope.launch {
            try {
                ReminderNotifications.handleAction(app, intent)
            } finally {
                pending.finish()
            }
        }
    }
}
