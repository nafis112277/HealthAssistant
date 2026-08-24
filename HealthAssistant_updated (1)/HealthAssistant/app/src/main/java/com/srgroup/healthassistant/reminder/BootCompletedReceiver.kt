package com.srgroup.healthassistant.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * AlarmManager alarms are wiped on reboot, so every active medication
 * schedule needs to be re-armed. Reading Room + rescheduling N alarms
 * shouldn't block the boot broadcast (10s execution limit), so hand off
 * to a WorkManager one-shot job instead of doing it inline here - this
 * is exactly the kind of deferrable background work WorkManager is for,
 * as opposed to the exact-time firing itself (that's AlarmManager's job).
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val request = OneTimeWorkRequestBuilder<RescheduleAlarmsWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
