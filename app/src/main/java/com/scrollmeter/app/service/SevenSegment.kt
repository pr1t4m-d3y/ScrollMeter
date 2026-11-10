package com.scrollmeter.app.service

/**
 * Which of the seven segments light up for each HUD character (U12).
 *
 * ```
 *    aaa
 *   f   b
 *    ggg
 *   e   c
 *    ddd
 * ```
 *
 * "m" lights c, e and g so it reads like the "n" in Android's "Show refresh rate" overlay.
 * "." is not in the map: the view draws it as a small square at the baseline.
 */
object SevenSegment {

    val SEGMENTS: Map<Char, String> = mapOf(
        '0' to "abcdef",
        '1' to "bc",
        '2' to "abged",
        '3' to "abgcd",
        '4' to "fgbc",
        '5' to "afgcd",
        '6' to "afgedc",
        '7' to "abc",
        '8' to "abcdefg",
        '9' to "abcdfg",
        'm' to "ceg",
    )

    fun segmentsFor(c: Char): String = SEGMENTS[c] ?: ""
}
