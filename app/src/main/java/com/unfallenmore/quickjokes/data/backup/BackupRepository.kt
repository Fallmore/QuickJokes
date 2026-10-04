package com.unfallenmore.quickjokes.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.unfallenmore.quickjokes.data.local.QuickJokesDatabase
import com.unfallenmore.quickjokes.data.local.entity.UserProfile
import com.unfallenmore.quickjokes.data.repository.JokeAudioStore
import com.unfallenmore.quickjokes.data.repository.TopicImageStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Экспорт/импорт всех пользовательских данных в JSON-файл (SAF, без разрешений).
 *
 * Экспорт: единый снимок (профиль, входы, достижения, монеты, свои темы
 * с картинками base64, сессии, шутки с голосовыми записями base64).
 * Импорт: сначала валидация и распаковка файлов, затем — замена данных
 * одной транзакцией (ничего не теряется при сбое в середине).
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: QuickJokesDatabase,
    private val imageStore: TopicImageStore,
    private val audioStore: JokeAudioStore,
) {

    /** Собирает снимок и пишет JSON в [uri]. */
    suspend fun exportTo(uri: Uri) {
        val data = snapshot()
        val json = BackupCodec.encode(data)
        withContext(Dispatchers.IO) {
            val stream = openOutput(uri) ?: error("Не удалось открыть файл для записи")
            stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }
    }

    /** Читает JSON из [uri], проверяет и заменяет им все пользовательские данные. */
    suspend fun importFrom(uri: Uri) {
        val json = withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: error("Не удалось открыть файл")
            stream.use { it.readBytes().toString(Charsets.UTF_8) }
        }
        val data = BackupCodec.decode(json)

        // Файлы (картинки тем и голосовые записи) — до транзакции с БД:
        // они должны существовать к моменту записи строк
        withContext(Dispatchers.IO) {
            data.topicImages.forEach { (path, base64) ->
                val bytes = runCatching { Base64.decode(base64, Base64.NO_WRAP) }
                    .getOrElse { throw IllegalArgumentException("Повреждена картинка в файле бэкапа") }
                if (!imageStore.write(path, bytes)) {
                    throw IllegalArgumentException("Небезопасный путь картинки в файле бэкапа")
                }
            }
            data.jokeAudios.forEach { (path, base64) ->
                val bytes = runCatching { Base64.decode(base64, Base64.NO_WRAP) }
                    .getOrElse { throw IllegalArgumentException("Повреждённая запись в файле бэкапа") }
                if (!audioStore.write(path, bytes)) {
                    throw IllegalArgumentException("Небезопасный путь записи в файле бэкапа")
                }
            }
        }

        db.withTransaction {
            val dao = db.backupDao()
            // Очистка: сессии первыми (шутки каскадом), свои темы последними
            dao.clearSessions()
            dao.clearTransactions()
            dao.clearUnlockedAchievements()
            dao.clearLoginDays()
            dao.clearCustomTopics()

            // Вставка в порядке внешних ключей: темы → сессии → шутки
            dao.insertTopics(data.customTopics)
            dao.insertSessions(data.sessions)
            dao.insertJokes(data.jokes)
            dao.insertTransactions(data.transactions)
            dao.insertUnlockedAchievements(data.unlockedAchievements)
            dao.insertLoginDays(data.loginDays)
            dao.insertProfile(data.profile)
        }

        // Копии файлов, оставшиеся от заменённых данных, прибираем
        val keepImages = withContext(Dispatchers.IO) { imageStorePaths() }
        val keepAudios = withContext(Dispatchers.IO) { audioStorePaths() }
        withContext(Dispatchers.IO) {
            imageStore.pruneExcept(keepImages)
            audioStore.pruneExcept(keepAudios)
        }
    }

    private suspend fun snapshot(): BackupData {
        val dao = db.backupDao()
        val topics = dao.customTopics()
        val jokes = dao.jokes()

        val images = withContext(Dispatchers.IO) {
            topics.mapNotNull { topic ->
                val path = topic.imagePath ?: return@mapNotNull null
                val file = imageStore.file(path) ?: return@mapNotNull null
                path to Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            }.toMap()
        }

        val audios = withContext(Dispatchers.IO) {
            jokes.mapNotNull { joke ->
                val path = joke.audioPath ?: return@mapNotNull null
                val bytes = audioStore.readBytes(path) ?: return@mapNotNull null
                path to Base64.encodeToString(bytes, Base64.NO_WRAP)
            }.toMap()
        }

        return BackupData(
            profile = dao.profile() ?: UserProfile(),
            loginDays = dao.loginDays(),
            unlockedAchievements = dao.unlockedAchievements(),
            transactions = dao.transactions(),
            customTopics = topics,
            sessions = dao.sessions(),
            jokes = jokes,
            topicImages = images,
            jokeAudios = audios,
        )
    }

    private suspend fun imageStorePaths(): Set<String> =
        db.backupDao().customTopics().mapNotNull { it.imagePath }.toSet()

    private suspend fun audioStorePaths(): Set<String> =
        db.backupDao().jokes().mapNotNull { it.audioPath }.toSet()

    private fun openOutput(uri: Uri) =
        runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri)
}
