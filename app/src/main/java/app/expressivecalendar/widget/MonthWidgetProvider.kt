package app.expressivecalendar.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import app.expressivecalendar.MainActivity
import app.expressivecalendar.R
import app.expressivecalendar.data.CalendarRepository
import app.expressivecalendar.data.HolidayKind
import app.expressivecalendar.data.HolidayRepository
import app.expressivecalendar.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A month-at-a-glance widget. Drawn to a bitmap rather than built from 42 RemoteViews cells, the
 * same approach Expressive Clock takes for its dial: it is the only way to get the cookie-shaped
 * "today" marker and the app's own font on a home screen.
 */
class MonthWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) =
        render(context, intArrayOf(id))

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_DATE_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) refresh(context)
    }

    private fun render(context: Context, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(8_000) {
                    val manager = AppWidgetManager.getInstance(context)
                    val data = load(context)
                    ids.forEach { id ->
                        val opts = manager.getAppWidgetOptions(id)
                        val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceAtLeast(180)
                        val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 250).coerceAtLeast(180)
                        val bmp = draw(context, data, dp(context, wDp), dp(context, hDp))
                        val views = RemoteViews(context.packageName, R.layout.widget_month).apply {
                            setImageViewBitmap(R.id.month_image, bmp)
                            setOnClickPendingIntent(
                                R.id.month_image,
                                PendingIntent.getActivity(
                                    context, 0, Intent(context, MainActivity::class.java),
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                                ),
                            )
                        }
                        manager.updateAppWidget(id, views)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private data class Data(
        val month: YearMonth,
        val firstDay: DayOfWeek,
        val eventDays: Set<LocalDate>,
        val publicHolidays: Set<LocalDate>,
        val observances: Set<LocalDate>,
    )

    private suspend fun load(context: Context): Data {
        val settings = SettingsStore(context).settings.first()
        val month = YearMonth.now()
        val from = month.atDay(1).minusDays(7)
        val to = month.plusMonths(1).atDay(1).plusDays(14)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val eventDays = if (!granted) emptySet() else runCatching {
            CalendarRepository(context).instances(from, to)
                .filter { it.calendarId !in settings.hiddenCalendarIds }
                .flatMap { e -> generateSequence(maxOf(e.firstDay, from)) { it.plusDays(1) }.takeWhile { !it.isAfter(minOf(e.lastDay, to)) }.toList() }
                .toSet()
        }.getOrDefault(emptySet())
        val holidays = if (!settings.showHolidays) emptyList() else {
            val repo = HolidayRepository(context)
            (from.year..to.year).flatMap { repo.cachedHolidays(settings.effectiveCountry, it) }
        }
        return Data(
            month, settings.weekStart.firstDay(), eventDays,
            holidays.filter { it.kind == HolidayKind.PUBLIC }.map { it.date }.toSet(),
            holidays.filter { it.kind == HolidayKind.OBSERVANCE }.map { it.date }.toSet(),
        )
    }

    private fun draw(context: Context, d: Data, w: Int, h: Int): Bitmap {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val c = Colors.resolve(context, dark)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val font = runCatching { ResourcesCompat.getFont(context, R.font.google_sans_flex) }.getOrNull() ?: Typeface.DEFAULT
        val bold = if (Build.VERSION.SDK_INT >= 28) Typeface.create(font, 600, false) else Typeface.create(font, Typeface.BOLD)
        val regular = if (Build.VERSION.SDK_INT >= 28) Typeface.create(font, 450, false) else font
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Card
        val radius = dp(context, 28).toFloat()
        paint.color = c.surface
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)

        val pad = dp(context, 14).toFloat()
        val titleSize = min(w, h) * .10f
        paint.typeface = bold; paint.textSize = titleSize; paint.color = c.onSurface
        canvas.drawText(d.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()), pad + 4, pad + titleSize, paint)
        paint.color = c.primary; paint.typeface = regular; paint.textSize = titleSize * .62f
        val yearText = d.month.year.toString()
        canvas.drawText(yearText, w - pad - paint.measureText(yearText) - 4, pad + titleSize, paint)

        val top = pad + titleSize * 1.55f
        val cellW = (w - pad * 2) / 7f
        val order = (0..6).map { d.firstDay.plus(it.toLong()) }
        paint.textSize = cellW * .28f; paint.typeface = bold
        order.forEachIndexed { i, dow ->
            val weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
            paint.color = if (weekend) c.tertiary else c.onSurfaceVariant
            val s = dow.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault())
            canvas.drawText(s, pad + cellW * i + (cellW - paint.measureText(s)) / 2, top + paint.textSize, paint)
        }

        val gridTop = top + paint.textSize * 1.8f
        val cellH = (h - gridTop - pad) / 6f
        val first = d.month.atDay(1)
        val lead = (first.dayOfWeek.value - d.firstDay.value + 7) % 7
        val start = first.minusDays(lead.toLong())
        val today = LocalDate.now()
        val numSize = min(cellW, cellH) * .40f
        for (i in 0 until 42) {
            val day = start.plusDays(i.toLong())
            val cx = pad + cellW * (i % 7) + cellW / 2
            val cy = gridTop + cellH * (i / 7) + cellH / 2
            val inMonth = day.month == d.month.month
            val r = min(cellW, cellH) * .44f
            if (day in d.publicHolidays) {
                paint.color = c.tertiaryContainer
                canvas.drawRoundRect(RectF(cx - cellW / 2 + 2, cy - cellH / 2 + 2, cx + cellW / 2 - 2, cy + cellH / 2 - 2), r * .5f, r * .5f, paint)
            }
            if (day == today) {
                paint.color = c.primary
                canvas.drawPath(cookie(cx, cy, r, 9), paint)
            }
            paint.typeface = if (day == today) bold else regular
            paint.textSize = numSize
            paint.color = when {
                day == today -> c.onPrimary
                day in d.publicHolidays -> c.onTertiaryContainer
                else -> c.onSurface
            }
            paint.alpha = if (inMonth) 255 else 90
            val s = day.dayOfMonth.toString()
            canvas.drawText(s, cx - paint.measureText(s) / 2, cy + numSize * .35f, paint)
            val dots = listOfNotNull(
                if (day in d.eventDays) (if (day == today) c.onPrimary else c.primary) else null,
                if (day in d.observances && day !in d.publicHolidays) c.tertiary else null,
            )
            dots.forEachIndexed { k, col ->
                paint.color = col; paint.alpha = if (inMonth) 255 else 90
                val dx = (k - (dots.size - 1) / 2f) * numSize * .4f
                canvas.drawCircle(cx + dx, cy + numSize * .78f, numSize * .1f, paint)
            }
            paint.alpha = 255
        }
        return bmp
    }

    /** Material's "cookie": a circle with n gentle scallops. */
    private fun cookie(cx: Float, cy: Float, r: Float, n: Int): Path {
        val p = Path()
        val steps = n * 24
        for (i in 0..steps) {
            val a = 2 * PI * i / steps
            val rad = r * (0.92f + 0.08f * cos(n * a).toFloat())
            val x = cx + rad * cos(a - PI / 2).toFloat()
            val y = cy + rad * sin(a - PI / 2).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    private class Colors(
        val surface: Int, val onSurface: Int, val onSurfaceVariant: Int,
        val primary: Int, val onPrimary: Int,
        val tertiary: Int, val tertiaryContainer: Int, val onTertiaryContainer: Int,
    ) {
        companion object {
            fun resolve(ctx: Context, dark: Boolean): Colors {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    fun col(id: Int) = ctx.getColor(id)
                    return if (dark) Colors(
                        col(android.R.color.system_neutral1_900), col(android.R.color.system_neutral1_100), col(android.R.color.system_neutral2_200),
                        col(android.R.color.system_accent1_200), col(android.R.color.system_accent1_800),
                        col(android.R.color.system_accent3_200), col(android.R.color.system_accent3_700), col(android.R.color.system_accent3_100),
                    ) else Colors(
                        col(android.R.color.system_neutral1_10), col(android.R.color.system_neutral1_900), col(android.R.color.system_neutral2_700),
                        col(android.R.color.system_accent1_600), col(android.R.color.system_accent1_0),
                        col(android.R.color.system_accent3_600), col(android.R.color.system_accent3_100), col(android.R.color.system_accent3_900),
                    )
                }
                return if (dark) Colors(
                    0xFF0E1415.toInt(), 0xFFDEE3E5.toInt(), 0xFFBFC8CA.toInt(), 0xFF4FD8EB.toInt(), 0xFF00363D.toInt(),
                    0xFFB5CCC5.toInt(), 0xFF374A45.toInt(), 0xFFD1E8E1.toInt(),
                ) else Colors(
                    0xFFF5FAFB.toInt(), 0xFF171D1E.toInt(), 0xFF3F484A.toInt(), 0xFF006874.toInt(), 0xFFFFFFFF.toInt(),
                    0xFF4F635D.toInt(), 0xFFD1E8E1.toInt(), 0xFF0C1F1B.toInt(),
                )
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private fun dp(ctx: Context, v: Int): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MonthWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, MonthWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }
}
