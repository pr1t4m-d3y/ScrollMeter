package com.scrollmeter.app

import com.scrollmeter.app.data.Aggregator
import com.scrollmeter.app.data.DaySummary
import com.scrollmeter.app.data.Amount
import com.scrollmeter.app.data.HourlyBucketEntity
import com.scrollmeter.app.data.NiceScale
import com.scrollmeter.app.data.Weeks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** PRD unit tests U10 and U11, plus the week maths (FR6.3) and chart scales. */
class AggregatorTest {

    private val sunday = LocalDate.of(2026, 9, 27)
    private val thursday = LocalDate.of(2026, 10, 1)

    private fun bucket(date: LocalDate, hour: Int, app: String, strokes: Int, metres: Double) =
        HourlyBucketEntity(date.toString(), hour, app, strokes, metres)

    @Test
    fun u10_dayTotalIsTheSumOfIts24Buckets() {
        val buckets = ArrayList<HourlyBucketEntity>()
        var expectedMetres = 0.0
        var expectedStrokes = 0
        for (hour in 0..23) {
            buckets.add(bucket(thursday, hour, "facebook", hour + 1, hour * 10.0))
            expectedMetres += hour * 10.0
            expectedStrokes += hour + 1
        }
        // Another day must not leak in.
        buckets.add(bucket(thursday.minusDays(1), 5, "facebook", 500, 5_000.0))

        val day = Aggregator.daySummary(thursday, buckets)
        val hours = Aggregator.hourly(thursday, buckets)

        assertEquals(24, hours.size)
        assertEquals(expectedMetres, day.metres, 1e-9)
        assertEquals(expectedStrokes, day.strokes)
        assertEquals(day.metres, hours.sumOf { it.metres }, 1e-9)
    }

    @Test
    fun facebookLiteIsDrawnAsFacebook_instagramStaysSeparate() {
        val buckets = listOf(
            bucket(thursday, 21, "facebook", 10, 100.0),
            bucket(thursday, 21, "facebook_lite", 5, 40.0),
            bucket(thursday, 21, "instagram", 7, 60.0),
        )
        val day = Aggregator.daySummary(thursday, buckets)
        assertEquals(140.0, day.facebook.metres, 1e-9)
        assertEquals(15, day.facebook.strokes)
        assertEquals(60.0, day.instagram.metres, 1e-9)
        assertEquals(7, day.instagram.strokes)

        val slot = Aggregator.hourly(thursday, buckets)[21]
        assertEquals(140.0, slot.facebookMetres, 1e-9)
        assertEquals(60.0, slot.instagramMetres, 1e-9)
    }

    @Test
    fun u11_weekAverageMidWeekDividesByDaysWithData() {
        val days = Weeks.daysOf(sunday).map { date ->
            when (date) {
                sunday -> DaySummary(date, Amount(10, 1_000.0))
                sunday.plusDays(1) -> DaySummary(date, Amount(10, 2_000.0))
                sunday.plusDays(2) -> DaySummary(date, Amount(10, 3_000.0))
                else -> DaySummary(date)
            }
        }
        // Tuesday of that week: three days of data, four days still to come.
        assertEquals(2_000.0, Aggregator.dailyAverage(days, sunday.plusDays(2)), 1e-9)
        // A whole week later the answer is the same: empty days never dilute it.
        assertEquals(2_000.0, Aggregator.dailyAverage(days, sunday.plusDays(10)), 1e-9)
    }

    @Test
    fun fr6_4_futureDaysAndEmptyWeeksAreHandled() {
        val days = Weeks.daysOf(sunday).map { DaySummary(it, Amount(1, 700.0)) }
        // Only Sunday and Monday have happened.
        assertEquals(700.0, Aggregator.dailyAverage(days, sunday.plusDays(1)), 1e-9)
        assertEquals(0.0, Aggregator.dailyAverage(Weeks.daysOf(sunday).map { DaySummary(it) }, thursday), 0.0)
    }

    @Test
    fun fr6_3_weeksRunSundayToSaturday_labelledByIsoWeekOfMonday() {
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        assertEquals(sunday, Weeks.startOf(thursday))
        assertEquals(sunday, Weeks.startOf(sunday))
        assertEquals(sunday, Weeks.startOf(LocalDate.of(2026, 10, 3)))            // Saturday
        assertEquals(sunday.plusDays(7), Weeks.startOf(LocalDate.of(2026, 10, 4)))
        assertEquals(40, Weeks.isoWeekNumber(sunday))

        val weeks = Weeks.lastFour(thursday)
        assertEquals(listOf(37, 38, 39, 40), weeks.map { Weeks.isoWeekNumber(it) })
        assertEquals(sunday, weeks.last())
        assertEquals(7, Weeks.daysOf(sunday).size)
        assertEquals(LocalDate.of(2026, 10, 3), Weeks.daysOf(sunday).last())
    }

    @Test
    fun weekDaysAlwaysReturnsSevenDaysInOrder() {
        val buckets = listOf(bucket(thursday, 9, "instagram", 3, 30.0))
        val days = Aggregator.weekDays(sunday, buckets)
        assertEquals(7, days.size)
        assertEquals(sunday, days[0].date)
        assertEquals(30.0, days[4].metres, 1e-9)
        assertEquals(0.0, days[3].metres, 0.0)
    }

    @Test
    fun peakHourAndBusiestHour() {
        val buckets = listOf(
            bucket(thursday, 22, "facebook", 5, 300.0),
            bucket(thursday, 9, "instagram", 5, 120.0),
            bucket(sunday, 22, "facebook", 5, 80.0),
            bucket(sunday, 9, "facebook", 5, 200.0),
        )
        val peak = Aggregator.peakHour(Aggregator.hourly(thursday, buckets))
        assertEquals(22, peak?.hour)
        assertEquals(300.0, peak?.metres ?: 0.0, 1e-9)
        assertNull(Aggregator.peakHour(Aggregator.hourly(thursday.plusDays(1), buckets)))

        val heat = Aggregator.heatmap(sunday, buckets)
        assertEquals(7, heat.size)
        assertEquals(24, heat[0].size)
        assertEquals(200.0, heat[0][9], 1e-9)       // Sunday row
        assertEquals(300.0, heat[4][22], 1e-9)      // Thursday row
        // 22:00 has 380 m across the week, 09:00 has 320 m.
        assertEquals(22, Aggregator.busiestHour(heat))
        assertNull(Aggregator.busiestHour(Aggregator.heatmap(sunday.minusWeeks(1), buckets)))
    }

    @Test
    fun heatLevelsMatchTheLegend() {
        assertEquals(0, Aggregator.heatLevel(0.0))
        assertEquals(1, Aggregator.heatLevel(0.1))
        assertEquals(1, Aggregator.heatLevel(24.9))
        assertEquals(2, Aggregator.heatLevel(25.0))
        assertEquals(3, Aggregator.heatLevel(50.0))
        assertEquals(3, Aggregator.heatLevel(99.9))
        assertEquals(4, Aggregator.heatLevel(100.0))
        assertEquals(4, Aggregator.heatLevel(5_000.0))
    }

    @Test
    fun niceScalesRoundUpToTheNextNiceValue() {
        assertEquals(1_000.0, NiceScale.weeklyMaxMetres(0.0), 0.0)
        assertEquals(1_000.0, NiceScale.weeklyMaxMetres(790.0), 0.0)
        assertEquals(2_000.0, NiceScale.weeklyMaxMetres(1_790.0), 0.0)
        assertEquals(4_000.0, NiceScale.weeklyMaxMetres(2_600.0), 0.0)
        assertEquals(4_000.0, NiceScale.weeklyMaxMetres(4_000.0), 0.0)
        assertEquals(6_000.0, NiceScale.weeklyMaxMetres(4_100.0), 0.0)
        assertEquals(12_000.0, NiceScale.weeklyMaxMetres(10_500.0), 0.0)

        assertEquals(200.0, NiceScale.hourlyMaxMetres(0.0), 0.0)
        assertEquals(200.0, NiceScale.hourlyMaxMetres(180.0), 0.0)
        assertEquals(400.0, NiceScale.hourlyMaxMetres(201.0), 0.0)
        assertEquals(800.0, NiceScale.hourlyMaxMetres(640.0), 0.0)
        assertEquals(1_200.0, NiceScale.hourlyMaxMetres(900.0), 0.0)
        assertEquals(12_000.0, NiceScale.hourlyMaxMetres(10_500.0), 0.0)
    }

    @Test
    fun groupTotalsAddUpAcrossDays() {
        val days = listOf(
            DaySummary(sunday, Amount(10, 100.0), Amount(1, 5.0)),
            DaySummary(sunday.plusDays(1), Amount(20, 300.0), Amount(2, 15.0)),
        )
        val (facebook, instagram) = Aggregator.groupTotals(days)
        assertEquals(Amount(30, 400.0), facebook)
        assertEquals(Amount(3, 20.0), instagram)
    }
}
