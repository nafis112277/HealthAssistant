package com.srgroup.healthassistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per daily/weekly check-in.
 * Nullable fields let patient log only what applies that day
 * (e.g. blood sugar in the morning, weight once a week).
 */
@Entity(tableName = "vital_log")
data class VitalLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val bloodSugarMgDl: Int? = null,
    val systolicBp: Int? = null,
    val diastolicBp: Int? = null,
    val weightKg: Float? = null,
    val symptomNote: String? = null,
    val aiUrgencyLevel: String? = null, // "Low" / "Medium" / "High" - set after AI triage, doctor can override
    val loggedAtEpochMillis: Long = System.currentTimeMillis()
)
