package com.scrollmeter.app.ui.weekly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollmeter.app.ui.components.AppTile
import com.scrollmeter.app.ui.components.ComparisonBars
import com.scrollmeter.app.ui.components.HeatLegend
import com.scrollmeter.app.ui.components.HeroPlaceholder
import com.scrollmeter.app.ui.components.OverflowMenu
import com.scrollmeter.app.ui.components.PeakHeatmap
import com.scrollmeter.app.ui.components.ScreenHeader
import com.scrollmeter.app.ui.components.ScrollCard
import com.scrollmeter.app.ui.components.SecondaryButton
import com.scrollmeter.app.ui.components.SectionLabel
import com.scrollmeter.app.ui.components.WeekTabs
import com.scrollmeter.app.ui.components.WeeklyReportChart
import com.scrollmeter.app.ui.theme.ScrollColors
import com.scrollmeter.app.ui.theme.ScrollType
import com.scrollmeter.app.util.Fmt
import java.time.LocalDate

/** Screen 3: Weekly report. */
@Composable
fun WeeklyScreen(
    viewModel: WeeklyViewModel,
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    WeeklyContent(state, viewModel::selectWeek, viewModel::selectDay, onBack, onOpenDay)
}

/** Stateless Weekly report screen. Used by the screenshot tests too. */
@Composable
fun WeeklyContent(
    state: WeeklyUiState,
    onSelectWeek: (Int) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 40.dp),
    ) {
        // 1. Header
        ScreenHeader(title = "Weekly report", onBack = onBack, trailing = { OverflowMenu() })

        // 2. Range line
        Spacer(Modifier.height(16.dp))
        Text(
            text = state.rangeLabel,
            style = ScrollType.custom(17, 600),
            color = ScrollColors.SoftText,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        // 3. Week tabs
        Spacer(Modifier.height(16.dp))
        WeekTabs(state.tabLabels, state.selectedTab, onSelectWeek)

        // 4. Distance by week
        Spacer(Modifier.height(16.dp))
        SectionLabel("Distance by week")
        Spacer(Modifier.height(16.dp))
        ScrollCard(hero = true) {
            if (state.loading) {
                HeroPlaceholder()
            } else {
                Text(
                    text = Fmt.kmOneDecimal(state.averageMetres),
                    style = ScrollType.displaySmall,
                    color = ScrollColors.TextPrimary,
                )
            }
            Text(
                text = "Daily average distance",
                style = ScrollType.body,
                color = ScrollColors.TextSecondary,
            )
            Spacer(Modifier.height(18.dp))
            WeeklyReportChart(
                days = state.days,
                selected = state.selectedDay,
                today = state.today,
                averageMetres = state.averageMetres,
                onSelect = onSelectDay,
            )
            Spacer(Modifier.height(16.dp))
            SecondaryButton(
                text = "Open ${Fmt.dayTitle(state.selectedDay)}",
                onClick = { onOpenDay(state.selectedDay) },
            )
        }

        // 5. Peak scrolling times
        Spacer(Modifier.height(16.dp))
        SectionLabel("Peak scrolling times")
        Spacer(Modifier.height(16.dp))
        ScrollCard(hero = true) {
            val busiest = state.busiestHour
            if (state.loading) {
                HeroPlaceholder()
            } else {
                Text(
                    text = if (busiest == null) "–" else Fmt.hour(busiest),
                    style = ScrollType.displaySmall,
                    color = ScrollColors.TextPrimary,
                )
            }
            Text(
                text = "Busiest scrolling hour",
                style = ScrollType.body,
                color = ScrollColors.TextSecondary,
            )
            Spacer(Modifier.height(22.dp))
            PeakHeatmap(heat = state.heat, busiestHour = busiest)
            Spacer(Modifier.height(18.dp))
            HeatLegend()
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Metres scrolled per hour",
                style = ScrollType.micro,
                color = ScrollColors.TextSecondary,
            )
        }

        // 6. Compared with last week
        Spacer(Modifier.height(16.dp))
        SectionLabel(if (state.isCurrentWeek) "Compared with last week" else "Compared with the week before")
        Spacer(Modifier.height(16.dp))
        ScrollCard {
            Text(
                text = WeeklyText.comparison(
                    state.thisWeekMetres,
                    state.lastWeekMetres,
                    state.isCurrentWeek,
                    state.weekNumber,
                ),
                style = ScrollType.custom(17, 500).copy(lineHeight = 25.5.sp),
                color = ScrollColors.Sentence,
            )
            Spacer(Modifier.height(16.dp))
            ComparisonBars(
                firstLabel = if (state.isCurrentWeek) "This week" else "Week ${state.weekNumber}",
                firstMetres = state.thisWeekMetres,
                secondLabel = if (state.isCurrentWeek) "Last week" else "Week before",
                secondMetres = state.lastWeekMetres,
            )
        }

        // 7. Most scrolled apps (the bigger one first)
        Spacer(Modifier.height(16.dp))
        SectionLabel("Most scrolled apps")
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val facebookFirst = state.facebook.metres >= state.instagram.metres
            if (facebookFirst) {
                FacebookTile(state, Modifier.weight(1f))
                InstagramTile(state, Modifier.weight(1f))
            } else {
                InstagramTile(state, Modifier.weight(1f))
                FacebookTile(state, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FacebookTile(state: WeeklyUiState, modifier: Modifier) {
    AppTile(
        color = ScrollColors.Facebook,
        letter = "F",
        name = "Facebook",
        distance = Fmt.km(state.facebook.metres),
        strokes = Fmt.strokes(state.facebook.strokes),
        modifier = modifier,
    )
}

@Composable
private fun InstagramTile(state: WeeklyUiState, modifier: Modifier) {
    AppTile(
        color = ScrollColors.Instagram,
        letter = "I",
        name = "Instagram",
        distance = Fmt.km(state.instagram.metres),
        strokes = Fmt.strokes(state.instagram.strokes),
        modifier = modifier,
    )
}
