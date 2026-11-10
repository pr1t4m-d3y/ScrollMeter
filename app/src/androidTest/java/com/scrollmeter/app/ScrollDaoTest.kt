package com.scrollmeter.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scrollmeter.app.data.AppDatabase
import com.scrollmeter.app.data.HourlyBucketEntity
import com.scrollmeter.app.data.ScrollDao
import com.scrollmeter.app.data.SessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented database tests (needs a phone or emulator: `./gradlew connectedAndroidTest`).
 * Covers the storage half of I4 (session closed in the database) and I5 (data up to the
 * last flush survives).
 */
@RunWith(AndroidJUnit4::class)
class ScrollDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ScrollDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.scrollDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun session(id: Long, endedAt: Long?, strokes: Int) = SessionEntity(
        id = id,
        app = "facebook",
        startedAt = 1_000,
        endedAt = endedAt,
        strokes = strokes,
        pixels = strokes * 100L,
        metres = strokes * 0.5,
        day = "2026-10-01",
        lastScrollAt = 5_000,
    )

    @Test
    fun bucketAmountsAreAddedAcrossFlushes() = runBlocking {
        val first = HourlyBucketEntity("2026-10-01", 21, "facebook", 10, 5.0)
        val second = HourlyBucketEntity("2026-10-01", 21, "facebook", 4, 2.5)
        dao.applyBatch(emptyList(), listOf(first))
        dao.applyBatch(emptyList(), listOf(second))

        val rows = dao.observeBuckets("2026-10-01", "2026-10-01").first()
        assertEquals(1, rows.size)
        assertEquals(14, rows[0].strokes)
        assertEquals(7.5, rows[0].metres, 1e-9)
        assertEquals(14, dao.strokesForDay("2026-10-01"))
        assertEquals(7.5, dao.metresForDay("2026-10-01"), 1e-9)
        assertEquals(0, dao.strokesForDay("2026-10-02"))
    }

    @Test
    fun sessionIsReplacedByIdOnEveryFlush() = runBlocking {
        dao.applyBatch(listOf(session(1, endedAt = null, strokes = 3)), emptyList())
        dao.applyBatch(listOf(session(1, endedAt = null, strokes = 9)), emptyList())
        dao.applyBatch(listOf(session(1, endedAt = 9_000, strokes = 12)), emptyList())

        val rows = dao.sessionsForDay("2026-10-01")
        assertEquals(1, rows.size)
        assertEquals(12, rows[0].strokes)
        assertEquals(9_000L, rows[0].endedAt)
    }

    @Test
    fun danglingOpenSessionIsClosedAtItsLastScroll() = runBlocking {
        dao.applyBatch(listOf(session(1, endedAt = null, strokes = 3)), emptyList())
        assertNull(dao.sessionsForDay("2026-10-01")[0].endedAt)

        assertEquals(1, dao.closeDanglingSessions())
        assertEquals(5_000L, dao.sessionsForDay("2026-10-01")[0].endedAt)
    }

    @Test
    fun dayRangeQueryIsInclusive() = runBlocking {
        dao.applyBatch(
            emptyList(),
            listOf(
                HourlyBucketEntity("2026-09-26", 9, "instagram", 1, 1.0),
                HourlyBucketEntity("2026-09-27", 9, "instagram", 1, 1.0),
                HourlyBucketEntity("2026-10-03", 9, "instagram", 1, 1.0),
                HourlyBucketEntity("2026-10-04", 9, "instagram", 1, 1.0),
            ),
        )
        val week = dao.observeBuckets("2026-09-27", "2026-10-03").first()
        assertEquals(listOf("2026-09-27", "2026-10-03"), week.map { it.day }.sorted())
    }
}
