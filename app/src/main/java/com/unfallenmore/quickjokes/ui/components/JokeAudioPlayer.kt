package com.unfallenmore.quickjokes.ui.components

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.io.File

/**
 * Проигрыватель голосовых шуток: одна запись за раз на экран.
 * Повторный тап по активной шутке — стоп; окончание записи само снимает
 * «играет»-состояние (колбэк MediaPlayer приходит не из главного потока).
 */
@Stable
class JokeAudioPlayerState {

    /** id сейчас играющей шутки (null — тишина). */
    var playingId by mutableStateOf<Long?>(null)
        private set

    private var player: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Проиграть запись [file] шутки [jokeId] либо остановить, если она уже играет. */
    fun toggle(jokeId: Long, file: File?) {
        if (file == null) return
        if (playingId == jokeId) {
            stop()
            return
        }
        stop()

        val candidate = MediaPlayer()
        candidate.setOnCompletionListener { mainHandler.post { stopIf(candidate) } }
        runCatching {
            candidate.setDataSource(file.absolutePath)
            candidate.prepare()
            candidate.start()
        }.onSuccess {
            player = candidate
            playingId = jokeId
        }.onFailure {
            runCatching { candidate.release() }
            player = null
            playingId = null
        }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingId = null
    }

    /** Остановить, если всё ещё играет именно [instance] (уже сменили шутку). */
    private fun stopIf(instance: MediaPlayer) {
        if (player === instance) stop()
    }

    fun release() = stop()
}

@Composable
fun rememberJokeAudioPlayer(): JokeAudioPlayerState {
    val state = remember { JokeAudioPlayerState() }
    DisposableEffect(Unit) {
        onDispose { state.release() }
    }
    return state
}

/** Кнопка ▶/⏸ для голосовой шутки. */
@Composable
fun PlayPauseButton(
    jokeId: Long,
    file: File?,
    player: JokeAudioPlayerState,
    modifier: Modifier = Modifier,
) {
    if (file == null) return
    val playing = player.playingId == jokeId
    IconButton(
        onClick = { player.toggle(jokeId, file) },
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (playing) "Остановить воспроизведение" else "Прослушать запись",
            tint = if (playing) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}
