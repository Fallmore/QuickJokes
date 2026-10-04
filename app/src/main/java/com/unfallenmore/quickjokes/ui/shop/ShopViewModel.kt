package com.unfallenmore.quickjokes.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unfallenmore.quickjokes.data.local.entity.Topic
import com.unfallenmore.quickjokes.data.local.entity.UserProfile
import com.unfallenmore.quickjokes.data.repository.ProfileRepository
import com.unfallenmore.quickjokes.data.repository.TopicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShopUiState(
    val loading: Boolean = true,
    val coins: Int = 0,
    val customSlots: Int = 0,
    val freeSlots: Int = 0,
    val lockedTopics: List<Topic> = emptyList(),
    val slotCost: Int = TopicRepository.CUSTOM_SLOT_COST,
)

sealed interface ShopEvent {
    data class TopicUnlocked(val title: String, val spent: Int) : ShopEvent
    data object SlotBought : ShopEvent
    data class NotEnough(val required: Int) : ShopEvent
}

@HiltViewModel
class ShopViewModel @Inject constructor(
    private val topicRepository: TopicRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private val _events = MutableSharedFlow<ShopEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<ShopUiState> = combine(
        topicRepository.observeAll(),
        profileRepository.observeProfile(),
    ) { topics, profile ->
        ShopUiState(
            loading = false,
            coins = profile.coins,
            customSlots = profile.customTopicSlots,
            freeSlots = (profile.customTopicSlots - topics.count { it.isCustom })
                .coerceAtLeast(0),
            lockedTopics = topics.filter { it.isLocked },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShopUiState())

    fun unlockTopic(topic: Topic) {
        viewModelScope.launch {
            val balance = uiState.value.coins
            if (topic.cost > balance) {
                _events.tryEmit(ShopEvent.NotEnough(topic.cost))
                return@launch
            }
            if (topicRepository.unlockTopic(topic.id)) {
                _events.tryEmit(ShopEvent.TopicUnlocked(topic.title, topic.cost))
            } else {
                _events.tryEmit(ShopEvent.NotEnough(topic.cost))
            }
        }
    }

    fun buySlot() {
        viewModelScope.launch {
            val state = uiState.value
            if (state.coins < state.slotCost) {
                _events.tryEmit(ShopEvent.NotEnough(state.slotCost))
                return@launch
            }
            if (topicRepository.buyCustomSlot()) {
                _events.tryEmit(ShopEvent.SlotBought)
            } else {
                _events.tryEmit(ShopEvent.NotEnough(state.slotCost))
            }
        }
    }
}
