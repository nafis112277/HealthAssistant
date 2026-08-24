package com.srgroup.healthassistant.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.srgroup.healthassistant.data.db.AppDatabase
import com.srgroup.healthassistant.data.model.AdminAuth
import com.srgroup.healthassistant.data.model.ClinicSubscription
import com.srgroup.healthassistant.data.model.Doctor
import com.srgroup.healthassistant.data.model.DoctorPatientAssignment
import com.srgroup.healthassistant.data.model.MedicationSchedule
import com.srgroup.healthassistant.data.model.PatientProfile
import com.srgroup.healthassistant.security.CredentialHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** One row in the assignment editor: a doctor + which patients are theirs. */
data class DoctorAssignmentRow(
    val doctor: Doctor,
    val assignedPatients: List<PatientProfile>,
    val unassignedPatients: List<PatientProfile>   // candidates that could be added
)

/** Snapshot of clinic-wide numbers for the Analytics tab. */
data class ClinicAnalytics(
    val totalPatients: Int,
    val totalDoctors: Int,
    val highRiskAlertsOpen: Int,
    val medicationAdherenceRate7d: Float, // 0f..1f — confirmed doses / expected doses, see buildAnalytics()
    val followUpCompletionRate: Float,   // 0f..1f — reviewed / total drafts
    val totalFollowUpDrafts: Int
)

enum class AdminAuthState { CHECKING, NEEDS_SETUP, NEEDS_LOGIN, AUTHENTICATED }

class AdminViewModel(
    private val appContext: Context,
    private val db: AppDatabase = AppDatabase.getInstance(appContext)
) : ViewModel() {

    // ── Admin auth ────────────────────────────────────────────────────────────
    private val _authState = MutableStateFlow(AdminAuthState.CHECKING)
    val authState: StateFlow<AdminAuthState> = _authState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private fun refreshAuthState() {
        viewModelScope.launch {
            val existing = db.adminAuthDao().getOnce()
            _authState.value = if (existing == null) AdminAuthState.NEEDS_SETUP else AdminAuthState.NEEDS_LOGIN
        }
    }

    fun setupAdminPassword(password: String, confirm: String) {
        _authError.value = null
        if (password.length < 6) { _authError.value = "পাসওয়ার্ড কমপক্ষে ৬ অক্ষর হতে হবে।"; return }
        if (password != confirm) { _authError.value = "পাসওয়ার্ড মিলছে না।"; return }
        viewModelScope.launch {
            db.adminAuthDao().upsert(AdminAuth(passwordHash = CredentialHasher.hash(password)))
            _authState.value = AdminAuthState.AUTHENTICATED
        }
    }

    fun loginAdmin(password: String) {
        _authError.value = null
        viewModelScope.launch {
            val auth = db.adminAuthDao().getOnce()
            if (auth != null && CredentialHasher.matches(password, auth.passwordHash)) {
                _authState.value = AdminAuthState.AUTHENTICATED
            } else {
                _authError.value = "ভুল পাসওয়ার্ড।"
            }
        }
    }

    fun logoutAdmin() {
        _authState.value = AdminAuthState.NEEDS_LOGIN
    }

    // ── Doctor/patient assignment ────────────────────────────────────────────
    val allDoctors: StateFlow<List<Doctor>> = db.doctorDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPatients: StateFlow<List<PatientProfile>> = db.patientProfileDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAssignments: StateFlow<List<DoctorPatientAssignment>> = db.assignmentDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Per-doctor view combining doctors + patients + the assignment table. */
    val assignmentRows: StateFlow<List<DoctorAssignmentRow>> = combine(
        allDoctors, allPatients, allAssignments
    ) { doctors, patients, assignments ->
        val patientsByDoctor = assignments.groupBy({ it.doctorId }, { it.patientId })
        doctors.map { doc ->
            val assignedIds = patientsByDoctor[doc.id].orEmpty().toSet()
            DoctorAssignmentRow(
                doctor = doc,
                assignedPatients = patients.filter { it.id in assignedIds },
                unassignedPatients = patients.filter { it.id !in assignedIds }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun assignPatient(doctorId: Long, patientId: Long) {
        viewModelScope.launch {
            db.assignmentDao().insert(DoctorPatientAssignment(doctorId = doctorId, patientId = patientId))
        }
    }

    fun unassignPatient(doctorId: Long, patientId: Long) {
        viewModelScope.launch {
            db.assignmentDao().delete(doctorId, patientId)
        }
    }

    fun addDoctor(name: String, specialty: String, pin: String) {
        if (name.isBlank() || pin.length < 4) return
        viewModelScope.launch {
            db.doctorDao().insert(
                Doctor(name = name.trim(), specialty = specialty.trim(), pinHash = CredentialHasher.hash(pin))
            )
        }
    }

    fun resetDoctorPin(doctorId: Long, newPin: String) {
        if (newPin.length < 4) return
        viewModelScope.launch {
            val doctor = db.doctorDao().getById(doctorId) ?: return@launch
            db.doctorDao().update(doctor.copy(pinHash = CredentialHasher.hash(newPin)))
        }
    }

    // ── Subscription / billing (basic structure, no payment integration) ────
    val subscription: StateFlow<ClinicSubscription?> = db.clinicSubscriptionDao().observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Seed a default trial subscription row so the tab isn't blank on
        // first run. Doctor seeding lives in DoctorViewModel only (single
        // seed path — two ViewModels seeding independently risked a race
        // that created duplicate default doctors).
        viewModelScope.launch { seedSubscriptionIfNeeded() }
        refreshAuthState()
    }

    fun updatePlan(planName: String, monthlyFeeBdt: Int, patientCap: Int) {
        viewModelScope.launch {
            val current = subscription.value ?: ClinicSubscription()
            db.clinicSubscriptionDao().upsert(
                current.copy(
                    planName = planName,
                    monthlyFeeBdt = monthlyFeeBdt,
                    patientCap = patientCap,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun setSubscriptionStatus(status: String) {
        viewModelScope.launch {
            val current = subscription.value ?: ClinicSubscription()
            db.clinicSubscriptionDao().upsert(
                current.copy(status = status, updatedAtEpochMillis = System.currentTimeMillis())
            )
        }
    }

    private suspend fun seedSubscriptionIfNeeded() {
        // observe() emits null before any row exists (query with no match
        // returns null Flow value) — only seed on that first null emission.
        val existing = db.clinicSubscriptionDao().observe()
        var seeded = false
        existing.collect { row ->
            if (row == null && !seeded) {
                seeded = true
                db.clinicSubscriptionDao().upsert(ClinicSubscription())
            }
        }
    }

    // ── Clinic-level analytics ───────────────────────────────────────────────
    private val _analytics = MutableStateFlow<ClinicAnalytics?>(null)
    val analytics: StateFlow<ClinicAnalytics?> = _analytics.asStateFlow()

    fun refreshAnalytics() {
        viewModelScope.launch {
            _analytics.value = buildAnalytics()
        }
    }

    /**
     * "Adherence rate" = confirmed doses / expected doses over the last 7
     * days. Expected doses = (active medication schedules across all
     * patients) * 7 - an approximation: it assumes every active schedule
     * was active for the full week (a medication added mid-week makes the
     * denominator slightly too high, so adherence reads slightly low for
     * that patient that week). Confirmed doses come from
     * MedicationTakenLog, written only when a patient taps "নেওয়া হয়েছে"
     * on the reminder notification - a fired reminder with no tap simply
     * doesn't produce a row, which is what makes the ratio meaningful.
     */
    private suspend fun buildAnalytics(): ClinicAnalytics {
        // one-shot reads via first emission - fine here since this is a
        // manual "refresh" action, not a live subscription.
        val patientList = db.patientProfileDao().observeAll().first()
        val doctorList = db.doctorDao().observeAll().first()

        val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)

        val activeSchedules: List<MedicationSchedule> = patientList
            .map { db.medicationScheduleDao().getActiveForPatientOnce(it.id) }
            .flatten()
        val expectedDoses = activeSchedules.size * 7
        val takenDoses = db.medicationTakenLogDao().countTakenClinicWideSince(sevenDaysAgo)
        val adherenceRate = if (expectedDoses == 0) 0f
            else (takenDoses.toFloat() / expectedDoses).coerceAtMost(1f)

        val highRiskOpen = db.vitalLogDao().observeHighUrgencyLogs().first().size

        val totalDrafts = db.followUpDraftDao().countAllOnce()
        val reviewedDrafts = db.followUpDraftDao().getAllReviewedOnce().size
        val completionRate = if (totalDrafts == 0) 0f else reviewedDrafts.toFloat() / totalDrafts

        return ClinicAnalytics(
            totalPatients = patientList.size,
            totalDoctors = doctorList.size,
            highRiskAlertsOpen = highRiskOpen,
            medicationAdherenceRate7d = adherenceRate,
            followUpCompletionRate = completionRate,
            totalFollowUpDrafts = totalDrafts
        )
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AdminViewModel(context.applicationContext) as T
        }
    }
}
