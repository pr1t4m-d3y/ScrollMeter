package com.scrollmeter.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per session (PRD "Data model"). Raw scroll events are never stored.
 *
 * Differences from the PRD table, both deliberate:
 *  - [id] is assigned by the tracker (time-based, strictly increasing) instead of
 *    auto-generated, so a session can be re-saved every 10 s without waiting for the insert.
 *  - [lastScrollAt] is an extra column. If the process is killed while a session is open,
 *    the next start closes it at this time instead of leaving it open forever.
 */
@Entity(tableName = "session", indices = [Index("day")])
data class SessionEntity(
    @PrimaryKey val id: Long,
    /** `facebook`, `facebook_lite` or `instagram`. */
    val app: String,
    val startedAt: Long,
    /** Null while the session is open. */
    val endedAt: Long?,
    val strokes: Int,
    /** Raw downward pixels. */
    val pixels: Long,
    /** Pixels converted to metres at write time. */
    val metres: Double,
    /** Local date `YYYY-MM-DD`, for fast queries. */
    val day: String,
    val lastScrollAt: Long,
)

/** One row per app per hour (FR6.1). Every chart and total is computed from these. */
@Entity(tableName = "hourly_bucket", primaryKeys = ["day", "hour", "app"])
data class HourlyBucketEntity(
    val day: String,
    val hour: Int,
    val app: String,
    val strokes: Int,
    val metres: Double,
)
