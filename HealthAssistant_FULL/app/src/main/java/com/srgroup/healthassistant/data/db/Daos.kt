package com.srgroup.healthassistant.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.srgroup.healthassistant.data.model.AdminAuth
import com.srgroup.healthassistant.data.model.ChatMessage
import com.srgroup.healthassistant.data.model.ClinicSubscription
import com.srgroup.healthassistant.data.model.Doctor
import com.srgroup.healthassistant.data.model.DoctorPatientAssignment
import com.srgroup.healthassistant.data.model.FollowUpDraft
import com.srgroup.healthassistant.data.model.HealthRecord
import com.srgroup.healthassistant.data.model.MedicationSchedule
import com.srgroup.healthassistant.data.model.MedicationTakenLog
import com.srgroup.healthassistant.data.model.PatientProfile
import com.srgroup.healthassistant.data.model.VitalLog
import kotlinx.coroutines.flow.Flow

// ─── Step 1 DAOs ────────────────────────────────────────────────────────────

@Dao
interface PatientProfileDao {
    @Insert
    suspend fun insert(profile: PatientProfile): Long

    @Query("SELECT * FROM patient_profile ORDER BY id DESC LIMIT 1")
    fun observeLatestProfile(): Flow<PatientProfile?>

    @Query("SELECT * FROM patient_profile WHERE id = :id")
    suspend fun getById(id: Long): PatientProfile?

    @Query("SELECT * FROM patient_profile ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<PatientProfile>>
}

@Dao
interface VitalLogDao {
    @Insert
    suspend fun insert(log: VitalLog): Long

    @Query("SELECT * FROM vital_log WHERE patientId = :patientId ORDER BY loggedAtEpochMillis DESC")
    fun observeLogsForPatient(patientId: Long): Flow<List<VitalLog>>

    @Query("SELECT * FROM vital_log WHERE patientId = :patientId ORDER BY loggedAtEpochMillis DESC LIMIT :limit")
    suspend fun getRecentForPatient(patientId: Long, limit: Int = 20): List<VitalLog>

    // Doctor dashboard: latest urgency per patient for risk sorting
    @Query("""
        SELECT * FROM vital_log
        WHERE id IN (
            SELECT MAX(id) FROM vital_log GROUP BY patientId
        )
        ORDER BY
            CASE aiUrgencyLevel WHEN 'High' THEN 0 WHEN 'Medium' THEN 1 ELSE 2 END,
            loggedAtEpochMillis DESC
    """)
    fun observeLatestLogPerPatientSortedByRisk(): Flow<List<VitalLog>>

    @Query("SELECT * FROM vital_log WHERE aiUrgencyLevel = 'High' ORDER BY loggedAtEpochMillis DESC")
    fun observeHighUrgencyLogs(): Flow<List<VitalLog>>

    // Clinic analytics (Step 3): patients who logged at least once since a
    // cutoff timestamp. Used as a logging-adherence proxy - see note on
    // AdminViewModel.buildAnalytics() about why this isn't a true
    // "medication taken" adherence rate.
    @Query("SELECT DISTINCT patientId FROM vital_log WHERE loggedAtEpochMillis >= :sinceEpochMillis")
    suspend fun getDistinctPatientIdsLoggedSinceOnce(sinceEpochMillis: Long): List<Long>
}

@Dao
interface MedicationScheduleDao {
    @Insert
    suspend fun insert(schedule: MedicationSchedule): Long

    @Update
    suspend fun update(schedule: MedicationSchedule)

    @Query("SELECT * FROM medication_schedule WHERE patientId = :patientId AND isActive = 1 ORDER BY hourOfDay, minuteOfHour")
    fun observeActiveForPatient(patientId: Long): Flow<List<MedicationSchedule>>

    @Query("SELECT * FROM medication_schedule WHERE isActive = 1")
    suspend fun getAllActiveOnce(): List<MedicationSchedule>

    @Query("SELECT * FROM medication_schedule WHERE patientId = :patientId AND isActive = 1")
    suspend fun getActiveForPatientOnce(patientId: Long): List<MedicationSchedule>

    @Query("SELECT * FROM medication_schedule WHERE id = :id")
    suspend fun getById(id: Long): MedicationSchedule?
}

@Dao
interface MedicationTakenLogDao {
    @Insert
    suspend fun insert(log: MedicationTakenLog): Long

    // Real medication adherence (Step 3 analytics): confirmed doses vs
    // expected doses over a period. "Expected" = active schedule count *
    // days elapsed - an approximation (assumes a schedule was active the
    // whole window and doesn't account for a med added mid-window), but a
    // much closer proxy to the spec's "adherence rate" than the logging-based
    // one this replaces.
    @Query("SELECT COUNT(*) FROM medication_taken_log WHERE patientId = :patientId AND takenAtEpochMillis >= :sinceEpochMillis")
    suspend fun countTakenForPatientSince(patientId: Long, sinceEpochMillis: Long): Int

    @Query("SELECT COUNT(*) FROM medication_taken_log WHERE takenAtEpochMillis >= :sinceEpochMillis")
    suspend fun countTakenClinicWideSince(sinceEpochMillis: Long): Int
}

@Dao
interface HealthRecordDao {
    @Insert
    suspend fun insert(record: HealthRecord): Long

    @Query("SELECT * FROM health_record WHERE patientId = :patientId ORDER BY addedAtEpochMillis DESC")
    fun observeForPatient(patientId: Long): Flow<List<HealthRecord>>
}

// ─── Step 2 DAOs ────────────────────────────────────────────────────────────

@Dao
interface DoctorDao {
    @Insert
    suspend fun insert(doctor: Doctor): Long

    @Update
    suspend fun update(doctor: Doctor)

    @Query("SELECT * FROM doctor ORDER BY name ASC")
    fun observeAll(): Flow<List<Doctor>>

    @Query("SELECT * FROM doctor WHERE id = :id")
    suspend fun getById(id: Long): Doctor?
}

@Dao
interface AssignmentDao {
    @Insert
    suspend fun insert(assignment: DoctorPatientAssignment): Long

    /** All patient IDs assigned to a doctor — used to build the patient list. */
    @Query("SELECT patientId FROM doctor_patient_assignment WHERE doctorId = :doctorId")
    fun observePatientIdsForDoctor(doctorId: Long): Flow<List<Long>>

    /** All assignments — used by Admin Panel (Step 3). */
    @Query("SELECT * FROM doctor_patient_assignment")
    fun observeAll(): Flow<List<DoctorPatientAssignment>>

    @Query("DELETE FROM doctor_patient_assignment WHERE doctorId = :doctorId AND patientId = :patientId")
    suspend fun delete(doctorId: Long, patientId: Long)
}

@Dao
interface FollowUpDraftDao {
    @Insert
    suspend fun insert(draft: FollowUpDraft): Long

    @Update
    suspend fun update(draft: FollowUpDraft)

    @Query("SELECT * FROM followup_draft WHERE patientId = :patientId ORDER BY createdAtEpochMillis DESC")
    fun observeForPatient(patientId: Long): Flow<List<FollowUpDraft>>

    /** Pending drafts across all patients — doctor review queue. */
    @Query("SELECT * FROM followup_draft WHERE status = 'pending' ORDER BY createdAtEpochMillis DESC")
    fun observePendingDrafts(): Flow<List<FollowUpDraft>>

    /** Reviewed (approved/edited/rejected) drafts — used for the "follow-up completion" analytic. */
    @Query("SELECT * FROM followup_draft WHERE status != 'pending'")
    suspend fun getAllReviewedOnce(): List<FollowUpDraft>

    @Query("SELECT COUNT(*) FROM followup_draft")
    suspend fun countAllOnce(): Int
}

// ─── Step 3 DAOs ────────────────────────────────────────────────────────────

@Dao
interface ClinicSubscriptionDao {
    // Upsert-by-replace: there's always exactly one row (SINGLETON_ID),
    // so REPLACE is simpler than a separate insert/update branch.
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(subscription: ClinicSubscription)

    @Query("SELECT * FROM clinic_subscription WHERE id = ${ClinicSubscription.SINGLETON_ID}")
    fun observe(): Flow<ClinicSubscription?>
}

@Dao
interface AdminAuthDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(auth: AdminAuth)

    @Query("SELECT * FROM admin_auth WHERE id = ${AdminAuth.SINGLETON_ID}")
    suspend fun getOnce(): AdminAuth?

    @Query("SELECT * FROM admin_auth WHERE id = ${AdminAuth.SINGLETON_ID}")
    fun observe(): Flow<AdminAuth?>
}

// ─── Chat History ──────────────────────────────────────────────────────────

@Dao
interface ChatMessageDao {
    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Query("SELECT * FROM chat_message WHERE patientId = :patientId ORDER BY createdAtEpochMillis ASC")
    fun observeForPatient(patientId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_message WHERE patientId = :patientId ORDER BY createdAtEpochMillis ASC LIMIT :limit")
    suspend fun getRecentForPatient(patientId: Long, limit: Int = 100): List<ChatMessage>

    @Query("DELETE FROM chat_message WHERE patientId = :patientId")
    suspend fun clearForPatient(patientId: Long)
}
