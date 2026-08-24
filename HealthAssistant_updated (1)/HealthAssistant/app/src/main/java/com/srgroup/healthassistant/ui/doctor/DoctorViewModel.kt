package com.srgroup.healthassistant.ui.doctor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.srgroup.healthassistant.ai.GemmaInferenceHelper
import com.srgroup.healthassistant.data.db.AppDatabase
import com.srgroup.healthassistant.data.model.Doctor
import com.srgroup.healthassistant.data.model.DoctorPatientAssignment
import com.srgroup.healthassistant.data.model.FollowUpDraft
import com.srgroup.healthassistant.data.model.PatientProfile
import com.srgroup.healthassistant.data.model.VitalLog
import com.srgroup.healthassistant.security.CredentialHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state for a patient card in the list. */
data class PatientRiskItem(
    val profile: PatientProfile,
    val latestLog: VitalLog?,          // null if never logged
    val riskLevel: String              // "High" | "Medium" | "Low" | "Unknown"
)

class DoctorViewModel(
    private val appContext: Context,
    private val db: AppDatabase = AppDatabase.getInstance(appContext),
    private val gemma: GemmaInferenceHelper = GemmaInferenceHelper(appContext)
) : ViewModel() {

    // ── Active doctor session ────────────────────────────────────────────────
    // Doctor picks their name, then must enter their PIN — checked against
    // Doctor.pinHash. A doctor with pinHash == null can't log in at all
    // (forces the admin to set one before that account is usable).
    private val _activeDoctorId = MutableStateFlow<Long?>(null)
    val activeDoctorId: StateFlow<Long?> = _activeDoctorId.asStateFlow()

    private val _pendingDoctorId = MutableStateFlow<Long?>(null)
    val pendingDoctorId: StateFlow<Long?> = _pendingDoctorId.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    val allDoctors: StateFlow<List<Doctor>> = db.doctorDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Patient list sorted by risk ─────────────────────────────────────────
    // Combines all-patients + their latest vital log, sorts High→Med→Low
    private val _patientRiskList = MutableStateFlow<List<PatientRiskItem>>(emptyList())
    val patientRiskList: StateFlow<List<PatientRiskItem>> = _patientRiskList.asStateFlow()

    // ── Escalation alerts feed (High urgency logs) ──────────────────────────
    val escalationAlerts: StateFlow<List<VitalLog>> = db.vitalLogDao()
        .observeHighUrgencyLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Follow-up drafts (pending review) ───────────────────────────────────
    val pendingDrafts: StateFlow<List<FollowUpDraft>> = db.followUpDraftDao()
        .observePendingDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── AI summary / draft generation state ─────────────────────────────────
    private val _aiWorking = MutableStateFlow(false)
    val aiWorking: StateFlow<Boolean> = _aiWorking.asStateFlow()

    private val _aiOutput = MutableStateFlow<String?>(null)
    val aiOutput: StateFlow<String?> = _aiOutput.asStateFlow()

    // ── Selected patient detail ──────────────────────────────────────────────
    private val _selectedPatient = MutableStateFlow<PatientProfile?>(null)
    val selectedPatient: StateFlow<PatientProfile?> = _selectedPatient.asStateFlow()

    private val _selectedPatientLogs = MutableStateFlow<List<VitalLog>>(emptyList())
    val selectedPatientLogs: StateFlow<List<VitalLog>> = _selectedPatientLogs.asStateFlow()

    private val _selectedPatientDrafts = MutableStateFlow<List<FollowUpDraft>>(emptyList())
    val selectedPatientDrafts: StateFlow<List<FollowUpDraft>> = _selectedPatientDrafts.asStateFlow()

    init {
        // Model load in background — same pattern as patient app.
        viewModelScope.launch { runCatching { gemma.initialize() } }
        // Seed one default doctor (PIN "0000") ONLY if the doctor table is
        // completely empty, so the dashboard isn't a dead end on first run.
        // Change this PIN immediately from the Admin Panel before real use.
        viewModelScope.launch { seedDefaultDoctorIfNeeded() }
        // Start watching all patients + risk logs.
        viewModelScope.launch { watchPatientRiskList() }
    }

    fun selectDoctor(doctorId: Long) {
        // Just opens the PIN entry step — doesn't grant access yet.
        _pendingDoctorId.value = doctorId
        _loginError.value = null
    }

    fun cancelPendingLogin() {
        _pendingDoctorId.value = null
        _loginError.value = null
    }

    fun attemptLogin(pin: String) {
        val doctorId = _pendingDoctorId.value ?: return
        viewModelScope.launch {
            val doctor = db.doctorDao().getById(doctorId)
            val hash = doctor?.pinHash
            when {
                doctor == null -> _loginError.value = "ডাক্তার পাওয়া যায়নি।"
                hash == null -> _loginError.value =
                    "এই ডাক্তারের জন্য এখনো PIN সেট করা হয়নি। অ্যাডমিন প্যানেল থেকে সেট করুন।"
                CredentialHasher.matches(pin, hash) -> {
                    _activeDoctorId.value = doctorId
                    _pendingDoctorId.value = null
                    _loginError.value = null
                }
                else -> _loginError.value = "ভুল PIN। আবার চেষ্টা করুন।"
            }
        }
    }

    fun logout() {
        _activeDoctorId.value = null
        _pendingDoctorId.value = null
    }

    fun selectPatient(patientId: Long) {
        viewModelScope.launch {
            _selectedPatient.value = db.patientProfileDao().getById(patientId)
            db.vitalLogDao().observeLogsForPatient(patientId).collect { logs ->
                _selectedPatientLogs.value = logs
            }
        }
        viewModelScope.launch {
            db.followUpDraftDao().observeForPatient(patientId).collect { drafts ->
                _selectedPatientDrafts.value = drafts
            }
        }
    }

    fun clearSelectedPatient() {
        _selectedPatient.value = null
        _selectedPatientLogs.value = emptyList()
        _selectedPatientDrafts.value = emptyList()
        _aiOutput.value = null
    }

    /** Generate AI summary of a patient's recent history. */
    fun generatePatientSummary(patient: PatientProfile) {
        viewModelScope.launch {
            _aiWorking.value = true
            _aiOutput.value = null

            val logs = db.vitalLogDao().getRecentForPatient(patient.id, limit = 10)
            val meds = db.medicationScheduleDao().getActiveForPatientOnce(patient.id)

            val prompt = buildSummaryPrompt(patient, logs, meds.map { it.medicationName })

            val summary = runCatching { gemma.generateReply(prompt) }
                .getOrElse { "AI সারাংশ তৈরি করা যায়নি। ম্যানুয়ালি রেকর্ড পর্যালোচনা করুন।" }

            _aiOutput.value = summary
            _aiWorking.value = false
        }
    }

    /** Generate a follow-up message draft for a patient and save to DB. */
    fun generateFollowUpDraft(patient: PatientProfile) {
        viewModelScope.launch {
            _aiWorking.value = true

            val logs = db.vitalLogDao().getRecentForPatient(patient.id, limit = 5)
            val prompt = buildFollowUpPrompt(patient, logs)

            val draftText = runCatching { gemma.generateReply(prompt) }
                .getOrElse {
                    "রোগী ${patient.age} বছর বয়সী, রোগ: ${patient.chronicConditions}। " +
                    "সম্প্রতি ${logs.firstOrNull()?.symptomNote ?: "কোনো লগ নেই"}। " +
                    "ডাক্তারের সাথে যোগাযোগ করুন।"
                }

            db.followUpDraftDao().insert(
                FollowUpDraft(
                    patientId = patient.id,
                    doctorId = _activeDoctorId.value,
                    aiGeneratedText = draftText
                )
            )
            _aiWorking.value = false
        }
    }

    /** Doctor approves a draft (optionally with edits). */
    fun approveDraft(draft: FollowUpDraft, editedText: String) {
        viewModelScope.launch {
            db.followUpDraftDao().update(
                draft.copy(
                    editedText = editedText,
                    status = if (editedText == draft.aiGeneratedText) "approved" else "edited",
                    reviewedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }

    /** Doctor rejects a draft. */
    fun rejectDraft(draft: FollowUpDraft) {
        viewModelScope.launch {
            db.followUpDraftDao().update(
                draft.copy(
                    status = "rejected",
                    reviewedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun watchPatientRiskList() {
        // NOTE: this used to be a nested collect { ... collect { ... } } which
        // is a bug - the outer collect() suspends forever inside the inner
        // collect(), so it only ever reacts to the FIRST emission of the
        // outer flow (new vital logs after that point were silently ignored).
        // combine() re-evaluates whenever either source flow emits.
        combine(
            db.vitalLogDao().observeLatestLogPerPatientSortedByRisk(),
            db.patientProfileDao().observeAll()
        ) { latestLogs, patients ->
            val logMap = latestLogs.associateBy { it.patientId }
            patients.map { p ->
                val log = logMap[p.id]
                PatientRiskItem(
                    profile = p,
                    latestLog = log,
                    riskLevel = log?.aiUrgencyLevel ?: "Unknown"
                )
            }.sortedBy { riskOrder(it.riskLevel) }
        }.collect { _patientRiskList.value = it }
    }

    private fun riskOrder(level: String) = when (level) {
        "High" -> 0; "Medium" -> 1; "Low" -> 2; else -> 3
    }

    private fun buildSummaryPrompt(
        patient: PatientProfile,
        logs: List<VitalLog>,
        medications: List<String>
    ): String {
        val logLines = logs.joinToString("\n") { log ->
            buildString {
                log.bloodSugarMgDl?.let { append("ব্লাড সুগার: $it mg/dL | ") }
                if (log.systolicBp != null && log.diastolicBp != null)
                    append("BP: ${log.systolicBp}/${log.diastolicBp} | ")
                log.weightKg?.let { append("ওজন: $it kg | ") }
                log.symptomNote?.let { append("লক্ষণ: $it | ") }
                log.aiUrgencyLevel?.let { append("ঝুঁকি: $it") }
            }
        }
        return """
তুমি একজন সহকারী যে ডাক্তারকে রোগীর সারাংশ দেয়। সংক্ষিপ্ত ও পরিষ্কার বাংলায় উত্তর দাও।
রোগ নির্ণয় বা চিকিৎসা দেবে না — শুধু তথ্য উপস্থাপন করো।

রোগীর তথ্য:
বয়স: ${patient.age}
রোগের ইতিহাস: ${patient.chronicConditions}
বর্তমান ওষুধ: ${medications.joinToString(", ").ifBlank { "তথ্য নেই" }}
অ্যালার্জি: ${patient.allergies.ifBlank { "নেই" }}

সাম্প্রতিক লগ (নতুন → পুরনো):
$logLines

এই রোগীর সাম্প্রতিক স্বাস্থ্য অবস্থার একটি সংক্ষিপ্ত সারাংশ দাও (৩-৫ বাক্য)।
        """.trimIndent()
    }

    private fun buildFollowUpPrompt(patient: PatientProfile, logs: List<VitalLog>): String {
        val recent = logs.firstOrNull()
        return """
তুমি একজন সহকারী যে ডাক্তারের পক্ষে রোগীকে ফলো-আপ বার্তা লেখো।
বার্তাটি বাংলায়, সহজ ভাষায়, সংক্ষিপ্ত (৩-৪ বাক্য)।
ডাক্তার এই বার্তাটি review করবেন তারপর পাঠাবেন।
কোনো prescription বা diagnosis দেবে না।

রোগী: ${patient.age} বছর, ${patient.chronicConditions}
সর্বশেষ অবস্থা: ${recent?.symptomNote ?: "লগ নেই"}, ঝুঁকি: ${recent?.aiUrgencyLevel ?: "অজানা"}

রোগীকে ফলো-আপ বার্তা লেখো:
        """.trimIndent()
    }

    private suspend fun seedDefaultDoctorIfNeeded() {
        val existing = db.doctorDao().observeAll()
        existing.collect { list ->
            if (list.isEmpty()) {
                db.doctorDao().insert(
                    Doctor(name = "ডা. রহিম", specialty = "মেডিসিন", pinHash = CredentialHasher.hash("0000"))
                )
            }
            // Collect only once to seed — cancel after first emission.
            return@collect
        }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DoctorViewModel(context.applicationContext) as T
        }
    }
}
