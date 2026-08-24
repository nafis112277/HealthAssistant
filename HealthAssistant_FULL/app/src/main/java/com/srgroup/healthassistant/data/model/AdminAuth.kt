package com.srgroup.healthassistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single admin-password row, gates the whole Admin Panel (Step 3).
 * First launch: no row → app shows a "set password" screen instead of a
 * login screen. Same singleton-row pattern as ClinicSubscription.
 */
@Entity(tableName = "admin_auth")
data class AdminAuth(
    @PrimaryKey val id: Long = SINGLETON_ID,
    val passwordHash: String,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val SINGLETON_ID = 1L
    }
}
