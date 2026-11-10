package com.scrollmeter.app

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.inspector.WindowInspector
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.scrollmeter.app.data.SessionEntity
import com.scrollmeter.app.service.ScrollAccessibilityService
import com.scrollmeter.app.service.ServiceStatus
import com.scrollmeter.app.service.SevenSegmentView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * JVM stand-ins (Robolectric) for the emulator tests I1, I3, I4 and I5: the REAL service,
 * tracker, HUD views, Room database and activity run here, driven by fake accessibility
 * events. What this cannot prove is how the real Facebook / Instagram apps behave.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1300dp-xhdpi")
class AppIntegrationTest {

    private val facebook = "com.facebook.katana"
    private val feed = "androidx.recyclerview.widget.RecyclerView"

    /**
     * The settings file is cached for the whole test JVM, so put the HUD style back to the
     * PRD defaults before every test.
     */
    @Before
    fun resetHudStyle() {
        val store = (RuntimeEnvironment.getApplication() as ScrollMeterApp).container.settings
        runBlocking {
            store.setHudDigitHeightDp(26)
            store.setHudColour("#2BEA2B")
            store.setHudOutline(false)
            store.setHudDecimals(false)
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Moves both Robolectric's clock and the real clock, whichever the code under test reads. */
    private fun pass(ms: Long) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(ms))
        Thread.sleep(ms.coerceAtMost(400))
        idle()
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            idle()
            if (condition()) return
            Thread.sleep(25)
        }
        throw AssertionError("timed out waiting for: $what")
    }

    private fun windowEvent(pkg: String, cls: String) =
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = pkg
            className = cls
        }

    private fun scrollEvent(pkg: String, deltaY: Int, deltaX: Int = 0) =
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED).apply {
            packageName = pkg
            className = feed
            scrollDeltaY = deltaY
            scrollDeltaX = deltaX
        }

    private fun hudViews(): List<SevenSegmentView> =
        WindowInspector.getGlobalWindowViews().filterIsInstance<SevenSegmentView>()

    private fun hudTexts(): List<String> = hudViews().map { it.text }

    private fun today(): LocalDate =
        Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun sessionsToday(): List<SessionEntity> = runBlocking {
        val app = RuntimeEnvironment.getApplication() as ScrollMeterApp
        app.container.repository.observeSessions(today()).first()
    }

    private fun startService(): ScrollAccessibilityService {
        val service = Robolectric.buildService(ScrollAccessibilityService::class.java).create().get()
        // Android calls this when the user switches the service on.
        val connect = ScrollAccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
        connect.isAccessible = true
        connect.invoke(service)
        waitUntil("service connected") { ServiceStatus.connected.value }
        return service
    }

    @Test
    fun service_countsScrollsInFacebook_showsHud_hidesOnLeave_andSavesTheSession() {
        val service = startService()
        assertTrue("no HUD before a watched app opens", hudViews().isEmpty())

        // I1 / F1: Facebook comes to the foreground.
        service.onAccessibilityEvent(windowEvent(facebook, "com.facebook.katana.activity.FbMainTabActivity"))
        idle()
        assertEquals("HUD appears with both counters", listOf("0", "0m"), hudTexts().sorted())

        // Three swipes more than 300 ms apart, each made of two quick events (one stroke each).
        repeat(3) {
            service.onAccessibilityEvent(scrollEvent(facebook, 600))
            service.onAccessibilityEvent(scrollEvent(facebook, 600))
            pass(400)
        }
        // Things that must NOT count: upward, horizontal, another app, the notification shade.
        service.onAccessibilityEvent(scrollEvent(facebook, -900))
        service.onAccessibilityEvent(scrollEvent(facebook, 10, deltaX = 800))
        service.onAccessibilityEvent(scrollEvent("com.whatsapp", 900))
        service.onAccessibilityEvent(scrollEvent("com.android.systemui", 900))
        pass(150)

        val texts = hudTexts()
        assertTrue("strokes counter shows 3, was $texts", "3" in texts)
        assertEquals("metres counter is the other view", 1, texts.count { it.endsWith("m") })

        // E3 / E4: the shade and the keyboard do not end tracking; the HUD stays.
        service.onAccessibilityEvent(windowEvent("com.android.systemui", "android.widget.FrameLayout"))
        service.onAccessibilityEvent(
            windowEvent("com.google.android.inputmethod.latin", "android.inputmethodservice.SoftInputWindow")
        )
        idle()
        assertEquals(2, hudViews().size)

        // I3 / E2: switching to another app removes the HUD at once.
        service.onAccessibilityEvent(windowEvent("com.whatsapp", "com.whatsapp.HomeActivity"))
        idle()
        assertTrue("HUD removed after leaving", hudViews().isEmpty())

        // I4 / I5: the session was written and closed in the database.
        waitUntil("session saved") { sessionsToday().isNotEmpty() }
        val session = sessionsToday().single()
        assertEquals("facebook", session.app)
        assertEquals(3, session.strokes)
        assertEquals(3_600L, session.pixels)
        assertNotNull("closed when the user left", session.endedAt)
        assertTrue(session.metres > 0.0)

        // FR5.3 / E2: back within 60 s continues the same session.
        service.onAccessibilityEvent(windowEvent(facebook, "com.facebook.katana.activity.FbMainTabActivity"))
        pass(600)   // past the 500 ms straggler guard
        service.onAccessibilityEvent(scrollEvent(facebook, 700))
        pass(150)
        assertTrue("today's total continues: ${hudTexts()}", "4" in hudTexts())

        // FR1.4 / E1: screen off hides the HUD and ends the session.
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        idle()
        assertTrue("HUD removed on screen off", hudViews().isEmpty())
        waitUntil("resumed session saved") { sessionsToday().singleOrNull()?.strokes == 4 }
        assertNotNull(sessionsToday().single().endedAt)

        // E1: unlocking with Facebook still on top brings the HUD back.
        service.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        idle()
        assertEquals(2, hudViews().size)

        // Switching the service off writes everything and removes the HUD.
        service.onUnbind(null)
        idle()
        assertTrue(hudViews().isEmpty())
        assertTrue(!ServiceStatus.connected.value)
    }

    /** The Settings controls for size, colour, decimals and outline reach the live HUD. */
    @Test
    fun service_appliesHudStyleSettingsToTheLiveHud() {
        val app = RuntimeEnvironment.getApplication() as ScrollMeterApp
        val store = app.container.settings
        val density = app.resources.displayMetrics.density
        val service = startService()
        service.onAccessibilityEvent(windowEvent(facebook, "com.facebook.katana.activity.FbMainTabActivity"))
        idle()

        // Defaults from the PRD: 26 dp, green, no outline, whole metres.
        assertEquals(listOf("0", "0m"), hudTexts().sorted())
        assertTrue(hudViews().all { it.digitHeight == 26f * density && it.color == 0xFF2BEA2B.toInt() && !it.outline })

        runBlocking {
            store.setHudDigitHeightDp(40)
            store.setHudColour("#FFC233")
            store.setHudOutline(true)
            store.setHudDecimals(true)
        }
        waitUntil("HUD restyled") {
            val views = hudViews()
            views.size == 2 && views.all {
                it.digitHeight == 40f * density && it.color == 0xFFFFC233.toInt() && it.outline
            } && "0.0m" in hudTexts()
        }

        // Out-of-range sizes are clamped to the 16 to 48 dp range.
        runBlocking { store.setHudDigitHeightDp(500) }
        waitUntil("size clamped") { hudViews().all { it.digitHeight == 48f * density } }

        service.onUnbind(null)
        idle()
    }

    @Test
    fun service_ignoresAppsThatAreNotWatched() {
        val service = startService()
        service.onAccessibilityEvent(windowEvent("com.whatsapp", "com.whatsapp.HomeActivity"))
        service.onAccessibilityEvent(scrollEvent("com.whatsapp", 900))
        pass(400)
        service.onAccessibilityEvent(scrollEvent("com.google.android.youtube", 900))
        idle()
        assertTrue("no HUD outside watched apps", hudViews().isEmpty())
        service.onUnbind(null)
        idle()
        assertTrue("nothing recorded", sessionsToday().isEmpty())
    }

    /** The real activity starts, and first launch lands on Setup because nothing is granted. */
    @Test
    fun activity_launchesAndOpensSetupFirst() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        // Let DataStore load and Compose settle.
        repeat(40) {
            idle()
            Thread.sleep(25)
        }
        val file = File("build/screenshots/0_first_launch.png")
        file.parentFile?.mkdirs()
        activity.window.decorView.captureRoboImage(file.path)
        assertTrue(file.exists() && file.length() > 0)
        assertNull("activity must not be finishing", activity.takeIf { it.isFinishing })
    }
}
