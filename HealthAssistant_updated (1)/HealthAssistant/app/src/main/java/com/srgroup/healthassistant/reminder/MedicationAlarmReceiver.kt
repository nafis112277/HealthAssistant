package com.srgroup.healthassistant.reminder

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.srgroup.healthassistant.MainActivity
import com.srgroup.healthassistant.data.model.MedicationSchedule

class MedicationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        val patientId = intent.getLongExtra(EXTRA_PATIENT_ID, -1L)
        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: return
        val dosageNote = intent.getStringExtra(EXTRA_DOSAGE_NOTE) ?: ""
        val hour = intent.getIntExtra(EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)

        val firedAt = System.currentTimeMillis()
        showNotification(context, scheduleId, patientId, medName, dosageNote, firedAt)

        // Daily-repeating reminder: re-arm for the same time tomorrow.
        // (setExactAndAllowWhileIdle is one-shot by design on modern Android,
        // so "repeat daily" has to be done by rescheduling here rather than
        // via AlarmManager.setRepeating, which isn't exact anyway.)
        MedicationReminderScheduler.schedule(
            context,
            MedicationSchedule(
                id = scheduleId,
                patientId = patientId,
                medicationName = medName,
                dosageNote = dosageNote,
                hourOfDay = hour,
                minuteOfHour = minute
            )
        )
    }

    private fun showNotification(
        context: Context,
        scheduleId: Long,
        patientId: Long,
        medName: String,
        dosageNote: String,
        firedAtEpochMillis: Long
    ) {
        NotificationChannels.ensureCreated(context)

        val openAppIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = android.app.PendingIntent.getActivity(
            context, scheduleId.toInt(), openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val takenIntent = Intent(context, MedicationActionReceiver::class.java).apply {
            action = MedicationActionReceiver.ACTION_MARK_TAKEN
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_PATIENT_ID, patientId)
            putExtra(MedicationActionReceiver.EXTRA_SCHEDULED_AT, firedAtEpochMillis)
        }
        // requestCode must differ from the content intent's or FLAG_UPDATE_CURRENT
        // makes them collide and overwrite each other's extras.
        val takenPendingIntent = android.app.PendingIntent.getBroadcast(
            context, scheduleId.toInt() * 31 + 1, takenIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.MEDICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // replace with app icon asset
            .setContentTitle("ওষুধ খাওয়ার সময় হয়েছে")
            .setContentText(if (dosageNote.isNotBlank()) "$medName — $dosageNote" else medName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "নেওয়া হয়েছে", takenPendingIntent)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return // permission not granted - silently skip rather than crash
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(scheduleId.toInt(), notification) // unique per medication, so multiple meds don't overwrite each other's notification
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        const val EXTRA_PATIENT_ID = "patient_id"
        const val EXTRA_MED_NAME = "med_name"
        const val EXTRA_DOSAGE_NOTE = "dosage_note"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
    }
}
