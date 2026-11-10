package com.scrollmeter.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScrollDao {

    @Upsert
    abstract suspend fun upsertSession(session: SessionEntity)

    @Query(
        "UPDATE hourly_bucket SET strokes = strokes + :strokes, metres = metres + :metres " +
            "WHERE day = :day AND hour = :hour AND app = :app"
    )
    abstract suspend fun addToBucket(
        day: String,
        hour: Int,
        app: String,
        strokes: Int,
        metres: Double,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertBucket(bucket: HourlyBucketEntity): Long

    /** One tracker flush: bucket amounts are ADDED, sessions are replaced by id. */
    @Transaction
    open suspend fun applyBatch(sessions: List<SessionEntity>, buckets: List<HourlyBucketEntity>) {
        for (b in buckets) {
            val updated = addToBucket(b.day, b.hour, b.app, b.strokes, b.metres)
            if (updated == 0) insertBucket(b)
        }
        for (s in sessions) upsertSession(s)
    }

    /** Days are `YYYY-MM-DD` strings, so BETWEEN compares them in date order. */
    @Query("SELECT * FROM hourly_bucket WHERE day BETWEEN :fromDay AND :toDay")
    abstract fun observeBuckets(fromDay: String, toDay: String): Flow<List<HourlyBucketEntity>>

    @Query("SELECT * FROM session WHERE day = :day ORDER BY startedAt ASC")
    abstract fun observeSessions(day: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session WHERE day = :day ORDER BY startedAt ASC")
    abstract suspend fun sessionsForDay(day: String): List<SessionEntity>

    @Query("SELECT COALESCE(SUM(strokes), 0) FROM hourly_bucket WHERE day = :day")
    abstract suspend fun strokesForDay(day: String): Int

    @Query("SELECT COALESCE(SUM(metres), 0.0) FROM hourly_bucket WHERE day = :day")
    abstract suspend fun metresForDay(day: String): Double

    /** A session left open by a killed process ends at its last recorded scroll (E13). */
    @Query("UPDATE session SET endedAt = lastScrollAt WHERE endedAt IS NULL")
    abstract suspend fun closeDanglingSessions(): Int
}
