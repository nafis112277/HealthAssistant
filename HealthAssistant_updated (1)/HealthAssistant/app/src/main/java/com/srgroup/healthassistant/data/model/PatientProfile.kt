package com.srgroup.healthassistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Onboarding data captured once per patient.
 * chronicConditions / currentMedications / allergies are stored as
 * comma-separated text for step 1 simplicity; move to child tables
 * (one row per condition/medication) once Doctor Dashboard (step 2)
 * needs structured querying.
 */
@Entity(tableName = "patient_profile")
data class PatientProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val age: Int,
    val chronicConditions: String,   // e.g. "ডায়াবেটিস, উচ্চ রক্তচাপ"
    val currentMedications: String,  // e.g. "মেটফরমিন ৫০০মিগ্রা - দিনে ২ বার"
    val allergies: String,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)
