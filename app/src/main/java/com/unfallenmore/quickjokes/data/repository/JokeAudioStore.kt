package com.unfallenmore.quickjokes.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Голосовые записи шуток (диктофон) во внутреннем хранилище.
 *
 * Запись создаётся в [joke_audio/], в [com.unfallenmore.quickjokes.data.local.entity.Joke.audioPath]
 * пишется относительный путь к ней — шутка переживает перезапуски и обновления.
 * Пути, приходящие из бэкапа, проверяются на принадлежность каталогу
 * (защита от «../» в чужом файле) — как в TopicImageStore.
 */
@Singleton
class JokeAudioStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Создаёт пустой файл-цель под новую запись. Recorder пишет прямо в него. */
    fun newTarget(): File =
        File(audioDir, "${UUID.randomUUID()}$SUFFIX")

    /** Относительный путь созданного файла (для записи в БД). */
    fun relativePath(file: File): String = "$AUDIO_DIR/${file.name}"

    /**
     * Файл по относительному пути из шутки.
     * Путь проверяется на принадлежность filesDir — читать файлы
     * за пределами своего хранилища не стоит.
     */
    fun file(relativePath: String?): File? {
        if (relativePath.isNullOrBlank()) return null
        val filesRoot = context.filesDir.canonicalPath
        val file = runCatching { File(context.filesDir, relativePath).canonicalFile }.getOrNull()
            ?: return null
        if (!file.path.startsWith(filesRoot + File.separator)) return null
        return file.takeIf { it.exists() }
    }

    /** Удаляет запись (удаление шутки/сессии, отменённая запись). */
    fun delete(relativePath: String?) {
        file(relativePath)?.delete()
    }

    /** Удаляет файл-цель, если запись не была сохранена. */
    fun discard(target: File) {
        runCatching { if (target.exists()) target.delete() }
    }

    /**
     * Записывает байты по относительному пути из бэкапа.
     * @return false, если путь небезопасен или запись не удалась.
     */
    fun write(relativePath: String, bytes: ByteArray): Boolean {
        if (!isInsideAudio(relativePath)) return false
        return runCatching {
            val target = File(context.filesDir, relativePath)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }.isSuccess
    }

    /** Удаляет все записи, кроме перечисленных в [keep] (прибирайка после импорта). */
    fun pruneExcept(keep: Set<String>) {
        audioDir.listFiles()?.forEach { file ->
            if ("$AUDIO_DIR/${file.name}" !in keep) {
                runCatching { file.delete() }
            }
        }
    }

    /** Читает запись в память для экспорта (или null, если файла нет). */
    suspend fun readBytes(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        file(relativePath)?.let { runCatching { it.readBytes() }.getOrNull() }
    }

    /** Относительный путь лежит внутри joke_audio/ и не выходит из него. */
    private fun isInsideAudio(relativePath: String): Boolean {
        if (!relativePath.startsWith("$AUDIO_DIR/")) return false
        val root = runCatching { File(context.filesDir, AUDIO_DIR).canonicalPath }
            .getOrNull() ?: return false
        val target = runCatching { File(context.filesDir, relativePath).canonicalFile }
            .getOrNull() ?: return false
        return target.path.startsWith(root + File.separator)
    }

    private val audioDir: File
        get() = File(context.filesDir, AUDIO_DIR).apply { mkdirs() }

    companion object {
        const val AUDIO_DIR = "joke_audio"
        private const val SUFFIX = ".m4a"
    }
}
