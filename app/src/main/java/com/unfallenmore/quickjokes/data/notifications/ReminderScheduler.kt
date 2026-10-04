package com.unfallenmore.quickjokes.data.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.unfallenmore.quickjokes.domain.ReminderTime
import java.util.concurrent.TimeUnit

/** Ключи данных, передаваемых в [ReminderWorker]. */
internal const val KEY_HOUR = "hour"
internal const val KEY_MINUTE = "minute"

/**
 * Планировщик ежедневного напоминания.
 *
 * Не периодический Work, а разовая задача, перепланирующая саму себя:
 * периодика 24h «плывёт» (время привязывается к фактическому запуску),
 * а разовая с пересчётом задержки до следующего [hour]:minute приходит
 * в выбранное время. WorkManager переживает перезагрузку и force-stop
 * (после запуска приложения планировщик сверяется с настройками снова).
 */
object ReminderScheduler {

    const val UNIQUE_NAME = "daily_reminder"

    /** Планирует следующее напоминание на ближайшее [hour]:minute. */
    fun scheduleNext(context: Context, hour: Int, minute: Int) {
        val delayMs = ReminderTime.nextDelayMillis(System.currentTimeMillis(), hour, minute)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putInt(KEY_HOUR, hour)
                    .putInt(KEY_MINUTE, minute)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Отменяет запланированное напоминание. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }
}
