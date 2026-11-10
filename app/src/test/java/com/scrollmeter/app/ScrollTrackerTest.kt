package com.scrollmeter.app

import com.scrollmeter.app.model.WatchedApp
import com.scrollmeter.app.service.BucketDelta
import com.scrollmeter.app.service.Distance
import com.scrollmeter.app.service.FlushBatch
import com.scrollmeter.app.service.ScrollTracker
import com.scrollmeter.app.service.SessionSnapshot
import com.scrollmeter.app.service.TrackerConfig
import com.scrollmeter.app.service.TrackerStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** PRD unit tests U1 to U10 plus the edge cases the tracker owns (E1, E11, E17, FR3.3). */
class ScrollTrackerTest {

    private class FakeStore : TrackerStore {
        val batches = ArrayList<FlushBatch>()

        override fun persist(batch: FlushBatch) {
            batches.add(batch)
        }

        /** Latest saved version of every session, in the order they first appeared. */
        fun sessions(): List<SessionSnapshot> {
            val latest = LinkedHashMap<Long, SessionSnapshot>()
            for (batch in batches) for (s in batch.sessions) latest[s.id] = s
            return latest.values.toList()
        }

        fun buckets(): List<BucketDelta> = batches.flatMap { it.buckets }
    }

    private val fb = WatchedApp.FACEBOOK
    private val ig = WatchedApp.INSTAGRAM
    private val store = FakeStore()

    /** 400 dpi, 1 px per dp (so the 24 dp stroke threshold is 24 px), 2,400 px screen. */
    private fun tracker(zone: ZoneId = ZoneOffset.UTC, density: Float = 1f): ScrollTracker =
        ScrollTracker(TrackerConfig(ydpi = 400f, density = density, screenHeightPx = 2400), store) { zone }

    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        ZonedDateTime.of(2026, 10, day, hour, minute, second, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()

    private val t0 = at(1, 21, 0)

    // ---------------------------------------------------------------- U1

    @Test
    fun u1_2400pxAt400dpi_is0_1524m() {
        assertEquals(0.1524, Distance.pixelsToMetres(2400, 400f), 1e-9)
    }

    @Test
    fun fr4_2_implausibleYdpiFallsBackToDensityDpi() {
        assertEquals(400f, Distance.effectiveDpi(400f, 420), 0f)
        assertEquals(420f, Distance.effectiveDpi(72f, 420), 0f)
        assertEquals(420f, Distance.effectiveDpi(900f, 420), 0f)
        assertEquals(420f, Distance.effectiveDpi(Float.NaN, 420), 0f)
    }

    // ---------------------------------------------------------------- U2 to U5 strokes

    @Test
    fun u2_eventsHundredMsApart_areOneStroke() {
        val tracker = tracker()
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, 50, t0)
        tracker.onScroll(fb, 50, t0 + 100)
        tracker.onScroll(fb, 50, t0 + 200)
        tracker.flush()

        val session = store.sessions().single()
        assertEquals(1, session.strokes)
        assertEquals(150L, session.pixels)
        assertEquals(1, tracker.todayStrokes)
    }

    @Test
    fun u3_twoBurstsFourHundredMsApart_areTwoStrokes() {
        val tracker = tracker()
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, 50, t0)
        tracker.onScroll(fb, 50, t0 + 400)
        tracker.flush()

        assertEquals(2, store.sessions().single().strokes)
    }

    @Test
    fun u4_negativeDeltasOnly_countNothing() {
        val tracker = tracker()
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, -300, t0)
        tracker.onScroll(fb, -300, t0 + 100)
        tracker.flush()

        assertEquals(0, tracker.todayStrokes)
        assertEquals(0.0, tracker.todayMetres, 0.0)
        assertTrue(store.sessions().isEmpty())
    }

    @Test
    fun u5_burstUnder24dp_isNotAStroke() {
        val tracker = tracker()
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, 10, t0)
        tracker.onScroll(fb, 10, t0 + 50)
        tracker.flush()

        assertEquals(0, tracker.todayStrokes)
        assertEquals(0.0, tracker.todayMetres, 0.0)
        assertTrue(store.sessions().isEmpty())
    }

    @Test
    fun fr3_4_thresholdScalesWithDensity() {
        val tracker = tracker(density = 3f)   // 24 dp = 72 px
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, 60, t0)
        assertEquals(0, tracker.todayStrokes)
        tracker.onScroll(fb, 20, t0 + 100)    // same burst, now 80 px
        assertEquals(1, tracker.todayStrokes)
        // The pixels held back while the burst was too small are counted once it qualifies.
        assertEquals(Distance.pixelsToMetres(80, 400f), tracker.todayMetres, 1e-12)
    }

    @Test
    fun fr3_3_eachReelPageIsExactlyOneStrokeAndOneScreen() {
        val tracker = tracker()
        tracker.onAppEntered(fb, t0)
        for (i in 0 until 10) tracker.onPage(fb, 1, t0 + i * 2_000L)
        tracker.flush()

        val session = store.sessions().single()
        assertEquals(10, session.strokes)
        assertEquals(24_000L, session.pixels)
        assertEquals(1.524, session.metres, 1e-9)
    }

    @Test
    fun fr1_2_scrollsAreIgnoredWhenTheAppIsNotActive() {
        val tracker = tracker()
        tracker.onScroll(fb, 500, t0)             // never entered
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(ig, 500, t0 + 1_000)     // a different app than the active one
        tracker.flush()

        assertEquals(0, tracker.todayStrokes)
        assertTrue(store.sessions().isEmpty())
    }

    // ---------------------------------------------------------------- U6 to U8 sessions

    @Test
    fun u6_leaveAndReturnAfter45s_isTheSameSession() {
        val tracker = tracker()
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, 100, t0)
        tracker.onAppLeft(t0 + 1_000)
        tracker.onAppEntered(fb, t0 + 46_000)
        tracker.onScroll(fb, 100, t0 + 47_000)
        tracker.flush()

        val session = store.sessions().single()
        assertEquals(2, session.strokes)
        assertNull("resumed session is open again", session.endedAt)
        assertEquals(t0, session.startedAt)
    }

    @Test
    fun u7_leaveAndReturnAfter90s_isANewSession() {
        val tracker = tracker()
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, 100, t0)
        tracker.onAppLeft(t0 + 1_000)
        tracker.onAppEntered(fb, t0 + 91_000)
        tracker.onScroll(fb, 100, t0 + 92_000)
        tracker.flush()

        val sessions = store.sessions()
        assertEquals(2, sessions.size)
        assertEquals(t0 + 1_000, sessions[0].endedAt)
        assertEquals(1, sessions[0].strokes)
        assertEquals(t0 + 92_000, sessions[1].startedAt)
        assertNull(sessions[1].endedAt)
    }

    @Test
    fun u8_noScrollForFiveMinutes_endsSessionAtLastScroll() {
        val tracker = tracker()
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, 100, t0)
        tracker.onScroll(fb, 100, t0 + 30_000)
        tracker.onTick(t0 + 30_000 + ScrollTracker.IDLE_END_MS)

        val session = store.sessions().single()
        assertEquals(t0 + 30_000, session.endedAt)
        assertEquals(0, tracker.sessionStrokes)   // "This session" HUD mode is back to zero
        assertEquals(2, tracker.todayStrokes)
    }

    @Test
    fun fr5_2_idleGapWithoutATick_stillSplitsSessions() {
        val tracker = tracker()
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, 100, t0)
        tracker.onScroll(fb, 100, t0 + 6 * 60_000)
        tracker.flush()

        val sessions = store.sessions()
        assertEquals(2, sessions.size)
        assertEquals(t0, sessions[0].endedAt)
        assertEquals(t0 + 6 * 60_000, sessions[1].startedAt)
    }

    @Test
    fun e1_screenOffEndsSession_andUnlockStartsANewOne() {
        val tracker = tracker()
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, 100, t0)
        tracker.onScreenOff(t0 + 1_000)
        assertNull(tracker.activeApp)

        tracker.onAppEntered(fb, t0 + 10_000)     // well inside the 60 s window
        tracker.onScroll(fb, 100, t0 + 11_000)
        tracker.flush()

        val sessions = store.sessions()
        assertEquals("screen off must not be resumable", 2, sessions.size)
        assertEquals(t0 + 1_000, sessions[0].endedAt)
    }

    @Test
    fun switchingToAnotherWatchedApp_startsItsOwnSession() {
        val tracker = tracker()
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, 100, t0)
        tracker.onAppEntered(ig, t0 + 5_000)
        tracker.onScroll(ig, 100, t0 + 6_000)
        tracker.flush()

        val sessions = store.sessions()
        assertEquals(2, sessions.size)
        assertEquals(fb, sessions[0].app)
        assertEquals(t0 + 5_000, sessions[0].endedAt)
        assertEquals(ig, sessions[1].app)
    }

    @Test
    fun fr7_3_sessionCountersResetWithEachNewSession() {
        val tracker = tracker()
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, 100, t0)
        tracker.onScroll(fb, 100, t0 + 1_000)
        assertEquals(2, tracker.sessionStrokes)

        tracker.onAppLeft(t0 + 2_000)
        tracker.onAppEntered(fb, t0 + 200_000)    // too late to resume
        assertEquals(0, tracker.sessionStrokes)
        tracker.onScroll(fb, 100, t0 + 201_000)
        assertEquals(1, tracker.sessionStrokes)
        assertEquals(3, tracker.todayStrokes)
    }

    // ---------------------------------------------------------------- U9 midnight

    @Test
    fun u9_sessionAcrossMidnight_isSplitAt0000() {
        val tracker = tracker()
        val start = at(1, 23, 50)
        val midnight = at(2, 0, 0)
        tracker.onAppEntered(fb, start)
        // One 400 px stroke every minute from 23:50 to 00:20 inclusive: 10 before, 21 after.
        for (minute in 0..30) tracker.onScroll(fb, 400, start + minute * 60_000L)
        tracker.onAppLeft(at(2, 0, 20, 30))

        val sessions = store.sessions()
        assertEquals(2, sessions.size)

        val first = sessions[0]
        assertEquals("2026-10-01", first.day)
        assertEquals(start, first.startedAt)
        assertEquals(midnight, first.endedAt)
        assertEquals(10, first.strokes)
        assertEquals(4_000L, first.pixels)
        assertEquals(Distance.pixelsToMetres(4_000, 400f), first.metres, 1e-9)

        val second = sessions[1]
        assertEquals("2026-10-02", second.day)
        assertEquals(midnight, second.startedAt)
        assertEquals(at(2, 0, 20, 30), second.endedAt)
        assertEquals(21, second.strokes)
        assertEquals(8_400L, second.pixels)

        // "Today" on the HUD restarted at midnight.
        assertEquals(21, tracker.todayStrokes)

        // Hourly buckets follow the same split.
        val buckets = store.buckets()
        assertEquals(10, buckets.filter { it.day == "2026-10-01" && it.hour == 23 }.sumOf { it.strokes })
        assertEquals(21, buckets.filter { it.day == "2026-10-02" && it.hour == 0 }.sumOf { it.strokes })
    }

    @Test
    fun e11_sessionLeftOpenPastMidnightWithoutScrolling_endsAtMidnight() {
        val tracker = tracker()
        tracker.onAppEntered(fb, at(1, 23, 58))
        tracker.onScroll(fb, 400, at(1, 23, 58))
        tracker.onAppLeft(at(2, 0, 2))

        val session = store.sessions().single()
        assertEquals("2026-10-01", session.day)
        assertEquals(at(2, 0, 0), session.endedAt)
    }

    @Test
    fun e17_localDateOfTheEventDecidesTheDay() {
        // 18:29 UTC is 23:59 in India; 18:31 UTC is 00:01 the next day there.
        val tracker = tracker(zone = ZoneId.of("Asia/Kolkata"))
        tracker.onAppEntered(fb, at(1, 18, 29))
        tracker.onScroll(fb, 400, at(1, 18, 29))
        tracker.onScroll(fb, 400, at(1, 18, 31))
        tracker.flush()

        val sessions = store.sessions()
        assertEquals(2, sessions.size)
        assertEquals("2026-10-01", sessions[0].day)
        assertEquals(at(1, 18, 30), sessions[0].endedAt)
        assertEquals("2026-10-02", sessions[1].day)
        assertEquals(0, store.buckets().first { it.day == "2026-10-02" }.hour)
        assertEquals(23, store.buckets().first { it.day == "2026-10-01" }.hour)
    }

    // ---------------------------------------------------------------- U10 buckets and totals

    @Test
    fun u10_bucketsAddUpToTheSessionsAndSplitByHour() {
        val tracker = tracker()
        tracker.onAppEntered(fb, at(1, 21, 59, 50))
        tracker.onScroll(fb, 400, at(1, 21, 59, 50))
        tracker.onScroll(fb, 400, at(1, 22, 0, 5))
        tracker.onScroll(fb, 400, at(1, 22, 0, 10))
        tracker.onAppLeft(at(1, 22, 1))

        val buckets = store.buckets()
        assertEquals(1, buckets.filter { it.hour == 21 }.sumOf { it.strokes })
        assertEquals(2, buckets.filter { it.hour == 22 }.sumOf { it.strokes })

        val session = store.sessions().single()
        assertEquals(session.strokes, buckets.sumOf { it.strokes })
        assertEquals(session.metres, buckets.sumOf { it.metres }, 1e-12)
        assertEquals(session.metres, tracker.todayMetres, 1e-12)
    }

    @Test
    fun flushWritesOnlyWhatChanged() {
        val tracker = tracker()
        tracker.onAppEntered(fb, t0)
        tracker.onTick(t0 + 10_000)
        assertTrue("nothing counted, nothing written", store.batches.isEmpty())

        tracker.onScroll(fb, 100, t0 + 11_000)
        tracker.onTick(t0 + 20_000)
        assertEquals(1, store.batches.size)
        tracker.onTick(t0 + 30_000)
        assertEquals("no new scrolls, no new write", 1, store.batches.size)
    }

    @Test
    fun seedTodayContinuesTheTotalAfterARestart() {
        val tracker = tracker()
        tracker.onClockChanged(t0)
        tracker.seedToday("2026-10-01", 100, 50.0)
        tracker.seedToday("2026-09-30", 999, 999.0)   // another day: ignored
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, 100, t0)

        assertEquals(101, tracker.todayStrokes)
        assertEquals(50.0 + Distance.pixelsToMetres(100, 400f), tracker.todayMetres, 1e-9)
    }

    @Test
    fun sessionIdsAreUniqueAndIncreasing() {
        val tracker = tracker()
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, 100, t0)
        tracker.onScreenOff(t0)                    // same millisecond on purpose
        tracker.onAppEntered(fb, t0)
        tracker.onScroll(fb, 100, t0)
        tracker.flush()

        val sessions = store.sessions()
        assertEquals(2, sessions.size)
        assertTrue(sessions[1].id > sessions[0].id)
        assertNotNull(sessions[0].endedAt)
    }
}
