package com.srgroup.healthassistant.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.srgroup.healthassistant.data.db.AppDatabase
import com.srgroup.healthassistant.data.model.MedicationTakenLog

class LogMedicationTakenWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val scheduleId = inputData.getLong(KEY_SCHEDULE_ID, -1L)
        val patientId = inputData.getLong(KEY_PATIENT_ID, -1L)
        val scheduledAt = inputData.getLong(KEY_SCHEDULED_AT, -1L)
        if (scheduleId < 0 || patientId < 0 || scheduledAt < 0) return Result.failure()

        val db = AppDatabase.getInstance(applicationContext)
        db.medicationTakenLogDao().insert(
            MedicationTakenLog(
                patientId = patientId,
                medicationScheduleId = scheduleId,
                scheduledEpochMillis = scheduledAt
            )
        )
        return Result.success()
    }

    companion object {
        const val KEY_SCHEDULE_ID = "schedule_id"
        const val KEY_PATIENT_ID = "patient_id"
        const val KEY_SCHEDULED_AT = "scheduled_at"
    }
}
