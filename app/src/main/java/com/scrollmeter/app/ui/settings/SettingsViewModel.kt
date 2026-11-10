package com.scrollmeter.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollmeter.app.data.AppSettings
import com.scrollmeter.app.data.OverlayMode
import com.scrollmeter.app.data.SettingsStore
import com.scrollmeter.app.model.WatchedApp
import com.scrollmeter.app.service.ServiceStatus
import com.scrollmeter.app.util.PermissionChecklist
import com.scrollmeter.app.util.PermissionIntents
import com.scrollmeter.app.util.SystemPermissions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything Screen 4 draws. */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val checklist: PermissionChecklist = PermissionChecklist(
        restrictedSettings = false,
        accessibility = false,
        overlay = false,
        autoLaunch = false,
        battery = false,
    ),
    /** True when the HUD can only work through "Display over other apps" and that is missing. */
    val hudNeedsOverlayPermission: Boolean = false,
)

class SettingsViewModel(
    private val store: SettingsStore,
    permissions: StateFlow<SystemPermissions>,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> =
        combine(
            store.settings,
            permissions,
            ServiceStatus.hudNeedsOverlayPermission,
        ) { settings, system, hudBlocked ->
            SettingsUiState(
                settings = settings,
                checklist = PermissionChecklist.from(system, settings, PermissionIntents.sdkInt),
                hudNeedsOverlayPermission = hudBlocked && !system.overlay,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setWatched(app: WatchedApp, on: Boolean) {
        viewModelScope.launch { store.setWatched(app, on) }
    }

    fun setOverlayEnabled(on: Boolean) {
        viewModelScope.launch { store.setOverlayEnabled(on) }
    }

    fun setOverlayMode(mode: OverlayMode) {
        viewModelScope.launch { store.setOverlayMode(mode) }
    }

    fun setHudDigitHeightDp(dp: Int) {
        viewModelScope.launch { store.setHudDigitHeightDp(dp) }
    }

    fun setHudColour(hex: String) {
        viewModelScope.launch { store.setHudColour(hex) }
    }

    fun setHudDecimals(on: Boolean) {
        viewModelScope.launch { store.setHudDecimals(on) }
    }

    fun setHudOutline(on: Boolean) {
        viewModelScope.launch { store.setHudOutline(on) }
    }

    /** Auto launch cannot be detected: it counts as done once its page has been opened. */
    fun confirmAutoLaunch() {
        viewModelScope.launch { store.setAutoLaunchConfirmed(true) }
    }
}
