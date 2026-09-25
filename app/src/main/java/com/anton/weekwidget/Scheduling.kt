package com.anton.weekwidget

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.Duration
import java.time.ZonedDateTime

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.refresh(applicationContext)
        // Перезаводим одноразовые задания, которые только что сработали.
        when (inputData.getString(Scheduler.KEY_SOURCE)) {
            Scheduler.SOURCE_MIDNIGHT ->
                Scheduler.scheduleMidnight(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
            Scheduler.SOURCE_CALENDAR ->
                Scheduler.scheduleCalendarObserver(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
            Scheduler.SOURCE_PERIODIC -> {
                // Страховка: если цепочки почему-то пропали — восстановить.
                Scheduler.scheduleMidnight(applicationContext, ExistingWorkPolicy.KEEP)
                Scheduler.scheduleCalendarObserver(applicationContext, ExistingWorkPolicy.KEEP)
            }
        }
        return Result.success()
    }
}

object Scheduler {
    const val KEY_SOURCE = "source"
    const val SOURCE_NOW = "now"
    const val SOURCE_MIDNIGHT = "midnight"
    const val SOURCE_CALENDAR = "calendar"
    const val SOURCE_PERIODIC = "periodic"

    private const val WORK_NOW = "week-widget-now"
    private const val WORK_MIDNIGHT = "week-widget-midnight"
    private const val WORK_CALENDAR = "week-widget-calendar"
    private const val WORK_PERIODIC = "week-widget-periodic"

    private fun wm(context: Context) = WorkManager.getInstance(context)

    private fun request(source: String) =
        OneTimeWorkRequestBuilder<RefreshWorker>().setInputData(workDataOf(KEY_SOURCE to source))

    fun scheduleAll(context: Context) {
        refreshNow(context)
        scheduleMidnight(context, ExistingWorkPolicy.REPLACE)
        scheduleCalendarObserver(context, ExistingWorkPolicy.REPLACE)
        schedulePeriodic(context)
    }

    fun refreshNow(context: Context) {
        wm(context).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request(SOURCE_NOW).build())
    }

    /** Сдвиг окна: обновление сразу после полуночи. */
    fun scheduleMidnight(context: Context, policy: ExistingWorkPolicy) {
        val now = ZonedDateTime.now()
        val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusSeconds(30)
        val req = request(SOURCE_MIDNIGHT).setInitialDelay(Duration.between(now, next)).build()
        wm(context).enqueueUniqueWork(WORK_MIDNIGHT, policy, req)
    }

    /** Срабатывает, когда меняется что-либо в календаре (синхронизация, правка события). */
    fun scheduleCalendarObserver(context: Context, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
            .setTriggerContentUpdateDelay(Duration.ofSeconds(3))
            .setTriggerContentMaxDelay(Duration.ofSeconds(15))
            .build()
        val req = request(SOURCE_CALENDAR).setConstraints(constraints).build()
        wm(context).enqueueUniqueWork(WORK_CALENDAR, policy, req)
    }

    /** Подстраховка раз в час. */
    fun schedulePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(Duration.ofHours(1))
            .setInputData(workDataOf(KEY_SOURCE to SOURCE_PERIODIC))
            .build()
        wm(context).enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancelAll(context: Context) {
        listOf(WORK_NOW, WORK_MIDNIGHT, WORK_CALENDAR, WORK_PERIODIC)
            .forEach { wm(context).cancelUniqueWork(it) }
    }
}
