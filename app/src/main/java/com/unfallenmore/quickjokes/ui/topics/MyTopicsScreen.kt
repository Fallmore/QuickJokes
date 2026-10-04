package com.unfallenmore.quickjokes.ui.topics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.unfallenmore.quickjokes.data.local.entity.Topic
import com.unfallenmore.quickjokes.data.model.TopicType
import com.unfallenmore.quickjokes.data.repository.TopicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyTopicsUiState(
    val loading: Boolean = true,
    val topics: List<Topic> = emptyList(),
    val freeSlots: Int = 0,
)

@HiltViewModel
class MyTopicsViewModel @Inject constructor(
    private val topicRepository: TopicRepository,
    profileRepository: com.unfallenmore.quickjokes.data.repository.ProfileRepository,
) : ViewModel() {

    val uiState: StateFlow<MyTopicsUiState> = kotlinx.coroutines.flow.combine(
        topicRepository.observeAll(),
        profileRepository.observeProfile(),
    ) { topics, profile ->
        MyTopicsUiState(
            loading = false,
            topics = topics.filter { it.isCustom },
            freeSlots = (profile.customTopicSlots - topics.count { it.isCustom })
                .coerceAtLeast(0),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyTopicsUiState())

    fun deleteTopic(id: Long) {
        viewModelScope.launch { topicRepository.deleteTopic(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTopicsScreen(
    onBack: () -> Unit,
    onCreateClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    viewModel: MyTopicsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var deleteCandidate by remember { mutableStateOf<Topic?>(null) }

    deleteCandidate?.let { topic ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Удалить тему?") },
            text = { Text("«${topic.title}» и её сессии будут удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTopic(topic.id)
                        deleteCandidate = null
                    },
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Отмена") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои темы") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.freeSlots > 0) {
                FloatingActionButton(onClick = onCreateClick) {
                    Icon(Icons.Default.Add, contentDescription = "Создать тему")
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.topics.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (state.freeSlots > 0) {
                            "Своих тем пока нет.\nСоздай первую!"
                        } else {
                            "Нет свободных слотов.\nКупи слот в магазине."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (state.freeSlots > 0) {
                        Spacer(Modifier.size(12.dp))
                        TextButton(onClick = onCreateClick) {
                            Text("Создать тему")
                        }
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.topics, key = { it.id }) { topic ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditClick(topic.id) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(topic.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when (topic.type) {
                                        TopicType.TEXT -> "Текст"
                                        TopicType.IMAGE -> "Картинка"
                                        TopicType.SITUATION -> "Ситуация"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onEditClick(topic.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                            }
                            IconButton(onClick = { deleteCandidate = topic }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                if (state.freeSlots > 0) {
                    item {
                        Text(
                            "Свободных слотов: ${state.freeSlots}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
