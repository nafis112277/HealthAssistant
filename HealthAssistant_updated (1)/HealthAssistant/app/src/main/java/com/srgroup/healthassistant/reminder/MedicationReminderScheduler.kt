package com.srgroup.healthassistant.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.srgroup.healthassistant.data.model.MedicationSchedule
import java.util.Calendar

/**
 * Uses AlarmManager (not WorkManager) for the actual reminder trigger.
 * WorkManager is built for deferrable background work and does NOT
 * guarantee exact wall-clock firing - a 8:00 AM medication reminder
 * that might fire at 8:40 defeats the point. AlarmManager.setExactAndAllowWhileIdle
 * is the correct tool for "notify at this exact time even in Doze".
 * (work-runtime-ktx dependency stays in build.gradle for step 2/3 needs -
 * e.g. periodic sync/summary jobs - just not used for this feature.)
 */
object MedicationReminderScheduler {

    fun schedule(context: Context, schedule: MedicationSchedule) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, schedule)

        val triggerAt = nextTriggerTimeMillis(schedule.hourOfDay, schedule.minuteOfHour)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // User denied "Alarms & reminders" permission - fall back to
            // inexact so reminder still fires, just not to-the-minute.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancel(context: Context, schedule: MedicationSchedule) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context, schedule))
    }

    private fun buildPendingIntent(context: Context, schedule: MedicationSchedule): PendingIntent {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(MedicationAlarmReceiver.EXTRA_SCHEDULE_ID, schedule.id)
            putExtra(MedicationAlarmReceiver.EXTRA_PATIENT_ID, schedule.patientId)
            putExtra(MedicationAlarmReceiver.EXTRA_MED_NAME, schedule.medicationName)
            putExtra(MedicationAlarmReceiver.EXTRA_DOSAGE_NOTE, schedule.dosageNote)
            putExtra(MedicationAlarmReceiver.EXTRA_HOUR, schedule.hourOfDay)
            putExtra(MedicationAlarmReceiver.EXTRA_MINUTE, schedule.minuteOfHour)
        }
        // requestCode = schedule.id so each medication gets its own alarm slot
        return PendingIntent.getBroadcast(
            context,
            schedule.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTriggerTimeMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (trigger.timeInMillis <= now.timeInMillis) {
            trigger.add(Calendar.DAY_OF_YEAR, 1) // already passed today - fire tomorrow
        }
        return trigger.timeInMillis
    }
}
