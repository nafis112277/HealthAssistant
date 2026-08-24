package com.srgroup.healthassistant.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Links a doctor to a patient. Step 3 (Admin Panel) will manage these;
 * Step 2 reads them to build the doctor's patient list.
 */
@Entity(
    tableName = "doctor_patient_assignment",
    foreignKeys = [
        ForeignKey(entity = Doctor::class,         parentColumns = ["id"], childColumns = ["doctorId"],  onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PatientProfile::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("doctorId"), Index("patientId")]
)
data class DoctorPatientAssignment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val doctorId: Long,
    val patientId: Long,
    val assignedAtEpochMillis: Long = System.currentTimeMillis()
)
