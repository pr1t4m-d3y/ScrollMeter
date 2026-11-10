package com.scrollmeter.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.R
import com.scrollmeter.app.ui.theme.ScrollColors
import com.scrollmeter.app.ui.theme.ScrollType
import java.time.LocalDate

/** Card: full width, radius 30, `card` fill, padding 20 (hero cards 26 / 20 / 22). */
@Composable
fun ScrollCard(
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val padding = if (hero) {
        PaddingValues(start = 20.dp, top = 26.dp, end = 20.dp, bottom = 22.dp)
    } else {
        PaddingValues(20.dp)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ScrollColors.Card, RoundedCornerShape(30.dp))
            .padding(padding),
        content = content,
    )
}

/** 44 x 44 dp icon button (minimum touch target). */
@Composable
fun IconButton44(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    tint: Color = ScrollColors.TextPrimary,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Screen header: 44 dp row with optional back chevron, title and optional trailing slot. */
@Composable
fun ScreenHeader(
    title: String,
    titleStyle: TextStyle = ScrollType.screenTitle,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton44(R.drawable.ic_back, "Back", onBack)
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = titleStyle,
            color = ScrollColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

/** Overflow (three dots). The only item in v1 is the "Share as image" placeholder. */
@Composable
fun OverflowMenu() {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton44(R.drawable.ic_overflow, "More options", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Share as image (coming later)", style = ScrollType.bodySmall) },
                onClick = { open = false },
                enabled = false,
            )
        }
    }
}

/**
 * Date strip: 7 equal columns, gap 4, square cells up to 54 dp, day number 20 sp.
 * Selected = grey circle + bold; past = body colour; future = muted and not tappable.
 */
@Composable
fun DateStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (day in days) {
            val isSelected = day == selected
            val isFuture = day.isAfter(today)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 54.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(if (isSelected) ScrollColors.PillSelected else Color.Transparent)
                        .selectable(
                            selected = isSelected,
                            enabled = !isFuture,
                            role = Role.Tab,
                            onClick = { onSelect(day) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = ScrollType.custom(20, if (isSelected) 700 else 500),
                        color = when {
                            isSelected -> ScrollColors.TextPrimary
                            isFuture -> ScrollColors.TextMuted
                            else -> ScrollColors.TextBody
                        },
                    )
                }
            }
        }
    }
}

/** Week tabs: 4 equal columns, gap 6, height 56, fully round; selected = `cardRaised` fill. */
@Composable
fun WeekTabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) ScrollColors.CardRaised else Color.Transparent)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = ScrollType.custom(18, if (isSelected) 700 else 500),
                    color = if (isSelected) ScrollColors.TextPrimary else ScrollColors.TextSecondary,
                )
            }
        }
    }
}

/** Segmented control: `cardRaised` track, radius 26, padding 4; two 44 dp segments. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .background(ScrollColors.CardRaised, RoundedCornerShape(26.dp))
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = ScrollType.custom(15, if (isSelected) 700 else 500),
                    color = if (isSelected) Color.Black else ScrollColors.SoftText,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Switch: 52 x 30 track, 24 dp knob, padding 3. On = Facebook blue, off = #4A4D52. */
@Composable
fun ScrollSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val knobX by animateDpAsState(if (checked) 22.dp else 0.dp, label = "knob")
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 44.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 30.dp)
                .background(
                    if (checked) ScrollColors.Facebook else ScrollColors.SwitchOff,
                    CircleShape,
                )
                .padding(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(knobX.roundToPx(), 0) }
                    .size(24.dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}

/** Round 44 dp step button ("−" / "+") in the `cardRaised` style of the secondary button. */
@Composable
fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(ScrollColors.CardRaised)
            .clickable(
                enabled = enabled,
                onClickLabel = description,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = ScrollType.custom(22, 600), color = ScrollColors.TextPrimary)
    }
}

/** Colour choice: a 26 dp dot in a 44 dp touch target; the selected one gets a white ring. */
@Composable
fun ColourDot(
    color: Color,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .border(2.dp, ScrollColors.TextPrimary, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(color, CircleShape),
        )
    }
}

/** Small primary button: height 40, fully round, Facebook blue; pressed = 85% opacity. */
@Composable
fun PrimaryButtonSmall(text: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(40.dp)
                .alpha(if (pressed) 0.85f else 1f)
                .background(ScrollColors.Facebook, CircleShape)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = ScrollType.custom(14, 700), color = ScrollColors.OnPrimary)
        }
    }
}

/** Secondary button: height 46, full width, fully round, `cardRaised`; pressed = #2E3034. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(CircleShape)
            .background(if (pressed) ScrollColors.Pressed else ScrollColors.CardRaised)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ScrollType.custom(15, 600), color = ScrollColors.Link)
    }
}

/** Text link with a full 44 dp touch target ("Weekly report", "See full day"). */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Int = 44,
) {
    Box(
        modifier = modifier
            .heightIn(min = minHeight.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = ScrollType.rowTitle, color = ScrollColors.Link)
    }
}

/** Loading state: the hero number is a 120 x 40 `divider` block. No spinner. */
@Composable
fun HeroPlaceholder() {
    Box(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .size(width = 120.dp, height = 40.dp)
            .background(ScrollColors.Divider, RoundedCornerShape(10.dp)),
    )
}

/** Section label above a card on the Weekly report: 16 sp 600 #C9CBCF, inset 8. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = ScrollType.rowTitle,
        color = ScrollColors.SoftText,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/** Small rounded tag used for "Avg." and "Peak" on the Weekly report charts. */
@Composable
fun ChartTag(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(ScrollColors.Tag, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = ScrollType.custom(11, 600),
            color = ScrollColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Tracking-stopped card (design spec "States"). Shown at the top of Today only when needed. */
@Composable
fun TrackingStoppedCard(onFix: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ScrollColors.StoppedCard, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Counting has stopped",
                style = ScrollType.custom(16, 700),
                color = ScrollColors.StoppedTitle,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Turn Scroll Meter back on in Settings",
                style = ScrollType.bodySmall,
                color = ScrollColors.TextBody,
            )
        }
        Spacer(Modifier.width(12.dp))
        PrimaryButtonSmall("Fix", onFix)
    }
}
