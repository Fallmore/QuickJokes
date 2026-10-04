package com.unfallenmore.quickjokes.ui.history

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unfallenmore.quickjokes.data.model.SessionStatus
import com.unfallenmore.quickjokes.ui.components.PlayPauseButton
import com.unfallenmore.quickjokes.ui.components.rememberJokeAudioPlayer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailsScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: SessionDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val audioPlayer = rememberJokeAudioPlayer()
    var confirmDeleteSession by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionDetailsEvent.SessionDeleted -> onDeleted()
                is SessionDetailsEvent.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    if (confirmDeleteSession) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSession = false },
            title = { Text("Удалить сессию?") },
            text = { Text("Все шутки этой сессии будут удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteSession = false
                        viewModel.deleteSession()
                    },
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSession = false }) { Text("Отмена") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сессия") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDeleteSession = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Удалить сессию",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.loading) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val session = state.session
        if (session == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Сессия удалена")
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = onBack) { Text("Назад") }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            Instant.ofEpochMilli(session.startTime)
                                .atZone(ZoneId.systemDefault())
                                .format(dateFormatter),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "${session.totalJokes} шуток • %.1f шт/мин • %s монет"
                                .format(
                                    session.jokesPerMinute,
                                    if (session.coinsEarned > 0) "+${session.coinsEarned}" else "0",
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Сложность: ${session.difficulty.ruLabel} • " +
                                if (session.status == SessionStatus.TIME_OUT) {
                                    "время вышло"
                                } else {
                                    "завершено досрочно"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { confirmDeleteSession = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Удалить")
                    }
                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
                            }
                            context.startActivity(
                                Intent.createChooser(sendIntent, "Поделиться"),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Поделиться")
                    }
                }
            }

            item {
                Text("Шутки", style = MaterialTheme.typography.titleMedium)
            }

            if (state.jokes.isEmpty()) {
                item { Text("Шуток не записалось", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.jokes, key = { it.id }) { joke ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${joke.orderIndex}.",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            val isVoiceJoke = joke.text.isBlank()
                            Text(
                                text = if (isVoiceJoke) "🎙 Запись голосом" else joke.text,
                                color = if (isVoiceJoke) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (joke.hasAudio) {
                                PlayPauseButton(
                                    jokeId = joke.id,
                                    file = viewModel.audioFile(joke),
                                    player = audioPlayer,
                                )
                            }
                            IconButton(onClick = { viewModel.deleteJoke(joke.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить шутку",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
