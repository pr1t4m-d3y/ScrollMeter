package com.scrollmeter.app

import com.scrollmeter.app.service.ScrollEventInterpreter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** FR2: which raw scroll events count, and as what. */
class ScrollEventInterpreterTest {

    private val screen = 2400
    private val list = "androidx.recyclerview.widget.RecyclerView"

    private fun ScrollEventInterpreter.event(
        key: Int = 1,
        cls: String? = list,
        dx: Int = 0,
        dy: Int = 0,
        x: Int = 0,
        y: Int = 0,
        from: Int = -1,
        to: Int = -1,
    ): Boolean = interpret(key, cls, dx, dy, x, y, from, to, screen)

    @Test
    fun fr2_2_positiveDeltaYIsDownwardPixels() {
        val i = ScrollEventInterpreter()
        assertTrue(i.event(dy = 120))
        assertEquals(120, i.outPixels)
        assertEquals(0, i.outPages)
    }

    @Test
    fun fr2_5_upwardScrollIsIgnored() {
        val i = ScrollEventInterpreter()
        assertFalse(i.event(dy = -120))
        assertEquals(0, i.outPixels)
    }

    @Test
    fun fr2_6_horizontalScrollIsIgnored() {
        val i = ScrollEventInterpreter()
        assertFalse(i.event(dx = 300, dy = 10))
        assertFalse(i.event(dx = -300))
    }

    @Test
    fun mostlyVerticalDiagonalStillCounts() {
        val i = ScrollEventInterpreter()
        assertTrue(i.event(dx = 10, dy = 300))
        assertEquals(300, i.outPixels)
    }

    @Test
    fun hugeSingleJumpIsNotAFingerScroll() {
        val i = ScrollEventInterpreter()
        assertFalse(i.event(dy = screen * 2 + 1))
        assertTrue(i.event(dy = screen * 2))
    }

    @Test
    fun fr2_3_scrollYFallbackUsesTheChangeBetweenEvents() {
        val i = ScrollEventInterpreter()
        assertFalse("first event only sets the baseline", i.event(y = 100))
        assertTrue(i.event(y = 350))
        assertEquals(250, i.outPixels)
        assertFalse("moving back up is ignored", i.event(y = 300))
        assertTrue(i.event(y = 420))
        assertEquals(120, i.outPixels)
    }

    @Test
    fun fr2_3_scrollXFallbackIsHorizontalAndIgnored() {
        val i = ScrollEventInterpreter()
        assertFalse(i.event(x = 0))
        assertFalse(i.event(x = 500))
    }

    @Test
    fun fr2_3_viewsAreTrackedSeparately() {
        val i = ScrollEventInterpreter()
        assertFalse(i.event(key = 1, y = 100))
        assertFalse(i.event(key = 2, y = 5_000))   // a different view: its own baseline
        assertTrue(i.event(key = 1, y = 160))
        assertEquals(60, i.outPixels)
    }

    @Test
    fun fr2_3_listIndexFallbackEstimatesPixelsFromRows() {
        val i = ScrollEventInterpreter()
        val cls = "android.widget.ListView"
        assertFalse(i.event(cls = cls, from = 0, to = 4))
        assertTrue(i.event(cls = cls, from = 2, to = 6))
        // 2 rows of a 5-row screen = 2/5 of one screen height.
        assertEquals(960, i.outPixels)
        assertEquals(0, i.outPages)
    }

    @Test
    fun fr2_4_verticalPagerCountsOnePagePerIndexStep() {
        val i = ScrollEventInterpreter()
        val cls = "com.example.reels.VerticalViewPager"
        assertFalse(i.event(cls = cls, from = 0, to = 0))
        // Such pagers scroll sideways internally; the page index is what matters.
        assertTrue(i.event(cls = cls, dx = 1080, from = 1, to = 1))
        assertEquals(1, i.outPages)
        assertEquals(0, i.outPixels)
        assertFalse("same page again", i.event(cls = cls, dx = 5, from = 1, to = 1))
        assertFalse("going back up a reel", i.event(cls = cls, from = 0, to = 0))
        assertTrue(i.event(cls = cls, from = 1, to = 1))
        assertEquals(1, i.outPages)
    }

    @Test
    fun fr2_4_fullScreenItemViewWithoutDeltasCountsPages() {
        val i = ScrollEventInterpreter()
        val cls = "com.example.FeedContainer"
        assertFalse(i.event(cls = cls, from = 3, to = 3))
        assertTrue(i.event(cls = cls, from = 4, to = 4))
        assertEquals(1, i.outPages)
    }

    @Test
    fun fr2_6_horizontalPagerNeverCounts() {
        val i = ScrollEventInterpreter()
        val cls = "androidx.viewpager.widget.ViewPager"
        assertFalse(i.event(cls = cls, dx = 1080, from = 0, to = 0))
        assertFalse(i.event(cls = cls, dx = 1080, from = 1, to = 1))
        // Once a view has reported deltas, index changes alone never count for it.
        assertFalse(i.event(cls = cls, from = 2, to = 2))
    }

    @Test
    fun viewsThatReportDeltasNeverDoubleCountThroughFallbacks() {
        val i = ScrollEventInterpreter()
        assertTrue(i.event(dy = 100, y = 100, from = 0, to = 5))
        assertFalse(i.event(y = 400, from = 3, to = 8))
    }

    @Test
    fun pageJumpsAreIgnored() {
        val i = ScrollEventInterpreter()
        val cls = "com.example.reels.VerticalViewPager"
        assertFalse(i.event(cls = cls, from = 0, to = 0))
        assertFalse(i.event(cls = cls, from = 10, to = 10))
    }

    @Test
    fun resetForgetsBaselines() {
        val i = ScrollEventInterpreter()
        assertFalse(i.event(y = 100))
        i.reset()
        assertFalse("baseline again after reset", i.event(y = 900))
        assertTrue(i.event(y = 1_000))
        assertEquals(100, i.outPixels)
    }

    @Test
    fun moreViewsThanSlotsStillWorks() {
        val i = ScrollEventInterpreter(capacity = 2)
        assertFalse(i.event(key = 1, y = 10))
        assertFalse(i.event(key = 2, y = 10))
        assertFalse(i.event(key = 3, y = 10))      // evicts view 1
        assertTrue(i.event(key = 3, y = 60))
        assertFalse("view 1 was evicted, so this is a new baseline", i.event(key = 1, y = 500))
    }
}
