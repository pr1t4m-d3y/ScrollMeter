package com.scrollmeter.app.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.scrollmeter.app.BuildConfig
import com.scrollmeter.app.ScrollMeterApp
import com.scrollmeter.app.data.AppSettings
import com.scrollmeter.app.data.OverlayMode
import com.scrollmeter.app.data.ScrollRepository
import com.scrollmeter.app.data.SettingsStore
import com.scrollmeter.app.model.WatchedApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one background component of the app.
 *
 * In:   accessibility events (window changes + scrolls), screen on/off, clock changes,
 *       settings changes.
 * Out:  numbers to [ScrollTracker], the floating [HudController], database writes.
 *
 * Privacy: it reads only the event type, package name, view class name and scroll numbers.
 * It never calls `getSource()`, never reads text, and `canRetrieveWindowContent` is false.
 */
class ScrollAccessibilityService : AccessibilityService() {

    private sealed interface StoreCommand {
        class Write(val batch: FlushBatch) : StoreCommand
        class Startup(val day: String) : StoreCommand
        class Seed(val day: String) : StoreCommand
    }

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<StoreCommand>(Channel.UNLIMITED)
    private val handler = Handler(Looper.getMainLooper())
    private val interpreter = ScrollEventInterpreter()

    private lateinit var repository: ScrollRepository
    private lateinit var settingsStore: SettingsStore
    private lateinit var config: TrackerConfig
    private lateinit var tracker: ScrollTracker
    private lateinit var hud: HudController
    private var ready = false

    private var settings = AppSettings()
    private var hudColour = DEFAULT_HUD_COLOUR
    private var ignoredPackages: Set<String> = emptySet()
    private var debugLog = false

    /** Last non-ignored window's app if it is a watched one, else null. Kept up to date even while locked. */
    private var foregroundApp: WatchedApp? = null

    /** False while the screen is off or the lock screen is showing. */
    private var interactive = true
    private var lastDeactivatedAt = 0L

    private var visitStartedAt = 0L
    private var visitCounted = false

    private var ticking = false
    private var hudPending = false
    private var lastHudAt = 0L
    private var receiverRegistered = false

    // ------------------------------------------------------------------ lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (ready) return
        val container = (application as ScrollMeterApp).container
        repository = container.repository
        settingsStore = container.settings
        debugLog = BuildConfig.DEBUG || Log.isLoggable(TAG, Log.DEBUG)

        config = TrackerConfig(ydpi = 160f, density = 1f, screenHeightPx = 1)
        readDisplay()
        tracker = ScrollTracker(config, TrackerStore { batch ->
            commands.trySend(StoreCommand.Write(batch))
        })
        tracker.onClockChanged(System.currentTimeMillis())
        hud = HudController(this)
        ignoredPackages = buildIgnoredPackages()
        interactive = !isLocked()

        startWriter()
        commands.trySend(StoreCommand.Startup(tracker.today))
        mainScope.launch {
            settingsStore.settings.collect { onSettingsChanged(it) }
        }
        registerReceivers()

        ready = true
        ServiceStatus.connected.value = true
        if (debugLog) Log.d(TAG, "service connected, ydpi=${config.ydpi} screen=${config.screenHeightPx}")
    }

    override fun onInterrupt() {
        // Nothing to interrupt: this service gives no feedback.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (!ready) return
        ready = false
        ServiceStatus.connected.value = false
        handler.removeCallbacksAndMessages(null)
        ticking = false
        hudPending = false
        // Close the open session and write everything before the process may go away.
        tracker.onScreenOff(System.currentTimeMillis())
        tracker.flush()
        hud.hide()
        if (receiverRegistered) {
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.w(TAG, "receiver already unregistered", e)
            }
            receiverRegistered = false
        }
        mainScope.cancel()
        // Closing (not cancelling) lets the writer finish the queued batches first.
        commands.close()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!ready) return
        readDisplay()      // E6: screen height and dpi follow the new orientation
        hud.relayout()     // I6: counters move to the new top corners
    }

    // ------------------------------------------------------------------ events

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!ready || event == null) return
        val pkg = event.packageName ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScrollEvent(pkg, event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowEvent(pkg, event.className)
        }
    }

    /** FR2. Hot path: no allocations unless debug logging is on. */
    private fun onScrollEvent(pkg: CharSequence, event: AccessibilityEvent) {
        // FR2.1: anything that is not a watched, switched-on app stops here.
        val app = WatchedApp.fromPackage(pkg) ?: return
        if (!settings.isWatched(app)) return

        val now = System.currentTimeMillis()
        if (tracker.activeApp !== app) {
            // Self-heal: a watched app is scrolling, so it is on screen even though we missed
            // (or were misled about) the window change. Skip stragglers right after leaving.
            if (SystemClock.uptimeMillis() - lastDeactivatedAt < HEAL_GUARD_MS) return
            foregroundApp = app
            interactive = true
            syncActive(now)
            if (tracker.activeApp !== app) return
        }

        val className = event.className
        val viewKey = (className?.hashCode() ?: 0) * 31 + event.windowId
        val counted = interpreter.interpret(
            viewKey,
            className,
            event.scrollDeltaX,
            event.scrollDeltaY,
            event.scrollX,
            event.scrollY,
            event.fromIndex,
            event.toIndex,
            config.screenHeightPx,
        )
        if (debugLog) logScroll(app, event, counted)
        if (!counted) return

        val before = tracker.version
        if (interpreter.outPages > 0) {
            tracker.onPage(app, interpreter.outPages, now)
        } else {
            tracker.onScroll(app, interpreter.outPixels, now)
        }
        if (tracker.version != before) {
            if (!visitCounted) {
                visitCounted = true
                if (app !in settings.scrollSeenApps) {
                    mainScope.launch { settingsStore.markScrollSeen(app) }
                }
            }
            requestHudRefresh()
        }
    }

    /** FR1: track which app owns the foreground window. */
    private fun onWindowEvent(pkg: CharSequence, className: CharSequence?) {
        val name = pkg.toString()
        if (isIgnoredWindow(name, className)) return   // FR1.3
        val app = WatchedApp.fromPackage(name)
        if (debugLog) Log.d(TAG, "window pkg=$name cls=$className watched=${app != null}")
        if (app !== foregroundApp) interpreter.reset()
        foregroundApp = app
        syncActive(System.currentTimeMillis())
    }

    /**
     * FR1.3 / E3 / E4 / E22: windows that must not look like "the user left the app":
     * keyboards, the system UI (notification shade, volume panel), system dialogs, and our own
     * HUD windows. Our own activity is NOT ignored: opening Scroll Meter does leave Facebook.
     */
    private fun isIgnoredWindow(pkg: String, className: CharSequence?): Boolean {
        if (pkg == packageName) {
            return className == null || !className.toString().endsWith("MainActivity")
        }
        if (pkg == "com.android.systemui" || pkg == "android") return true
        if (pkg in ignoredPackages) return true
        if (className != null) {
            val cls = className.toString()
            if (cls.contains("InputMethod") || cls.contains("SoftInputWindow")) return true
        }
        return false
    }

    private fun buildIgnoredPackages(): Set<String> {
        val set = HashSet<String>()
        set.add("com.google.android.inputmethod.latin")
        set.add("com.touchtype.swiftkey")
        set.add("com.samsung.android.honeyboard")
        set.add("com.baidu.input_oppo")
        set.add("com.sohu.inputmethod.sogouoem")
        set.add("com.emoji.keyboard.touchpal")
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            for (info in imm.enabledInputMethodList) set.add(info.packageName)
        } catch (e: Exception) {
            Log.w(TAG, "could not list keyboards", e)
        }
        try {
            val current = Settings.Secure.getString(
                contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
            )
            if (!current.isNullOrEmpty()) set.add(current.substringBefore('/'))
        } catch (e: Exception) {
            Log.w(TAG, "could not read current keyboard", e)
        }
        return set
    }

    // ------------------------------------------------------------------ active state

    /**
     * Makes the tracker, the 10 s ticker and the HUD match the current facts:
     * tracking is active only when the screen is unlocked AND the foreground app is a watched
     * app that is switched on (FR1.2).
     */
    private fun syncActive(now: Long) {
        val fg = foregroundApp
        val want = if (interactive && fg != null && settings.isWatched(fg)) fg else null
        val current = tracker.activeApp
        if (want !== current) {
            if (current != null) {
                endVisit(current, now)
                tracker.onAppLeft(now)
                lastDeactivatedAt = SystemClock.uptimeMillis()
            }
            if (want != null) {
                tracker.onAppEntered(want, now)
                visitStartedAt = now
                visitCounted = false
            }
        }
        if (want != null) startTicker() else stopTicker()
        refreshHudNow()
    }

    private fun onScreenOff() {
        val now = System.currentTimeMillis()
        interactive = false
        val current = tracker.activeApp
        if (current != null) endVisit(current, now)
        tracker.onScreenOff(now)   // FR1.4 / E1: session ends now and will not resume
        lastDeactivatedAt = SystemClock.uptimeMillis()
        stopTicker()
        refreshHudNow()
    }

    /** E15: remember visits where a watched app was open but never produced a counted scroll. */
    private fun endVisit(app: WatchedApp, now: Long) {
        if (visitCounted || app in settings.scrollSeenApps) return
        val ms = now - visitStartedAt
        if (ms in MIN_QUIET_VISIT_MS..MAX_QUIET_VISIT_MS) {
            mainScope.launch { settingsStore.addQuietVisit(app, ms) }
        }
    }

    private fun onSettingsChanged(new: AppSettings) {
        settings = new
        hudColour = try {
            Color.parseColor(new.hudColour)
        } catch (e: IllegalArgumentException) {
            DEFAULT_HUD_COLOUR
        }
        if (ready) syncActive(System.currentTimeMillis())
    }

    private fun isLocked(): Boolean {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguard.isKeyguardLocked
    }

    // ------------------------------------------------------------------ ticker (only while active)

    private val tick = object : Runnable {
        override fun run() {
            if (!ready || tracker.activeApp == null) {
                ticking = false
                return
            }
            tracker.onTick(System.currentTimeMillis())   // idle check + 10 s flush
            refreshHudNow()
            handler.postDelayed(this, ScrollTracker.FLUSH_INTERVAL_MS)
        }
    }

    private fun startTicker() {
        if (ticking) return
        ticking = true
        handler.postDelayed(tick, ScrollTracker.FLUSH_INTERVAL_MS)
    }

    private fun stopTicker() {
        if (!ticking) return
        ticking = false
        handler.removeCallbacks(tick)
    }

    // ------------------------------------------------------------------ HUD

    private val hudRunnable = Runnable {
        hudPending = false
        refreshHudNow()
    }

    /** FR7.2: at most one HUD update every 100 ms. */
    private fun requestHudRefresh() {
        if (hudPending) return
        val wait = lastHudAt + HUD_MIN_INTERVAL_MS - SystemClock.uptimeMillis()
        if (wait <= 0) {
            refreshHudNow()
        } else {
            hudPending = true
            handler.postDelayed(hudRunnable, wait)
        }
    }

    private fun refreshHudNow() {
        if (hudPending) {
            handler.removeCallbacks(hudRunnable)
            hudPending = false
        }
        lastHudAt = SystemClock.uptimeMillis()
        // FR7.1: shown only while tracking is active and the overlay switch is on.
        if (tracker.activeApp == null || !settings.overlayEnabled) {
            hud.hide()
            return
        }
        hud.setStyle(settings.hudDigitHeightDp, hudColour, settings.hudOutline)
        val strokes: Int
        val metres: Double
        if (settings.overlayMode == OverlayMode.SESSION) {   // FR7.3
            strokes = tracker.sessionStrokes
            metres = tracker.sessionMetres
        } else {
            strokes = tracker.todayStrokes
            metres = tracker.todayMetres
        }
        hud.show(HudFormat.strokes(strokes), HudFormat.metres(metres, settings.hudDecimals))
    }

    // ------------------------------------------------------------------ system broadcasts

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!ready) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> {
                    // With no lock screen there is no USER_PRESENT, so resume here.
                    if (!isLocked()) {
                        interactive = true
                        syncActive(System.currentTimeMillis())
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    // E1: unlocked. If a watched app is still on top, a new session can start.
                    interactive = true
                    syncActive(System.currentTimeMillis())
                }
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED -> {
                    // E17: use the local date at the time of each event from now on.
                    tracker.onClockChanged(System.currentTimeMillis())
                    commands.trySend(StoreCommand.Seed(tracker.today))
                    refreshHudNow()
                }
            }
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    // ------------------------------------------------------------------ database writer

    /**
     * One coroutine handles every database command in order, on a background thread.
     * The tracker only ever queues work, so the event handler never waits for disk.
     */
    private fun startWriter() {
        writerScope.launch {
            for (command in commands) {
                try {
                    when (command) {
                        is StoreCommand.Write -> repository.persist(command.batch)
                        is StoreCommand.Startup -> {
                            repository.closeDanglingSessions()   // E13
                            seedToday(command.day)
                        }
                        is StoreCommand.Seed -> seedToday(command.day)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "database command failed", e)
                }
            }
        }
    }

    /** Loads what is already saved for today so "Today's total" continues after a restart. */
    private suspend fun seedToday(day: String) {
        val totals = repository.totalsForDay(day)
        withContext(Dispatchers.Main) {
            tracker.seedToday(day, totals.strokes, totals.metres)
            if (ready) requestHudRefresh()
        }
    }

    // ------------------------------------------------------------------ helpers

    /** FR4.1 / FR4.2: physical vertical dpi, with the densityDpi fallback. */
    private fun readDisplay() {
        val metrics = resources.displayMetrics
        config.ydpi = Distance.effectiveDpi(metrics.ydpi, metrics.densityDpi)
        config.density = metrics.density
        config.screenHeightPx = try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.currentWindowMetrics.bounds.height()
        } catch (e: Exception) {
            metrics.heightPixels
        }
        if (config.screenHeightPx <= 0) config.screenHeightPx = metrics.heightPixels
    }

    /** E14: numbers only, so a Facebook / Instagram update that changes its views is easy to spot. */
    private fun logScroll(app: WatchedApp, event: AccessibilityEvent, counted: Boolean) {
        Log.d(
            TAG,
            "scroll app=${app.key} cls=${event.className} dx=${event.scrollDeltaX} " +
                "dy=${event.scrollDeltaY} x=${event.scrollX} y=${event.scrollY} " +
                "from=${event.fromIndex} to=${event.toIndex} counted=$counted " +
                "px=${interpreter.outPixels} pages=${interpreter.outPages}"
        )
    }

    private companion object {
        const val TAG = "ScrollMeter"
        val DEFAULT_HUD_COLOUR = 0xFF2BEA2B.toInt()
        const val HUD_MIN_INTERVAL_MS = 100L
        const val HEAL_GUARD_MS = 500L
        const val MIN_QUIET_VISIT_MS = 5_000L
        const val MAX_QUIET_VISIT_MS = 3_600_000L
    }
}
