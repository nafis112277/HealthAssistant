package com.srgroup.healthassistant.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Handles the "নেওয়া হয়েছে" (Taken) notification action. Kept as a
 * plain BroadcastReceiver (not a Worker directly) because notification
 * action taps need an immediate response - here that's just dismissing
 * the notification; the actual DB write is handed off to WorkManager so
 * this receiver's onReceive() (which has a hard ~10s limit and no
 * guaranteed coroutine scope) doesn't do the suspend DB call itself.
 */
class MedicationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_TAKEN) return

        val scheduleId = intent.getLongExtra(MedicationAlarmReceiver.EXTRA_SCHEDULE_ID, -1L)
        val patientId = intent.getLongExtra(MedicationAlarmReceiver.EXTRA_PATIENT_ID, -1L)
        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, System.currentTimeMillis())

        // Dismiss right away so the tap feels instant, regardless of how
        // long the background DB write takes.
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(scheduleId.toInt())

        if (scheduleId < 0 || patientId < 0) return // malformed intent - nothing to log

        val request = OneTimeWorkRequestBuilder<LogMedicationTakenWorker>()
            .setInputData(
                workDataOf(
                    LogMedicationTakenWorker.KEY_SCHEDULE_ID to scheduleId,
                    LogMedicationTakenWorker.KEY_PATIENT_ID to patientId,
                    LogMedicationTakenWorker.KEY_SCHEDULED_AT to scheduledAt
                )
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    companion object {
        const val ACTION_MARK_TAKEN = "com.srgroup.healthassistant.ACTION_MARK_TAKEN"
        const val EXTRA_SCHEDULED_AT = "scheduled_at"
    }
}
