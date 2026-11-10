package com.scrollmeter.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.R
import com.scrollmeter.app.data.SessionUi
import com.scrollmeter.app.model.WatchedApp
import com.scrollmeter.app.ui.theme.ScrollColors
import com.scrollmeter.app.ui.theme.ScrollType
import com.scrollmeter.app.util.Fmt

/** The dot colour for an app. Charts fold Facebook Lite into Facebook blue; rows keep its own tint. */
fun appColor(app: WatchedApp): Color = when (app) {
    WatchedApp.FACEBOOK -> ScrollColors.Facebook
    WatchedApp.FACEBOOK_LITE -> ScrollColors.FacebookLite
    WatchedApp.INSTAGRAM -> ScrollColors.Instagram
}

/** 1 dp row separator inside cards. */
@Composable
fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ScrollColors.Divider),
    )
}

/** Legend row: height 24, gap 12; 12 dp dot, name, value 18 sp 600 on the right. */
@Composable
fun LegendRow(color: Color, name: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = ScrollType.body,
            color = ScrollColors.TextBody,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = ScrollType.custom(18, 600), color = ScrollColors.TextPrimary)
    }
}

/**
 * Session row: min 64 dp, top divider, gap 14; app dot, time over "App · N strokes",
 * distance 17 sp 700 on the right. Day detail adds the 6 dp timeline strip, inset 26.
 */
@Composable
fun SessionRow(session: SessionUi, showTimeline: Boolean) {
    val color = appColor(session.app)
    Column(modifier = Modifier.fillMaxWidth()) {
        RowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.timeRange,
                    style = ScrollType.rowTitle,
                    color = ScrollColors.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    text = "${session.app.label} · ${Fmt.strokes(session.strokes)}",
                    style = ScrollType.bodySmall,
                    color = ScrollColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = Fmt.short(session.metres),
                style = ScrollType.custom(17, 700),
                color = ScrollColors.TextPrimary,
            )
        }
        if (showTimeline) {
            TimelineStrip(
                startFraction = session.startFraction,
                endFraction = session.endFraction,
                color = color,
                modifier = Modifier.padding(start = 26.dp, bottom = 14.dp),
            )
        }
    }
}

/** Thin 24-hour strip marking where a session falls in the day. */
@Composable
fun TimelineStrip(
    startFraction: Float,
    endFraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = ScrollColors.Divider, cornerRadius = radius)
        val minWidth = 3.dp.toPx()
        var left = size.width * startFraction
        var width = size.width * (endFraction - startFraction)
        if (width < minWidth) width = minWidth
        if (left + width > size.width) left = size.width - width
        drawRoundRect(
            color = color,
            topLeft = Offset(left, 0f),
            size = Size(width, size.height),
            cornerRadius = radius,
        )
    }
}

/** Stat tile: half width, radius 24, padding 18; label 14 sp, value 22 sp 800, sub-line 14 sp. */
@Composable
fun StatTile(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(ScrollColors.Card, RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Text(label, style = ScrollType.bodySmall, color = ScrollColors.TextSecondary, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Text(value, style = ScrollType.statValue, color = ScrollColors.TextPrimary, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            text = sub,
            style = ScrollType.bodySmall,
            color = ScrollColors.TextBody,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * App tile: half width, radius 24, padding 18; 40 dp rounded square (radius 12) in the app
 * colour with a letter (no brand logos), name 16 sp 600, km 22 sp 800, strokes 13 sp.
 */
@Composable
fun AppTile(
    color: Color,
    letter: String,
    name: String,
    distance: String,
    strokes: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(ScrollColors.Card, RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, style = ScrollType.custom(20, 800), color = Color.Black)
        }
        Spacer(Modifier.height(12.dp))
        Text(name, style = ScrollType.rowTitle, color = ScrollColors.TextPrimary, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(distance, style = ScrollType.statValue, color = ScrollColors.TextPrimary, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(strokes, style = ScrollType.caption, color = ScrollColors.TextSecondary, maxLines = 1)
    }
}

/**
 * Permission row: min 64 dp, top divider; name 16 sp 600 over reason 13 sp; on the right a
 * 32 dp green tick circle when granted, or the blue "Allow" button.
 */
@Composable
fun PermissionRow(name: String, reason: String, granted: Boolean, onAllow: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        RowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = ScrollType.rowTitle, color = ScrollColors.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(reason, style = ScrollType.caption, color = ScrollColors.TextSecondary)
            }
            Spacer(Modifier.width(12.dp))
            if (granted) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(ScrollColors.GoodBg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tick),
                        contentDescription = "Granted",
                        tint = ScrollColors.Good,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                PrimaryButtonSmall("Allow", onAllow)
            }
        }
    }
}

/** Tooltip pill: height 40, padding 0/18, fully round, #2E3034; date 14 sp + value 18 sp 800. */
@Composable
fun TooltipPill(date: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(40.dp)
            .background(ScrollColors.Pressed, CircleShape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(date, style = ScrollType.bodySmall, color = ScrollColors.TextBody, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Text(value, style = ScrollType.custom(18, 800), color = ScrollColors.TextPrimary, maxLines = 1)
    }
}
