package com.scrollmeter.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.scrollmeter.app.data.AppSettings
import com.scrollmeter.app.data.HourlyBucketEntity
import com.scrollmeter.app.data.SessionEntity
import com.scrollmeter.app.model.WatchedApp
import com.scrollmeter.app.service.SevenSegmentView
import com.scrollmeter.app.ui.components.LocalAnimateCharts
import com.scrollmeter.app.ui.day.DayContent
import com.scrollmeter.app.ui.day.DayUiState
import com.scrollmeter.app.ui.settings.SettingsContent
import com.scrollmeter.app.ui.settings.SettingsUiState
import com.scrollmeter.app.ui.theme.ScrollColors
import com.scrollmeter.app.ui.theme.ScrollMeterTheme
import com.scrollmeter.app.ui.today.TodayContent
import com.scrollmeter.app.ui.today.TodayUiState
import com.scrollmeter.app.ui.weekly.WeeklyContent
import com.scrollmeter.app.ui.weekly.WeeklyUiState
import com.scrollmeter.app.util.PermissionChecklist
import com.scrollmeter.app.util.SystemPermissions
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Renders the real Compose screens and the real HUD view on the JVM (Robolectric) and saves
 * them as PNGs in `app/build/screenshots/`. This is the visual half of instrumented test I7
 * for machines without a phone or emulator: open the PNGs and compare with the design spec.
 *
 * Each test also fails if a screen throws while composing or drawing.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1700dp-xhdpi")
class ScreenshotTest {

    private val utc = ZoneOffset.UTC
    private val sunday = LocalDate.of(2026, 9, 27)
    private val thursday = LocalDate.of(2026, 10, 1)   // "today"
    private val granted = SystemPermissions(accessibility = true, overlay = true, battery = true)

    private fun bucket(date: LocalDate, hour: Int, app: String, strokes: Int, metres: Double) =
        HourlyBucketEntity(date.toString(), hour, app, strokes, metres)

    private fun at(date: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(date.year, date.monthValue, date.dayOfMonth, hour, minute, 0, 0, utc)
            .toInstant().toEpochMilli()

    private fun session(
        id: Long, h1: Int, m1: Int, h2: Int, m2: Int, app: String, strokes: Int, metres: Double,
    ) = SessionEntity(
        id = id,
        app = app,
        startedAt = at(thursday, h1, m1),
        endedAt = at(thursday, h2, m2),
        strokes = strokes,
        pixels = 0,
        metres = metres,
        day = thursday.toString(),
        lastScrollAt = at(thursday, h2, m2),
    )

    /** Thursday adds up to the mockup's headline numbers: 1.79 km and 1,412 strokes. */
    private val buckets = listOf(
        // Sunday 27 Sep
        bucket(sunday, 10, "facebook", 300, 420.0),
        bucket(sunday, 21, "facebook", 700, 980.0),
        bucket(sunday, 22, "instagram", 500, 700.0),
        bucket(sunday, 23, "facebook", 350, 500.0),
        // Monday
        bucket(sunday.plusDays(1), 8, "instagram", 60, 80.0),
        bucket(sunday.plusDays(1), 21, "facebook", 600, 760.0),
        bucket(sunday.plusDays(1), 22, "facebook", 440, 560.0),
        // Tuesday
        bucket(sunday.plusDays(2), 13, "facebook", 200, 260.0),
        bucket(sunday.plusDays(2), 20, "instagram", 520, 640.0),
        bucket(sunday.plusDays(2), 21, "facebook", 900, 1_150.0),
        bucket(sunday.plusDays(2), 22, "facebook", 800, 1_050.0),
        // Wednesday
        bucket(sunday.plusDays(3), 7, "facebook", 30, 40.0),
        bucket(sunday.plusDays(3), 22, "facebook", 500, 620.0),
        bucket(sunday.plusDays(3), 23, "instagram", 190, 240.0),
        // Thursday (today)
        bucket(thursday, 7, "facebook", 90, 120.0),
        bucket(thursday, 13, "instagram", 160, 210.0),
        bucket(thursday, 21, "facebook", 400, 520.0),
        bucket(thursday, 22, "facebook", 520, 640.0),
        bucket(thursday, 22, "instagram", 150, 180.0),
        bucket(thursday, 23, "facebook_lite", 92, 120.0),
        // Last week
        bucket(sunday.minusDays(5), 21, "facebook", 2_000, 2_900.0),
        bucket(sunday.minusDays(4), 22, "instagram", 1_500, 2_300.0),
        bucket(sunday.minusDays(2), 22, "facebook", 3_000, 4_100.0),
        bucket(sunday.minusDays(1), 20, "facebook", 2_500, 3_500.0),
    )

    private val sessions = listOf(
        session(1, 7, 12, 7, 25, "facebook", 90, 120.0),
        session(2, 13, 2, 13, 20, "instagram", 160, 210.0),
        session(3, 21, 5, 21, 43, "facebook", 400, 520.0),
        session(4, 22, 10, 23, 8, "facebook", 612, 760.0),
        session(5, 23, 20, 23, 21, "facebook_lite", 2, 3.0),     // hidden: under 3 strokes
    )

    private fun shoot(name: String, content: @Composable () -> Unit) {
        val file = File("build/screenshots/$name.png")
        file.parentFile?.mkdirs()
        captureRoboImage(file.path) {
            ScrollMeterTheme {
                // Charts draw at full height instead of animating up from zero.
                CompositionLocalProvider(LocalAnimateCharts provides false) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ScrollColors.Bg),
                    ) {
                        content()
                    }
                }
            }
        }
        assertTrue("screenshot was not written: $file", file.exists() && file.length() > 0)
    }

    @Test
    fun today() {
        val state = TodayUiState.build(thursday, thursday, buckets, sessions, granted, utc)
        shoot("1_today") { TodayContent(state, {}, {}, {}, {}) }
    }

    @Test
    fun todayEmptyWithTrackingStopped() {
        val state = TodayUiState.build(
            thursday, thursday, emptyList(), emptyList(), SystemPermissions(accessibility = false), utc,
        )
        shoot("1_today_empty_stopped") { TodayContent(state, {}, {}, {}, {}) }
    }

    @Test
    fun todayShowingAPastDay() {
        val state = TodayUiState.build(thursday, sunday.plusDays(2), buckets, emptyList(), granted, utc)
        shoot("1_today_past_day") { TodayContent(state, {}, {}, {}, {}) }
    }

    @Test
    fun dayDetail() {
        val state = DayUiState.build(thursday, buckets, sessions, utc)
        shoot("2_day") { DayContent(state, thursday, thursday, {}, {}) }
    }

    @Test
    fun weeklyReport() {
        val state = WeeklyUiState.build(thursday, 3, buckets, null)
        shoot("3_weekly") { WeeklyContent(state, {}, {}, {}, {}) }
    }

    @Test
    fun weeklyReportEarlierWeekWithFirstBarSelected() {
        val state = WeeklyUiState.build(thursday, 2, buckets, sunday.minusDays(7))
        shoot("3_weekly_w39_first_bar") { WeeklyContent(state, {}, {}, {}, {}) }
    }

    @Test
    fun settingsThreeOfFive() {
        val settings = AppSettings()
        val state = SettingsUiState(
            settings = settings,
            checklist = PermissionChecklist.from(
                SystemPermissions(accessibility = true, overlay = true), settings, sdkInt = 34,
            ),
        )
        shoot("4_settings_3_of_5") { SettingsContent(state, {}, { _, _ -> }, {}, {}, {}) }
    }

    @Test
    fun settingsAllDoneWithUnsupportedAppAndSessionMode() {
        val settings = AppSettings(
            autoLaunchConfirmed = true,
            overlayMode = com.scrollmeter.app.data.OverlayMode.SESSION,
            hudDigitHeightDp = 34,
            hudColour = "#FFC233",
            hudOutline = true,
            unsupportedApps = setOf(WatchedApp.FACEBOOK_LITE),
        )
        val state = SettingsUiState(
            settings = settings,
            checklist = PermissionChecklist.from(granted, settings, sdkInt = 34),
        )
        shoot("4_settings_5_of_5") { SettingsContent(state, {}, { _, _ -> }, {}, {}, {}) }
    }

    /** Screen 5: the HUD digits, drawn by the real view onto a grey "video" background. */
    @Test
    fun hudDigits() {
        val context: android.content.Context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        val samples = listOf("12" to false, "80m" to false, "1412" to false, "0123456789" to false, "80.4m" to true)

        val views = samples.map { (text, outline) ->
            SevenSegmentView(context).apply {
                this.text = text
                this.outline = outline
                measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                layout(0, 0, measuredWidth, measuredHeight)
            }
        }
        val margin = (14 * density).toInt()
        val width = views.maxOf { it.measuredWidth } + margin * 2
        val height = views.sumOf { it.measuredHeight + margin } + margin
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(90, 90, 96))
        var y = margin
        for (view in views) {
            canvas.save()
            canvas.translate(margin.toFloat(), y.toFloat())
            view.draw(canvas)
            canvas.restore()
            y += view.measuredHeight + margin
        }
        val file = File("build/screenshots/5_hud_digits.png")
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0)

        // 26 dp digit height, width 0.6 x height (design spec "Floating HUD").
        val twoDigits = views[0]
        assertTrue(twoDigits.measuredHeight in (26 * density).toInt()..(26 * density).toInt() + 1)
    }
}
