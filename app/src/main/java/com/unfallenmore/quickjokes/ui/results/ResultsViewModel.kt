package com.unfallenmore.quickjokes.ui.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unfallenmore.quickjokes.data.local.dao.AchievementDao
import com.unfallenmore.quickjokes.data.local.dao.ProfileDao
import com.unfallenmore.quickjokes.data.local.entity.Achievement
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ResultsUiState(
    val loading: Boolean = true,
    val session: Session? = null,
    val jokes: List<Joke> = emptyList(),
    val newAchievements: List<Achievement> = emptyList(),
    /** Счётчик шуток на момент закрытия — не «мигает» при удалении списка. */
    val jokeCount: Int = 0,
    val jokesPerMinute: Double = 0.0,
    val coinsEarned: Int = 0,
    val deleted: Boolean = false,
)

sealed interface ResultsEvent {
    data class Error(val message: String) : ResultsEvent
    data object Deleted : ResultsEvent
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val profileDao: ProfileDao,
    private val achievementDao: AchievementDao,
    private val audioStore: JokeAudioStore,
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    private val _events = MutableSharedFlow<ResultsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ResultsEvent> = _events.asSharedFlow()

    val uiState: StateFlow<ResultsUiState> = combine(
        sessionRepository.observeSession(sessionId),
        sessionRepository.observeJokes(sessionId),
        sessionRepository.observeSession(sessionId).flatMapLatest { session ->
            if (session == null) flowOf(emptyList())
            else profileDao.observeUnlockedSince(session.startTime)
        },
        achievementDao.observeCatalog(),
    ) { session, jokes, unlocked, catalog ->
        val unlockedCodes = unlocked.map { it.achievementCode }.toHashSet()
        ResultsUiState(
            loading = false,
            session = session,
            jokes = jokes,
            newAchievements = catalog.filter { it.code in unlockedCodes },
            jokeCount = session?.totalJokes ?: jokes.size,
            jokesPerMinute = session?.jokesPerMinute ?: 0.0,
            coinsEarned = session?.coinsEarned ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResultsUiState())

    /** Файл голосовой записи шутки (null — не голосовая или файла нет). */
    fun audioFile(joke: Joke): File? = audioStore.file(joke.audioPath)

    /** Текст для системного Share: заголовок + список шуток. */
    fun shareText(): String {
        val state = uiState.value
        val jokes = state.jokes.joinToString(separator = "\n") {
            "• ${it.text.ifBlank { "🎙 запись" }}"
        }
        return buildString {
            appendLine("QuickJokes: ${state.jokeCount} шуток")
            appendLine("Темп: ${state.jokesPerMinute} шт/мин")
            appendLine()
            if (jokes.isNotEmpty()) append(jokes) else append("Шуток не записалось!")
        }
    }

    fun deleteSession() {
        viewModelScope.launch {
            runCatching { sessionRepository.deleteSession(sessionId) }
                .onSuccess { _events.tryEmit(ResultsEvent.Deleted) }
                .onFailure { _events.tryEmit(ResultsEvent.Error("Не удалось удалить")) }
        }
    }
}
