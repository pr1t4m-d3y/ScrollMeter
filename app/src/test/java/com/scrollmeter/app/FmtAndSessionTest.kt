package com.scrollmeter.app

import com.scrollmeter.app.data.SessionEntity
import com.scrollmeter.app.data.SessionMapper
import com.scrollmeter.app.model.WatchedApp
import com.scrollmeter.app.util.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Dashboard number / date formats (design spec) and session list rules (FR5.4). */
class FmtAndSessionTest {

    private val utc = ZoneOffset.UTC

    private fun at(hour: Int, minute: Int, day: Int = 1): Long =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, utc).toInstant().toEpochMilli()

    private fun session(
        id: Long,
        start: Long,
        end: Long?,
        strokes: Int,
        app: String = "facebook",
        metres: Double = 10.0,
    ) = SessionEntity(
        id = id,
        app = app,
        startedAt = start,
        endedAt = end,
        strokes = strokes,
        pixels = 1_000,
        metres = metres,
        day = "2026-10-01",
        lastScrollAt = end ?: (start + 60_000),
    )

    // ---------------------------------------------------------------- formats

    @Test
    fun heroDistance_kmWithTwoDecimalsUnderTen_oneDecimalAbove() {
        assertEquals("1.79 km", Fmt.km(1_790.0))
        assertEquals("2.60 km", Fmt.km(2_600.0))
        assertEquals("0.09 km", Fmt.km(85.0))
        assertEquals("12.4 km", Fmt.km(12_400.0))
        assertEquals("10.0 km", Fmt.km(9_996.0))
    }

    @Test
    fun heroDistance_emptyAndTinyValues() {
        assertEquals("0 m", Fmt.km(0.0))
        assertEquals("0 m", Fmt.km(0.3))
        assertEquals("7 m", Fmt.km(7.2))
        assertEquals("0 m", Fmt.km(Double.NaN))
        assertEquals("0 m", Fmt.km(-4.0))
    }

    @Test
    fun weeklyAverageUsesOneDecimal() {
        assertEquals("2.1 km", Fmt.kmOneDecimal(2_100.0))
        assertEquals("0 m", Fmt.kmOneDecimal(0.0))
        assertEquals("30 m", Fmt.kmOneDecimal(30.0))
        assertEquals("0.4 km", Fmt.kmOneDecimal(420.0))
    }

    @Test
    fun rowDistance_metresUnderOneKm() {
        assertEquals("412 m", Fmt.short(412.0))
        assertEquals("0 m", Fmt.short(0.0))
        assertEquals("1.00 km", Fmt.short(999.7))
        assertEquals("1.24 km", Fmt.short(1_240.0))
    }

    @Test
    fun axisLabels() {
        assertEquals("2 km", Fmt.axisKm(2_000.0))
        assertEquals("4 km", Fmt.axisKm(4_000.0))
        assertEquals("0.5 km", Fmt.axisKm(500.0))
        assertEquals("400 m", Fmt.axisMetres(400.0))
        assertEquals("100 m", Fmt.axisMetres(100.0))
        assertEquals("1.5 km", Fmt.axisMetres(1_500.0))
        assertEquals("2 km", Fmt.axisMetres(2_000.0))
    }

    @Test
    fun countsUseThousandsSeparators() {
        assertEquals("1,412", Fmt.count(1_412))
        assertEquals("980", Fmt.count(980))
        assertEquals("1,980 strokes", Fmt.strokes(1_980))
        assertEquals("1 stroke", Fmt.strokes(1))
        assertEquals("0 strokes", Fmt.strokes(0))
    }

    @Test
    fun datesAndTimes() {
        assertEquals("Thu, 1 Oct", Fmt.dayTitle(LocalDate.of(2026, 10, 1)))
        assertEquals("27 Sep", Fmt.shortDate(LocalDate.of(2026, 9, 27)))
        assertEquals("27 Sep – 3 Oct (Week 40)", Fmt.weekRange(LocalDate.of(2026, 9, 27)))
        assertEquals("22:00", Fmt.hour(22))
        assertEquals("07:00", Fmt.hour(7))
        assertEquals("21:05", Fmt.time(at(21, 5), utc))
    }

    @Test
    fun durations() {
        assertEquals("45 s", Fmt.duration(45_000))
        assertEquals("38 min", Fmt.duration(38 * 60_000L))
        assertEquals("1 h 12 min", Fmt.duration(72 * 60_000L))
        assertEquals("0 s", Fmt.duration(-5))
    }

    // ---------------------------------------------------------------- sessions

    @Test
    fun fr5_4_sessionsUnderThreeStrokesAreHiddenFromLists() {
        val rows = listOf(
            session(1, at(9, 0), at(9, 10), strokes = 2),
            session(2, at(10, 0), at(10, 10), strokes = 3),
            session(3, at(8, 0), at(8, 5), strokes = 40),
        )
        val visible = SessionMapper.visible(rows, utc)
        assertEquals(listOf(3L, 2L), visible.map { it.id })   // oldest first, the 2-stroke one gone
    }

    @Test
    fun sessionRowTextAndTimeline() {
        val ui = SessionMapper.toUi(session(1, at(21, 5), at(21, 43), strokes = 420, app = "instagram"), utc)
        assertEquals("21:05 – 21:43", ui.timeRange)
        assertEquals(WatchedApp.INSTAGRAM, ui.app)
        assertEquals(38 * 60_000L, ui.durationMs)
        assertFalse(ui.open)
        assertEquals((21 * 60 + 5) / 1440f, ui.startFraction, 1e-4f)
        assertEquals((21 * 60 + 43) / 1440f, ui.endFraction, 1e-4f)
    }

    @Test
    fun openSessionShowsNow_andMidnightEndFillsToTheEdge() {
        val open = SessionMapper.toUi(session(1, at(21, 5), null, strokes = 5), utc)
        assertEquals("21:05 – now", open.timeRange)
        assertTrue(open.open)

        // A session split at midnight ends at 00:00 of the next day.
        val split = SessionMapper.toUi(session(2, at(23, 50), at(0, 0, day = 2), strokes = 10), utc)
        assertEquals("23:50 – 00:00", split.timeRange)
        assertEquals(1f, split.endFraction, 1e-4f)
    }

    @Test
    fun longestSession() {
        val visible = SessionMapper.visible(
            listOf(
                session(1, at(9, 0), at(9, 10), strokes = 5),
                session(2, at(21, 5), at(21, 43), strokes = 5),
                session(3, at(12, 0), at(12, 20), strokes = 5),
            ),
            utc,
        )
        assertEquals(2L, SessionMapper.longest(visible)?.id)
        assertNull(SessionMapper.longest(emptyList()))
    }

    @Test
    fun watchedAppLookups() {
        assertEquals(WatchedApp.FACEBOOK, WatchedApp.fromPackage("com.facebook.katana"))
        assertEquals(WatchedApp.FACEBOOK_LITE, WatchedApp.fromPackage("com.facebook.lite"))
        assertEquals(WatchedApp.INSTAGRAM, WatchedApp.fromPackage("com.instagram.android"))
        assertNull(WatchedApp.fromPackage("com.whatsapp"))
        assertNull(WatchedApp.fromPackage(null))
        assertEquals(WatchedApp.FACEBOOK_LITE, WatchedApp.fromKey("facebook_lite"))
        assertNull(WatchedApp.fromKey("youtube"))
    }
}
