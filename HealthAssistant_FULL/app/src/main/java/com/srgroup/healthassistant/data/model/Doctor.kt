package com.srgroup.healthassistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "doctor")
data class Doctor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val specialty: String = "",
    val pinHash: String? = null,   // null = no PIN set yet (blocks dashboard access — see DoctorViewModel)
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)
