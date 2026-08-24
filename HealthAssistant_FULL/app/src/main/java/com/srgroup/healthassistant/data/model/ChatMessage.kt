package com.srgroup.healthassistant.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Persistent chat message log tied to a patient.
 * Stores each message (user or AI) with timestamp for history retrieval.
 */
@Entity(
    tableName = "chat_message",
    foreignKeys = [
        ForeignKey(
            entity = PatientProfile::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val text: String,
    val isUser: Boolean,  // true = patient message, false = AI reply
    val urgency: String? = null,  // "Low", "Medium", "High" (only for user messages)
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)
