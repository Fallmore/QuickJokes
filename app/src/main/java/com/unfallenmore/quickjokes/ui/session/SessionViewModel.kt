package com.unfallenmore.quickjokes.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unfallenmore.quickjokes.data.audio.AudioRecorder
import com.unfallenmore.quickjokes.data.local.entity.Joke
import com.unfallenmore.quickjokes.data.local.entity.Topic
import com.unfallenmore.quickjokes.data.model.Difficulty
import com.unfallenmore.quickjokes.data.model.InputMode
import com.unfallenmore.quickjokes.data.model.SessionStatus
import com.unfallenmore.quickjokes.data.repository.JokeAudioStore
import com.unfallenmore.quickjokes.data.repository.SessionRepository
import com.unfallenmore.quickjokes.data.repository.TopicImageStore
import com.unfallenmore.quickjokes.data.repository.TopicRepository
import com.unfallenmore.quickjokes.domain.JokeRules
import com.unfallenmore.quickjokes.domain.RewardCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SessionUiState(
    val sessionId: Long = 0L,
    val topic: Topic? = null,
    /** Остаток секунд до конца сессии. */
    val remainingSec: Int = 0,
    val totalSec: Int = 0,
    val jokes: List<Joke> = emptyList(),
    val inputText: String = "",
    val inputError: String? = null,
    val finished: Boolean = false,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val inputMode: InputMode = InputMode.TEXT,
    /** Цель сессии в шутках (зависит от сложности и времени). */
    val goalCount: Int = 0,
    /** Файл картинки темы (для типа IMAGE) — показываем во время сессии. */
    val topicImageFile: File? = null,
    /** Идёт ли сейчас запись речи (диктофон). */
    val recording: Boolean = false,
    /** Секунд в текущей записи — показываем на кнопке. */
    val recordingElapsedSec: Int = 0,
) {
    val progressFraction: Float
        get() = if (totalSec <= 0) 0f else (1f - remainingSec.toFloat() / totalSec).coerceIn(0f, 1f)

    val goalReached: Boolean
        get() = goalCount > 0 && jokes.size >= goalCount
}

sealed interface SessionEvent {
    data class Finished(val sessionId: Long) : SessionEvent

    /** Сообщение в снекбар (ошибки записи, микрофон и т.п.). */
    data class Message(val text: String) : SessionEvent
}

/**
 * Ведение сессии.
 *
 * Таймер считает по [endTimestamp], а не по тикам: при сворачивании
 * приложения остаток пересчитывается как endAt - now, поэтому точность
 * не зависит от частоты кадров и убитых в фоне корутин.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val topicRepository: TopicRepository,
    private val imageStore: TopicImageStore,
    private val audioStore: JokeAudioStore,
    private val recorder: AudioRecorder,
) : ViewModel() {

    private val topicId: Long = checkNotNull(savedStateHandle["topicId"])
    private val durationSec: Int = checkNotNull(savedStateHandle["durationSec"])
    private val difficulty: Difficulty =
        Difficulty.fromString(savedStateHandle["difficulty"])
    private val inputMode: InputMode =
        InputMode.fromString(savedStateHandle["inputMode"])

    private val _state = MutableStateFlow(
        SessionUiState(
            sessionId = savedStateHandle[KEY_SESSION_ID] ?: 0L,
            totalSec = durationSec,
            remainingSec = durationSec,
            difficulty = difficulty,
            inputMode = inputMode,
            goalCount = RewardCalculator.goalCount(difficulty, durationSec),
        ),
    )
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    /** epoch millis окончания сессии; переживает поворот через SavedStateHandle. */
    private var endAtMillis: Long = savedStateHandle[KEY_END_AT] ?: 0L
    private var tickerJob: Job? = null
    private var finishing = false

    /** epoch millis старта текущей записи (0 — не пишем). */
    private var recordingStartedAtMs = 0L

    init {
        viewModelScope.launch {
            val topic = topicRepository.getById(topicId)
            _state.update {
                it.copy(topic = topic, topicImageFile = imageStore.file(topic?.imagePath))
            }

            if (_state.value.sessionId == 0L) {
                val startMillis = System.currentTimeMillis()
                val id = sessionRepository.startSession(
                    topicId = topicId,
                    durationSec = durationSec,
                    difficulty = difficulty,
                    inputMode = inputMode,
                    startMillis = startMillis,
                )
                endAtMillis = startMillis + durationSec * 1000L
                savedStateHandle[KEY_END_AT] = endAtMillis
                savedStateHandle[KEY_SESSION_ID] = id
                _state.update { it.copy(sessionId = id) }
            } else if (endAtMillis <= 0L) {
                // Сессия восстановлена после смерти процесса, а время окончания
                // потеряно — пересчитываем из записи в БД.
                val restored = sessionRepository.observeSession(_state.value.sessionId).first()
                endAtMillis = restored?.endTime
                    ?: (System.currentTimeMillis() + durationSec * 1000L)
                savedStateHandle[KEY_END_AT] = endAtMillis
            }

            observeJokes()
            startTicker()
        }
    }

    private fun observeJokes() {
        viewModelScope.launch {
            sessionRepository.observeJokes(_state.value.sessionId)
                .collect { jokes -> _state.update { it.copy(jokes = jokes) } }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val remainingMs = endAtMillis - now
                val remainingSec = ((remainingMs + 999) / 1000).toInt().coerceAtLeast(0)
                val recordingSec = if (recordingStartedAtMs > 0L) {
                    ((now - recordingStartedAtMs) / 1000).toInt().coerceAtLeast(0)
                } else {
                    0
                }
                _state.update {
                    it.copy(remainingSec = remainingSec, recordingElapsedSec = recordingSec)
                }

                if (remainingMs <= 0L) {
                    finish(SessionStatus.TIME_OUT)
                    break
                }
                delay(TICK_MS)
            }
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(inputText = text, inputError = null) }
    }

    /**
     * Начать запись речи (диктофон). Разрешение MICROPHONE запрашивается
     * на экране до вызова; здесь — только фактический старт.
     */
    fun startRecording() {
        val current = _state.value
        if (current.finished || current.recording || current.sessionId == 0L || finishing) return

        val target = audioStore.newTarget()
        if (recorder.start(target)) {
            recordingStartedAtMs = System.currentTimeMillis()
            _state.update { it.copy(recording = true, recordingElapsedSec = 0) }
        } else {
            audioStore.discard(target)
            _events.tryEmit(SessionEvent.Message("Не удалось начать запись — проверь микрофон"))
        }
    }

    /**
     * Остановить запись и сохранить её как шутку.
     * Слишком короткая запись отбрасывается с подсказкой.
     */
    fun stopRecording() {
        if (!_state.value.recording) return
        viewModelScope.launch { saveCurrentRecording() }
    }

    /** Прервать запись — файл не сохраняется. */
    fun cancelRecording() {
        if (!_state.value.recording) return
        recorder.cancel()
        recordingStartedAtMs = 0L
        _state.update { it.copy(recording = false, recordingElapsedSec = 0) }
    }

    /** Общая часть: снять запись с рекордера и, если прошло — засчитать. */
    private suspend fun saveCurrentRecording(silent: Boolean = false) {
        val elapsedMs = recorder.elapsedMillis()
        val file = recorder.stop()
        recordingStartedAtMs = 0L
        _state.update { it.copy(recording = false, recordingElapsedSec = 0) }

        if (file == null || elapsedMs < JokeRules.MIN_AUDIO_MS) {
            file?.let { audioStore.discard(it) }
            if (!silent) {
                _events.tryEmit(SessionEvent.Message("Запись слишком короткая — скажи целиком"))
            }
            return
        }

        val ok = sessionRepository.addAudioJoke(
            sessionId = _state.value.sessionId,
            audioRelativePath = audioStore.relativePath(file),
        )
        if (!ok) {
            audioStore.discard(file)
            if (!silent) {
                _events.tryEmit(SessionEvent.Message("Не удалось сохранить шутку"))
            }
        }
    }

    fun addJoke() {
        val current = _state.value
        if (current.finished) return

        val text = current.inputText
        if (!JokeRules.isValid(text)) {
            _state.update {
                it.copy(inputError = "Минимум ${JokeRules.MIN_LENGTH} символа")
            }
            return
        }

        viewModelScope.launch {
            val ok = sessionRepository.addJoke(current.sessionId, text)
            if (ok) {
                _state.update { it.copy(inputText = "", inputError = null) }
            } else {
                _state.update { it.copy(inputError = "Не удалось сохранить шутку") }
            }
        }
    }

    fun finishEarly() {
        finish(SessionStatus.FINISHED_EARLY)
    }

    private fun finish(status: SessionStatus) {
        if (finishing) return
        val sessionId = _state.value.sessionId
        if (sessionId == 0L) return
        finishing = true

        viewModelScope.launch {
            // Запись, шедшая в момент конца сессии, — засчитываем (не теряем)
            if (_state.value.recording) {
                saveCurrentRecording(silent = true)
            }
            sessionRepository.finishSession(sessionId, status)
            _state.update { it.copy(finished = true, remainingSec = 0) }
            _events.tryEmit(SessionEvent.Finished(sessionId))
        }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        // Недоконченная запись не должна оставлять файл-сироту
        recorder.release()
        super.onCleared()
    }

    companion object {
        private const val KEY_END_AT = "session_end_at"
        private const val KEY_SESSION_ID = "session_id"
        private const val TICK_MS = 250L
    }
}
