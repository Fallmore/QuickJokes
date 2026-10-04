package com.unfallenmore.quickjokes.data.notifications

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Показывает напоминание и перепланирует следующее (на завтрашнее время).
 *
 * Ничего не инжектит: контекст приложения доступен напрямую, зависимости
 * Hilt для одного уведомления не нужны.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val hour = inputData.getInt(KEY_HOUR, DEFAULT_HOUR)
        val minute = inputData.getInt(KEY_MINUTE, DEFAULT_MINUTE)

        runCatching { ReminderNotifier.notify(applicationContext) }

        // Следующее напоминание планируем даже при сбое показа — важнее не потерять серию
        val rescheduled = runCatching {
            ReminderScheduler.scheduleNext(applicationContext, hour, minute)
        }.isSuccess

        return if (rescheduled) Result.success() else Result.retry()
    }

    private companion object {
        const val DEFAULT_HOUR = 19
        const val DEFAULT_MINUTE = 0
    }
}
