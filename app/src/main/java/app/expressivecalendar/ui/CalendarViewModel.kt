package app.expressivecalendar.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.expressivecalendar.data.CalendarBackup
import app.expressivecalendar.data.CalendarRepository
import app.expressivecalendar.data.IcsEvent
import app.expressivecalendar.data.ImportResult
import app.expressivecalendar.data.CalendarSettings
import app.expressivecalendar.data.Country
import app.expressivecalendar.data.DeviceCalendar
import app.expressivecalendar.data.EventDraft
import app.expressivecalendar.data.EventInstance
import app.expressivecalendar.data.Holiday
import app.expressivecalendar.data.HolidayRepository
import app.expressivecalendar.data.SettingsStore
import app.expressivecalendar.data.countryName
import app.expressivecalendar.data.mergeHolidays
import app.expressivecalendar.notify.ReminderNotifications
import app.expressivecalendar.widget.MonthWidgetProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** The date range currently worth having in memory. */
private data class Window(val from: LocalDate, val toExclusive: LocalDate)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = SettingsStore(app)
    private val calendarsRepo = CalendarRepository(app)
    private val backup = CalendarBackup(app)
    val holidaysRepo = HolidayRepository(app)

    val settings: StateFlow<CalendarSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, CalendarSettings())

    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded = _settingsLoaded.asStateFlow()

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission = _hasPermission.asStateFlow()

    private val _notificationsAllowed = MutableStateFlow(ReminderNotifications.canPost(app))
    /** Whether the system lets us post notifications (permission + app-level switch). */
    val notificationsAllowed = _notificationsAllowed.asStateFlow()

    /** Another installed app that already shows calendar reminders, if any. */
    val otherReminderApp: String? by lazy { ReminderNotifications.otherReminderApp(app) }

    /** An event occurrence to open, from tapping a reminder notification: (eventId, beginMillis). */
    private val _pendingOpen = MutableStateFlow<Pair<Long, Long>?>(null)
    val pendingOpen = _pendingOpen.asStateFlow()

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth = _visibleMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    private val _holidayStatus = MutableStateFlow<String?>(null)
    /** Human-readable problem with holiday loading, or null when fine. */
    val holidayStatus = _holidayStatus.asStateFlow()

    private val window = combine(_visibleMonth, _selectedDate) { month, sel ->
        val today = LocalDate.now()
        val from = minOf(month.minusMonths(1).atDay(1), sel.minusDays(7), today.minusDays(1))
        val to = maxOf(month.plusMonths(2).atDay(1), sel.plusDays(8), today.plusDays(180))
        // Round to whole months so small selection changes don't trigger a requery.
        Window(YearMonth.from(from).atDay(1), YearMonth.from(to).plusMonths(1).atDay(1))
    }.distinctUntilChanged()

    private val refreshTick = MutableStateFlow(0)

    val calendars: StateFlow<List<DeviceCalendar>> = combine(_hasPermission, refreshTick) { p, _ -> p }
        .flatMapLatest { granted -> if (granted) calendarsRepo.changes() else emptyFlow() }
        .mapLatest { runCatching { calendarsRepo.calendars() }.getOrDefault(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val events: StateFlow<Map<LocalDate, List<EventInstance>>> =
        combine(_hasPermission, window, settings.map { it.hiddenCalendarIds }.distinctUntilChanged(), refreshTick) { p, w, hidden, _ ->
            Triple(p, w, hidden)
        }.flatMapLatest { (granted, w, hidden) ->
            if (!granted) kotlinx.coroutines.flow.flowOf(emptyMap())
            else calendarsRepo.changes().mapLatest {
                val list = runCatching { calendarsRepo.instances(w.from, w.toExclusive) }
                    .getOrDefault(emptyList())
                    .filter { it.calendarId !in hidden }
                indexByDay(list, w)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val holidays: StateFlow<Map<LocalDate, List<Holiday>>> =
        combine(
            window,
            settings.map { it.showHolidays to it.effectiveCountries }.distinctUntilChanged(),
            refreshTick,
        ) { w, (show, ccs), _ -> Triple(w, show, ccs) }
            .mapLatest { (w, show, ccs) ->
                if (!show) return@mapLatest emptyMap()
                val years = (w.from.year..w.toExclusive.year).toList()
                val perCountry = ccs.associateWith { cc ->
                    years.map { y -> viewModelScope.async { holidaysRepo.holidays(cc, y) } }
                }.mapValues { (_, jobs) -> jobs.awaitAll().flatten() }
                val missing = perCountry.filterValues { it.isEmpty() }.keys
                _holidayStatus.value = if (missing.isNotEmpty()) {
                    "No holidays found for ${missing.joinToString { countryName(it) }}. Check your connection."
                } else null
                mergeHolidays(perCountry.values.flatten()).groupBy { it.date }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val countries: List<Country> by lazy { holidaysRepo.countries() }

    init {
        viewModelScope.launch {
            settingsStore.settings.first()
            _settingsLoaded.value = true
        }
    }

    private fun indexByDay(list: List<EventInstance>, w: Window): Map<LocalDate, List<EventInstance>> {
        val map = HashMap<LocalDate, MutableList<EventInstance>>()
        for (e in list) {
            var d = maxOf(e.firstDay, w.from)
            val last = minOf(e.lastDay, w.toExclusive.minusDays(1))
            while (!d.isAfter(last)) {
                map.getOrPut(d) { mutableListOf() } += e
                d = d.plusDays(1)
            }
        }
        return map
    }

    private fun checkPermission(): Boolean {
        val ctx = getApplication<Application>()
        return listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun onPermissionResult() {
        _hasPermission.value = checkPermission()
        _notificationsAllowed.value = ReminderNotifications.canPost(getApplication())
    }

    fun notificationsEnabled(s: CalendarSettings): Boolean =
        s.eventNotifications ?: (otherReminderApp == null)

    fun openEvent(eventId: Long, beginMillis: Long, allDay: Boolean) {
        val zone = if (allDay) java.time.ZoneOffset.UTC else java.time.ZoneId.systemDefault()
        select(java.time.Instant.ofEpochMilli(beginMillis).atZone(zone).toLocalDate())
        _pendingOpen.value = eventId to beginMillis
    }

    fun consumePendingOpen() {
        _pendingOpen.value = null
    }

    fun select(date: LocalDate) {
        _selectedDate.value = date
        _visibleMonth.value = YearMonth.from(date)
    }

    fun showMonth(month: YearMonth) {
        _visibleMonth.value = month
        if (YearMonth.from(_selectedDate.value) != month) {
            val today = LocalDate.now()
            _selectedDate.value = if (YearMonth.from(today) == month) today else month.atDay(1)
        }
    }

    fun goToToday() = select(LocalDate.now())

    fun updateSettings(transform: (CalendarSettings) -> CalendarSettings) {
        viewModelScope.launch { settingsStore.save(transform(settings.value)) }
    }

    fun refreshHolidays() {
        viewModelScope.launch {
            holidaysRepo.clearCache()
            refreshTick.value++
        }
    }

    suspend fun loadDraft(eventId: Long): EventDraft? = calendarsRepo.loadDraft(eventId)

    fun saveEvent(draft: EventDraft, onDone: (Result<Long>) -> Unit = {}) {
        viewModelScope.launch {
            val r = runCatching { calendarsRepo.save(draft) }
            r.exceptionOrNull()?.let { android.util.Log.e("ExpressiveCalendar", "save failed", it) }
            onDone(r)
            MonthWidgetProvider.refresh(getApplication())
        }
    }

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch {
            runCatching { calendarsRepo.delete(eventId) }
            MonthWidgetProvider.refresh(getApplication())
        }
    }

    suspend fun exportIcs(calendarIds: Set<Long>, to: android.net.Uri): Result<Int> =
        runCatching { backup.export(calendarIds, to) }
            .onFailure { android.util.Log.e("ExpressiveCalendar", "export failed", it) }

    suspend fun readIcs(from: android.net.Uri): Result<List<IcsEvent>> =
        runCatching { backup.read(from) }
            .onFailure { android.util.Log.e("ExpressiveCalendar", "read failed", it) }

    suspend fun importIcs(events: List<IcsEvent>, calendarId: Long): Result<ImportResult> =
        runCatching { backup.import(events, calendarId) }
            .onFailure { android.util.Log.e("ExpressiveCalendar", "import failed", it) }
            .also { MonthWidgetProvider.refresh(getApplication()) }

    fun createLocalCalendar() {
        viewModelScope.launch {
            runCatching { calendarsRepo.createLocalCalendar() }
            refreshTick.value++
        }
    }
}
