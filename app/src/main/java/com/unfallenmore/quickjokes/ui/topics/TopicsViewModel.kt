package com.unfallenmore.quickjokes.ui.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unfallenmore.quickjokes.data.local.entity.Topic
import com.unfallenmore.quickjokes.data.repository.TopicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Фильтры списка тем. */
enum class TopicFilter(val label: String) {
    ALL("Все"),
    UNLOCKED("Открытые"),
    LOCKED("Закрытые"),
    MINE("Мои"),
    VISUAL("Картинки и ситуации"),
}

data class TopicsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val filter: TopicFilter = TopicFilter.ALL,
    val topics: List<Topic> = emptyList(),
    val filtered: List<Topic> = emptyList(),
    val coins: Int = 0,
)

sealed interface TopicsEvent {
    data class Unlocked(val title: String, val coinsSpent: Int) : TopicsEvent
    data class NotEnoughCoins(val required: Int) : TopicsEvent
    data object AlreadyUnlocked : TopicsEvent
}

@HiltViewModel
class TopicsViewModel @Inject constructor(
    private val topicRepository: TopicRepository,
    private val profileRepository: com.unfallenmore.quickjokes.data.repository.ProfileRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(TopicFilter.ALL)

    private val _events = MutableSharedFlow<TopicsEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<TopicsUiState> = combine(
        topicRepository.observeAll(),
        profileRepository.observeProfile(),
        query,
        filter,
    ) { topics, profile, q, f ->
        val filtered = topics.filter { topic ->
            val matchesQuery = q.isBlank() ||
                topic.title.contains(q, ignoreCase = true) ||
                topic.description.contains(q, ignoreCase = true)

            val matchesFilter = when (f) {
                TopicFilter.ALL -> true
                TopicFilter.UNLOCKED -> !topic.isLocked
                TopicFilter.LOCKED -> topic.isLocked
                TopicFilter.MINE -> topic.isCustom
                TopicFilter.VISUAL ->
                    topic.type != com.unfallenmore.quickjokes.data.model.TopicType.TEXT
            }

            matchesQuery && matchesFilter
        }

        TopicsUiState(
            loading = false,
            query = q,
            filter = f,
            topics = topics,
            filtered = filtered,
            coins = profile.coins,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TopicsUiState())

    fun onQueryChange(value: String) = query.update { value }

    fun onFilterChange(value: TopicFilter) = filter.update { value }

    fun unlockTopic(topic: Topic) {
        viewModelScope.launch {
            val required = topic.cost
            val balance = profileRepository.getBalance()
            if (required > balance) {
                _events.tryEmit(TopicsEvent.NotEnoughCoins(required))
                return@launch
            }
            val ok = topicRepository.unlockTopic(topic.id)
            if (ok) {
                _events.tryEmit(TopicsEvent.Unlocked(topic.title, required))
            } else {
                _events.tryEmit(TopicsEvent.NotEnoughCoins(required))
            }
        }
    }
}
