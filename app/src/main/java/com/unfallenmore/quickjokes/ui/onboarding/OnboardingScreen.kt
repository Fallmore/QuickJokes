package com.unfallenmore.quickjokes.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class OnboardingItem(
    val icon: ImageVector,
    val title: String,
    val text: String,
)

private val items = listOf(
    OnboardingItem(
        icon = Icons.Default.Timer,
        title = "Выбери тему и время",
        text = "30, 60 или 120 секунд — и вперёд придумывать шутки на выбранную тему.",
    ),
    OnboardingItem(
        icon = Icons.Default.Mic,
        title = "Пиши быстро",
        text = "Каждая короткая шутка от 3 символов засчитывается и приносит монеты.",
    ),
    OnboardingItem(
        icon = Icons.Default.EmojiEvents,
        title = "Собирай награды",
        text = "Монеты, достижения и ежедневные бонусы за прогресс и серии входов.",
    ),
)

/** Экран онбординга: кратко объясняет, как играть. */
@Composable
fun OnboardingScreen(
    onStart: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Быстрые шуточки",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Тренажёр быстрых шуток",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))

            items.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Rowish(item)
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("Поехали", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Rowish(item: OnboardingItem) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.padding(start = 16.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}
