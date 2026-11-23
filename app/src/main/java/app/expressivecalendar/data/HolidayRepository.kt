package app.expressivecalendar.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class HolidayKind { PUBLIC, OBSERVANCE }

data class Holiday(
    val date: LocalDate,
    val name: String,
    val localName: String,
    val kind: HolidayKind,
    val countryCode: String,
    /** Every selected country that observes this holiday on this day (see [mergeHolidays]). */
    val countries: List<String> = listOf(countryCode),
)

data class Country(val code: String, val name: String)

fun countryName(code: String): String =
    Locale("", code).getDisplayCountry(Locale.getDefault()).ifBlank { code }

/** Regional-indicator flag emoji for an ISO country code. */
fun flagEmoji(code: String): String =
    if (code.length != 2) "" else code.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')) }.joinToString("") { String(it) }

/**
 * Collapses the same holiday shared by several countries on the same day (Christmas in the UK
 * and the US) into one entry listing them all. Public in any country wins over observance.
 */
fun mergeHolidays(list: List<Holiday>): List<Holiday> =
    list.groupBy { it.date to it.name.trim().lowercase() }.values.map { same ->
        same.first().copy(
            kind = if (same.any { it.kind == HolidayKind.PUBLIC }) HolidayKind.PUBLIC else HolidayKind.OBSERVANCE,
            countries = same.flatMap { it.countries }.distinct(),
        )
    }.sortedBy { it.date }

/**
 * Public holidays for any country, fetched once per country-year and cached on disk.
 *
 * Primary source is Nager.Date (free, no key). It does not cover every country (notably India,
 * Pakistan, Sri Lanka, Malaysia, the Gulf states), so for those it falls back to Google's public
 * holiday iCal feeds, which need no account and distinguish public holidays from observances.
 */
class HolidayRepository(context: Context) {
    private val dir = File(context.filesDir, "holidays").apply { mkdirs() }
    private val memory = HashMap<String, List<Holiday>>()
    private val lock = Mutex()

    /** Every ISO country, by display name. Holiday availability is resolved at fetch time. */
    fun countries(): List<Country> = Locale.getISOCountries()
        .map { Country(it, Locale("", it).getDisplayCountry(Locale.getDefault())) }
        .filter { it.name.isNotBlank() && it.name != it.code }
        .sortedBy { it.name }

    suspend fun holidays(country: String, year: Int, forceRefresh: Boolean = false): List<Holiday> =
        lock.withLock {
            val key = "${country}_$year"
            if (!forceRefresh) memory[key]?.let { return it }
            val file = File(dir, "$key.json")
            val fresh = file.exists() && (
                year < LocalDate.now().year ||
                    System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS
                )
            if (fresh && !forceRefresh) {
                readCache(file)?.let { memory[key] = it; return it }
            }
            val fetched = runCatching { fetch(country, year) }.getOrNull()
            val result = when {
                fetched != null -> fetched.also { writeCache(file, it) }
                else -> readCache(file) ?: emptyList() // offline: stale beats nothing
            }
            memory[key] = result
            result
        }

    private suspend fun fetch(country: String, year: Int): List<Holiday> = withContext(Dispatchers.IO) {
        val nager = runCatching { fetchNager(country, year) }.getOrNull()
        if (!nager.isNullOrEmpty()) return@withContext nager
        // Google's feed spans several years at once; cache every year it returns.
        val all = fetchGoogle(country)
        all.groupBy { it.date.year }.forEach { (y, list) ->
            if (y != year) writeCache(File(dir, "${country}_$y.json"), list)
        }
        all.filter { it.date.year == year }
    }

    private fun fetchNager(country: String, year: Int): List<Holiday> {
        val body = get("https://date.nager.at/api/v3/PublicHolidays/$year/$country") ?: return emptyList()
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val types = o.optJSONArray("types")
            val isPublic = types == null || (0 until types.length()).any {
                types.getString(it) in setOf("Public", "Bank")
            }
            Holiday(
                date = LocalDate.parse(o.getString("date")),
                name = o.getString("name"),
                localName = o.optString("localName", o.getString("name")),
                kind = if (isPublic) HolidayKind.PUBLIC else HolidayKind.OBSERVANCE,
                countryCode = country,
            )
        }.distinctBy { it.date to it.name }
    }

    private fun fetchGoogle(country: String): List<Holiday> {
        val id = GOOGLE_ALIASES[country] ?: country.lowercase()
        val url = "https://calendar.google.com/calendar/ical/en.$id%23holiday%40group.v.calendar.google.com/public/basic.ics"
        val body = get(url) ?: return emptyList()
        return parseIcs(body, country)
    }

    private fun parseIcs(text: String, country: String): List<Holiday> {
        // Unfold RFC 5545 continuation lines first.
        val lines = text.replace("\r\n", "\n").replace("\n ", "").split('\n')
        val out = mutableListOf<Holiday>()
        var date: LocalDate? = null
        var summary: String? = null
        var description = ""
        val fmt = DateTimeFormatter.BASIC_ISO_DATE
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> { date = null; summary = null; description = "" }
                line.startsWith("DTSTART") -> date = runCatching {
                    LocalDate.parse(line.substringAfter(':').take(8), fmt)
                }.getOrNull()
                line.startsWith("SUMMARY") -> summary = line.substringAfter(':').unescapeIcs()
                line.startsWith("DESCRIPTION") -> description = line.substringAfter(':')
                line == "END:VEVENT" -> {
                    val d = date; val s = summary
                    if (d != null && s != null) {
                        out += Holiday(
                            date = d, name = s, localName = s,
                            kind = if (description.startsWith("Public holiday")) HolidayKind.PUBLIC
                            else HolidayKind.OBSERVANCE,
                            countryCode = country,
                        )
                    }
                }
            }
        }
        return out.distinctBy { it.date to it.name }.sortedBy { it.date }
    }

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/json, text/calendar")
            c.setRequestProperty("User-Agent", "ExpressiveCalendar/1.0")
            when (c.responseCode) {
                200 -> c.inputStream.bufferedReader().use { it.readText() }
                204, 404 -> null
                else -> error("HTTP ${c.responseCode}")
            }
        } finally {
            c.disconnect()
        }
    }

    private fun writeCache(file: File, list: List<Holiday>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("date", it.date.toString()); put("name", it.name)
                put("localName", it.localName); put("kind", it.kind.name)
                put("cc", it.countryCode)
            })
        }
        runCatching { file.writeText(arr.toString()) }
    }

    private fun readCache(file: File): List<Holiday>? = runCatching {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Holiday(
                LocalDate.parse(o.getString("date")), o.getString("name"),
                o.getString("localName"), HolidayKind.valueOf(o.getString("kind")), o.getString("cc"),
            )
        }
    }.getOrNull()

    /** Disk cache only, never the network; for the widget, which must answer quickly. */
    fun cachedHolidays(country: String, year: Int): List<Holiday> =
        readCache(File(dir, "${country}_$year.json")).orEmpty()

    fun clearCache() {
        memory.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun String.unescapeIcs() =
        replace("\\,", ",").replace("\\;", ";").replace("\\n", " ").replace("\\\\", "\\")

    companion object {
        private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

        /** Countries whose Google holiday calendar predates the ISO-code naming. */
        private val GOOGLE_ALIASES = mapOf(
            // Verified against live feeds. Note `en.sa` is South Africa, not Saudi Arabia.
            "IN" to "indian", "MY" to "malaysia", "IL" to "jewish", "TW" to "taiwan",
            "SA" to "saudiarabian", "ZA" to "sa",
        )
    }
}
