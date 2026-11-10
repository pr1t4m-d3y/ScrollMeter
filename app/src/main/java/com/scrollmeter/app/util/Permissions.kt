package com.scrollmeter.app.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.scrollmeter.app.data.AppSettings
import com.scrollmeter.app.data.SettingsStore
import com.scrollmeter.app.model.WatchedApp
import com.scrollmeter.app.service.ScrollAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** What Android itself can tell us about the phone's setup. */
data class SystemPermissions(
    val accessibility: Boolean = false,
    val overlay: Boolean = false,
    val battery: Boolean = false,
    /** Optional "Usage access", only used for the FR8.2 health check. */
    val usageAccess: Boolean = false,
    /** Foreground time of the switched-on watched apps today, or 0 without usage access. */
    val watchedUsageMsToday: Long = 0L,
)

/** The five rows of the Setup screen (PRD "Permissions and Realme setup"). */
data class PermissionChecklist(
    val restrictedSettings: Boolean,
    val accessibility: Boolean,
    val overlay: Boolean,
    val autoLaunch: Boolean,
    val battery: Boolean,
) {
    val done: Int
        get() {
            var n = 0
            if (restrictedSettings) n++
            if (accessibility) n++
            if (overlay) n++
            if (autoLaunch) n++
            if (battery) n++
            return n
        }

    val allDone: Boolean get() = done == TOTAL

    companion object {
        const val TOTAL = 5

        fun from(system: SystemPermissions, settings: AppSettings, sdkInt: Int): PermissionChecklist =
            PermissionChecklist(
                // Cannot be read directly. Android 13+ needs it before accessibility can be
                // switched on, so "accessibility is on" proves it. Older versions do not have it.
                restrictedSettings = sdkInt < 33 || system.accessibility,
                accessibility = system.accessibility,
                overlay = system.overlay,
                // Cannot be detected at all: counts once its settings page has been opened.
                autoLaunch = settings.autoLaunchConfirmed,
                battery = system.battery,
            )
    }
}

/** FR8: is counting broken? Pure, unit-tested. */
object Health {
    const val USED_BUT_SILENT_MS = 3 * 60_000L

    fun trackingStopped(system: SystemPermissions, todayStrokes: Int): Boolean {
        // FR8.1 / E20: without the accessibility service nothing is counted.
        if (!system.accessibility) return true
        // FR8.2: a watched app was really used today, yet not one stroke was recorded.
        return system.usageAccess &&
            system.watchedUsageMsToday >= USED_BUT_SILENT_MS &&
            todayStrokes == 0
    }
}

/**
 * Reads permission state from the system. [refresh] runs every time the app returns to the
 * front (`onResume`), so a row flips to a tick as soon as the user comes back from Settings.
 */
class PermissionMonitor(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(readQuick())
    val state: StateFlow<SystemPermissions> = _state

    fun refresh() {
        _state.value = readQuick().copy(watchedUsageMsToday = _state.value.watchedUsageMsToday)
        scope.launch {
            val quick = readQuick()
            val usage = if (quick.usageAccess) watchedUsageMsToday() else 0L
            _state.value = quick.copy(watchedUsageMsToday = usage)
        }
    }

    /** Cheap checks only; safe on the main thread. */
    fun readQuick(): SystemPermissions = SystemPermissions(
        accessibility = isAccessibilityServiceEnabled(),
        overlay = Settings.canDrawOverlays(context),
        battery = isIgnoringBatteryOptimizations(),
        usageAccess = hasUsageAccess(),
    )

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(context, ScrollAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val component = ComponentName.unflattenFromString(splitter.next())
            if (component == expected) return true
        }
        return false
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun hasUsageAccess(): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    /** Sums today's resumed-to-paused time for the watched apps that are switched on. */
    private suspend fun watchedUsageMsToday(): Long = try {
        val settings = settingsStore.settings.first()
        val watched = HashSet<String>()
        for (app in WatchedApp.entries) if (settings.isWatched(app)) watched.add(app.packageName)

        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = usage.queryEvents(start, System.currentTimeMillis())
        val event = UsageEvents.Event()
        val resumedAt = HashMap<String, Long>()
        var total = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg !in watched) continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedAt[pkg] = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val since = resumedAt.remove(pkg)
                    if (since != null) total += event.timeStamp - since
                }
            }
        }
        total
    } catch (e: Exception) {
        Log.w("ScrollMeter", "usage check failed", e)
        0L
    }
}

/** Deep links for the "Allow" buttons. Each tries the most specific page first. */
object PermissionIntents {

    fun appInfo(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )

    fun accessibility(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun overlay(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", context.packageName, null)
        )

    fun battery(context: Context): List<Intent> = listOf(
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.fromParts("package", context.packageName, null)
        ),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appInfo(context),
    )

    /** Realme / ColorOS auto-launch pages differ by version; fall back to App info. */
    fun autoLaunch(context: Context): List<Intent> = listOf(
        component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        component("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
        component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        appInfo(context),
    )

    /** Opens the first intent that works. Returns false if none did. */
    fun open(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Page does not exist on this phone: try the next one.
            }
        }
        return false
    }

    private fun component(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))

    val sdkInt: Int get() = Build.VERSION.SDK_INT
}
