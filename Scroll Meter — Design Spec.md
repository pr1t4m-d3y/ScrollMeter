# Scroll Meter — Design Spec

Oct 5, 2026 · @Rika

## Foundations

The app is dark only, built on pure black with near-black cards, one blue and one orange for the two apps, and green only for the HUD. This spec turns the Scroll Meter Mockups canvas into exact values; all sizes are in dp, text in sp.

### Colours

| Token | Hex | Used for |
| --- | --- | --- |
| `bg` | `#000000` | Screen background |
| `card` | `#17181A` | Cards, tiles |
| `cardRaised` | `#24262A` | Selected week tab, secondary buttons, segmented track |
| `pillSelected` | `#2A2B2E` | Selected date in date strip |
| `divider` | `#2A2C30` | Row separators inside cards, empty bar stubs |
| `grid` | `#3A3C40` | Chart grid lines |
| `textPrimary` | `#FFFFFF` | Numbers, titles |
| `textBody` | `#D7D9DC` | Legend names, row secondary emphasis |
| `textSecondary` | `#B4B7BC` | Labels, captions, axis labels |
| `textMuted` | `#8A8E94` | Future dates, disabled |
| `link` | `#6AA6FF` | Text links ("Weekly report", "See full day") |
| `facebook` | `#3D8BFF` | Facebook data everywhere; primary buttons; switches on |
| `facebookLite` | `#7FB2FF` | Facebook Lite dot in settings |
| `instagram` | `#FF9F45` | Instagram data everywhere |
| `barSelected` | `#4C93FF` | Selected bar in Weekly report |
| `barIdle` | `#2E62B8` | Unselected bars in Weekly report |
| `good` | `#5BD38A` on `#1F3A2B` | Permission granted tick |
| `warn` | `#FFB46B` | "3 of 5 done" progress text |
| `hud` | `#2BEA2B` | HUD digits |

Heatmap scale (metres per hour): 0 = `#2A2C30`, 0–25 = `#2B4170`, 25–50 = `#2C5BAE`, 50–100 = `#3375E6`, 100+ = `#5AA0FF`.

### Typography

Font: Plus Jakarta Sans (bundle the TTFs in `res/font`). Fallback: system sans.

| Style | Size / weight | Letter spacing | Used for |
| --- | --- | --- | --- |
| `display` | 44 sp / 800 | −1 | Hero number on a card ("1.79 km") |
| `displaySmall` | 40–42 sp / 800 | −1 | Second-card number, weekly average |
| `screenTitle` | 28–30 sp / 800 | −0.5 | "Scroll Meter", "Weekly report" |
| `statValue` | 22 sp / 800 | 0 | Stat tiles, app tiles |
| `cardTitle` | 18 sp / 700 | 0 | "Sessions", "Permissions" |
| `rowTitle` | 16 sp / 600 | 0 | Session time, permission name |
| `body` | 16 sp / 500 | 0 | Legend names, labels under hero numbers |
| `bodySmall` | 14 sp / 500 | 0 | Row subtitles |
| `caption` | 13 sp / 500 | 0 | Axis labels, helper text |
| `micro` | 11–12 sp / 500 | 0 | Bar value labels, legends under heatmap |

### Spacing and shape

| Token | Value |
| --- | --- |
| Screen side padding | 16 |
| Card inner padding | 20 (24–26 top on hero cards) |
| Gap between cards | 16–18 |
| Row height (list rows) | min 64, 14 vertical padding |
| Card radius | 30 (large), 24 (tiles) |
| Pill radius | fully round |
| Bar width | 22 with 11 radius (weekly), full column width with 4 radius (hourly) |
| Min touch target | 44 × 44 |

### Icons

Outline icons, 1.8–2 dp stroke, 22–24 dp: back chevron, settings gear, overflow (three dots), lock (privacy), tick. Use Material Symbols Outlined or hand-drawn vectors; no brand logos for Facebook or Instagram — a coloured dot or a coloured rounded square with a letter stands in.

## Components

Fourteen reusable components cover every screen; build these in `ui/components` before any screen.

| Component | Size and shape | Content | States |
| --- | --- | --- | --- |
| **Card** | Full width, radius 30, `card` fill, padding 20 (hero 26/20/22) | Any | — |
| **Screen header** | 44 tall row | Back chevron (44 × 44) + `screenTitle` + optional overflow (44 × 44) | — |
| **Date strip** | 7 equal columns, gap 4; each cell square, max 54 | Day number 20 sp | Selected: `pillSelected` circle, 700 weight · Past: `textBody` 500 · Future: `textMuted`, not tappable |
| **Week tabs** | 4 equal columns, gap 6, height 56, fully round | "W37"… 18 sp | Selected: `cardRaised` fill, white 700 · Others: no fill, `textSecondary` 500 |
| **Segmented control** | Track `cardRaised`, radius 26, padding 4; 2 segments height 44 | Label 15 sp | Selected: white fill, black 700 text · Other: `#C9CBCF` 500 |
| **Switch** | 52 × 30 track, 24 knob, padding 3 | — | On: `facebook` track, knob right · Off: `#4A4D52`, knob left |
| **Primary button (small)** | Height 40, padding 0/18, fully round, `facebook` fill | 14 sp 700, text `#00122E` | Pressed: 85% opacity |
| **Secondary button** | Height 46, full width, fully round, `cardRaised` fill | 15 sp 600 `link` | Pressed: `#2E3034` |
| **Legend row** | Height 24, gap 12 | 12 dp dot · name (`body`, flex) · value 18 sp 600 right | — |
| **Session row** | Min 64, top divider 1 dp `divider`, gap 14 | 12 dp app dot · time (`rowTitle`) over "App · N strokes" (`bodySmall`) · distance 17 sp 700 right; Day detail adds a 6 dp timeline strip below, inset 26 | — |
| **Stat tile** | Half width, gap 12, radius 24, padding 18 | Label 14 sp · value `statValue` · sub-line 14 sp `textBody` | — |
| **App tile** | Half width, radius 24, padding 18 | 40 dp rounded square (radius 12) in app colour with letter · name 16 sp 600 · km `statValue` · strokes 13 sp | — |
| **Permission row** | Min 64, top divider | Name 16 sp 600 over reason 13 sp `textSecondary` · right: 32 dp tick circle or Allow button | Granted / Needed |
| **Tooltip pill** | Height 40, padding 0/18, fully round, `#2E3034` | Date 14 sp `textBody` + value 18 sp 800 | Centered on selected bar, clamped inside the card |

## Charts

All five charts are drawn by hand on a Compose `Canvas` with the same rules: axis labels on the right, three horizontal grid lines, Facebook blue below Instagram orange.

**Shared rules**

- Plot area + a 44–48 dp label column on the right, gap 8.
- Grid lines at top, middle and bottom, 1 dp `grid`. Labels `caption` in `textSecondary`, vertically centred on the line (top and middle only).
- Scale max = next "nice" value above the largest bar: 1, 2, 4, 6, 8, 10 km… or 200, 400, 800 m for hourly.
- Bars animate up from zero over 350 ms, ease-out, when a screen opens or the week changes.
- Every bar is a tap target the full column width and plot height.

| Chart | Where | Plot height | Bars | Extras |
| --- | --- | --- | --- | --- |
| **Weekly stacked** | Today | 220 | 7 columns, bar 22 wide, radius 11, Facebook bottom + Instagram top, clipped to one rounded shape | Day letters below: 34 dp circles, selected = `#E6E7E9` fill with black letter |
| **Hourly stacked** | Day detail | 200 | 24 columns, gap 2, radius 4; empty hour = 3 dp `divider` stub | X labels 0 / 6 / 12 / 18 / 24 spread across |
| **Weekly report** | Weekly report | 230 | 7 columns, bar 22, radius 11; selected `barSelected`, others `barIdle` | Alternating column bands `#0C0C0D` / `#1F2023`; dashed average line 2 dp `#8E9298` dash 6/6 with "Avg." tag (`#5A5D63`, radius 8); 44 dp tooltip row above; dashed 1.5 dp guide from tooltip to bar top |
| **Peak heatmap** | Weekly report | 7 rows × 14 dp, gap 8 | 24 cells per row, gap 2, radius 3 | Day letters on the right (16 dp column); "Peak" tag above the busiest hour + dashed guide; legend with 12 dp dots; x labels 0 / 6 / 12 / 18 / 24 h |
| **Comparison bars** | Weekly report | 14 per bar | Two horizontal bars, track `divider`, radius 7; this week `facebook`, last week `#6B7078` | Label row above each: week name left, km right (700) |

## Screen specs

Each screen is one vertical scroll on `bg`, side padding 16, top padding 24–28, bottom padding 40, listed here top to bottom with the gap before each block.

### 1 · Today

| # | Block | Gap above | Details |
| --- | --- | --- | --- |
| 1 | Header | — | "Scroll Meter" 30 sp 800, inset 8; settings icon button right → Settings |
| 2 | Sub-row | 18 | "Today" 16 sp 600 `textSecondary` left; "Weekly report" 16 sp 600 `link` right → Weekly report |
| 3 | Date strip | 18 | Current week Sun–Sat; tap a past day to switch the screen to that day (sub-row label becomes the date) |
| 4 | Tracking-stopped card | 18 | Only when needed (see States) |
| 5 | Distance card | 18 | Hero "1.79 km" + "Distance scrolled today" · 28 · Weekly stacked chart · 10 · day letters · 20 · legend rows (gap 12) |
| 6 | Strokes card | 18 | "1,412" 40 sp 800 + "strokes" 18 sp 500 on one baseline · "Across 4 sessions today" 15 sp · 10 · session rows · "See full day" 48 dp row link → Day detail |

Number formats: km with 2 decimals under 10 km, 1 decimal above; strokes with thousands separators ("1,412").

### 2 · Day detail

| # | Block | Gap above | Details |
| --- | --- | --- | --- |
| 1 | Header | — | Back → previous screen · "Thu, 1 Oct" 28 sp 800 · overflow (Share as image, later) |
| 2 | Summary card | 18 | Hero km + "Distance scrolled · 1,980 strokes" · 30 · Hourly chart · 10 · x labels · 22 · legend rows |
| 3 | Stat tiles | 18 | Longest session · Peak hour |
| 4 | Sessions card | 18 | "Sessions" `cardTitle` · 12 · session rows with timeline strip, oldest first |

Swipe left / right on the screen moves to the next / previous day.

### 3 · Weekly report

| # | Block | Gap above | Details |
| --- | --- | --- | --- |
| 1 | Header | — | Back · "Weekly report" · overflow |
| 2 | Range line | 16 | "27 Sep – 3 Oct (Week 40)" 17 sp 600 `#C9CBCF`, inset 8 |
| 3 | Week tabs | 16 | Last 4 weeks; selecting reloads everything below |
| 4 | Section label + Distance card | 16 / 16 | Label 16 sp 600 `#C9CBCF` inset 8 · hero "2.1 km" + "Daily average distance" · 18 · tooltip row · report chart · day letters · 16 · "Open \<date>" secondary button → Day detail |
| 5 | Section label + Peak card | 16 / 16 | Hero "22:00" + "Busiest scrolling hour" · 22 · heatmap · 18 · legend · "Metres scrolled per hour" 12 sp |
| 6 | Section label + Comparison card | 16 / 16 | Sentence 17 sp `#E4E5E7` line height 1.5 · 16 · two comparison bars |
| 7 | Section label + App tiles | 16 / 16 | Two app tiles side by side |

Default selected bar = today if in the week, otherwise the highest day.

### 4 · Settings

| # | Block | Gap above | Details |
| --- | --- | --- | --- |
| 1 | Header | — | Back · "Settings" |
| 2 | Permissions card | 16 | "Permissions" + "N of 5 done" (`warn` until 5, then `good`) · helper line · 5 permission rows |
| 3 | Apps to watch card | 16 | Facebook, Facebook Lite, Instagram rows with dot + switch |
| 4 | Floating counter card | 16 | Switch row · "Counters show" caption · segmented Today's total / This session |
| 5 | Privacy card | 16 | 44 dp lock circle + "No internet access" + "Everything stays on this phone." |

On first launch Settings opens instead of Today until all required permissions are granted; "Allow" opens the matching system page and the row refreshes on return (`onResume`).

## Floating HUD

The HUD copies Android's "Show refresh rate" look: blocky green digits built from rectangles, no background, sitting just under the status bar in both top corners.

&#91;embedded content: HUD digit geometry and placement\]

A digit is drawn from up to seven filled rectangles that overlap at the corners, which gives the solid blocky shape; "m" lights c, e and g so it reads like the reference image's "n".

| Property | Value |
| --- | --- |
| Digit height (H) | 26 dp default; setting range 16–48 |
| Digit width (W) | 0.6 × H (≈ 16 dp) |
| Segment thickness (T) | 0.17 × H, minimum 3 dp (≈ 4 dp) |
| Gap between characters | 0.16 × H, minimum 3 dp |
| Decimal point | T × T square at the baseline, its own narrow cell |
| Colour | `#2BEA2B`; options amber `#FFC233`, white, cyan `#3DE0FF` |
| Background | None |
| Outline (optional) | 2 dp black stroke behind the segments |
| Left counter | Strokes, integer, no separators ("1412") |
| Right counter | Metres + "m" glyph ("80m"); optional one decimal ("80.4m") |
| Position | 14 dp from the side edge; status bar height + 6 dp from the top |
| Rotation | Re-laid out to the new top corners in landscape |
| Touch | None at all: taps pass through |
| Appear / disappear | Instant (no animation), within 300 ms of entering or leaving a watched app |

## States

Screens have five non-happy states; none of them uses a spinner longer than a blink because all data is local.

| State | When | What it looks like |
| --- | --- | --- |
| **Empty** | No data for the selected day or week | Charts draw grid lines and grey 3 dp stubs; hero shows "0 m"; session card shows "No scrolling yet. Open Facebook and the counter starts." in `textSecondary` |
| **Loading** | First query after launch (under 200 ms) | Cards at full size with hero number as a 120 × 40 `divider` block; no spinner |
| **Tracking stopped** | Accessibility service off, or watched app used today with zero counted scrolls | Card at top of Today: fill `#3A1D1D`, radius 24, padding 18; title "Counting has stopped" 16 sp 700 `#FF8A80`; line "Turn Scroll Meter back on in Settings" 14 sp; primary button "Fix" → Settings |
| **Missing permission** | Any required permission not granted | Settings row shows Allow; Today shows the tracking-stopped card; HUD switch disabled with caption "Needs display permission" if overlay fallback is in use |
| **App not supported** | A watched app sends no usable scroll events (e.g. Facebook Lite) | Settings row caption "Not supported on this phone" in `warn`; switch disabled |

## Implementation notes

The theme tokens and the seven-segment view below are ready to paste into the project; the HUD view uses plain Android `View` so it stays light inside the service.

**Compose colour tokens**

```kotlin
object ScrollColors {
    val Bg = Color(0xFF000000)
    val Card = Color(0xFF17181A)
    val CardRaised = Color(0xFF24262A)
    val PillSelected = Color(0xFF2A2B2E)
    val Divider = Color(0xFF2A2C30)
    val Grid = Color(0xFF3A3C40)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextBody = Color(0xFFD7D9DC)
    val TextSecondary = Color(0xFFB4B7BC)
    val TextMuted = Color(0xFF8A8E94)
    val Link = Color(0xFF6AA6FF)
    val Facebook = Color(0xFF3D8BFF)
    val FacebookLite = Color(0xFF7FB2FF)
    val Instagram = Color(0xFFFF9F45)
    val BarSelected = Color(0xFF4C93FF)
    val BarIdle = Color(0xFF2E62B8)
    val Good = Color(0xFF5BD38A)
    val Warn = Color(0xFFFFB46B)
    val Hud = Color(0xFF2BEA2B)
    val Heat = listOf(
        Color(0xFF2A2C30), Color(0xFF2B4170), Color(0xFF2C5BAE),
        Color(0xFF3375E6), Color(0xFF5AA0FF)
    )
}
```

**SevenSegmentView**

```kotlin
class SevenSegmentView(context: Context) : View(context) {

    var text: String = ""
        set(value) { if (field != value) { field = value; requestLayout(); invalidate() } }
    var digitHeight = 26f * resources.displayMetrics.density
        set(value) { field = value; requestLayout(); invalidate() }
    var color = 0xFF2BEA2B.toInt()
    var outline = false

    private val segmentsFor = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
        '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg",
        'm' to "ceg"
    )
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.BLACK
    }
    private val r = RectF()

    private val h get() = digitHeight
    private val w get() = h * 0.6f
    private val t get() = maxOf(3f, h * 0.17f)
    private val gap get() = maxOf(3f, h * 0.16f)
    private val pad get() = if (outline) 2f else 0f
    private fun widthOf(c: Char) = if (c == '.') t else w

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val chars = text.sumOf { widthOf(it).toDouble() }.toFloat()
        val gaps = gap * (text.length - 1).coerceAtLeast(0)
        setMeasuredDimension((chars + gaps + pad * 2).toInt() + 1, (h + pad * 2).toInt() + 1)
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
        // pass 0 = black outline (optional), pass 1 = green fill
        for (pass in (if (outline) 0 else 1)..1) {
            var x = pad
            for (c in text) {
                val segs = if (c == '.') "p" else segmentsFor[c] ?: ""
                for (s in segs) {
                    if (s == 'p') r.set(0f, h - t, t, h) else segment(s)
                    r.offset(x, pad)
                    canvas.drawRect(r, if (pass == 0) edge else fill)
                }
                x += widthOf(c) + gap
            }
        }
    }
}
```

**Adding the HUD windows from the accessibility service**

```kotlin
private fun hudParams(gravity: Int) = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT
).apply {
    this.gravity = Gravity.TOP or gravity
    x = dp(14)
    y = statusBarHeight() + dp(6)
    layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
}

windowManager.addView(strokesView, hudParams(Gravity.START))
windowManager.addView(metresView, hudParams(Gravity.END))
```

Update the two views from the tracker on the main thread, at most every 100 ms; set `text` only when the string actually changes so `invalidate()` isn't called needlessly.
