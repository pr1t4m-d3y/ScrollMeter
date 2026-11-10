package com.scrollmeter.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollmeter.app.data.Aggregator
import com.scrollmeter.app.data.DaySummary
import com.scrollmeter.app.data.ScrollRepository
import com.scrollmeter.app.data.SessionMapper
import com.scrollmeter.app.data.SessionUi
import com.scrollmeter.app.data.Weeks
import com.scrollmeter.app.util.Health
import com.scrollmeter.app.util.SystemPermissions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/** Everything Screen 1 draws. */
data class TodayUiState(
    val loading: Boolean,
    val today: LocalDate,
    /** The day the whole screen is showing (today, or a past day picked in the date strip). */
    val selected: LocalDate,
    /** Sunday to Saturday of the current week. */
    val weekDays: List<DaySummary>,
    val day: DaySummary,
    /** Visible sessions of the selected day, oldest first. */
    val sessions: List<SessionUi>,
    val trackingStopped: Boolean,
) {
    val isToday: Boolean get() = selected == today

    companion object {
        fun loading(today: LocalDate) = TodayUiState(
            loading = true,
            today = today,
            selected = today,
            weekDays = Weeks.daysOf(Weeks.startOf(today)).map { DaySummary(it) },
            day = DaySummary(today),
            sessions = emptyList(),
            trackingStopped = false,
        )

        /** Pure: builds the screen state from database rows. Unit-tested (I7 numbers). */
        fun build(
            today: LocalDate,
            selected: LocalDate,
            buckets: List<com.scrollmeter.app.data.HourlyBucketEntity>,
            sessions: List<com.scrollmeter.app.data.SessionEntity>,
            permissions: SystemPermissions,
            zone: ZoneId,
        ): TodayUiState {
            val days = Aggregator.weekDays(Weeks.startOf(today), buckets)
            val todaySummary = days.firstOrNull { it.date == today } ?: DaySummary(today)
            return TodayUiState(
                loading = false,
                today = today,
                selected = selected,
                weekDays = days,
                day = days.firstOrNull { it.date == selected } ?: DaySummary(selected),
                sessions = SessionMapper.visible(sessions, zone),
                trackingStopped = Health.trackingStopped(permissions, todaySummary.strokes),
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repository: ScrollRepository,
    today: StateFlow<LocalDate>,
    permissions: StateFlow<SystemPermissions>,
) : ViewModel() {

    /** Date tapped in the date strip; null means "follow today". */
    private val picked = MutableStateFlow<LocalDate?>(null)

    val state: StateFlow<TodayUiState> =
        combine(today, picked) { now, pick ->
            // A pick is only valid inside the current week and not in the future.
            val inThisWeek = pick != null && !pick.isAfter(now) &&
                Weeks.startOf(pick) == Weeks.startOf(now)
            now to (if (inThisWeek) pick!! else now)
        }.flatMapLatest { (now, selected) ->
            val weekStart = Weeks.startOf(now)
            combine(
                repository.observeBuckets(weekStart, weekStart.plusDays(6)),
                repository.observeSessions(selected),
                permissions,
            ) { buckets, sessions, perms ->
                TodayUiState.build(now, selected, buckets, sessions, perms, ZoneId.systemDefault())
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TodayUiState.loading(today.value),
        )

    fun select(date: LocalDate) {
        picked.value = date
    }
}
