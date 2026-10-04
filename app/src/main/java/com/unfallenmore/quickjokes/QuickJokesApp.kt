package com.unfallenmore.quickjokes

import android.app.Application
import android.util.Log
import com.unfallenmore.quickjokes.data.repository.SettingsRepository
import com.unfallenmore.quickjokes.data.seed.DataSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class QuickJokesApp : Application() {

    @Inject
    lateinit var dataSeeder: DataSeeder

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** Область приложения: живёт всё время работы процесса, не привязана к Activity. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()

        applicationScope.launch {
            // Сбой сида не должен ронять приложение: логируем и продолжаем
            runCatching { dataSeeder.seed() }
                .onFailure { Log.e(TAG, "Data seeding failed", it) }

            // Сверяем запланированное напоминание с настройками
            // (WorkManager мог потерять задачу после обновления или перезагрузки)
            runCatching { settingsRepository.syncReminderWork() }
                .onFailure { Log.e(TAG, "Reminder sync failed", it) }
        }
    }

    /**
     * Сохраняет стек последнего вылета в filesDir/crash.log.
     * Нужен для диагностики на устройстве, где нет logcat.
     * Поведение системы (показ «упало») не меняется.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                File(filesDir, CRASH_LOG).writeText(
                    buildString {
                        appendLine("time=${System.currentTimeMillis()}")
                        appendLine("thread=${thread.name}")
                        appendLine(throwable.stackTraceToString())
                    },
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "QuickJokes"
        const val CRASH_LOG = "crash.log"
    }
}
