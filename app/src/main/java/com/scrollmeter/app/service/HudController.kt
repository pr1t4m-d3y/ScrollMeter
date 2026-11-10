package com.scrollmeter.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Owns the two floating counters (FR5 / FR7): strokes top-left, metres top-right, just under
 * the status bar. Adds and removes the overlay windows and keeps them in the right corners.
 *
 * The windows are `TYPE_ACCESSIBILITY_OVERLAY`, which needs no "Display over other apps"
 * permission. If that ever fails on a phone, it falls back to `TYPE_APPLICATION_OVERLAY`.
 *
 * The flags make the windows untouchable and unfocusable (FR7.5): every tap goes straight
 * through to Facebook / Instagram, and the keyboard is never affected.
 */
class HudController(private val service: AccessibilityService) {

    private val density = service.resources.displayMetrics.density

    private var strokesView: SevenSegmentView? = null
    private var metresView: SevenSegmentView? = null
    private var windowManager: WindowManager? = null
    private var windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
    private var attached = false
    private var retryAfter = 0L

    private var digitHeightDp = 26
    private var colour = 0xFF2BEA2B.toInt()
    private var outline = false

    val isShown: Boolean get() = attached

    /** Shows the counters (adding the windows if needed) with these exact strings. */
    fun show(strokes: String, metres: String) {
        if (!attached) attach()
        // The views only redraw when the string really changed.
        strokesView?.text = strokes
        metresView?.text = metres
    }

    /** Removes both windows at once, with no animation. */
    fun hide() {
        if (!attached) return
        val wm = windowManager
        val left = strokesView
        val right = metresView
        attached = false
        strokesView = null
        metresView = null
        windowManager = null
        if (wm != null) {
            // E19: if the overlay permission was revoked the system may already have removed them.
            try {
                if (left != null) wm.removeViewImmediate(left)
            } catch (e: Exception) {
                Log.w(TAG, "strokes window already gone", e)
            }
            try {
                if (right != null) wm.removeViewImmediate(right)
            } catch (e: Exception) {
                Log.w(TAG, "metres window already gone", e)
            }
        }
    }

    fun setStyle(digitHeightDp: Int, colour: Int, outline: Boolean) {
        this.digitHeightDp = digitHeightDp
        this.colour = colour
        this.outline = outline
        strokesView?.let { applyStyle(it) }
        metresView?.let { applyStyle(it) }
    }

    /** E6 / I6: after rotation, move the counters to the new top corners. */
    fun relayout() {
        if (!attached) return
        val wm = windowManager ?: return
        try {
            strokesView?.let { wm.updateViewLayout(it, params(wm, Gravity.LEFT, windowType)) }
            metresView?.let { wm.updateViewLayout(it, params(wm, Gravity.RIGHT, windowType)) }
        } catch (e: Exception) {
            Log.w(TAG, "relayout failed, re-adding", e)
            hide()
        }
    }

    private fun attach() {
        val now = SystemClock.uptimeMillis()
        if (now < retryAfter) return

        val left = SevenSegmentView(service)
        val right = SevenSegmentView(service)
        applyStyle(left)
        applyStyle(right)

        // 1. Preferred: accessibility overlay, through the service's own WindowManager.
        val serviceWm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (tryAdd(serviceWm, left, right, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)) {
            ServiceStatus.hudNeedsOverlayPermission.value = false
            return
        }

        // 2. Fallback: ordinary app overlay, which needs "Display over other apps".
        if (Settings.canDrawOverlays(service)) {
            val appWm = service.applicationContext
                .getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (tryAdd(appWm, left, right, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)) {
                ServiceStatus.hudNeedsOverlayPermission.value = false
                return
            }
        } else {
            ServiceStatus.hudNeedsOverlayPermission.value = true
        }

        // Nothing worked. Tracking carries on; try again in a few seconds, not on every event.
        retryAfter = now + RETRY_MS
    }

    private fun tryAdd(
        wm: WindowManager,
        left: SevenSegmentView,
        right: SevenSegmentView,
        type: Int,
    ): Boolean {
        try {
            wm.addView(left, params(wm, Gravity.LEFT, type))
        } catch (e: Exception) {
            Log.w(TAG, "could not add HUD window type $type", e)
            return false
        }
        try {
            wm.addView(right, params(wm, Gravity.RIGHT, type))
        } catch (e: Exception) {
            Log.w(TAG, "could not add second HUD window type $type", e)
            try {
                wm.removeViewImmediate(left)
            } catch (ignored: Exception) {
                // nothing to clean up
            }
            return false
        }
        windowManager = wm
        windowType = type
        strokesView = left
        metresView = right
        attached = true
        return true
    }

    private fun params(wm: WindowManager, side: Int, type: Int): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        // Status bar and camera cutout, as if they were visible, for the current rotation.
        var top = 0
        var sideInset = 0
        try {
            val insets = wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
            )
            top = insets.top
            sideInset = if (side == Gravity.LEFT) insets.left else insets.right
        } catch (e: Exception) {
            top = fallbackStatusBarHeight()
        }
        p.gravity = Gravity.TOP or side
        p.x = dp(SIDE_MARGIN_DP) + sideInset
        p.y = top + dp(TOP_MARGIN_DP)
        p.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        if (type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
            // Android 12+ only lets touches pass through an app overlay that is at most 80% opaque.
            p.alpha = 0.8f
        }
        p.title = "ScrollMeterHud"
        return p
    }

    private fun applyStyle(view: SevenSegmentView) {
        view.digitHeight = digitHeightDp * density
        view.color = colour
        view.outline = outline
    }

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    private fun fallbackStatusBarHeight(): Int {
        val res = service.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (id != 0) res.getDimensionPixelSize(id) else dp(24)
    }

    private companion object {
        const val TAG = "ScrollMeter"
        const val SIDE_MARGIN_DP = 14
        const val TOP_MARGIN_DP = 6
        const val RETRY_MS = 5_000L
    }
}
