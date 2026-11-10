package com.scrollmeter.app.ui.weekly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollmeter.app.data.Aggregator
import com.scrollmeter.app.data.Amount
import com.scrollmeter.app.data.DaySummary
import com.scrollmeter.app.data.HourlyBucketEntity
import com.scrollmeter.app.data.ScrollRepository
import com.scrollmeter.app.data.Weeks
import com.scrollmeter.app.util.Fmt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Everything Screen 3 draws. */
data class WeeklyUiState(
    val loading: Boolean,
    val today: LocalDate,
    /** "W37" … "W40", oldest first. */
    val tabLabels: List<String>,
    val selectedTab: Int,
    val weekStart: LocalDate,
    val weekNumber: Int,
    val isCurrentWeek: Boolean,
    /** "27 Sep – 3 Oct (Week 40)" */
    val rangeLabel: String,
    /** Sunday to Saturday. */
    val days: List<DaySummary>,
    val averageMetres: Double,
    /** The bar carrying the tooltip. */
    val selectedDay: LocalDate,
    /** 7 rows x 24 hours, metres. */
    val heat: List<List<Double>>,
    val busiestHour: Int?,
    val thisWeekMetres: Double,
    val lastWeekMetres: Double,
    val facebook: Amount,
    val instagram: Amount,
) {
    companion object {
        const val TAB_COUNT = 4

        fun loading(today: LocalDate): WeeklyUiState =
            build(today, TAB_COUNT - 1, emptyList(), null).copy(loading = true)

        /**
         * Pure: builds the whole screen from hourly buckets. [buckets] must cover the
         * selected week and the week before it. Unit-tested (I7 numbers).
         */
        fun build(
            today: LocalDate,
            selectedTab: Int,
            buckets: List<HourlyBucketEntity>,
            pickedDay: LocalDate?,
        ): WeeklyUiState {
            val weeks = Weeks.lastFour(today)
            val tab = selectedTab.coerceIn(0, weeks.size - 1)
            val weekStart = weeks[tab]
            val days = Aggregator.weekDays(weekStart, buckets)
            val previous = Aggregator.weekDays(weekStart.minusWeeks(1), buckets)
            val heat = Aggregator.heatmap(weekStart, buckets)
            val (facebook, instagram) = Aggregator.groupTotals(days)

            // Default selected bar = today if it is in this week, otherwise the highest day.
            val inWeek = { d: LocalDate -> !d.isBefore(weekStart) && !d.isAfter(weekStart.plusDays(6)) }
            val selected = when {
                pickedDay != null && inWeek(pickedDay) && !pickedDay.isAfter(today) -> pickedDay
                inWeek(today) -> today
                else -> days.maxByOrNull { it.metres }?.date ?: weekStart
            }

            return WeeklyUiState(
                loading = false,
                today = today,
                tabLabels = weeks.map { "W${Weeks.isoWeekNumber(it)}" },
                selectedTab = tab,
                weekStart = weekStart,
                weekNumber = Weeks.isoWeekNumber(weekStart),
                isCurrentWeek = tab == weeks.size - 1,
                rangeLabel = Fmt.weekRange(weekStart),
                days = days,
                averageMetres = Aggregator.dailyAverage(days, today),
                selectedDay = selected,
                heat = heat,
                busiestHour = Aggregator.busiestHour(heat),
                thisWeekMetres = days.sumOf { it.metres },
                lastWeekMetres = previous.sumOf { it.metres },
                facebook = facebook,
                instagram = instagram,
            )
        }
    }
}

/** The one-sentence comparison on the "Compared with last week" card. Pure, unit-tested. */
object WeeklyText {

    fun comparison(thisWeek: Double, lastWeek: Double, isCurrentWeek: Boolean, weekNumber: Int): String {
        val subject = if (isCurrentWeek) "this week" else "in week $weekNumber"
        val other = if (isCurrentWeek) "last week" else "the week before"
        if (thisWeek < 0.5 && lastWeek < 0.5) {
            return "No scrolling recorded $subject or $other."
        }
        if (lastWeek < 0.5) {
            return "You scrolled ${Fmt.km(thisWeek)} $subject. There is nothing from $other to compare with."
        }
        val difference = thisWeek - lastWeek
        if (kotlin.math.abs(difference) < 0.5) {
            return "You scrolled ${Fmt.km(thisWeek)} $subject, the same as $other."
        }
        val direction = if (difference < 0) "less" else "more"
        return "You scrolled ${Fmt.km(thisWeek)} $subject, " +
            "${Fmt.km(kotlin.math.abs(difference))} $direction than $other."
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyViewModel(
    private val repository: ScrollRepository,
    today: StateFlow<LocalDate>,
) : ViewModel() {

    private val tab = MutableStateFlow(WeeklyUiState.TAB_COUNT - 1)
    private val pickedDay = MutableStateFlow<LocalDate?>(null)

    val state: StateFlow<WeeklyUiState> =
        combine(today, tab) { now, index -> now to index }
            .flatMapLatest { (now, index) ->
                val weekStart = Weeks.lastFour(now)[index.coerceIn(0, WeeklyUiState.TAB_COUNT - 1)]
                combine(
                    // This week plus the week before, for the comparison card.
                    repository.observeBuckets(weekStart.minusWeeks(1), weekStart.plusDays(6)),
                    pickedDay,
                ) { buckets, picked ->
                    WeeklyUiState.build(now, index, buckets, picked)
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                WeeklyUiState.loading(today.value),
            )

    /** Selecting a week reloads everything below the tabs. */
    fun selectWeek(index: Int) {
        pickedDay.value = null
        tab.value = index
    }

    /** Tapping a bar moves the tooltip. */
    fun selectDay(date: LocalDate) {
        pickedDay.value = date
    }
}
