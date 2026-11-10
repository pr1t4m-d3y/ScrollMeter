package com.scrollmeter.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scrollmeter.app.model.WatchedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** FR7.3: what the HUD counters show. */
enum class OverlayMode(val key: String) {
    TODAY("today"),
    SESSION("session");

    companion object {
        fun fromKey(key: String?): OverlayMode = if (key == SESSION.key) SESSION else TODAY
    }
}

/** Snapshot of every setting (PRD "Data model", `settings`). Defaults match the PRD table. */
data class AppSettings(
    val watchFacebook: Boolean = true,
    val watchFacebookLite: Boolean = true,
    val watchInstagram: Boolean = true,
    val overlayEnabled: Boolean = true,
    val overlayMode: OverlayMode = OverlayMode.TODAY,
    val hudDigitHeightDp: Int = 26,
    val hudColour: String = "#2BEA2B",
    val hudDecimals: Boolean = false,
    val hudOutline: Boolean = false,
    /** Auto launch cannot be detected, so it counts as done once its page has been opened. */
    val autoLaunchConfirmed: Boolean = false,
    /** Apps that have been open for a long time without ever sending a usable scroll (E15). */
    val unsupportedApps: Set<WatchedApp> = emptySet(),
    /** Apps that have produced at least one counted scroll. */
    val scrollSeenApps: Set<WatchedApp> = emptySet(),
) {
    /** FR1.2: is this app switched on in "Apps to watch"? */
    fun isWatched(app: WatchedApp): Boolean = when (app) {
        WatchedApp.FACEBOOK -> watchFacebook
        WatchedApp.FACEBOOK_LITE -> watchFacebookLite
        WatchedApp.INSTAGRAM -> watchInstagram
    }
}

/** DataStore (Preferences) wrapper. Shared by the app screens and the service. */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { read(it) }.distinctUntilChanged()

    suspend fun setWatched(app: WatchedApp, on: Boolean) {
        store.edit { it[watchKey(app)] = on }
    }

    suspend fun setOverlayEnabled(on: Boolean) {
        store.edit { it[OVERLAY_ENABLED] = on }
    }

    suspend fun setOverlayMode(mode: OverlayMode) {
        store.edit { it[OVERLAY_MODE] = mode.key }
    }

    suspend fun setAutoLaunchConfirmed(done: Boolean) {
        store.edit { it[AUTO_LAUNCH_CONFIRMED] = done }
    }

    /** HUD digit height in dp, kept inside the design spec's 16 to 48 range. */
    suspend fun setHudDigitHeightDp(dp: Int) {
        store.edit { it[HUD_DIGIT_HEIGHT_DP] = dp.coerceIn(HUD_MIN_HEIGHT_DP, HUD_MAX_HEIGHT_DP) }
    }

    /** HUD colour as `#RRGGBB`; one of [HUD_COLOURS]. */
    suspend fun setHudColour(hex: String) {
        store.edit { it[HUD_COLOUR] = hex }
    }

    /** Metres with one decimal ("80.4m") instead of whole metres ("80m"). */
    suspend fun setHudDecimals(on: Boolean) {
        store.edit { it[HUD_DECIMALS] = on }
    }

    /** Thin black edge around the digits, for bright videos. */
    suspend fun setHudOutline(on: Boolean) {
        store.edit { it[HUD_OUTLINE] = on }
    }

    /** The app produced a counted scroll: it is supported, forget any "quiet" history. */
    suspend fun markScrollSeen(app: WatchedApp) {
        store.edit {
            it[scrollSeenKey(app)] = true
            it[quietMsKey(app)] = 0L
            it[quietVisitsKey(app)] = 0
        }
    }

    /** The app was in the foreground for [ms] without a single counted scroll. */
    suspend fun addQuietVisit(app: WatchedApp, ms: Long) {
        store.edit {
            if (it[scrollSeenKey(app)] == true) return@edit
            it[quietMsKey(app)] = (it[quietMsKey(app)] ?: 0L) + ms
            it[quietVisitsKey(app)] = (it[quietVisitsKey(app)] ?: 0) + 1
        }
    }

    private fun read(p: Preferences): AppSettings {
        val unsupported = HashSet<WatchedApp>()
        val seen = HashSet<WatchedApp>()
        for (app in WatchedApp.entries) {
            if (p[scrollSeenKey(app)] == true) {
                seen.add(app)
            } else if ((p[quietMsKey(app)] ?: 0L) >= UNSUPPORTED_AFTER_MS &&
                (p[quietVisitsKey(app)] ?: 0) >= UNSUPPORTED_AFTER_VISITS
            ) {
                unsupported.add(app)
            }
        }
        return AppSettings(
            watchFacebook = p[watchKey(WatchedApp.FACEBOOK)] ?: true,
            watchFacebookLite = p[watchKey(WatchedApp.FACEBOOK_LITE)] ?: true,
            watchInstagram = p[watchKey(WatchedApp.INSTAGRAM)] ?: true,
            overlayEnabled = p[OVERLAY_ENABLED] ?: true,
            overlayMode = OverlayMode.fromKey(p[OVERLAY_MODE]),
            hudDigitHeightDp = (p[HUD_DIGIT_HEIGHT_DP] ?: 26).coerceIn(HUD_MIN_HEIGHT_DP, HUD_MAX_HEIGHT_DP),
            hudColour = p[HUD_COLOUR] ?: "#2BEA2B",
            hudDecimals = p[HUD_DECIMALS] ?: false,
            hudOutline = p[HUD_OUTLINE] ?: false,
            autoLaunchConfirmed = p[AUTO_LAUNCH_CONFIRMED] ?: false,
            unsupportedApps = unsupported,
            scrollSeenApps = seen,
        )
    }

    companion object {
        /** E15: this much foreground time with zero scrolls, over several visits = not supported. */
        const val UNSUPPORTED_AFTER_MS = 10 * 60_000L
        const val UNSUPPORTED_AFTER_VISITS = 3

        /** Design spec "Floating HUD": digit height range and step for the size control. */
        const val HUD_MIN_HEIGHT_DP = 16
        const val HUD_MAX_HEIGHT_DP = 48
        const val HUD_HEIGHT_STEP_DP = 2

        /** Design spec "Floating HUD" colour options: green (default), amber, white, cyan. */
        val HUD_COLOURS = listOf("#2BEA2B", "#FFC233", "#FFFFFF", "#3DE0FF")

        private val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        private val OVERLAY_MODE = stringPreferencesKey("overlay_mode")
        private val HUD_DIGIT_HEIGHT_DP = intPreferencesKey("hud_digit_height_dp")
        private val HUD_COLOUR = stringPreferencesKey("hud_colour")
        private val HUD_DECIMALS = booleanPreferencesKey("hud_decimals")
        private val HUD_OUTLINE = booleanPreferencesKey("hud_outline")
        private val AUTO_LAUNCH_CONFIRMED = booleanPreferencesKey("auto_launch_confirmed")

        private fun watchKey(app: WatchedApp) = booleanPreferencesKey("watch_${app.key}")
        private fun scrollSeenKey(app: WatchedApp) = booleanPreferencesKey("scroll_seen_${app.key}")
        private fun quietMsKey(app: WatchedApp) = longPreferencesKey("quiet_ms_${app.key}")
        private fun quietVisitsKey(app: WatchedApp) = intPreferencesKey("quiet_visits_${app.key}")
    }
}
