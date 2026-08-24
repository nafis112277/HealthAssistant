package com.srgroup.healthassistant.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val MEDICATION_CHANNEL_ID = "medication_reminders"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MEDICATION_CHANNEL_ID,
                "ওষুধ খাওয়ার রিমাইন্ডার",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "নির্ধারিত সময়ে ওষুধ খাওয়ার কথা মনে করিয়ে দেয়"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
