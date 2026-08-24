package com.srgroup.healthassistant.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI-generated follow-up message draft for a patient.
 * status: "pending" (AI wrote, doctor not reviewed) |
 *         "approved" (doctor approved, ready to send) |
 *         "edited"   (doctor edited before approving) |
 *         "rejected" (doctor discarded)
 */
@Entity(
    tableName = "followup_draft",
    foreignKeys = [
        ForeignKey(entity = PatientProfile::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Doctor::class,         parentColumns = ["id"], childColumns = ["doctorId"],  onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("patientId"), Index("doctorId")]
)
data class FollowUpDraft(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val doctorId: Long?,                          // null before doctor is assigned
    val aiGeneratedText: String,                  // original AI output
    val editedText: String = aiGeneratedText,     // doctor may edit before approving
    val status: String = "pending",               // see kdoc above
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val reviewedAtEpochMillis: Long? = null
)
