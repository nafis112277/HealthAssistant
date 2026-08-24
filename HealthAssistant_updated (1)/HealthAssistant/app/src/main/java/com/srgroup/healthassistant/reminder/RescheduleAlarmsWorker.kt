package com.srgroup.healthassistant.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.srgroup.healthassistant.data.db.AppDatabase

class RescheduleAlarmsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val activeSchedules = db.medicationScheduleDao().getAllActiveOnce()
        activeSchedules.forEach { schedule ->
            MedicationReminderScheduler.schedule(applicationContext, schedule)
        }
        return Result.success()
    }
}
