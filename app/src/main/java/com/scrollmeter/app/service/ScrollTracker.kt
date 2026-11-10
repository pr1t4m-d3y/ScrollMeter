package com.scrollmeter.app.service

import com.scrollmeter.app.model.WatchedApp
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Screen facts the tracker needs. The service refreshes them on rotation. */
class TrackerConfig(
    /** Physical vertical dpi, already validated by [Distance.effectiveDpi]. */
    var ydpi: Float,
    /** Pixels per dp. */
    var density: Float,
    /** Full screen height in pixels: the length of one reel page. */
    var screenHeightPx: Int,
) {
    val minStrokePx: Float get() = ScrollTracker.MIN_STROKE_DP * density
}

/** Immutable copy of a session, safe to hand to the database thread. */
data class SessionSnapshot(
    val id: Long,
    val app: WatchedApp,
    val startedAt: Long,
    /** Null while the session is still open. */
    val endedAt: Long?,
    val strokes: Int,
    val pixels: Long,
    val metres: Double,
    /** Local date `YYYY-MM-DD` the session belongs to. */
    val day: String,
    val lastScrollAt: Long,
)

/** Amount to ADD to one hourly bucket (FR6.1). */
data class BucketDelta(
    val day: String,
    val hour: Int,
    val app: WatchedApp,
    val strokes: Int,
    val metres: Double,
)

class FlushBatch(val sessions: List<SessionSnapshot>, val buckets: List<BucketDelta>)

/** Where finished numbers go. In the app this queues a Room write; in tests it is a list. */
fun interface TrackerStore {
    fun persist(batch: FlushBatch)
}

/**
 * The single place that turns scroll events into numbers (PRD "Technical architecture"),
 * so the HUD and the dashboard always agree.
 *
 * Pure Kotlin, no Android classes: every rule in FR3 to FR6 is unit-tested on the JVM.
 * Not thread-safe by design; the service calls it only from the main thread.
 *
 * Inputs (all carry the wall-clock time `now` in epoch ms):
 *  - [onAppEntered] / [onAppLeft] / [onScreenOff]  foreground changes
 *  - [onScroll]  downward pixels                   [onPage]  reel page changes
 *  - [onTick]    every 10 s while a watched app is open (idle check + flush)
 *
 * Outputs:
 *  - [todayStrokes] / [todayMetres] / [sessionStrokes] / [sessionMetres] for the HUD
 *  - [TrackerStore.persist] batches for the database
 */
class ScrollTracker(
    private val config: TrackerConfig,
    private val store: TrackerStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    private class Live(
        val id: Long,
        val app: WatchedApp,
        val startedAt: Long,
        val day: String,
        /** Start of the day after [day]; a session never runs past it (FR5.5). */
        val dayEndsAt: Long,
    ) {
        var strokes = 0
        var pixels = 0L
        var metres = 0.0
        var lastScrollAt = startedAt
        var endedAt: Long? = null
        var dirty = true

        fun snapshot() =
            SessionSnapshot(id, app, startedAt, endedAt, strokes, pixels, metres, day, lastScrollAt)
    }

    /** The watched app currently in the foreground, or null. */
    var activeApp: WatchedApp? = null
        private set

    /** Bumped whenever anything the HUD shows may have changed. */
    var version = 0
        private set

    var todayStrokes = 0
        private set
    var todayMetres = 0.0
        private set

    /** Local date the "today" totals belong to. */
    val today: String get() = curDay

    val sessionStrokes: Int get() = hudSession()?.strokes ?: 0
    val sessionMetres: Double get() = hudSession()?.metres ?: 0.0

    // Open session, and the one just closed by leaving the app (FR5.3 resume window).
    private var session: Live? = null
    private var resumable: Live? = null
    private var leftAt = 0L
    private var resumeArmed = false

    // Current burst of downward events (FR3).
    private var burstApp: WatchedApp? = null
    private var burstPx = 0L
    private var burstCounted = false
    private var lastDownAt = 0L

    // Cached local clock window so the hot path does no date maths.
    private var hourStart = 0L
    private var hourEnd = 0L
    private var dayStart = 0L
    private var dayEnd = 0L
    private var curDay = ""
    private var curHour = 0

    // Hourly bucket being filled, plus finished ones waiting for the next flush.
    private var bucketApp: WatchedApp? = null
    private var bucketDay = ""
    private var bucketHour = -1
    private var bucketStrokes = 0
    private var bucketMetres = 0.0
    private val pendingBuckets = ArrayList<BucketDelta>()
    private val closed = ArrayList<Live>()

    private var lastId = 0L

    // ---------------------------------------------------------------- foreground

    /** FR1.2: a watched, switched-on app came to the foreground. */
    fun onAppEntered(app: WatchedApp, now: Long) {
        if (activeApp === app) return
        if (activeApp != null) onAppLeft(now)
        syncClock(now)
        activeApp = app
        resetBurst()
        val r = resumable
        if (r != null) {
            // FR5.3: back in the same app within 60 s keeps the same session.
            val inWindow = r.app === app && r.day == curDay &&
                now >= leftAt && now - leftAt <= RESUME_WINDOW_MS
            if (inWindow) {
                resumeArmed = true
            } else {
                resumable = null
                resumeArmed = false
            }
        }
        version++
    }

    /** FR5.2: the user left the watched app. The session closes but may resume (FR5.3). */
    fun onAppLeft(now: Long) {
        if (activeApp == null) return
        syncClock(now)
        activeApp = null
        resetBurst()
        resumeArmed = false
        val s = session
        if (s != null) {
            session = null
            close(s, now)
            resumable = s
            leftAt = now
        }
        version++
        flush()
    }

    /** FR1.4 / E1: screen off ends the session for good; unlocking starts a new one. */
    fun onScreenOff(now: Long) {
        onAppLeft(now)
        resumable = null
        resumeArmed = false
    }

    // ---------------------------------------------------------------- scrolling

    /**
     * Downward scroll of [downPx] pixels in [app] (FR2.5: callers never pass upward movement).
     *
     * FR3: events closer than 300 ms form one burst = one stroke. A burst only counts once it
     * has moved 24 dp; smaller bursts are accidental touches and add nothing, not even distance.
     */
    fun onScroll(app: WatchedApp, downPx: Int, now: Long) {
        if (downPx <= 0 || activeApp !== app) return
        syncClock(now)
        if (burstApp !== app || now - lastDownAt > STROKE_GAP_MS || now < lastDownAt) {
            burstApp = app
            burstPx = 0L
            burstCounted = false
        }
        lastDownAt = now
        if (burstCounted) {
            count(app, 0, downPx.toLong(), now)
        } else {
            burstPx += downPx
            if (burstPx >= config.minStrokePx) {
                burstCounted = true
                count(app, 1, burstPx, now)
            }
        }
    }

    /** FR2.4 / FR3.3: each reel page is exactly one stroke and one screen height. */
    fun onPage(app: WatchedApp, pages: Int, now: Long) {
        if (pages <= 0 || activeApp !== app) return
        syncClock(now)
        resetBurst()
        count(app, pages, pages.toLong() * config.screenHeightPx, now)
    }

    /** Called every [FLUSH_INTERVAL_MS] while a watched app is open. */
    fun onTick(now: Long) {
        syncClock(now)
        val s = session
        if (s != null && now - s.lastScrollAt >= IDLE_END_MS) {
            // FR5.2 / U8: no scroll for 5 minutes ends the session at the last scroll.
            session = null
            close(s, s.lastScrollAt)
            version++
        }
        flush()
    }

    // ---------------------------------------------------------------- clock and totals

    /**
     * E17: the wall clock or time zone changed (also sent by the system at midnight).
     * Writes what we have, then restarts "today" from zero; the service re-seeds it from
     * the database with [seedToday].
     */
    fun onClockChanged(now: Long) {
        flush()
        curDay = ""
        hourEnd = 0L
        syncClock(now)
    }

    /** Adds what the database already holds for [day] to the in-memory "today" totals. */
    fun seedToday(day: String, strokes: Int, metres: Double) {
        if (day != curDay) return
        todayStrokes += strokes
        todayMetres += metres
        version++
    }

    /** Writes everything not yet saved. Safe to call at any time. */
    fun flush() {
        sealBucket()
        val s = session
        val openDirty = s != null && s.dirty
        if (closed.isEmpty() && pendingBuckets.isEmpty() && !openDirty) return

        val sessions = ArrayList<SessionSnapshot>(closed.size + 1)
        for (c in closed) {
            sessions.add(c.snapshot())
            c.dirty = false
        }
        closed.clear()
        if (s != null && s.dirty) {
            sessions.add(s.snapshot())
            s.dirty = false
        }
        val buckets = ArrayList(pendingBuckets)
        pendingBuckets.clear()
        store.persist(FlushBatch(sessions, buckets))
    }

    // ---------------------------------------------------------------- internals

    private fun hudSession(): Live? = session ?: if (resumeArmed) resumable else null

    private fun resetBurst() {
        burstApp = null
        burstPx = 0L
        burstCounted = false
    }

    private fun count(app: WatchedApp, strokes: Int, px: Long, now: Long) {
        val s = ensureSession(app, now)
        val metres = Distance.pixelsToMetres(px, config.ydpi)
        s.strokes += strokes
        s.pixels += px
        s.metres += metres
        s.lastScrollAt = now
        s.dirty = true
        todayStrokes += strokes
        todayMetres += metres
        addToBucket(app, strokes, metres)
        version++
    }

    /** FR5: returns the session this scroll belongs to, starting or splitting one if needed. */
    private fun ensureSession(app: WatchedApp, now: Long): Live {
        var s = session
        if (s != null) {
            if (s.app !== app || now - s.lastScrollAt >= IDLE_END_MS) {
                // FR5.2: idle for 5 minutes. The old session ended at its last scroll.
                close(s, s.lastScrollAt)
                s = null
            } else if (s.day != curDay) {
                if (dayStart > s.lastScrollAt && dayStart <= now) {
                    // FR5.5 / E11: crossed midnight. Split at 00:00 into one session per day.
                    close(s, dayStart)
                    s = Live(nextId(now), app, dayStart, curDay, dayEnd)
                } else {
                    // E17: the clock jumped. Never rewrite the past; just start fresh.
                    close(s, s.lastScrollAt)
                    s = null
                }
            }
        }
        if (s == null) {
            val r = resumable
            if (resumeArmed && r != null && r.app === app && r.day == curDay) {
                // FR5.3: continue the session that was closed when the user briefly left.
                r.endedAt = null
                r.dirty = true
                s = r
            } else {
                // FR5.1: a session starts on the first counted scroll.
                s = Live(nextId(now), app, now, curDay, dayEnd)
            }
            resumable = null
            resumeArmed = false
        }
        session = s
        return s
    }

    private fun close(s: Live, endAt: Long) {
        var end = if (endAt < s.lastScrollAt) s.lastScrollAt else endAt
        if (end > s.dayEndsAt) end = s.dayEndsAt
        if (end < s.startedAt) end = s.startedAt
        s.endedAt = end
        s.dirty = true
        closed.add(s)
    }

    private fun addToBucket(app: WatchedApp, strokes: Int, metres: Double) {
        if (bucketApp !== app || bucketHour != curHour || bucketDay != curDay) {
            sealBucket()
            bucketApp = app
            bucketHour = curHour
            bucketDay = curDay
        }
        bucketStrokes += strokes
        bucketMetres += metres
    }

    private fun sealBucket() {
        val app = bucketApp
        if (app != null && (bucketStrokes != 0 || bucketMetres != 0.0)) {
            pendingBuckets.add(BucketDelta(bucketDay, bucketHour, app, bucketStrokes, bucketMetres))
        }
        bucketStrokes = 0
        bucketMetres = 0.0
    }

    /** Recomputes the local hour/day window only when [now] has left the cached hour. */
    private fun syncClock(now: Long) {
        if (now >= hourStart && now < hourEnd) return
        val zoneId = zone()
        val local = Instant.ofEpochMilli(now).atZone(zoneId)
        val hour = local.truncatedTo(ChronoUnit.HOURS)
        hourStart = hour.toInstant().toEpochMilli()
        hourEnd = hour.plusHours(1).toInstant().toEpochMilli()
        curHour = local.hour
        val date = local.toLocalDate()
        val day = date.toString()
        if (day != curDay) {
            curDay = day
            dayStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            // A new local day: "today" starts again from zero (E17: past days are never touched).
            todayStrokes = 0
            todayMetres = 0.0
            version++
        }
    }

    /** Session ids are time-based and strictly increasing, so they are unique across restarts. */
    private fun nextId(now: Long): Long {
        lastId = if (now > lastId) now else lastId + 1
        return lastId
    }

    companion object {
        /** FR3.2: a gap longer than this starts a new stroke. */
        const val STROKE_GAP_MS = 300L

        /** FR3.4: a burst shorter than this is an accidental touch. */
        const val MIN_STROKE_DP = 24f

        /** FR5.2: no scroll for this long ends the session. */
        const val IDLE_END_MS = 5 * 60_000L

        /** FR5.3: returning to the same app within this window continues the session. */
        const val RESUME_WINDOW_MS = 60_000L

        /** Write strategy: counters are flushed to the database this often. */
        const val FLUSH_INTERVAL_MS = 10_000L
    }
}
