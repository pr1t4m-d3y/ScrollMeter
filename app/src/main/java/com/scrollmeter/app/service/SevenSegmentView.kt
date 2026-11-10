package com.scrollmeter.app.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * One HUD counter. Each character is up to seven filled rectangles that overlap at the
 * corners, the same technique as Android's "Show refresh rate" overlay (design spec,
 * "Floating HUD"). A plain View, not Compose, so it stays light inside the service.
 */
class SevenSegmentView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    var text: String = ""
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    /** Digit height in pixels. Default 26 dp. */
    var digitHeight = 26f * density
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var color = 0xFF2BEA2B.toInt()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Optional 2 dp black edge so digits stay readable over bright video. */
    var outline = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.BLACK
    }
    private val r = RectF()

    private val h get() = digitHeight
    private val w get() = h * 0.6f
    private val t get() = maxOf(3f * density, h * 0.17f)
    private val gap get() = maxOf(3f * density, h * 0.16f)
    private val pad get() = if (outline) 2f * density else 0f

    private fun widthOf(c: Char) = if (c == '.') t else w

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var chars = 0f
        for (c in text) chars += widthOf(c)
        val gaps = gap * (text.length - 1).coerceAtLeast(0)
        setMeasuredDimension(
            (chars + gaps + pad * 2).toInt() + 1,
            (h + pad * 2).toInt() + 1,
        )
    }

    private fun segment(s: Char) {
        val half = h / 2f
        when (s) {
            'a' -> r.set(0f, 0f, w, t)
            'b' -> r.set(w - t, 0f, w, half + t / 2)
            'c' -> r.set(w - t, half - t / 2, w, h)
            'd' -> r.set(0f, h - t, w, h)
            'e' -> r.set(0f, half - t / 2, t, h)
            'f' -> r.set(0f, 0f, t, half + t / 2)
            'g' -> r.set(0f, half - t / 2, w, half + t / 2)
        }
    }

    override fun onDraw(canvas: Canvas) {
        fill.color = color
        // pass 0 = black outline (optional), pass 1 = coloured fill
        for (pass in (if (outline) 0 else 1)..1) {
            var x = pad
            for (c in text) {
                if (c == '.') {
                    r.set(0f, h - t, t, h)
                    r.offset(x, pad)
                    canvas.drawRect(r, if (pass == 0) edge else fill)
                } else {
                    val segs = SevenSegment.segmentsFor(c)
                    for (s in segs) {
                        segment(s)
                        r.offset(x, pad)
                        canvas.drawRect(r, if (pass == 0) edge else fill)
                    }
                }
                x += widthOf(c) + gap
            }
        }
    }
}
