package com.scrollmeter.app.ui.day

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.ui.components.HeroPlaceholder
import com.scrollmeter.app.ui.components.HourlyStackedChart
import com.scrollmeter.app.ui.components.LegendRow
import com.scrollmeter.app.ui.components.OverflowMenu
import com.scrollmeter.app.ui.components.RowDivider
import com.scrollmeter.app.ui.components.ScreenHeader
import com.scrollmeter.app.ui.components.ScrollCard
import com.scrollmeter.app.ui.components.SessionRow
import com.scrollmeter.app.ui.components.StatTile
import com.scrollmeter.app.ui.theme.ScrollColors
import com.scrollmeter.app.ui.theme.ScrollType
import com.scrollmeter.app.util.Fmt
import java.time.LocalDate

/**
 * Screen 2: Day detail. Swiping left / right moves to the next / previous day
 * (never past today).
 */
@Composable
fun DayScreen(
    viewModel: DayViewModel,
    date: LocalDate,
    today: LocalDate,
    onBack: () -> Unit,
    onChangeDate: (LocalDate) -> Unit,
) {
    LaunchedEffect(date) { viewModel.show(date) }
    val loaded by viewModel.state.collectAsState()
    // While the view model is switching days, show the placeholder for the new date.
    val state = if (loaded.date == date) loaded else DayUiState.loading(date)
    DayContent(state, date, today, onBack, onChangeDate)
}

/** Stateless Day detail screen. Used by the screenshot tests too. */
@Composable
fun DayContent(
    state: DayUiState,
    date: LocalDate,
    today: LocalDate,
    onBack: () -> Unit,
    onChangeDate: (LocalDate) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(date, today) {
                val threshold = 72.dp.toPx()
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragCancel = { dragged = 0f },
                    onDragEnd = {
                        if (dragged <= -threshold && date.isBefore(today)) {
                            onChangeDate(date.plusDays(1))    // swipe left: next day
                        } else if (dragged >= threshold) {
                            onChangeDate(date.minusDays(1))   // swipe right: previous day
                        }
                        dragged = 0f
                    },
                ) { _, amount -> dragged += amount }
            }
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 40.dp),
    ) {
        // 1. Header
        ScreenHeader(
            title = Fmt.dayTitle(date),
            onBack = onBack,
            trailing = { OverflowMenu() },
        )

        // 2. Summary card
        Spacer(Modifier.height(18.dp))
        ScrollCard(hero = true) {
            if (state.loading) {
                HeroPlaceholder()
            } else {
                Text(
                    text = Fmt.km(state.summary.metres),
                    style = ScrollType.display,
                    color = ScrollColors.TextPrimary,
                )
            }
            Text(
                text = "Distance scrolled · ${Fmt.strokes(state.summary.strokes)}",
                style = ScrollType.body,
                color = ScrollColors.TextSecondary,
            )
            Spacer(Modifier.height(30.dp))
            HourlyStackedChart(hours = state.hours, animationKey = date)
            Spacer(Modifier.height(22.dp))
            LegendRow(ScrollColors.Facebook, "Facebook", Fmt.km(state.summary.facebook.metres))
            Spacer(Modifier.height(12.dp))
            LegendRow(ScrollColors.Instagram, "Instagram", Fmt.km(state.summary.instagram.metres))
        }

        // 3. Stat tiles
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val longest = state.longest
            StatTile(
                label = "Longest session",
                value = if (longest == null) "–" else Fmt.duration(longest.durationMs),
                sub = longest?.timeRange ?: "No sessions",
                modifier = Modifier.weight(1f),
            )
            val peak = state.peak
            StatTile(
                label = "Peak hour",
                value = if (peak == null) "–" else Fmt.hour(peak.hour),
                sub = if (peak == null) "No scrolling" else Fmt.short(peak.metres),
                modifier = Modifier.weight(1f),
            )
        }

        // 4. Sessions card
        Spacer(Modifier.height(18.dp))
        ScrollCard {
            Text("Sessions", style = ScrollType.cardTitle, color = ScrollColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            if (state.sessions.isEmpty()) {
                RowDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No scrolling yet. Open Facebook and the counter starts.",
                    style = ScrollType.bodySmall,
                    color = ScrollColors.TextSecondary,
                )
            } else {
                for (session in state.sessions) {
                    SessionRow(session, showTimeline = true)
                }
            }
        }
    }
}
