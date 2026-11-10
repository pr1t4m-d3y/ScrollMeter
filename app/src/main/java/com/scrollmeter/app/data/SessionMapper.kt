package com.scrollmeter.app.data

import com.scrollmeter.app.model.WatchedApp
import com.scrollmeter.app.util.Fmt
import java.time.Instant
import java.time.ZoneId

/** A session ready to draw in a list row. */
data class SessionUi(
    val id: Long,
    val app: WatchedApp,
    /** "21:05 – 21:43", or "21:05 – now" while the session is still open. */
    val timeRange: String,
    val strokes: Int,
    val metres: Double,
    val durationMs: Long,
    /** Where the session sits in its day, as fractions 0..1, for the 24-hour timeline strip. */
    val startFraction: Float,
    val endFraction: Float,
    val open: Boolean,
)

/** Turns database rows into list rows. Pure Kotlin, unit-tested. */
object SessionMapper {

    /** FR5.4: sessions shorter than this stay in the totals but are hidden from lists. */
    const val MIN_VISIBLE_STROKES = 3

    private const val DAY_MS = 86_400_000f

    /** Visible sessions, oldest first. */
    fun visible(sessions: List<SessionEntity>, zone: ZoneId): List<SessionUi> =
        sessions
            .filter { it.strokes >= MIN_VISIBLE_STROKES }
            .sortedBy { it.startedAt }
            .map { toUi(it, zone) }

    fun toUi(s: SessionEntity, zone: ZoneId): SessionUi {
        val end = maxOf(s.endedAt ?: s.lastScrollAt, s.startedAt)
        val duration = end - s.startedAt
        val startOfDaySeconds = Instant.ofEpochMilli(s.startedAt).atZone(zone)
            .toLocalTime().toSecondOfDay()
        val startFraction = (startOfDaySeconds / 86_400f).coerceIn(0f, 1f)
        val endFraction = (startFraction + duration / DAY_MS).coerceIn(startFraction, 1f)
        val endLabel = if (s.endedAt == null) "now" else Fmt.time(end, zone)
        return SessionUi(
            id = s.id,
            app = WatchedApp.fromKey(s.app) ?: WatchedApp.FACEBOOK,
            timeRange = "${Fmt.time(s.startedAt, zone)} – $endLabel",
            strokes = s.strokes,
            metres = s.metres,
            durationMs = duration,
            startFraction = startFraction,
            endFraction = endFraction,
            open = s.endedAt == null,
        )
    }

    /** The longest visible session of the day, or null when there is none. */
    fun longest(sessions: List<SessionUi>): SessionUi? = sessions.maxByOrNull { it.durationMs }
}
