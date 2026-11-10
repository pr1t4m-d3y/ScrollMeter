package com.scrollmeter.app.service

import kotlin.math.abs

/**
 * FR2: turns the raw numbers on one `TYPE_VIEW_SCROLLED` event into either
 * "N downward pixels" or "N reel pages". Pure Kotlin so it runs in JVM unit tests.
 *
 * It only ever sees numbers and the view class name. No text, no view tree.
 *
 * Signals, in priority order:
 *  1. `scrollDeltaY` (FR2.2). Positive = down the feed.
 *  2. Change in `scrollY` between events of the same view (FR2.3).
 *  3. Change in `fromIndex` between events of the same view (FR2.3 / FR2.4). For a
 *     full-screen pager that is one page per step; for a list it is estimated as a
 *     share of one screen height.
 *
 * Upward (FR2.5) and horizontal (FR2.6) movement always returns false.
 *
 * No allocations: per-view memory is a few fixed arrays reused round-robin.
 */
class ScrollEventInterpreter(private val capacity: Int = 16) {

    /** Downward pixels for the last accepted event (0 when it was a page event). */
    var outPixels = 0
        private set

    /** Reel pages advanced for the last accepted event (0 when it was a pixel event). */
    var outPages = 0
        private set

    private val keys = IntArray(capacity)
    private val used = BooleanArray(capacity)
    private val lastX = IntArray(capacity)
    private val lastY = IntArray(capacity)
    private val lastIndex = IntArray(capacity)
    private val sawDelta = BooleanArray(capacity)
    private val kind = ByteArray(capacity)
    private var nextSlot = 0

    /**
     * @param viewKey stable id for "the same view" (class name hash + window id). The service
     *   cannot use the real view id because it never calls `getSource()`.
     * @return true when [outPixels] or [outPages] holds something to count.
     */
    fun interpret(
        viewKey: Int,
        className: CharSequence?,
        deltaX: Int,
        deltaY: Int,
        scrollX: Int,
        scrollY: Int,
        fromIndex: Int,
        toIndex: Int,
        screenHeightPx: Int,
    ): Boolean {
        outPixels = 0
        outPages = 0

        var slot = find(viewKey)
        val isNew = slot < 0
        if (isNew) slot = allocate(viewKey, className)

        val prevX = lastX[slot]
        val prevY = lastY[slot]
        val prevIndex = lastIndex[slot]
        lastX[slot] = scrollX
        lastY[slot] = scrollY
        lastIndex[slot] = fromIndex

        val maxJump = 2L * screenHeightPx

        // FR2.4: a pager that is known to be vertical. Its page index is the only signal we
        // trust, because such pagers often scroll sideways internally.
        if (kind[slot] == KIND_VERTICAL_PAGER) {
            return pageStep(isNew, prevIndex, fromIndex)
        }

        // 1. Primary signal: scroll deltas (FR2.2).
        if (deltaX != 0 || deltaY != 0) {
            sawDelta[slot] = true
            if (abs(deltaX) > abs(deltaY)) return false   // FR2.6 horizontal
            if (deltaY <= 0) return false                  // FR2.5 upward
            if (deltaY > maxJump) return false             // programmatic jump, not a finger
            outPixels = deltaY
            return true
        }

        // First sighting only sets the baseline. Views that report deltas never use fallbacks,
        // otherwise the same finger movement would be counted twice.
        if (isNew || sawDelta[slot]) return false

        // 2. Fallback: scroll position change (FR2.3).
        val dy = scrollY - prevY
        val dx = scrollX - prevX
        if (dy != 0 || dx != 0) {
            if (abs(dx) > abs(dy)) return false
            if (dy <= 0 || dy > maxJump) return false
            outPixels = dy
            return true
        }

        // 3. Fallback: item index change (FR2.3 / FR2.4).
        if (fromIndex >= 0 && prevIndex >= 0 && fromIndex > prevIndex) {
            val steps = fromIndex - prevIndex
            val visible = if (toIndex >= fromIndex) toIndex - fromIndex + 1 else 1
            if (visible == 1 || kind[slot] == KIND_PAGER) {
                if (steps > MAX_PAGE_STEP) return false
                outPages = steps
                return true
            }
            val px = steps.toLong() * screenHeightPx / visible
            if (px <= 0 || px > maxJump) return false
            outPixels = px.toInt()
            return true
        }
        return false
    }

    /** Forget every remembered view (used when the foreground app changes). */
    fun reset() {
        for (i in 0 until capacity) used[i] = false
        nextSlot = 0
    }

    private fun pageStep(isNew: Boolean, prevIndex: Int, fromIndex: Int): Boolean {
        if (isNew || fromIndex < 0 || prevIndex < 0) return false
        val steps = fromIndex - prevIndex
        if (steps <= 0 || steps > MAX_PAGE_STEP) return false
        outPages = steps
        return true
    }

    private fun find(viewKey: Int): Int {
        for (i in 0 until capacity) {
            if (used[i] && keys[i] == viewKey) return i
        }
        return -1
    }

    private fun allocate(viewKey: Int, className: CharSequence?): Int {
        val slot = nextSlot
        nextSlot = (nextSlot + 1) % capacity
        keys[slot] = viewKey
        used[slot] = true
        sawDelta[slot] = false
        kind[slot] = classify(className)
        return slot
    }

    private fun classify(className: CharSequence?): Byte {
        if (className == null) return KIND_PLAIN
        val reel = className.contains("reel", ignoreCase = true)
        val pager = className.contains("pager", ignoreCase = true)
        if (!reel && !pager) return KIND_PLAIN
        return if (reel || className.contains("vertical", ignoreCase = true)) {
            KIND_VERTICAL_PAGER
        } else {
            KIND_PAGER
        }
    }

    private companion object {
        const val KIND_PLAIN: Byte = 0
        const val KIND_PAGER: Byte = 1
        const val KIND_VERTICAL_PAGER: Byte = 2

        /** More pages than this in one event is a jump (e.g. "go to top"), not a swipe. */
        const val MAX_PAGE_STEP = 3
    }
}
