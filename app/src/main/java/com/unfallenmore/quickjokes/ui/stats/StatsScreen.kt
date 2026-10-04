package com.unfallenmore.quickjokes.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Статистика") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.period == period,
                        onClick = { viewModel.onPeriodChange(period) },
                        label = { Text(period.label) },
                    )
                }
            }

            if (state.loading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(state.totalJokes.toString(), "Шуток", Modifier.weight(1f))
                StatCard(state.sessions.toString(), "Сессий", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    "%.1f".format(state.avgJokesPerSession),
                    "В среднем / сессия",
                    Modifier.weight(1f),
                )
                StatCard(state.bestJokes.toString(), "Лучший результат", Modifier.weight(1f))
            }
            StatCard("+${state.coinsEarned} монет заработано", "Награды", Modifier.fillMaxWidth())

            Text("Шутки по дням", style = MaterialTheme.typography.titleMedium)
            BarChart(
                bars = state.bars,
                period = state.period,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )

            if (state.bars.all { it.value == 0 }) {
                Text(
                    "За этот период сессий не было",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Простой столбчатый график на Canvas — без сторонних библиотек.
 * Нулевые значения рисуются тонкой линией, чтобы ось читалась.
 */
@Composable
private fun BarChart(
    bars: List<BarPoint>,
    period: StatPeriod,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val max = (bars.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)

    Canvas(modifier = modifier) {
        if (bars.isEmpty()) return@Canvas

        val axisY = size.height - 1f
        drawLine(
            color = axisColor,
            start = Offset(0f, axisY),
            end = Offset(size.width, axisY),
            strokeWidth = 2f,
        )

        val slot = size.width / bars.size
        val barWidth = slot * 0.6f
        val gap = slot * 0.2f
        val corner = CornerRadius(barWidth / 3f)

        bars.forEachIndexed { index, point ->
            val x = index * slot + gap
            val fraction = point.value.toFloat() / max
            val barHeight = if (point.value == 0) 0f else (size.height - 8f) * fraction

            if (barHeight > 0f) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, axisY - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
            } else {
                drawLine(
                    color = axisColor,
                    start = Offset(x, axisY - 2f),
                    end = Offset(x + barWidth, axisY - 2f),
                    strokeWidth = 3f,
                )
            }
        }
    }

    // Подпись оси X: первая/последняя дата
    val first = bars.firstOrNull()
    val last = bars.lastOrNull()
    if (first != null && last != null && period != StatPeriod.DAY) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                first.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                last.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
