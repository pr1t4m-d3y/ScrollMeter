package com.scrollmeter.app

import com.scrollmeter.app.service.HudFormat
import com.scrollmeter.app.service.SevenSegment
import org.junit.Assert.assertEquals
import org.junit.Test

/** PRD unit tests U12 (glyph map) and U13 (HUD number format). */
class HudTest {

    private fun lit(c: Char): Set<Char> = SevenSegment.segmentsFor(c).toSet()

    @Test
    fun u12_everyGlyphLightsTheRightSegments() {
        assertEquals("abcdef".toSet(), lit('0'))
        assertEquals("bc".toSet(), lit('1'))
        assertEquals("abdeg".toSet(), lit('2'))
        assertEquals("abcdg".toSet(), lit('3'))
        assertEquals("bcfg".toSet(), lit('4'))
        assertEquals("acdfg".toSet(), lit('5'))
        assertEquals("acdefg".toSet(), lit('6'))
        assertEquals("abc".toSet(), lit('7'))
        assertEquals("abcdefg".toSet(), lit('8'))
        assertEquals("abcdfg".toSet(), lit('9'))
        assertEquals("ceg".toSet(), lit('m'))
    }

    @Test
    fun u12_noGlyphRepeatsASegmentAndUnknownCharactersAreBlank() {
        for ((glyph, segments) in SevenSegment.SEGMENTS) {
            assertEquals("glyph $glyph", segments.length, segments.toSet().size)
        }
        assertEquals("", SevenSegment.segmentsFor('x'))
        assertEquals("", SevenSegment.segmentsFor('.'))
    }

    @Test
    fun u13_metresFormat() {
        assertEquals("80m", HudFormat.metres(80.0, decimals = false))
        assertEquals("9999m", HudFormat.metres(9_999.0, decimals = false))
        assertEquals("12.4", HudFormat.metres(12_400.0, decimals = false))
    }

    @Test
    fun u13_metresAreCutNotRoundedUp() {
        assertEquals("0m", HudFormat.metres(0.0, decimals = false))
        assertEquals("79m", HudFormat.metres(79.9, decimals = false))
        assertEquals("9999m", HudFormat.metres(9_999.9, decimals = false))
        assertEquals("10.0", HudFormat.metres(10_000.0, decimals = false))
        assertEquals("12.4", HudFormat.metres(12_499.0, decimals = false))
        assertEquals("0m", HudFormat.metres(-5.0, decimals = false))
    }

    @Test
    fun u13_optionalOneDecimal() {
        assertEquals("80.4m", HudFormat.metres(80.4, decimals = true))
        assertEquals("0.0m", HudFormat.metres(0.0, decimals = true))
        assertEquals("12.4", HudFormat.metres(12_400.0, decimals = true))
    }

    @Test
    fun strokesAreAPlainInteger() {
        assertEquals("12", HudFormat.strokes(12))
        assertEquals("1412", HudFormat.strokes(1412))
        assertEquals("0", HudFormat.strokes(-3))
    }
}
