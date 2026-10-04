package com.unfallenmore.quickjokes.ui.topics

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unfallenmore.quickjokes.data.local.entity.Topic
import com.unfallenmore.quickjokes.data.model.TopicType
import com.unfallenmore.quickjokes.data.repository.TopicImageStore
import com.unfallenmore.quickjokes.data.repository.TopicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class TopicEditorState(
    val topicId: Long? = null,
    val loading: Boolean = true,
    val title: String = "",
    val description: String = "",
    val situation: String = "",
    val type: TopicType = TopicType.TEXT,
    /** Относительный путь копии картинки (для IMAGE). */
    val imagePath: String? = null,
    /** Файл копии для превью (null, пока картинки нет). */
    val imageFile: File? = null,
    val importingImage: Boolean = false,
    val isCustom: Boolean = true,
    val saving: Boolean = false,
    val dirty: Boolean = false,
) {
    val isNew: Boolean get() = topicId == null
    val canSave: Boolean get() =
        title.isNotBlank() && !saving && !importingImage &&
            (type != TopicType.IMAGE || imagePath != null)
}

sealed interface TopicEditorEvent {
    data object Saved : TopicEditorEvent
    data object NoFreeSlots : TopicEditorEvent
    data object Deleted : TopicEditorEvent
    data class Error(val message: String) : TopicEditorEvent
}

@HiltViewModel
class TopicEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val topicRepository: TopicRepository,
    private val imageStore: TopicImageStore,
) : ViewModel() {

    /** -1 (сантинел маршрута) = создание новой темы; 0 и больше = реальный id. */
    private val topicId: Long? =
        savedStateHandle.get<Long>("topicId")?.takeIf { it >= 0L }

    private val _state = MutableStateFlow(TopicEditorState(topicId = topicId))
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<TopicEditorEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TopicEditorEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val existing = topicId?.let { topicRepository.getById(it) }
            if (existing != null) {
                _state.update {
                    it.copy(
                        loading = false,
                        title = existing.title,
                        description = existing.description,
                        situation = existing.situation.orEmpty(),
                        type = existing.type,
                        imagePath = existing.imagePath,
                        imageFile = imageStore.file(existing.imagePath),
                        isCustom = existing.isCustom,
                    )
                }
            } else {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    /** Картинка выбрана в галерее: копируем её к себе и показываем превью. */
    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(importingImage = true) }
            val path = imageStore.import(uri)
            if (path == null) {
                _state.update { it.copy(importingImage = false) }
                _events.tryEmit(TopicEditorEvent.Error("Не удалось загрузить картинку"))
            } else {
                _state.update {
                    it.copy(
                        importingImage = false,
                        imagePath = path,
                        imageFile = imageStore.file(path),
                        dirty = true,
                    )
                }
            }
        }
    }

    /**
     * Убрать картинку. Для ещё не сохранённой темы файл удаляем сразу
     * (на БД он не ссылается), для существующей — при сохранении,
     * чтобы отмена правок не ломала тему.
     */
    fun clearImage() {
        val current = _state.value
        if (current.topicId == null) {
            imageStore.delete(current.imagePath)
        }
        _state.update { it.copy(imagePath = null, imageFile = null, dirty = true) }
    }

    fun onTitleChange(value: String) =
        _state.update { it.copy(title = value, dirty = true) }

    fun onDescriptionChange(value: String) =
        _state.update { it.copy(description = value, dirty = true) }

    fun onSituationChange(value: String) =
        _state.update { it.copy(situation = value, dirty = true) }

    fun onTypeChange(value: TopicType) =
        _state.update { it.copy(type = value, dirty = true) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }

            val situation = current.situation.trim().takeIf { it.isNotEmpty() }
            val description = current.description.trim()
            // Картинка живёт только у типа IMAGE: при смене типа копия удаляется
            val imagePath = if (current.type == TopicType.IMAGE) current.imagePath else null

            val result = runCatching {
                if (current.topicId == null) {
                    val id = topicRepository.createCustomTopic(
                        title = current.title.trim(),
                        description = description,
                        type = current.type,
                        imagePath = imagePath,
                        situation = if (current.type == TopicType.SITUATION) situation else null,
                    )
                    if (id == null) {
                        TopicEditorOutcome.NoSlots
                    } else {
                        // Картинка, выбранная, но не сохранённая из-за смены типа
                        if (current.imagePath != null && current.imagePath != imagePath) {
                            imageStore.delete(current.imagePath)
                        }
                        TopicEditorOutcome.Saved
                    }
                } else {
                    val existing = topicRepository.getById(current.topicId)
                        ?: return@runCatching TopicEditorOutcome.Saved
                    topicRepository.updateTopic(
                        existing.copy(
                            title = current.title.trim(),
                            description = description,
                            type = current.type,
                            imagePath = imagePath,
                            situation = if (current.type == TopicType.SITUATION) situation else null,
                        ),
                    )
                    // Копии, оставшиеся без ссылки (старая и/или выбранная,
                    // но отменённая сменой типа), — прибираем
                    listOfNotNull(existing.imagePath, current.imagePath)
                        .distinct()
                        .filter { it != imagePath }
                        .forEach { imageStore.delete(it) }
                    TopicEditorOutcome.Saved
                }
            }

            result.fold(
                onSuccess = { outcome ->
                    _state.update { it.copy(saving = false, dirty = false) }
                    when (outcome) {
                        TopicEditorOutcome.Saved -> _events.tryEmit(TopicEditorEvent.Saved)
                        TopicEditorOutcome.NoSlots -> _events.tryEmit(TopicEditorEvent.NoFreeSlots)
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(saving = false) }
                    _events.tryEmit(
                        TopicEditorEvent.Error(error.message ?: "Не удалось сохранить"),
                    )
                },
            )
        }
    }

    fun delete() {
        val id = _state.value.topicId ?: return
        viewModelScope.launch {
            runCatching { topicRepository.deleteTopic(id) }
                .onSuccess { _events.tryEmit(TopicEditorEvent.Deleted) }
                .onFailure { _events.tryEmit(TopicEditorEvent.Error("Не удалось удалить")) }
        }
    }

    private enum class TopicEditorOutcome { Saved, NoSlots }
}
