package com.unfallenmore.quickjokes.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unfallenmore.quickjokes.data.model.Difficulty
import com.unfallenmore.quickjokes.data.model.InputMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSetupScreen(
    onBack: () -> Unit,
    onStartClick: (durationSec: Int, difficulty: Difficulty, inputMode: InputMode) -> Unit,
    viewModel: SessionSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройка сессии") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        val topic = state.topic
        if (topic == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Клавиатура не должна перекрывать кнопку «Старт»
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(topic.title, style = MaterialTheme.typography.titleLarge)
                    if (topic.description.isNotBlank()) {
                        Text(
                            topic.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            SectionTitle("Сложность")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { diff ->
                    FilterChip(
                        selected = state.difficulty == diff,
                        onClick = { viewModel.selectDifficulty(diff) },
                        label = { Text(diff.ruLabel) },
                    )
                }
            }

            // Что именно даёт выбранная сложность
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "${state.effectiveDurationSec} сек • цель ${state.goalCount} шуток • " +
                            "база ${state.difficulty.baseReward} монет",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        "Премия за цель +${state.difficulty.goalBonus} • темп: " +
                            "${state.difficulty.jpmTier1}/мин -> +5, " +
                            "${state.difficulty.jpmTier2}/мин -> +10",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        "1 монета за каждую шутку",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            // Своё время — исключение из правила «время задаёт сложность»
            FilterChip(
                selected = state.useCustomDuration,
                onClick = {
                    if (state.useCustomDuration) {
                        viewModel.useDifficultyDuration()
                    } else {
                        viewModel.selectCustomDuration(state.customDurationSec)
                    }
                },
                label = {
                    Text(
                        if (state.useCustomDuration) {
                            "Своё время: ${state.customDurationSec} сек"
                        } else {
                            "Своё время"
                        },
                    )
                },
            )

            if (state.useCustomDuration) {
                OutlinedTextField(
                    value = state.customDurationSec.toString(),
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(3)
                        val value = digits.toIntOrNull()?.coerceIn(10, 600) ?: 10
                        viewModel.selectCustomDuration(value)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Своя длительность, сек (10–600)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }

            SectionTitle("Режим ввода")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.inputMode == InputMode.TEXT,
                    onClick = { viewModel.selectInputMode(InputMode.TEXT) },
                    label = { Text("Текст") },
                )
                FilterChip(
                    selected = state.inputMode == InputMode.VOICE,
                    onClick = { viewModel.selectInputMode(InputMode.VOICE) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.width(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Голос")
                        }
                    },
                )
                FilterChip(
                    selected = state.inputMode == InputMode.BOTH,
                    onClick = { viewModel.selectInputMode(InputMode.BOTH) },
                    label = { Text("Оба") },
                )
            }
            Text(
                "«Голос» и «Оба» пишут речь диктофоном — запись сохраняется " +
                "вместе со шуткой, её можно послушать в результатах и истории.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    onStartClick(
                        state.effectiveDurationSec,
                        state.difficulty,
                        state.inputMode,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("Старт", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}
