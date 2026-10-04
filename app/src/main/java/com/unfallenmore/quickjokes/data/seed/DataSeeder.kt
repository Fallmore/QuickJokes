package com.unfallenmore.quickjokes.data.seed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.unfallenmore.quickjokes.data.local.dao.AchievementDao
import com.unfallenmore.quickjokes.data.local.dao.TopicDao
import com.unfallenmore.quickjokes.data.local.entity.Achievement
import com.unfallenmore.quickjokes.data.local.entity.Topic
import com.unfallenmore.quickjokes.data.model.TopicType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сид данных при первом запуске:
 *  - каталог достижений — INSERT OR IGNORE при каждом запуске (безопасно
 *    добавлять новые достижения в обновлениях);
 *  - предустановленные темы — один раз (флаг в DataStore), чтобы удалённые
 *    пользователем темы не «воскресали».
 *
 * У предустановленных тем фиксированные отрицательные id из ассета,
 * поэтому IGNORE не перезаписывает строки, изменённые пользователем.
 */
@Singleton
class DataSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val topicDao: TopicDao,
    private val achievementDao: AchievementDao,
    private val dataStore: DataStore<Preferences>,
) {
    private val seededKey = booleanPreferencesKey("topics_seeded_v1")

    suspend fun seed() {
        seedAchievements()
        seedTopics()
    }

    private suspend fun seedAchievements() {
        val rows = AchievementCatalog.all.map {
            Achievement(
                code = it.code,
                title = it.title,
                description = it.description,
                type = it.type,
                threshold = it.threshold,
                rewardCoins = it.rewardCoins,
                sortOrder = it.sortOrder,
            )
        }
        achievementDao.insertAll(rows)
    }

    private suspend fun seedTopics() {
        if (isSeeded()) return

        val topics = readTopicsFromAsset()
        if (topics.isNotEmpty()) {
            topicDao.insertAll(topics)
        }
        dataStore.edit { it[seededKey] = true }
    }

    private suspend fun isSeeded(): Boolean = runCatching {
        dataStore.data.first()[seededKey] ?: false
    }.getOrElse { error ->
        // Повреждённый DataStore не должен блокировать запуск
        if (error is IOException) false else throw error
    }

    /** Сброс прогресса: снимает флаг, чтобы темы залились заново. */
    suspend fun clearSeedFlag() {
        dataStore.edit { it.remove(seededKey) }
    }

    private fun readTopicsFromAsset(): List<Topic> {
        val json = runCatching {
            context.assets.open(TOPICS_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()

        val root = JSONObject(json)
        val array = root.optJSONArray("topics") ?: return emptyList()

        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optLong("id", 0L)
                val title = obj.optString("title").trim()
                if (id >= 0L || title.isEmpty()) continue

                add(
                    Topic(
                        id = id,
                        title = title,
                        description = obj.optString("description"),
                        type = TopicType.fromString(obj.optString("type")),
                        situation = obj.optString("situation").takeIf { it.isNotBlank() },
                        isCustom = false,
                        isLocked = obj.optBoolean("isLocked"),
                        cost = obj.optInt("cost"),
                        createdAt = 0L,
                    ),
                )
            }
        }
    }

    companion object {
        const val TOPICS_ASSET = "topics.json"
    }
}
