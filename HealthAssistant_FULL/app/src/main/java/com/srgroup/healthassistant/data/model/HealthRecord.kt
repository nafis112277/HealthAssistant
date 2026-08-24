package com.srgroup.healthassistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single stored health record - a prescription photo/PDF or a lab
 * report. filePath points at app-private storage (see FileStorageHelper);
 * we never store these in shared/public storage since they're sensitive.
 */
@Entity(tableName = "health_record")
data class HealthRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val title: String,           // e.g. "প্রেসক্রিপশন - ডা. রহিম, ১২ আগস্ট"
    val recordType: String,      // "prescription" | "lab_report" | "other"
    val filePath: String,        // absolute path in app-private storage
    val note: String? = null,
    val addedAtEpochMillis: Long = System.currentTimeMillis()
)
