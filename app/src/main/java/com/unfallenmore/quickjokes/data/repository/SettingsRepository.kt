package com.unfallenmore.quickjokes.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.unfallenmore.quickjokes.data.local.QuickJokesDatabase
import com.unfallenmore.quickjokes.data.notifications.ReminderScheduler
import com.unfallenmore.quickjokes.data.seed.DataSeeder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Режим темы приложения. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Настройки ежедневного напоминания о тренировке. */
data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = DEFAULT_MINUTE,
) {
    companion object {
        const val DEFAULT_HOUR = 19
        const val DEFAULT_MINUTE = 0
    }
}

/**
 * Настройки приложения (DataStore) + сброс прогресса + напоминания.
 *
 * Планирование WorkManager живёт здесь же: так переключатель, время
 * и фактически запланированная задача не могут разойтись.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val db: QuickJokesDatabase,
    private val seeder: DataSeeder,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
) {
    val themeMode: Flow<ThemeMode> = dataStore.data
        .map { prefs ->
            val raw = prefs[THEME_KEY]
            ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_KEY] = mode.name }
    }

    // --- Напоминания ---

    val reminderSettings: Flow<ReminderSettings> = dataStore.data
        .map { prefs ->
            ReminderSettings(
                enabled = prefs[REMINDER_ENABLED_KEY] ?: false,
                hour = prefs[REMINDER_HOUR_KEY] ?: ReminderSettings.DEFAULT_HOUR,
                minute = prefs[REMINDER_MINUTE_KEY] ?: ReminderSettings.DEFAULT_MINUTE,
            )
        }

    /** Включает/выключает напоминание и синхронизирует запланированную задачу. */
    suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[REMINDER_ENABLED_KEY] = enabled }
        syncReminderWork()
    }

    /** Меняет время напоминания (перепланирует, если оно включено). */
    suspend fun setReminderTime(hour: Int, minute: Int) {
        dataStore.edit {
            it[REMINDER_HOUR_KEY] = hour
            it[REMINDER_MINUTE_KEY] = minute
        }
        syncReminderWork()
    }

    /**
     * Сверяет запланированную работу с настройками.
     * Вызывается при старте приложения (WorkManager мог потерять задачу
     * после обновления/перезагрузки) и при каждом изменении настроек.
     */
    suspend fun syncReminderWork() {
        val settings = reminderSettings.first()
        runCatching {
            if (settings.enabled) {
                ReminderScheduler.scheduleNext(context, settings.hour, settings.minute)
            } else {
                ReminderScheduler.cancel(context)
            }
        }
    }

    /**
     * Полный сброс: стирает БД, возвращает предустановленные темы и
     * каталог достижений. Не трогает настройки (режим темы).
     */
    suspend fun resetProgress() {
        db.clearAllTables()
        seeder.clearSeedFlag()
        seeder.seed()
    }

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_mode")
        private val REMINDER_ENABLED_KEY = booleanPreferencesKey("reminder_enabled")
        private val REMINDER_HOUR_KEY = intPreferencesKey("reminder_hour")
        private val REMINDER_MINUTE_KEY = intPreferencesKey("reminder_minute")
    }
}
