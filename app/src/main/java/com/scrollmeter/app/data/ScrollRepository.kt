package com.scrollmeter.app.data

import com.scrollmeter.app.service.FlushBatch
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Today's totals as already saved in the database. */
data class DayTotals(val strokes: Int, val metres: Double)

/**
 * The only door to the database.
 *  - The accessibility service writes through [persist].
 *  - The screens read through the `observe…` flows, which re-emit after every write, so the
 *    dashboard updates live while the service is counting.
 */
class ScrollRepository(private val dao: ScrollDao) {

    fun observeBuckets(from: LocalDate, to: LocalDate): Flow<List<HourlyBucketEntity>> =
        dao.observeBuckets(from.toString(), to.toString())

    fun observeSessions(day: LocalDate): Flow<List<SessionEntity>> =
        dao.observeSessions(day.toString())

    suspend fun persist(batch: FlushBatch) {
        val sessions = batch.sessions.map {
            SessionEntity(
                id = it.id,
                app = it.app.key,
                startedAt = it.startedAt,
                endedAt = it.endedAt,
                strokes = it.strokes,
                pixels = it.pixels,
                metres = it.metres,
                day = it.day,
                lastScrollAt = it.lastScrollAt,
            )
        }
        val buckets = batch.buckets.map {
            HourlyBucketEntity(it.day, it.hour, it.app.key, it.strokes, it.metres)
        }
        dao.applyBatch(sessions, buckets)
    }

    suspend fun totalsForDay(day: String): DayTotals =
        DayTotals(dao.strokesForDay(day), dao.metresForDay(day))

    suspend fun closeDanglingSessions(): Int = dao.closeDanglingSessions()
}
