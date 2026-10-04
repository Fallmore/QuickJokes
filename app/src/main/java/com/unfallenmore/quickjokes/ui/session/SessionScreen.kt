package com.unfallenmore.quickjokes.ui.session

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.unfallenmore.quickjokes.data.model.InputMode
import com.unfallenmore.quickjokes.data.model.TopicType
import com.unfallenmore.quickjokes.domain.JokeRules
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SessionScreen(
    onFinished: (sessionId: Long) -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmExit by remember { mutableStateOf(false) }

    // VOICE — шутки только записью; BOTH — текст плюс запись
    val voiceRecordEnabled = state.inputMode == InputMode.VOICE ||
        state.inputMode == InputMode.BOTH
    val voiceOnly = state.inputMode == InputMode.VOICE

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startRecording()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Нужен доступ к микрофону")
            }
        }
    }

    fun toggleRecording() {
        if (state.recording) {
            viewModel.stopRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionEvent.Finished -> onFinished(event.sessionId)
                is SessionEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    // «Назад» во время сессии не должен молча её бросать: спрашиваем.
    BackHandler { confirmExit = true }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Завершить досрочно?") },
            text = { Text("Сессия будет засчитана, шутки и монеты сохранятся.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmExit = false
                        viewModel.finishEarly()
                    },
                ) { Text("Завершить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("Продолжить") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.topic?.title ?: "Сессия") },
                actions = {
                    OutlinedButton(onClick = { confirmExit = true }) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Завершить")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Клавиатура не должна перекрывать поле ввода и кнопку
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Таймер
            TimerBlock(
                remainingSec = state.remainingSec,
                fraction = state.progressFraction,
            )

            // Подсказка темы (ситуация / картинка)
            val topic = state.topic
            if (topic != null && topic.type != TopicType.TEXT) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    val hint = when (topic.type) {
                        TopicType.SITUATION -> topic.situation ?: topic.description
                        TopicType.IMAGE -> topic.description
                        TopicType.TEXT -> topic.description
                    }
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Опора для шутки: сама картинка темы
                        if (topic.type == TopicType.IMAGE && state.topicImageFile != null) {
                            AsyncImage(
                                model = state.topicImageFile,
                                contentDescription = "Картинка темы",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        if (hint.isNotBlank()) {
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Ввод шутки: в VOICE — только запись, в BOTH — плюс микрофон справа
            if (voiceOnly) {
                RecordPanel(
                    recording = state.recording,
                    elapsedSec = state.recordingElapsedSec,
                    onRecordClick = { toggleRecording() },
                    onCancelClick = { viewModel.cancelRecording() },
                )
            } else {
                val inputError = state.inputError
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(if (voiceRecordEnabled) "Напиши или запиши…" else "Напиши шутку…")
                    },
                    isError = inputError != null,
                    supportingText = {
                        Text(
                            when {
                                inputError != null -> inputError
                                state.recording ->
                                    "Запись ${formatTimer(state.recordingElapsedSec)} — микрофон сохранит её как шутку"
                                voiceRecordEnabled ->
                                    "От ${JokeRules.MIN_LENGTH} символов — и шутка в счёт, микрофон справа записывает голос"
                                else -> "От ${JokeRules.MIN_LENGTH} символов — и шутка в счёт"
                            },
                        )
                    },
                    trailingIcon = if (voiceRecordEnabled) {
                        {
                            IconButton(onClick = { toggleRecording() }) {
                                Icon(
                                    imageVector = if (state.recording) {
                                        Icons.Default.Stop
                                    } else {
                                        Icons.Default.Mic
                                    },
                                    contentDescription = if (state.recording) {
                                        "Остановить запись"
                                    } else {
                                        "Записать шутку голосом"
                                    },
                                    tint = if (state.recording) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                            }
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            viewModel.addJoke()
                            keyboard?.hide()
                        },
                    ),
                    singleLine = false,
                    maxLines = 3,
                )

                Button(
                    onClick = {
                        viewModel.addJoke()
                        keyboard?.hide()
                    },
                    enabled = !state.recording,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Добавить")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Цель сессии: сколько шуток нужно и достигнута ли она
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Шуток: ${state.jokes.size}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.goalCount > 0) {
                        Text(
                            text = if (state.goalReached) {
                                "Цель взята! +${state.difficulty.goalBonus}"
                            } else {
                                "Цель: ${state.goalCount}"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = if (state.goalReached) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                if (state.goalCount > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (state.jokes.size.toFloat() / state.goalCount).coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = if (state.goalReached) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state.jokes.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Пока пусто.\nБыстрее — веселее!",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.jokes, key = { it.id }) { joke ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                                    text = if (isVoiceJoke) "🎙 Голосовая шутка" else joke.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isVoiceJoke) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordPanel(
    recording: Boolean,
    elapsedSec: Int,
    onRecordClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (recording) {
            Button(
                onClick = onRecordClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Остановить · ${formatTimer(elapsedSec)}")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onCancelClick) { Text("Отменить запись") }
            }
            Text(
                "Говори шутку — «Остановить» сохранит её в счёт",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Button(
                onClick = onRecordClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Записать шутку голосом")
            }
            Text(
                "Диктофон: говори — стоп превратит запись в шутку",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TimerBlock(remainingSec: Int, fraction: Float) {
    val danger = remainingSec <= 10
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatTimer(remainingSec),
            style = MaterialTheme.typography.displayLarge,
            color = if (danger) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = if (danger) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

private fun formatTimer(totalSec: Int): String {
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
