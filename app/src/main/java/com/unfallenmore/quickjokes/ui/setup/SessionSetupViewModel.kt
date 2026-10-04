package com.unfallenmore.quickjokes.ui.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unfallenmore.quickjokes.data.local.entity.Topic
import com.unfallenmore.quickjokes.data.model.Difficulty
import com.unfallenmore.quickjokes.data.model.InputMode
import com.unfallenmore.quickjokes.data.repository.TopicRepository
import com.unfallenmore.quickjokes.domain.RewardCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val topic: Topic? = null,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val customDurationSec: Int = 60,
    val useCustomDuration: Boolean = false,
    val inputMode: InputMode = InputMode.TEXT,
    val starting: Boolean = false,
) {
    /**
     * Время берётся у сложности; «Своё» — исключение для тренировки
     * под себя (10–600 сек).
     */
    val effectiveDurationSec: Int
        get() = if (useCustomDuration) customDurationSec else difficulty.durationSec

    /** Цель сессии в шутках для текущего времени и сложности. */
    val goalCount: Int
        get() = RewardCalculator.goalCount(difficulty, effectiveDurationSec)
}

@HiltViewModel
class SessionSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val topicRepository: TopicRepository,
) : ViewModel() {

    private val topicId: Long = checkNotNull(savedStateHandle["topicId"])

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val topic = topicRepository.getById(topicId)
            _state.value = _state.value.copy(topic = topic)
        }
    }

    fun selectCustomDuration(sec: Int) {
        _state.value = _state.value.copy(customDurationSec = sec, useCustomDuration = true)
    }

    /** Вернуться к времени, заданному сложностью. */
    fun useDifficultyDuration() {
        _state.value = _state.value.copy(useCustomDuration = false)
    }

    fun selectDifficulty(value: Difficulty) {
        _state.value = _state.value.copy(difficulty = value)
    }

    fun selectInputMode(value: InputMode) {
        _state.value = _state.value.copy(inputMode = value)
    }
}
