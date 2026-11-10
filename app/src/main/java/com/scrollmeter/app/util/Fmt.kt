package com.scrollmeter.app.util

import com.scrollmeter.app.data.Weeks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/** Every number and date string shown in the dashboard. Pure Kotlin, unit-tested. */
object Fmt {

    private val DAY_TITLE = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    private fun clean(metres: Double): Double = if (metres.isNaN() || metres < 0.0) 0.0 else metres

    /**
     * Hero numbers, legends and tooltips: km with 2 decimals under 10 km, 1 decimal above
     * ("1.79 km", "12.4 km"). Empty is "0 m". Under 10 m shows whole metres, because
     * "0.00 km" would read as nothing at all.
     */
    fun km(metres: Double): String {
        val m = clean(metres)
        if (m < 0.5) return "0 m"
        if (m < 10.0) return "${m.roundToInt()} m"
        val km = m / 1000.0
        return if (km < 9.995) {
            String.format(Locale.US, "%.2f km", km)
        } else {
            String.format(Locale.US, "%.1f km", km)
        }
    }

    /** Weekly "Daily average distance" hero: one decimal ("2.1 km"). */
    fun kmOneDecimal(metres: Double): String {
        val m = clean(metres)
        if (m < 0.5) return "0 m"
        if (m < 50.0) return "${m.roundToInt()} m"
        return String.format(Locale.US, "%.1f km", m / 1000.0)
    }

    /** Session rows and the peak-hour tile: metres under 1 km ("412 m"), km above. */
    fun short(metres: Double): String {
        val m = clean(metres)
        return if (m < 999.5) "${m.roundToInt()} m" else km(m)
    }

    /** Daily chart axis: "2 km", "0.5 km". */
    fun axisKm(metres: Double): String {
        val km = clean(metres) / 1000.0
        return if (km == floor(km)) {
            "${km.toInt()} km"
        } else {
            String.format(Locale.US, "%.1f km", km)
        }
    }

    /** Hourly chart axis: "400 m", "1.2 km". */
    fun axisMetres(metres: Double): String {
        val m = clean(metres)
        return if (m < 1000.0) "${m.roundToInt()} m" else axisKm(m)
    }

    /** Thousands separators: "1,412". */
    fun count(n: Int): String = String.format(Locale.US, "%,d", n)

    fun strokes(n: Int): String = if (n == 1) "1 stroke" else "${count(n)} strokes"

    /** "21:05" */
    fun time(epochMs: Long, zone: ZoneId): String =
        TIME.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** "22:00" */
    fun hour(hour: Int): String = String.format(Locale.US, "%02d:00", hour)

    /** "Thu, 1 Oct" */
    fun dayTitle(date: LocalDate): String = DAY_TITLE.format(date)

    /** "1 Oct" */
    fun shortDate(date: LocalDate): String = SHORT_DATE.format(date)

    /** "27 Sep – 3 Oct (Week 40)" */
    fun weekRange(weekStart: LocalDate): String =
        "${shortDate(weekStart)} – ${shortDate(weekStart.plusDays(6))} " +
            "(Week ${Weeks.isoWeekNumber(weekStart)})"

    /** "45 s", "38 min", "1 h 12 min" */
    fun duration(ms: Long): String {
        val safe = if (ms < 0) 0 else ms
        val minutes = safe / 60_000
        return when {
            minutes < 1 -> "${safe / 1000} s"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    }
}
