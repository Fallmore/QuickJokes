package com.unfallenmore.quickjokes.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Копии картинок тем во внутреннем хранилище.
 *
 * URI из галереи «протухает» (и может требовать разрешений при каждом чтении),
 * поэтому при выборе файл копируется к нам, а в [com.unfallenmore.quickjokes.data.local.entity.Topic.imagePath]
 * пишется относительный путь к копии — так тема переживает перезапуски
 * и обновления приложения. Приложение работает офлайн, картинки не откуда качать.
 */
@Singleton
class TopicImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Копирует выбранную картинку в своё хранилище. @return относительный путь или null. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(imagesDir, "${UUID.randomUUID()}$SUFFIX")
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) } > 0L
            } ?: false
            when {
                copied && target.length() > 0L -> "$IMAGES_DIR/${target.name}"
                else -> {
                    target.delete()
                    null
                }
            }
        }.getOrNull()
    }

    /**
     * Файл по относительному пути из темы.
     * Путь проверяется на принадлежность filesDir — в БД можем ошибиться,
     * а читать файлы за пределами своего хранилища не стоит.
     */
    fun file(relativePath: String?): File? {
        if (relativePath.isNullOrBlank()) return null
        val filesRoot = context.filesDir.canonicalPath
        val file = runCatching { File(context.filesDir, relativePath).canonicalFile }.getOrNull()
            ?: return null
        if (!file.path.startsWith(filesRoot + File.separator)) return null
        return file.takeIf { it.exists() }
    }

    /** Удаляет копию картинки (удаление темы, смена типа, замена картинки). */
    fun delete(relativePath: String?) {
        file(relativePath)?.delete()
    }

    /**
     * Записывает байты по относительному пути из бэкапа.
     * Путь обязан лежать внутри [IMAGES_DIR] (защита от «../» в чужом файле).
     * @return false, если путь небезопасен или запись не удалась.
     */
    fun write(relativePath: String, bytes: ByteArray): Boolean {
        if (!isInsideImages(relativePath)) return false
        return runCatching {
            val target = File(context.filesDir, relativePath)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }.isSuccess
    }

    /** Удаляет все копии, кроме перечисленных в [keep] (прибирайка после импорта). */
    fun pruneExcept(keep: Set<String>) {
        imagesDir.listFiles()?.forEach { file ->
            if ("$IMAGES_DIR/${file.name}" !in keep) {
                runCatching { file.delete() }
            }
        }
    }

    /** Относительный путь лежит внутри topic_images/ и не выходит из неё (canonical-проверка). */
    private fun isInsideImages(relativePath: String): Boolean {
        if (!relativePath.startsWith("$IMAGES_DIR/")) return false
        val root = runCatching { File(context.filesDir, IMAGES_DIR).canonicalPath }
            .getOrNull() ?: return false
        val target = runCatching { File(context.filesDir, relativePath).canonicalFile }
            .getOrNull() ?: return false
        return target.path.startsWith(root + File.separator)
    }

    private val imagesDir: File
        get() = File(context.filesDir, IMAGES_DIR).apply { mkdirs() }

    companion object {
        private const val IMAGES_DIR = "topic_images"
        private const val SUFFIX = ".img"
    }
}
