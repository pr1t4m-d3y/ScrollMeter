package com.scrollmeter.app.service

/** FR7.4 / U13: the exact strings the two HUD counters show. Pure Kotlin. */
object HudFormat {

    private const val EPSILON = 1e-6

    /** Left counter: integer, no thousands separators ("1412"). */
    fun strokes(strokes: Int): String = (if (strokes < 0) 0 else strokes).toString()

    /**
     * Right counter. Values are cut, never rounded up, so the HUD never shows a metre that
     * has not been scrolled yet.
     *
     *  - under 10,000 m: whole metres + "m" ("80m"), or one decimal when [decimals] is on ("80.4m")
     *  - 10,000 m and above: kilometres with one decimal and no unit ("12.4")
     */
    fun metres(metres: Double, decimals: Boolean): String {
        val m = if (metres.isNaN() || metres < 0.0) 0.0 else metres + EPSILON
        if (m >= 10_000.0) {
            val tenthsOfKm = (m / 100.0).toLong()
            return "${tenthsOfKm / 10}.${tenthsOfKm % 10}"
        }
        if (decimals) {
            val tenths = (m * 10.0).toLong()
            return "${tenths / 10}.${tenths % 10}m"
        }
        return "${m.toLong()}m"
    }
}
