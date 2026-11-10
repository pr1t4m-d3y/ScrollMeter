package com.scrollmeter.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.data.Aggregator
import com.scrollmeter.app.data.DaySummary
import com.scrollmeter.app.data.HourSlot
import com.scrollmeter.app.data.NiceScale
import com.scrollmeter.app.ui.theme.ScrollColors
import com.scrollmeter.app.ui.theme.ScrollType
import com.scrollmeter.app.util.Fmt
import java.time.LocalDate

/*
 * Shared chart rules (design spec "Charts"):
 *  - plot area + a 46 dp label column on the right, gap 8
 *  - three 1 dp grid lines (top, middle, bottom); labels on top and middle only
 *  - scale max = next "nice" value above the largest bar
 *  - bars grow from zero over 350 ms, ease-out
 *  - Facebook blue below Instagram orange
 * Everything is drawn by hand on a Compose Canvas; there is no chart library.
 */

private val LABEL_COLUMN = 46.dp
private val LABEL_GAP = 8.dp

/** Set to false to draw charts at full height straight away (used by screenshot tests). */
val LocalAnimateCharts = staticCompositionLocalOf { true }

/** 0 → 1 over 350 ms, restarted whenever [key] changes (screen opens, week changes). */
@Composable
fun rememberBarProgress(key: Any?): Float {
    if (!LocalAnimateCharts.current) return 1f
    val progress = remember { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 350, easing = LinearOutSlowInEasing))
    }
    return progress.value
}

private fun DrawScope.drawGrid() {
    val stroke = 1.dp.toPx()
    val half = stroke / 2f
    drawLine(ScrollColors.Grid, Offset(0f, half), Offset(size.width, half), stroke)
    drawLine(ScrollColors.Grid, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke)
    drawLine(ScrollColors.Grid, Offset(0f, size.height - half), Offset(size.width, size.height - half), stroke)
}

/** Two stacked colours clipped to one rounded bar. */
private fun DrawScope.drawStackedBar(
    left: Float,
    width: Float,
    height: Float,
    bottomShare: Float,
    radius: Float,
    bottomColor: Color,
    topColor: Color,
) {
    val top = size.height - height
    val bottomHeight = height * bottomShare
    val shape = Path().apply {
        addRoundRect(RoundRect(Rect(left, top, left + width, size.height), CornerRadius(radius)))
    }
    clipPath(shape) {
        drawRect(topColor, Offset(left, top), Size(width, height - bottomHeight))
        drawRect(bottomColor, Offset(left, size.height - bottomHeight), Size(width, bottomHeight))
    }
}

/** Axis labels on the right, vertically centred on the top and middle grid lines. */
@Composable
private fun AxisLabels(top: String, middle: String, height: Dp) {
    Box(
        modifier = Modifier
            .width(LABEL_COLUMN)
            .height(height),
    ) {
        Text(
            text = top,
            style = ScrollType.caption,
            color = ScrollColors.TextSecondary,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = (-9).dp),
        )
        Text(
            text = middle,
            style = ScrollType.caption,
            color = ScrollColors.TextSecondary,
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

/** Day letters under a 7-column chart: 34 dp circles; selected = #E6E7E9 with a black letter. */
@Composable
fun DayLetters(days: List<LocalDate>, selected: LocalDate?, today: LocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = LABEL_COLUMN + LABEL_GAP),
    ) {
        for (day in days) {
            val isSelected = day == selected
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            if (isSelected) ScrollColors.DayLetterSelected else Color.Transparent,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        // Monday = 1 ... Sunday = 7
                        text = "MTWTFSS"[day.dayOfWeek.value - 1].toString(),
                        style = ScrollType.custom(14, 600),
                        color = when {
                            isSelected -> Color.Black
                            day.isAfter(today) -> ScrollColors.TextMuted
                            else -> ScrollColors.TextSecondary
                        },
                    )
                }
            }
        }
    }
}

/**
 * Weekly stacked chart (Today): plot 220 dp, 7 columns, bar 22 wide with radius 11.
 * Tapping a column (full width and height) opens that day.
 */
@Composable
fun WeeklyStackedChart(
    days: List<DaySummary>,
    selected: LocalDate,
    today: LocalDate,
    onBarTap: (LocalDate) -> Unit,
) {
    val plotHeight = 220.dp
    val max = NiceScale.weeklyMaxMetres(days.maxOfOrNull { it.metres } ?: 0.0)
    val progress = rememberBarProgress(days.firstOrNull()?.date)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(plotHeight)
                    .pointerInput(days, today) {
                        detectTapGestures { tap ->
                            if (days.isEmpty()) return@detectTapGestures
                            val column = size.width / days.size.toFloat()
                            val index = (tap.x / column).toInt().coerceIn(0, days.size - 1)
                            val date = days[index].date
                            if (!date.isAfter(today)) onBarTap(date)
                        }
                    },
            ) {
                drawGrid()
                if (days.isEmpty()) return@Canvas
                val column = size.width / days.size
                val barWidth = 22.dp.toPx()
                val radius = 11.dp.toPx()
                val stub = 3.dp.toPx()
                days.forEachIndexed { index, day ->
                    val left = column * index + (column - barWidth) / 2f
                    val total = day.metres
                    if (total <= 0.0) {
                        // Empty day: a grey 3 dp stub.
                        drawRoundRect(
                            color = ScrollColors.Divider,
                            topLeft = Offset(left, size.height - stub),
                            size = Size(barWidth, stub),
                            cornerRadius = CornerRadius(stub / 2f),
                        )
                    } else {
                        val height = (total / max * size.height * progress).toFloat()
                            .coerceIn(stub, size.height)
                        drawStackedBar(
                            left = left,
                            width = barWidth,
                            height = height,
                            bottomShare = (day.facebook.metres / total).toFloat(),
                            radius = radius,
                            bottomColor = ScrollColors.Facebook,
                            topColor = ScrollColors.Instagram,
                        )
                    }
                }
            }
            Spacer(Modifier.width(LABEL_GAP))
            AxisLabels(Fmt.axisKm(max), Fmt.axisKm(max / 2.0), plotHeight)
        }
        Spacer(Modifier.height(10.dp))
        DayLetters(days.map { it.date }, selected, today)
    }
}

/**
 * Hourly stacked chart (Day detail): plot 200 dp, 24 columns, gap 2, radius 4.
 * An empty hour is a 3 dp grey stub. X labels 0 / 6 / 12 / 18 / 24.
 */
@Composable
fun HourlyStackedChart(hours: List<HourSlot>, animationKey: Any?) {
    val plotHeight = 200.dp
    val max = NiceScale.hourlyMaxMetres(hours.maxOfOrNull { it.metres } ?: 0.0)
    val progress = rememberBarProgress(animationKey)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(plotHeight),
            ) {
                drawGrid()
                if (hours.isEmpty()) return@Canvas
                val gap = 2.dp.toPx()
                val barWidth = (size.width - gap * (hours.size - 1)) / hours.size
                val radius = 4.dp.toPx()
                val stub = 3.dp.toPx()
                hours.forEachIndexed { index, slot ->
                    val left = index * (barWidth + gap)
                    val total = slot.metres
                    if (total <= 0.0) {
                        drawRoundRect(
                            color = ScrollColors.Divider,
                            topLeft = Offset(left, size.height - stub),
                            size = Size(barWidth, stub),
                            cornerRadius = CornerRadius(stub / 2f),
                        )
                    } else {
                        val height = (total / max * size.height * progress).toFloat()
                            .coerceIn(stub, size.height)
                        drawStackedBar(
                            left = left,
                            width = barWidth,
                            height = height,
                            bottomShare = (slot.facebookMetres / total).toFloat(),
                            radius = radius,
                            bottomColor = ScrollColors.Facebook,
                            topColor = ScrollColors.Instagram,
                        )
                    }
                }
            }
            Spacer(Modifier.width(LABEL_GAP))
            AxisLabels(Fmt.axisMetres(max), Fmt.axisMetres(max / 2.0), plotHeight)
        }
        Spacer(Modifier.height(10.dp))
        HourAxisLabels(suffix = "")
    }
}

/** "0 / 6 / 12 / 18 / 24" spread across the plot width. */
@Composable
private fun HourAxisLabels(suffix: String, endPadding: Dp = LABEL_COLUMN + LABEL_GAP) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = endPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (label in listOf("0", "6", "12", "18", "24$suffix")) {
            Text(label, style = ScrollType.caption, color = ScrollColors.TextSecondary)
        }
    }
}

/**
 * Weekly report chart: 44 dp tooltip row, then a 230 dp plot with alternating column bands,
 * 22 dp bars (selected brighter), a dashed average line with an "Avg." tag, and a dashed
 * guide from the tooltip down to the selected bar. Tapping a column moves the tooltip.
 */
@Composable
fun WeeklyReportChart(
    days: List<DaySummary>,
    selected: LocalDate,
    today: LocalDate,
    averageMetres: Double,
    onSelect: (LocalDate) -> Unit,
) {
    val plotHeight = 230.dp
    val max = NiceScale.weeklyMaxMetres(days.maxOfOrNull { it.metres } ?: 0.0)
    val progress = rememberBarProgress(days.firstOrNull()?.date)
    val selectedIndex = days.indexOfFirst { it.date == selected }.coerceAtLeast(0)
    val selectedDay = days.getOrNull(selectedIndex)
    val averageShare = (averageMetres / max).toFloat().coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Tooltip row: the pill is centred on the selected bar and clamped inside the card.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
        ) {
            if (selectedDay != null) {
                TooltipPill(
                    date = Fmt.shortDate(selectedDay.date),
                    value = Fmt.km(selectedDay.metres),
                    modifier = Modifier.layout { measurable, constraints ->
                        val pill = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        val plotWidth = constraints.maxWidth - (LABEL_COLUMN + LABEL_GAP).roundToPx()
                        val centre = plotWidth * (selectedIndex + 0.5f) / days.size
                        val maxX = (constraints.maxWidth - pill.width).coerceAtLeast(0)
                        val x = (centre - pill.width / 2f).toInt().coerceIn(0, maxX)
                        layout(constraints.maxWidth, pill.height) { pill.place(x, 0) }
                    },
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(plotHeight),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(plotHeight)
                        .pointerInput(days, today) {
                            detectTapGestures { tap ->
                                if (days.isEmpty()) return@detectTapGestures
                                val column = size.width / days.size.toFloat()
                                val index = (tap.x / column).toInt().coerceIn(0, days.size - 1)
                                val date = days[index].date
                                if (!date.isAfter(today)) onSelect(date)
                            }
                        },
                ) {
                    if (days.isEmpty()) return@Canvas
                    val column = size.width / days.size
                    // Alternating column bands.
                    for (index in days.indices) {
                        drawRect(
                            color = if (index % 2 == 0) ScrollColors.BandDark else ScrollColors.BandLight,
                            topLeft = Offset(column * index, 0f),
                            size = Size(column, size.height),
                        )
                    }
                    drawGrid()
                    val barWidth = 22.dp.toPx()
                    val radius = 11.dp.toPx()
                    val stub = 3.dp.toPx()
                    var selectedTop = size.height
                    days.forEachIndexed { index, day ->
                        val left = column * index + (column - barWidth) / 2f
                        val isSelected = index == selectedIndex
                        val total = day.metres
                        val height = if (total <= 0.0) {
                            stub
                        } else {
                            (total / max * size.height * progress).toFloat().coerceIn(stub, size.height)
                        }
                        if (isSelected) selectedTop = size.height - height
                        drawRoundRect(
                            color = when {
                                total <= 0.0 -> ScrollColors.Divider
                                isSelected -> ScrollColors.BarSelected
                                else -> ScrollColors.BarIdle
                            },
                            topLeft = Offset(left, size.height - height),
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius(minOf(radius, height / 2f)),
                        )
                    }
                    // Dashed average line.
                    if (averageMetres > 0.0) {
                        val y = size.height * (1f - averageShare)
                        drawLine(
                            color = ScrollColors.AverageLine,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(6.dp.toPx(), 6.dp.toPx())
                            ),
                        )
                    }
                    // Dashed guide from the tooltip down to the top of the selected bar.
                    val guideX = column * (selectedIndex + 0.5f)
                    drawLine(
                        color = ScrollColors.AverageLine,
                        start = Offset(guideX, (-4).dp.toPx()),
                        end = Offset(guideX, selectedTop),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 4.dp.toPx())
                        ),
                    )
                }
                if (averageMetres > 0.0) {
                    // "Avg." tag sits on the line at the left edge of the plot.
                    ChartTag(
                        text = "Avg.",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 4.dp, y = plotHeight * (1f - averageShare) - 10.dp),
                    )
                }
            }
            Spacer(Modifier.width(LABEL_GAP))
            AxisLabels(Fmt.axisKm(max), Fmt.axisKm(max / 2.0), plotHeight)
        }
        Spacer(Modifier.height(10.dp))
        DayLetters(days.map { it.date }, selected, today)
    }
}

/**
 * Peak heatmap: 7 rows x 14 dp (gap 8), 24 cells per row (gap 2, radius 3), day letters in a
 * 16 dp column on the right, a "Peak" tag and dashed guide on the busiest hour, x labels
 * 0 / 6 / 12 / 18 / 24 h.
 */
@Composable
fun PeakHeatmap(heat: List<List<Double>>, busiestHour: Int?) {
    val rowHeight = 14.dp
    val rowGap = 8.dp
    val lettersColumn = 16.dp
    val lettersGap = 8.dp
    val gridHeight = rowHeight * 7 + rowGap * 6

    Column(modifier = Modifier.fillMaxWidth()) {
        // "Peak" tag row, centred above the busiest hour.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
        ) {
            if (busiestHour != null) {
                ChartTag(
                    text = "Peak",
                    modifier = Modifier.layout { measurable, constraints ->
                        val tag = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        val plotWidth = constraints.maxWidth - (lettersColumn + lettersGap).roundToPx()
                        val centre = plotWidth * (busiestHour + 0.5f) / 24f
                        val maxX = (constraints.maxWidth - tag.width).coerceAtLeast(0)
                        val x = (centre - tag.width / 2f).toInt().coerceIn(0, maxX)
                        layout(constraints.maxWidth, tag.height) { tag.place(x, 0) }
                    },
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(gridHeight),
            ) {
                val gap = 2.dp.toPx()
                val cellWidth = (size.width - gap * 23) / 24f
                val cellHeight = rowHeight.toPx()
                val step = (rowHeight + rowGap).toPx()
                val radius = CornerRadius(3.dp.toPx())
                heat.forEachIndexed { rowIndex, row ->
                    val top = rowIndex * step
                    for (hour in 0 until minOf(24, row.size)) {
                        drawRoundRect(
                            color = ScrollColors.Heat[Aggregator.heatLevel(row[hour])],
                            topLeft = Offset(hour * (cellWidth + gap), top),
                            size = Size(cellWidth, cellHeight),
                            cornerRadius = radius,
                        )
                    }
                }
                if (busiestHour != null) {
                    val x = busiestHour * (cellWidth + gap) + cellWidth / 2f
                    drawLine(
                        color = ScrollColors.TextPrimary,
                        start = Offset(x, (-2).dp.toPx()),
                        end = Offset(x, size.height),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 4.dp.toPx())
                        ),
                    )
                }
            }
            Spacer(Modifier.width(lettersGap))
            Column(
                modifier = Modifier.width(lettersColumn),
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                for (letter in listOf("S", "M", "T", "W", "T", "F", "S")) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(letter, style = ScrollType.custom(11, 500), color = ScrollColors.TextSecondary)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HourAxisLabels(suffix = " h", endPadding = lettersColumn + lettersGap)
    }
}

/** Heatmap legend: five 12 dp dots with their metre ranges. */
@Composable
fun HeatLegend() {
    val labels = listOf("0", "0–25", "25–50", "50–100", "100+")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(ScrollColors.Heat[index], CircleShape),
                )
                Spacer(Modifier.width(5.dp))
                Text(label, style = ScrollType.custom(11, 500), color = ScrollColors.TextSecondary)
            }
        }
    }
}

/**
 * Comparison bars: two horizontal 14 dp bars on a `divider` track (radius 7). Above each:
 * the week name on the left and km on the right (700).
 */
@Composable
fun ComparisonBars(
    firstLabel: String,
    firstMetres: Double,
    secondLabel: String,
    secondMetres: Double,
) {
    val max = maxOf(firstMetres, secondMetres)
    Column(modifier = Modifier.fillMaxWidth()) {
        ComparisonBar(firstLabel, firstMetres, max, ScrollColors.Facebook)
        Spacer(Modifier.height(14.dp))
        ComparisonBar(secondLabel, secondMetres, max, ScrollColors.LastWeekBar)
    }
}

@Composable
private fun ComparisonBar(label: String, metres: Double, max: Double, color: Color) {
    val share = if (max <= 0.0) 0f else (metres / max).toFloat().coerceIn(0f, 1f)
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = ScrollType.bodySmall,
            color = ScrollColors.TextBody,
            modifier = Modifier.weight(1f),
        )
        Text(Fmt.km(metres), style = ScrollType.custom(14, 700), color = ScrollColors.TextPrimary)
    }
    Spacer(Modifier.height(6.dp))
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        val radius = CornerRadius(7.dp.toPx())
        drawRoundRect(color = ScrollColors.Divider, cornerRadius = radius)
        if (share > 0f) {
            drawRoundRect(
                color = color,
                size = Size((size.width * share).coerceAtLeast(size.height), size.height),
                cornerRadius = radius,
            )
        }
    }
}
