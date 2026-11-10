package com.scrollmeter.app.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollmeter.app.data.Aggregator
import com.scrollmeter.app.data.DaySummary
import com.scrollmeter.app.data.HourSlot
import com.scrollmeter.app.data.HourlyBucketEntity
import com.scrollmeter.app.data.ScrollRepository
import com.scrollmeter.app.data.SessionEntity
import com.scrollmeter.app.data.SessionMapper
import com.scrollmeter.app.data.SessionUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/** Everything Screen 2 draws. */
data class DayUiState(
    val loading: Boolean,
    val date: LocalDate,
    val summary: DaySummary,
    /** 24 slots, hour 0 to 23. */
    val hours: List<HourSlot>,
    /** Visible sessions, oldest first. */
    val sessions: List<SessionUi>,
    val longest: SessionUi?,
    val peak: HourSlot?,
) {
    companion object {
        fun loading(date: LocalDate) = DayUiState(
            loading = true,
            date = date,
            summary = DaySummary(date),
            hours = List(24) { HourSlot(it) },
            sessions = emptyList(),
            longest = null,
            peak = null,
        )

        /** Pure: builds the screen state from database rows. Unit-tested. */
        fun build(
            date: LocalDate,
            buckets: List<HourlyBucketEntity>,
            sessions: List<SessionEntity>,
            zone: ZoneId,
        ): DayUiState {
            val hours = Aggregator.hourly(date, buckets)
            val visible = SessionMapper.visible(sessions, zone)
            return DayUiState(
                loading = false,
                date = date,
                summary = Aggregator.daySummary(date, buckets),
                hours = hours,
                sessions = visible,
                longest = SessionMapper.longest(visible),
                peak = Aggregator.peakHour(hours),
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(
    private val repository: ScrollRepository,
    initialDate: LocalDate,
) : ViewModel() {

    private val date = MutableStateFlow(initialDate)

    val state: StateFlow<DayUiState> =
        date.flatMapLatest { day ->
            combine(
                repository.observeBuckets(day, day),
                repository.observeSessions(day),
            ) { buckets, sessions ->
                DayUiState.build(day, buckets, sessions, ZoneId.systemDefault())
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DayUiState.loading(initialDate),
        )

    fun show(day: LocalDate) {
        date.value = day
    }
}
