package com.unfallenmore.quickjokes.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unfallenmore.quickjokes.data.local.entity.Session
import com.unfallenmore.quickjokes.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Группа сессий одного дня. */
data class HistoryDay(
    val date: LocalDate,
    val sessions: List<Session>,
) {
    val jokesTotal: Int get() = sessions.sumOf { it.totalJokes }
    val coinsTotal: Int get() = sessions.sumOf { it.coinsEarned }
}

data class HistoryUiState(
    val loading: Boolean = true,
    val days: List<HistoryDay> = emptyList(),
)

sealed interface HistoryEvent {
    data class Error(val message: String) : HistoryEvent
    data object SessionDeleted : HistoryEvent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _events = MutableSharedFlow<HistoryEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<HistoryUiState> = sessionRepository.observeHistory()
        .map { sessions ->
            val zone = ZoneId.systemDefault()
            val grouped = sessions
                .groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
                .map { (date, list) -> HistoryDay(date, list.sortedByDescending { it.startTime }) }
                .sortedByDescending { it.date }

            HistoryUiState(loading = false, days = grouped)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            runCatching { sessionRepository.deleteSession(sessionId) }
                .onSuccess { _events.tryEmit(HistoryEvent.SessionDeleted) }
                .onFailure { _events.tryEmit(HistoryEvent.Error("Не удалось удалить сессию")) }
        }
    }
}
