package com.scrollmeter.app.data

import com.scrollmeter.app.model.AppGroup
import com.scrollmeter.app.model.WatchedApp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

/** Strokes and metres for one app group over some period. */
data class Amount(val strokes: Int = 0, val metres: Double = 0.0)

/** One day, split into the two chart colours. */
data class DaySummary(
    val date: LocalDate,
    val facebook: Amount = Amount(),
    val instagram: Amount = Amount(),
) {
    val metres: Double get() = facebook.metres + instagram.metres
    val strokes: Int get() = facebook.strokes + instagram.strokes
}

/** One hour of one day. */
data class HourSlot(
    val hour: Int,
    val facebookMetres: Double = 0.0,
    val instagramMetres: Double = 0.0,
) {
    val metres: Double get() = facebookMetres + instagramMetres
}

/** FR6.3: weeks run Sunday to Saturday; the label is the ISO week number of that week's Monday. */
object Weeks {

    fun startOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    fun isoWeekNumber(weekStart: LocalDate): Int =
        weekStart.plusDays(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** The 7 dates of the week starting at [weekStart], Sunday first. */
    fun daysOf(weekStart: LocalDate): List<LocalDate> = List(7) { weekStart.plusDays(it.toLong()) }

    /** Week starts of the last four weeks, oldest first; the last one is the current week. */
    fun lastFour(today: LocalDate): List<LocalDate> {
        val current = startOf(today)
        return List(4) { current.minusWeeks((3 - it).toLong()) }
    }
}

/**
 * FR6.2: every day, week and peak-hour figure is computed here from hourly buckets.
 * Pure functions over lists, so the numbers on the screens are unit-tested on the JVM.
 */
object Aggregator {

    private fun groupOf(appKey: String): AppGroup =
        if (appKey == WatchedApp.INSTAGRAM.key) AppGroup.INSTAGRAM else AppGroup.FACEBOOK

    fun daySummary(date: LocalDate, buckets: List<HourlyBucketEntity>): DaySummary {
        val day = date.toString()
        var fbStrokes = 0
        var fbMetres = 0.0
        var igStrokes = 0
        var igMetres = 0.0
        for (b in buckets) {
            if (b.day != day) continue
            if (groupOf(b.app) == AppGroup.INSTAGRAM) {
                igStrokes += b.strokes
                igMetres += b.metres
            } else {
                fbStrokes += b.strokes
                fbMetres += b.metres
            }
        }
        return DaySummary(date, Amount(fbStrokes, fbMetres), Amount(igStrokes, igMetres))
    }

    /** Seven summaries, Sunday to Saturday. */
    fun weekDays(weekStart: LocalDate, buckets: List<HourlyBucketEntity>): List<DaySummary> =
        Weeks.daysOf(weekStart).map { daySummary(it, buckets) }

    /** 24 slots for one day. */
    fun hourly(date: LocalDate, buckets: List<HourlyBucketEntity>): List<HourSlot> {
        val day = date.toString()
        val fb = DoubleArray(24)
        val ig = DoubleArray(24)
        for (b in buckets) {
            if (b.day != day || b.hour !in 0..23) continue
            if (groupOf(b.app) == AppGroup.INSTAGRAM) ig[b.hour] += b.metres else fb[b.hour] += b.metres
        }
        return List(24) { HourSlot(it, fb[it], ig[it]) }
    }

    /** The hour with the most metres, or null when the day is empty. */
    fun peakHour(hours: List<HourSlot>): HourSlot? {
        val best = hours.maxByOrNull { it.metres } ?: return null
        return if (best.metres > 0.0) best else null
    }

    /**
     * FR6.4: week total / days with data so far, so a partly finished week is not diluted
     * by days that have not happened (or had no scrolling).
     */
    fun dailyAverage(days: List<DaySummary>, today: LocalDate): Double {
        var total = 0.0
        var withData = 0
        for (d in days) {
            if (d.date.isAfter(today)) continue
            if (d.metres > 0.0) {
                total += d.metres
                withData++
            }
        }
        return if (withData == 0) 0.0 else total / withData
    }

    /** 7 rows (Sunday to Saturday) x 24 columns of metres per hour. */
    fun heatmap(weekStart: LocalDate, buckets: List<HourlyBucketEntity>): List<List<Double>> =
        Weeks.daysOf(weekStart).map { date -> hourly(date, buckets).map { it.metres } }

    /** Hour of day (0 to 23) with the most metres across the whole week, or null if empty. */
    fun busiestHour(heat: List<List<Double>>): Int? {
        var bestHour = -1
        var bestMetres = 0.0
        for (hour in 0..23) {
            var sum = 0.0
            for (row in heat) sum += row[hour]
            if (sum > bestMetres) {
                bestMetres = sum
                bestHour = hour
            }
        }
        return if (bestHour < 0) null else bestHour
    }

    /** Heatmap shade index: 0, 0 to 25, 25 to 50, 50 to 100, 100+ metres per hour. */
    fun heatLevel(metres: Double): Int = when {
        metres <= 0.0 -> 0
        metres < 25.0 -> 1
        metres < 50.0 -> 2
        metres < 100.0 -> 3
        else -> 4
    }

    /** Totals per colour group over a set of days. */
    fun groupTotals(days: List<DaySummary>): Pair<Amount, Amount> {
        var fb = Amount()
        var ig = Amount()
        for (d in days) {
            fb = Amount(fb.strokes + d.facebook.strokes, fb.metres + d.facebook.metres)
            ig = Amount(ig.strokes + d.instagram.strokes, ig.metres + d.instagram.metres)
        }
        return fb to ig
    }
}

/** Chart scale maximums (design spec "Charts": next nice value above the largest bar). */
object NiceScale {

    private val HOURLY_STEPS = doubleArrayOf(
        200.0, 400.0, 800.0, 1200.0, 1600.0, 2000.0, 3000.0, 4000.0, 6000.0, 8000.0, 10000.0,
    )

    /** Daily bars: 1 km, then the next even km (2, 4, 6, 8, 10 ...). Returned in metres. */
    fun weeklyMaxMetres(largestMetres: Double): Double {
        val km = largestMetres / 1000.0
        if (km <= 1.0) return 1000.0
        return ceil(km / 2.0) * 2.0 * 1000.0
    }

    /** Hourly bars: 200, 400, 800 m and upwards. Returned in metres. */
    fun hourlyMaxMetres(largestMetres: Double): Double {
        for (step in HOURLY_STEPS) if (largestMetres <= step) return step
        return ceil(largestMetres / 2000.0) * 2000.0
    }
}
