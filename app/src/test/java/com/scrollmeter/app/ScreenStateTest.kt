package com.scrollmeter.app

import com.scrollmeter.app.data.AppSettings
import com.scrollmeter.app.data.HourlyBucketEntity
import com.scrollmeter.app.data.SessionEntity
import com.scrollmeter.app.ui.day.DayUiState
import com.scrollmeter.app.ui.today.TodayUiState
import com.scrollmeter.app.ui.weekly.WeeklyText
import com.scrollmeter.app.ui.weekly.WeeklyUiState
import com.scrollmeter.app.util.Health
import com.scrollmeter.app.util.PermissionChecklist
import com.scrollmeter.app.util.SystemPermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The JVM half of instrumented test I7: Today, Day and Weekly screens with seeded data show
 * the correct numbers. (Bar heights are these numbers divided by the chart scale.)
 * Also FR8 (health check) and the Setup checklist count.
 */
class ScreenStateTest {

    private val utc = ZoneOffset.UTC
    private val sunday = LocalDate.of(2026, 9, 27)
    private val thursday = LocalDate.of(2026, 10, 1)      // "today" in these tests
    private val allGranted = SystemPermissions(accessibility = true, overlay = true, battery = true)

    private fun bucket(date: LocalDate, hour: Int, app: String, strokes: Int, metres: Double) =
        HourlyBucketEntity(date.toString(), hour, app, strokes, metres)

    private fun at(date: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(date.year, date.monthValue, date.dayOfMonth, hour, minute, 0, 0, utc)
            .toInstant().toEpochMilli()

    private fun session(id: Long, date: LocalDate, hour: Int, minutes: Int, strokes: Int, app: String) =
        SessionEntity(
            id = id,
            app = app,
            startedAt = at(date, hour, 0),
            endedAt = at(date, hour, minutes),
            strokes = strokes,
            pixels = 0,
            metres = strokes * 1.0,
            day = date.toString(),
            lastScrollAt = at(date, hour, minutes),
        )

    /** Two weeks of seeded data. */
    private val seeded = listOf(
        // This week (27 Sep to 3 Oct)
        bucket(sunday, 21, "facebook", 300, 900.0),
        bucket(sunday.plusDays(1), 22, "facebook", 500, 1_500.0),
        bucket(sunday.plusDays(1), 22, "instagram", 100, 300.0),
        bucket(thursday, 9, "instagram", 200, 400.0),
        bucket(thursday, 22, "facebook", 600, 1_190.0),
        bucket(thursday, 23, "facebook_lite", 100, 200.0),
        // Last week (20 to 26 Sep)
        bucket(sunday.minusDays(3), 22, "facebook", 900, 5_000.0),
        bucket(sunday.minusDays(1), 20, "instagram", 300, 1_490.0),
    )

    // ---------------------------------------------------------------- Today

    @Test
    fun i7_todayScreenNumbers() {
        val sessions = listOf(
            session(1, thursday, 9, 12, strokes = 200, app = "instagram"),
            session(2, thursday, 22, 40, strokes = 600, app = "facebook"),
            session(3, thursday, 23, 1, strokes = 2, app = "facebook_lite"),   // hidden: under 3 strokes
        )
        val state = TodayUiState.build(thursday, thursday, seeded, sessions, allGranted, utc)

        assertFalse(state.loading)
        assertTrue(state.isToday)
        assertEquals(7, state.weekDays.size)
        assertEquals(1_790.0, state.day.metres, 1e-9)                // hero: "1.79 km"
        assertEquals(1_390.0, state.day.facebook.metres, 1e-9)       // Facebook + Facebook Lite
        assertEquals(400.0, state.day.instagram.metres, 1e-9)
        assertEquals(900, state.day.strokes)
        assertEquals(listOf(900.0, 1_800.0, 0.0, 0.0, 1_790.0, 0.0, 0.0), state.weekDays.map { it.metres })
        assertEquals(listOf(1L, 2L), state.sessions.map { it.id })
        assertFalse(state.trackingStopped)
    }

    @Test
    fun todayScreenShowingAPastDay() {
        val state = TodayUiState.build(thursday, sunday.plusDays(1), seeded, emptyList(), allGranted, utc)
        assertFalse(state.isToday)
        assertEquals(1_800.0, state.day.metres, 1e-9)
        assertEquals(1_500.0, state.day.facebook.metres, 1e-9)
        assertEquals(300.0, state.day.instagram.metres, 1e-9)
    }

    @Test
    fun e21_firstDayIsAnEmptyState() {
        val state = TodayUiState.build(thursday, thursday, emptyList(), emptyList(), allGranted, utc)
        assertEquals(0.0, state.day.metres, 0.0)
        assertEquals(0, state.day.strokes)
        assertTrue(state.sessions.isEmpty())
        assertEquals(7, state.weekDays.size)
    }

    // ---------------------------------------------------------------- Day detail

    @Test
    fun i7_dayScreenNumbers() {
        val sessions = listOf(
            session(1, thursday, 9, 12, strokes = 200, app = "instagram"),
            session(2, thursday, 22, 40, strokes = 600, app = "facebook"),
        )
        val state = DayUiState.build(thursday, seeded, sessions, utc)

        assertEquals(1_790.0, state.summary.metres, 1e-9)
        assertEquals(900, state.summary.strokes)
        assertEquals(24, state.hours.size)
        assertEquals(400.0, state.hours[9].instagramMetres, 1e-9)
        assertEquals(1_190.0, state.hours[22].facebookMetres, 1e-9)
        assertEquals(200.0, state.hours[23].facebookMetres, 1e-9)
        assertEquals(0.0, state.hours[10].metres, 0.0)               // empty hour: grey stub
        assertEquals(22, state.peak?.hour)                           // Peak hour tile
        assertEquals(2L, state.longest?.id)                          // Longest session tile
        assertEquals("22:00 – 22:40", state.longest?.timeRange)
    }

    @Test
    fun dayScreenWithNoData() {
        val state = DayUiState.build(thursday.plusDays(1), seeded, emptyList(), utc)
        assertEquals(0.0, state.summary.metres, 0.0)
        assertNull(state.peak)
        assertNull(state.longest)
    }

    // ---------------------------------------------------------------- Weekly report

    @Test
    fun i7_weeklyScreenNumbers() {
        val state = WeeklyUiState.build(thursday, 3, seeded, null)

        assertEquals(listOf("W37", "W38", "W39", "W40"), state.tabLabels)
        assertEquals("27 Sep – 3 Oct (Week 40)", state.rangeLabel)
        assertTrue(state.isCurrentWeek)
        assertEquals(40, state.weekNumber)
        // 900 + 1,800 + 1,790 over the three days that have data.
        assertEquals(4_490.0, state.thisWeekMetres, 1e-9)
        assertEquals(4_490.0 / 3, state.averageMetres, 1e-9)
        assertEquals(6_490.0, state.lastWeekMetres, 1e-9)
        assertEquals(thursday, state.selectedDay)                    // default bar = today
        // 22:00 column: 1,500 + 300 + 1,190 = 2,990 m, the busiest of the week.
        assertEquals(22, state.busiestHour)
        assertEquals(1_800.0, state.heat[1][22], 1e-9)               // Monday row
        assertEquals(3_790.0, state.facebook.metres, 1e-9)
        assertEquals(1_500, state.facebook.strokes)
        assertEquals(700.0, state.instagram.metres, 1e-9)
        assertEquals(300, state.instagram.strokes)
    }

    @Test
    fun weeklyScreenForAnEarlierWeek_defaultsToItsHighestDay() {
        val state = WeeklyUiState.build(thursday, 2, seeded, null)

        assertEquals("20 Sep – 26 Sep (Week 39)", state.rangeLabel)
        assertFalse(state.isCurrentWeek)
        assertEquals(6_490.0, state.thisWeekMetres, 1e-9)
        assertEquals(0.0, state.lastWeekMetres, 0.0)
        assertEquals(sunday.minusDays(3), state.selectedDay)
        assertEquals(6_490.0 / 2, state.averageMetres, 1e-9)
    }

    @Test
    fun weeklyScreenTappedBarMovesTheTooltip_butNeverToAFutureOrForeignDay() {
        assertEquals(sunday, WeeklyUiState.build(thursday, 3, seeded, sunday).selectedDay)
        // Saturday is still in the future: stay on today.
        assertEquals(thursday, WeeklyUiState.build(thursday, 3, seeded, sunday.plusDays(6)).selectedDay)
        // A date from another week is ignored.
        assertEquals(thursday, WeeklyUiState.build(thursday, 3, seeded, sunday.minusDays(2)).selectedDay)
    }

    @Test
    fun comparisonSentence() {
        assertEquals(
            "You scrolled 4.49 km this week, 2.00 km less than last week.",
            WeeklyText.comparison(4_490.0, 6_490.0, isCurrentWeek = true, weekNumber = 40),
        )
        assertEquals(
            "You scrolled 6.49 km in week 39, 2.00 km more than the week before.",
            WeeklyText.comparison(6_490.0, 4_490.0, isCurrentWeek = false, weekNumber = 39),
        )
        assertEquals(
            "You scrolled 1.00 km this week, the same as last week.",
            WeeklyText.comparison(1_000.0, 1_000.2, isCurrentWeek = true, weekNumber = 40),
        )
        assertEquals(
            "You scrolled 1.00 km this week. There is nothing from last week to compare with.",
            WeeklyText.comparison(1_000.0, 0.0, isCurrentWeek = true, weekNumber = 40),
        )
        assertEquals(
            "No scrolling recorded this week or last week.",
            WeeklyText.comparison(0.0, 0.0, isCurrentWeek = true, weekNumber = 40),
        )
    }

    // ---------------------------------------------------------------- FR8 health + Setup checklist

    @Test
    fun fr8_1_accessibilityOffMeansTrackingStopped() {
        assertTrue(Health.trackingStopped(SystemPermissions(accessibility = false), todayStrokes = 500))
        assertFalse(Health.trackingStopped(SystemPermissions(accessibility = true), todayStrokes = 0))
    }

    @Test
    fun fr8_2_watchedAppUsedTodayButNothingCounted() {
        val used = SystemPermissions(
            accessibility = true,
            usageAccess = true,
            watchedUsageMsToday = 10 * 60_000L,
        )
        assertTrue(Health.trackingStopped(used, todayStrokes = 0))
        assertFalse(Health.trackingStopped(used, todayStrokes = 12))
        // A quick glance at Facebook is not enough to raise the alarm.
        assertFalse(Health.trackingStopped(used.copy(watchedUsageMsToday = 20_000L), todayStrokes = 0))
        // Without usage access the optional check is simply skipped.
        assertFalse(Health.trackingStopped(used.copy(usageAccess = false), todayStrokes = 0))
    }

    @Test
    fun trackingStoppedCardFollowsTodayEvenWhenAPastDayIsSelected() {
        val off = SystemPermissions(accessibility = false)
        assertTrue(TodayUiState.build(thursday, sunday, seeded, emptyList(), off, utc).trackingStopped)
    }

    @Test
    fun setupChecklistCountsFiveRows() {
        val none = PermissionChecklist.from(SystemPermissions(), AppSettings(), sdkInt = 34)
        assertEquals(0, none.done)
        assertFalse(none.allDone)

        // Accessibility on proves "Allow restricted settings" on Android 13+: 2 rows at once.
        val accessibilityOnly =
            PermissionChecklist.from(SystemPermissions(accessibility = true), AppSettings(), sdkInt = 34)
        assertEquals(2, accessibilityOnly.done)

        // Android 11 and 12 have no restricted-settings step, so that row is already done.
        assertEquals(1, PermissionChecklist.from(SystemPermissions(), AppSettings(), sdkInt = 30).done)

        val all = PermissionChecklist.from(
            SystemPermissions(accessibility = true, overlay = true, battery = true),
            AppSettings(autoLaunchConfirmed = true),
            sdkInt = 34,
        )
        assertEquals(5, all.done)
        assertTrue(all.allDone)
    }
}
