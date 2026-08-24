package com.srgroup.healthassistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per confirmed dose. Written only when the patient taps
 * "নেওয়া হয়েছে" on the reminder notification - a fired reminder with
 * no tap means no row, which is exactly what lets adherence be computed
 * as takenCount / expectedCount over a period.
 */
@Entity(tableName = "medication_taken_log")
data class MedicationTakenLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val medicationScheduleId: Long,
    val scheduledEpochMillis: Long,  // the reminder time this confirms
    val takenAtEpochMillis: Long = System.currentTimeMillis()
)
