package com.unfallenmore.quickjokes.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Запись речи шуток — простой диктофон поверх [MediaRecorder] (AAC в .m4a).
 *
 * Никакого распознавания: приложение только пишет звук, файл сохраняется
 * вместе со шуткой. Всё локально, без сети.
 *
 * Поток: [start] (файл-цель создаётся заранее) → [stop] (файл остаётся,
 * его путь пишется в БД) либо [cancel] (файл удаляется).
 */
@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAtMillis: Long = 0L

    val isRecording: Boolean
        get() = recorder != null

    /** Длительность текущей записи в мс (0, если не пишем). */
    fun elapsedMillis(): Long =
        if (recorder == null) 0L else System.currentTimeMillis() - startedAtMillis

    /**
     * Начать запись в [file]. false — микрофон недоступен или IO-сбой
     * (разрешение должно быть запрошено до вызова).
     */
    fun start(file: File): Boolean {
        if (recorder != null) return false
        val candidate = createRecorder()
        val ok = runCatching {
            candidate.setAudioSource(MediaRecorder.AudioSource.MIC)
            candidate.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            candidate.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            candidate.setAudioSamplingRate(SAMPLE_RATE_HZ)
            candidate.setAudioChannels(CHANNELS)
            candidate.setAudioEncodingBitRate(BIT_RATE)
            candidate.setOutputFile(file.absolutePath)
            candidate.prepare()
            candidate.start()
        }.isSuccess

        if (ok) {
            recorder = candidate
            target = file
            startedAtMillis = System.currentTimeMillis()
        } else {
            // Недокатанная цель не должна оставаться мусорить
            runCatching { candidate.release() }
            runCatching { file.delete() }
        }
        return ok
    }

    /**
     * Остановить запись и отдать готовый файл.
     * null — ошибка остановки (слишком короткая запись, сбой IO);
     * в этом случае файл удаляется.
     */
    fun stop(): File? {
        val current = recorder ?: return null
        val file = target
        val ok = runCatching { current.stop() }.isSuccess
        releaseRecorder()
        return if (ok && file != null && file.exists() && file.length() > 0L) {
            file
        } else {
            file?.let { runCatching { it.delete() } }
            null
        }
    }

    /** Прервать запись — файл-цель удаляется. */
    fun cancel() {
        val current = recorder
        val file = target
        if (current != null) {
            runCatching { current.stop() }
            releaseRecorder()
        }
        if (file != null) {
            runCatching { file.delete() }
        }
        target = null
    }

    /** Освободить ресурсы (уход со экрана, смерть ViewModel). */
    fun release() {
        cancel()
    }

    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private fun releaseRecorder() {
        runCatching { recorder?.release() }
        recorder = null
        target = null
    }

    companion object {
        private const val SAMPLE_RATE_HZ = 44_100
        private const val CHANNELS = 1
        private const val BIT_RATE = 64_000
    }
}
