package com.srgroup.healthassistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per medication reminder. Kept separate from the free-text
 * currentMedications field on PatientProfile (onboarding) because a
 * reminder needs structured fields (time, active/inactive) that a
 * plain string can't hold.
 */
@Entity(tableName = "medication_schedule")
data class MedicationSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val medicationName: String,
    val dosageNote: String,       // e.g. "৫০০মিগ্রা, ১টি ট্যাবলেট"
    val hourOfDay: Int,           // 0-23, local time
    val minuteOfHour: Int,        // 0-59
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)
