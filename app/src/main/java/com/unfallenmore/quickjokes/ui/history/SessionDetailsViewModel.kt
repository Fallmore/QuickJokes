package com.unfallenmore.quickjokes.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unfallenmore.quickjokes.data.local.entity.Joke
import com.unfallenmore.quickjokes.data.local.entity.Session
import com.unfallenmore.quickjokes.data.repository.JokeAudioStore
import com.unfallenmore.quickjokes.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SessionDetailsUiState(
    val loading: Boolean = true,
    val session: Session? = null,
    val jokes: List<Joke> = emptyList(),
)

sealed interface SessionDetailsEvent {
    data object SessionDeleted : SessionDetailsEvent
    data class Error(val message: String) : SessionDetailsEvent
}

@HiltViewModel
class SessionDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val audioStore: JokeAudioStore,
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    private val _events = MutableSharedFlow<SessionDetailsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionDetailsEvent> = _events.asSharedFlow()

    val uiState: StateFlow<SessionDetailsUiState> = combine(
        sessionRepository.observeSession(sessionId),
        sessionRepository.observeJokes(sessionId),
    ) { session, jokes ->
        SessionDetailsUiState(
            loading = false,
            session = session,
            jokes = jokes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailsUiState())

    fun deleteJoke(jokeId: Long) {
        viewModelScope.launch {
            runCatching { sessionRepository.deleteJoke(jokeId) }
                .onFailure { _events.tryEmit(SessionDetailsEvent.Error("Не удалось удалить")) }
        }
    }

    fun deleteSession() {
        viewModelScope.launch {
            runCatching { sessionRepository.deleteSession(sessionId) }
                .onSuccess { _events.tryEmit(SessionDetailsEvent.SessionDeleted) }
                .onFailure { _events.tryEmit(SessionDetailsEvent.Error("Не удалось удалить")) }
        }
    }

    /** Файл голосовой записи шутки (null — не голосовая или файла нет). */
    fun audioFile(joke: Joke): File? = audioStore.file(joke.audioPath)

    fun shareText(): String {
        val state = uiState.value
        return buildString {
            appendLine("QuickJokes: ${state.jokes.size} шуток")
            appendLine()
            state.jokes.forEach { appendLine("• ${it.text.ifBlank { "🎙 запись" }}") }
        }
    }
}
