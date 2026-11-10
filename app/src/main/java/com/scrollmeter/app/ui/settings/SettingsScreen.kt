package com.scrollmeter.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.R
import com.scrollmeter.app.data.OverlayMode
import com.scrollmeter.app.data.SettingsStore
import com.scrollmeter.app.model.WatchedApp
import com.scrollmeter.app.ui.components.ColourDot
import com.scrollmeter.app.ui.components.PermissionRow
import com.scrollmeter.app.ui.components.RowDivider
import com.scrollmeter.app.ui.components.ScreenHeader
import com.scrollmeter.app.ui.components.ScrollCard
import com.scrollmeter.app.ui.components.ScrollSwitch
import com.scrollmeter.app.ui.components.SegmentedControl
import com.scrollmeter.app.ui.components.StepButton
import com.scrollmeter.app.ui.components.appColor
import com.scrollmeter.app.ui.theme.ScrollColors
import com.scrollmeter.app.ui.theme.ScrollType
import com.scrollmeter.app.util.PermissionChecklist
import com.scrollmeter.app.util.PermissionIntents

/** Screen 4: Setup and settings. Rows refresh when the user returns from system Settings. */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    SettingsContent(
        state = state,
        onBack = onBack,
        onSetWatched = viewModel::setWatched,
        onSetOverlayEnabled = viewModel::setOverlayEnabled,
        onSetOverlayMode = viewModel::setOverlayMode,
        onConfirmAutoLaunch = viewModel::confirmAutoLaunch,
        onSetHudDigitHeightDp = viewModel::setHudDigitHeightDp,
        onSetHudColour = viewModel::setHudColour,
        onSetHudDecimals = viewModel::setHudDecimals,
        onSetHudOutline = viewModel::setHudOutline,
    )
}

/** Stateless Settings screen. Used by the screenshot tests too. */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSetWatched: (WatchedApp, Boolean) -> Unit,
    onSetOverlayEnabled: (Boolean) -> Unit,
    onSetOverlayMode: (OverlayMode) -> Unit,
    onConfirmAutoLaunch: () -> Unit,
    onSetHudDigitHeightDp: (Int) -> Unit = {},
    onSetHudColour: (String) -> Unit = {},
    onSetHudDecimals: (Boolean) -> Unit = {},
    onSetHudOutline: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val checklist = state.checklist
    val settings = state.settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 40.dp),
    ) {
        // 1. Header
        ScreenHeader(title = "Settings", onBack = onBack)

        // 2. Permissions card
        Spacer(Modifier.height(16.dp))
        ScrollCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Permissions",
                    style = ScrollType.cardTitle,
                    color = ScrollColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${checklist.done} of ${PermissionChecklist.TOTAL} done",
                    style = ScrollType.custom(14, 600),
                    color = if (checklist.allDone) ScrollColors.Good else ScrollColors.Warn,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Scroll Meter needs these to count reliably. Work down the list once.",
                style = ScrollType.caption,
                color = ScrollColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                name = "Allow restricted settings",
                reason = "Lets a sideloaded app turn on accessibility",
                granted = checklist.restrictedSettings,
                onAllow = { PermissionIntents.open(context, listOf(PermissionIntents.appInfo(context))) },
            )
            PermissionRow(
                name = "Accessibility service",
                reason = "Counts scrolls in the watched apps",
                granted = checklist.accessibility,
                onAllow = { PermissionIntents.open(context, listOf(PermissionIntents.accessibility())) },
            )
            PermissionRow(
                name = "Display over other apps",
                reason = "Draws the counters on top of Facebook",
                granted = checklist.overlay,
                onAllow = {
                    PermissionIntents.open(
                        context,
                        listOf(PermissionIntents.overlay(context), PermissionIntents.appInfo(context)),
                    )
                },
            )
            PermissionRow(
                name = "Auto launch",
                reason = "Restarts counting after a reboot",
                granted = checklist.autoLaunch,
                onAllow = {
                    if (PermissionIntents.open(context, PermissionIntents.autoLaunch(context))) {
                        onConfirmAutoLaunch()
                    }
                },
            )
            PermissionRow(
                name = "Battery: allow background",
                reason = "Stops the phone from closing the counter",
                granted = checklist.battery,
                onAllow = { PermissionIntents.open(context, PermissionIntents.battery(context)) },
            )
        }

        // 3. Apps to watch card
        Spacer(Modifier.height(16.dp))
        ScrollCard {
            Text("Apps to watch", style = ScrollType.cardTitle, color = ScrollColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            for (app in WatchedApp.entries) {
                val unsupported = app in settings.unsupportedApps
                RowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(appColor(app), CircleShape),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = ScrollType.rowTitle, color = ScrollColors.TextPrimary)
                        if (unsupported) {
                            // "App not supported" state: no usable scroll events from this app.
                            Text(
                                text = "Not supported on this phone",
                                style = ScrollType.caption,
                                color = ScrollColors.Warn,
                            )
                        }
                    }
                    ScrollSwitch(
                        checked = settings.isWatched(app) && !unsupported,
                        onCheckedChange = { onSetWatched(app, it) },
                        enabled = !unsupported,
                    )
                }
            }
        }

        // 4. Floating counter card
        Spacer(Modifier.height(16.dp))
        ScrollCard {
            Text("Floating counter", style = ScrollType.cardTitle, color = ScrollColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            RowDivider()
            val hudBlocked = state.hudNeedsOverlayPermission
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show counters while scrolling",
                        style = ScrollType.rowTitle,
                        color = ScrollColors.TextPrimary,
                    )
                    if (hudBlocked) {
                        // "Missing permission" state when the overlay fallback is in use.
                        Text(
                            text = "Needs display permission",
                            style = ScrollType.caption,
                            color = ScrollColors.Warn,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                ScrollSwitch(
                    checked = settings.overlayEnabled && !hudBlocked,
                    onCheckedChange = onSetOverlayEnabled,
                    enabled = !hudBlocked,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("Counters show", style = ScrollType.caption, color = ScrollColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            SegmentedControl(
                options = listOf("Today's total", "This session"),
                selectedIndex = if (settings.overlayMode == OverlayMode.SESSION) 1 else 0,
                enabled = settings.overlayEnabled && !hudBlocked,
                onSelect = { index ->
                    onSetOverlayMode(if (index == 1) OverlayMode.SESSION else OverlayMode.TODAY)
                },
            )

            // Counter style. These four controls are an addition to the design spec's
            // Settings layout (the spec defines the options but no place to change them).
            val styleEnabled = settings.overlayEnabled && !hudBlocked
            Spacer(Modifier.height(16.dp))
            RowDivider()
            SettingRow(title = "Digit size", caption = "${settings.hudDigitHeightDp} dp") {
                StepButton(
                    symbol = "−",
                    description = "Smaller digits",
                    enabled = styleEnabled && settings.hudDigitHeightDp > SettingsStore.HUD_MIN_HEIGHT_DP,
                    onClick = {
                        onSetHudDigitHeightDp(settings.hudDigitHeightDp - SettingsStore.HUD_HEIGHT_STEP_DP)
                    },
                )
                Spacer(Modifier.width(8.dp))
                StepButton(
                    symbol = "+",
                    description = "Larger digits",
                    enabled = styleEnabled && settings.hudDigitHeightDp < SettingsStore.HUD_MAX_HEIGHT_DP,
                    onClick = {
                        onSetHudDigitHeightDp(settings.hudDigitHeightDp + SettingsStore.HUD_HEIGHT_STEP_DP)
                    },
                )
            }
            RowDivider()
            SettingRow(title = "Colour", caption = null) {
                val names = listOf("Green", "Amber", "White", "Cyan")
                SettingsStore.HUD_COLOURS.forEachIndexed { index, hex ->
                    ColourDot(
                        color = Color(android.graphics.Color.parseColor(hex)),
                        description = names[index],
                        selected = hex.equals(settings.hudColour, ignoreCase = true),
                        enabled = styleEnabled,
                        onClick = { onSetHudColour(hex) },
                    )
                }
            }
            RowDivider()
            SettingRow(title = "Show one decimal", caption = "80.4m instead of 80m") {
                ScrollSwitch(
                    checked = settings.hudDecimals,
                    onCheckedChange = onSetHudDecimals,
                    enabled = styleEnabled,
                )
            }
            RowDivider()
            SettingRow(title = "Black outline", caption = "Easier to read over bright video") {
                ScrollSwitch(
                    checked = settings.hudOutline,
                    onCheckedChange = onSetHudOutline,
                    enabled = styleEnabled,
                )
            }
        }

        // 5. Privacy card
        Spacer(Modifier.height(16.dp))
        ScrollCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(ScrollColors.CardRaised, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        tint = ScrollColors.TextPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "No internet access",
                        style = ScrollType.rowTitle,
                        color = ScrollColors.TextPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Everything stays on this phone.",
                        style = ScrollType.bodySmall,
                        color = ScrollColors.TextSecondary,
                    )
                }
            }
        }
    }
}

/** A settings row: title over an optional caption on the left, controls on the right. */
@Composable
private fun SettingRow(
    title: String,
    caption: String?,
    controls: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = ScrollType.rowTitle, color = ScrollColors.TextPrimary)
            if (caption != null) {
                Text(caption, style = ScrollType.caption, color = ScrollColors.TextSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        controls()
    }
}
