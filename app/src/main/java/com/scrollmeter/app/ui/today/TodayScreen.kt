package com.scrollmeter.app.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.R
import com.scrollmeter.app.ui.components.DateStrip
import com.scrollmeter.app.ui.components.HeroPlaceholder
import com.scrollmeter.app.ui.components.IconButton44
import com.scrollmeter.app.ui.components.LegendRow
import com.scrollmeter.app.ui.components.RowDivider
import com.scrollmeter.app.ui.components.ScreenHeader
import com.scrollmeter.app.ui.components.ScrollCard
import com.scrollmeter.app.ui.components.SessionRow
import com.scrollmeter.app.ui.components.TextLink
import com.scrollmeter.app.ui.components.TrackingStoppedCard
import com.scrollmeter.app.ui.components.WeeklyStackedChart
import com.scrollmeter.app.ui.theme.ScrollColors
import com.scrollmeter.app.ui.theme.ScrollType
import com.scrollmeter.app.util.Fmt
import java.time.LocalDate

/** Screen 1: Today (home). Layout follows the design spec block list, top to bottom. */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onOpenSettings: () -> Unit,
    onOpenWeekly: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    TodayContent(state, viewModel::select, onOpenSettings, onOpenWeekly, onOpenDay)
}

/** Stateless Today screen: draws exactly what [state] says. Used by the screenshot tests too. */
@Composable
fun TodayContent(
    state: TodayUiState,
    onSelectDate: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWeekly: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 40.dp),
    ) {
        // 1. Header
        ScreenHeader(
            title = "Scroll Meter",
            titleStyle = ScrollType.appTitle,
            trailing = { IconButton44(R.drawable.ic_settings, "Settings", onOpenSettings) },
        )

        // 2. Sub-row: "Today" (or the picked date) and the Weekly report link
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.isToday) "Today" else Fmt.dayTitle(state.selected),
                style = ScrollType.rowTitle,
                color = ScrollColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            TextLink("Weekly report", onOpenWeekly)
        }

        // 3. Date strip
        Spacer(Modifier.height(18.dp))
        DateStrip(
            days = state.weekDays.map { it.date },
            selected = state.selected,
            today = state.today,
            onSelect = onSelectDate,
        )

        // 4. Tracking-stopped card, only when needed
        if (state.trackingStopped) {
            Spacer(Modifier.height(18.dp))
            TrackingStoppedCard(onFix = onOpenSettings)
        }

        // 5. Distance card
        Spacer(Modifier.height(18.dp))
        ScrollCard(hero = true) {
            if (state.loading) {
                HeroPlaceholder()
            } else {
                Text(
                    text = Fmt.km(state.day.metres),
                    style = ScrollType.display,
                    color = ScrollColors.TextPrimary,
                )
            }
            Text(
                text = if (state.isToday) {
                    "Distance scrolled today"
                } else {
                    "Distance scrolled on ${Fmt.dayTitle(state.selected)}"
                },
                style = ScrollType.body,
                color = ScrollColors.TextSecondary,
            )
            Spacer(Modifier.height(28.dp))
            WeeklyStackedChart(
                days = state.weekDays,
                selected = state.selected,
                today = state.today,
                onBarTap = onOpenDay,
            )
            Spacer(Modifier.height(20.dp))
            LegendRow(ScrollColors.Facebook, "Facebook", Fmt.km(state.day.facebook.metres))
            Spacer(Modifier.height(12.dp))
            LegendRow(ScrollColors.Instagram, "Instagram", Fmt.km(state.day.instagram.metres))
        }

        // 6. Strokes card
        Spacer(Modifier.height(18.dp))
        ScrollCard {
            if (state.loading) {
                HeroPlaceholder()
            } else {
                Row {
                    Text(
                        text = Fmt.count(state.day.strokes),
                        style = ScrollType.displaySmall,
                        color = ScrollColors.TextPrimary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.day.strokes == 1) "stroke" else "strokes",
                        style = ScrollType.custom(18, 500),
                        color = ScrollColors.TextSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            val count = state.sessions.size
            Text(
                text = "Across $count ${if (count == 1) "session" else "sessions"}" +
                    if (state.isToday) " today" else "",
                style = ScrollType.custom(15, 500),
                color = ScrollColors.TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            if (state.sessions.isEmpty()) {
                // Empty state (design spec "States").
                RowDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No scrolling yet. Open Facebook and the counter starts.",
                    style = ScrollType.bodySmall,
                    color = ScrollColors.TextSecondary,
                )
                Spacer(Modifier.height(6.dp))
            } else {
                for (session in state.sessions) {
                    SessionRow(session, showTimeline = false)
                }
            }
            RowDivider()
            TextLink(
                text = "See full day",
                onClick = { onOpenDay(state.selected) },
                modifier = Modifier.fillMaxWidth(),
                minHeight = 48,
            )
        }
    }
}
