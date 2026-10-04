package com.unfallenmore.quickjokes.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unfallenmore.quickjokes.data.local.dao.StatsDao
import com.unfallenmore.quickjokes.data.local.entity.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** Период агрегации. */
enum class StatPeriod(val label: String) {
    DAY("День"),
    WEEK("Неделя"),
    MONTH("Месяц"),
}

/** Точка графика: день + число шуток. */
data class BarPoint(val date: LocalDate, val value: Int)

data class StatsUiState(
    val period: StatPeriod = StatPeriod.DAY,
    val loading: Boolean = true,
    val totalJokes: Int = 0,
    val sessions: Int = 0,
    val avgJokesPerSession: Double = 0.0,
    val bestJokes: Int = 0,
    val coinsEarned: Int = 0,
    val bars: List<BarPoint> = emptyList(),
)

/**
 * Статистика за день/неделю/месяц.
 *
 * Границы периода считаются в зоне пользователя, дальше передаются в SQL
 * как epoch millis — так «сегодня» не съезжает из-за часовых поясов.
 * График собирается в Kotlin по локальным датам (та же зона).
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsDao: StatsDao,
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        refresh(StatPeriod.DAY)
    }

    fun onPeriodChange(period: StatPeriod) = refresh(period)

    private fun refresh(period: StatPeriod) {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val (from, to) = range(period, today)

            val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val toMillis = to.atStartOfDay(zone).toInstant().toEpochMilli()

            val totalJokes = statsDao.totalJokesBetween(fromMillis, toMillis)
            val sessions = statsDao.sessionsBetween(fromMillis, toMillis)
            val avg = statsDao.avgJokesBetween(fromMillis, toMillis)
            val best = statsDao.bestJokesBetween(fromMillis, toMillis)
            val coins = statsDao.coinsBetween(fromMillis, toMillis)

            // График: дни периода (не больше 31 столбца за раз)
            val recent = statsDao.recentSessions(BAR_LIMIT)
            val byDay = recent
                .groupBy {
                    Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate()
                }
                .mapValues { (_, list) -> list.sumOf { it.totalJokes } }

            val bars = when (period) {
                StatPeriod.DAY -> {
                    // Почасовой профиль за сегодняшние сессии (шаг 2 часа)
                    val todaySessions = recent.filter {
                        Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() == today
                    }
                    val byHour = todaySessions.groupBy {
                        Instant.ofEpochMilli(it.startTime).atZone(zone).hour
                    }.mapValues { (_, list) -> list.sumOf { it.totalJokes } }

                    (0 until 24 step 2).map { hour ->
                        BarPoint(date = today, value = byHour[hour] ?: 0)
                    }
                }

                StatPeriod.WEEK -> {
                    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    (0 until 7).map { offset ->
                        val d = start.plusDays(offset.toLong())
                        BarPoint(date = d, value = byDay[d] ?: 0)
                    }
                }

                StatPeriod.MONTH -> {
                    val start = today.withDayOfMonth(1)
                    (0 until today.lengthOfMonth()).map { offset ->
                        val d = start.plusDays(offset.toLong())
                        BarPoint(date = d, value = byDay[d] ?: 0)
                    }
                }
            }

            _state.update {
                StatsUiState(
                    period = period,
                    loading = false,
                    totalJokes = totalJokes,
                    sessions = sessions,
                    avgJokesPerSession = avg,
                    bestJokes = best,
                    coinsEarned = coins,
                    bars = bars,
                )
            }
        }
    }

    /** [from, to) — полуинтервал дат. */
    private fun range(period: StatPeriod, today: LocalDate): Pair<LocalDate, LocalDate> =
        when (period) {
            StatPeriod.DAY -> today to today.plusDays(1)
            StatPeriod.WEEK -> {
                val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                start to start.plusWeeks(1)
            }

            StatPeriod.MONTH -> {
                val start = today.withDayOfMonth(1)
                start to start.plusMonths(1)
            }
        }

    companion object {
        private const val BAR_LIMIT = 400
    }
}
