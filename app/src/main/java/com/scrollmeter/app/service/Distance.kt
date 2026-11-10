package com.scrollmeter.app.service

/** FR4: pixels to real-world metres using the physical vertical density of the screen. */
object Distance {
    const val METRES_PER_INCH = 0.0254

    /** metres = pixels / ydpi x 0.0254. Example: 2,400 px at 400 dpi = 0.1524 m. */
    fun pixelsToMetres(pixels: Long, ydpi: Float): Double =
        pixels / ydpi.toDouble() * METRES_PER_INCH

    /** FR4.2: trust `ydpi` only when it is plausible (120 to 700), else use `densityDpi`. */
    fun effectiveDpi(ydpi: Float, densityDpi: Int): Float =
        if (ydpi.isNaN() || ydpi < 120f || ydpi > 700f) densityDpi.toFloat() else ydpi
}
