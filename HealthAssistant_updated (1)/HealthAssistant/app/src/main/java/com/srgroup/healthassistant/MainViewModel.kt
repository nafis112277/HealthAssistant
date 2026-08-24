package com.srgroup.healthassistant

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.srgroup.healthassistant.ai.GemmaInferenceHelper
import com.srgroup.healthassistant.ai.GemmaModelRepository
import com.srgroup.healthassistant.ai.UrgencyClassifier
import com.srgroup.healthassistant.data.db.AppDatabase
import com.srgroup.healthassistant.data.model.HealthRecord
import com.srgroup.healthassistant.data.model.MedicationSchedule
import com.srgroup.healthassistant.data.model.PatientProfile
import com.srgroup.healthassistant.data.model.VitalLog
import com.srgroup.healthassistant.data.storage.FileStorageHelper
import com.srgroup.healthassistant.reminder.MedicationReminderScheduler
import com.srgroup.healthassistant.data.model.ChatMessage
import com.srgroup.healthassistant.ui.logging.NewVitalLogInput
import com.srgroup.healthassistant.ui.medication.NewMedicationInput
import com.srgroup.healthassistant.ui.onboarding.OnboardingFormState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.work.WorkInfo
import com.srgroup.healthassistant.ai.GemmaModelDownloadWorker

/**
 * Chat's model readiness, expanded to cover the download step:
 * NOT_DOWNLOADED -> DOWNLOADING -> LOADING -> READY (or FAILED at any point).
 * progressPercent is only meaningful during DOWNLOADING.
 */
sealed class ModelState {
    object NotDownloaded : ModelState()
    data class Downloading(val progressPercent: Int) : ModelState()
    object Loading : ModelState()
    object Ready : ModelState()
    data class Failed(val message: String? = null) : ModelState()
}

class MainViewModel(
    private val appContext: Context,
    private val db: AppDatabase = AppDatabase.getInstance(appContext),
    private val gemma: GemmaInferenceHelper = GemmaInferenceHelper(appContext)
) : ViewModel() {

    // -1L = onboarding not yet done. Seeded from DB on init so re-launches
    // skip the onboarding screen if a profile already exists.
    private val _patientId = MutableStateFlow(-1L)

    // null = still checking DB; true/false drives start destination in nav.
    private val _onboardingDone = MutableStateFlow<Boolean?>(null)
    val onboardingDone: StateFlow<Boolean?> = _onboardingDone.asStateFlow()

    // Messages flow from database - persisted across app restarts
    val messages: StateFlow<List<ChatMessage>> = _patientId
        .flatMapLatest { id ->
            if (id < 0) MutableStateFlow(emptyList()) else db.chatMessageDao().observeForPatient(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    /**
     * ⚠️ REQUIRED BEFORE RELEASE — this is not a working public URL.
     *
     * There is no anonymous, publicly-hosted URL for Gemma .task files from Google.
     * Gemma model weights are gated: to obtain the file you (the developer) must:
     *   1. Log in to Hugging Face (https://huggingface.co/google/gemma-2b-it) or Kaggle
     *      (https://www.kaggle.com/models/google/gemma-2) and accept the Gemma Terms of Use.
     *   2. Download the converted .task file once (see https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference
     *      for the conversion/format docs — Google's "AI Edge Gallery" sample app follows this same flow).
     *   3. Re-upload that file to storage YOU control (Firebase Storage, Cloudflare R2, S3, your own CDN)
     *      with public read access, and put THAT url below.
     *
     * Doing it this way means end users never see a login/token prompt — they just tap
     * "Download model" in the app and it fetches silently from your CDN, same as any other
     * in-app asset download. Do NOT ask end users for their own Hugging Face token, and do
     * NOT ship pointing at a third-party mirror you don't control — those get taken down
     * without notice and will break the app for every installed user at once.
     *
     * Download size: ~2.5 GB (int4 quantized). Verify MD5/SHA256 after you host it.
     */
    private val gemmaModelUrl = "REPLACE_WITH_YOUR_OWN_HOSTED_GEMMA_TASK_URL"

    private val _isReplying = MutableStateFlow(false)
    val isReplying: StateFlow<Boolean> = _isReplying.asStateFlow()

    val medicationSchedules: StateFlow<List<MedicationSchedule>> = _patientId
        .flatMapLatest { id ->
            if (id < 0) MutableStateFlow(emptyList()) else db.medicationScheduleDao().observeActiveForPatient(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vitalLogs: StateFlow<List<VitalLog>> = _patientId
        .flatMapLatest { id ->
            if (id < 0) MutableStateFlow(emptyList()) else db.vitalLogDao().observeLogsForPatient(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val healthRecords: StateFlow<List<HealthRecord>> = _patientId
        .flatMapLatest { id ->
            if (id < 0) MutableStateFlow(emptyList()) else db.healthRecordDao().observeForPatient(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // Check DB first — if patient already exists, skip onboarding.
            val existing = db.patientProfileDao().observeLatestProfile().first()
            if (existing != null) {
                _patientId.value = existing.id
                _onboardingDone.value = true
            } else {
                _onboardingDone.value = false
            }
        }

        // Start model load in background immediately. UI reads modelState.
        viewModelScope.launch {
            if (GemmaModelRepository.isDownloaded(appContext)) {
                loadModel()
            } else {
                _modelState.value = ModelState.NotDownloaded
                // Also covers the case where a download was already running
                // (e.g. app was killed and relaunched) - resume watching it
                // instead of forcing the user to tap download again.
                observeExistingDownload()
            }
        }
    }

    private suspend fun loadModel() {
        _modelState.value = ModelState.Loading
        val result = runCatching { gemma.initialize() }
        _modelState.value = if (result.isSuccess) ModelState.Ready
            else ModelState.Failed("মডেল লোড ব্যর্থ। ডিভাইসে RAM কম থাকতে পারে।")
    }

    private var downloadObserverStarted = false

    fun startModelDownload(wifiOnly: Boolean = true) {
        // Fail fast with a clear message instead of silently attempting (and failing) a
        // download against the placeholder URL — see the kdoc on gemmaModelUrl above.
        if (gemmaModelUrl.startsWith("REPLACE_WITH_")) {
            _modelState.value = ModelState.Failed(
                "Gemma মডেলের ডাউনলোড লিঙ্ক এখনো কনফিগার করা হয়নি। MODEL_SETUP.md দেখে নিজস্ব হোস্টেড URL বসাও।"
            )
            return
        }
        GemmaModelRepository.enqueueDownload(appContext, gemmaModelUrl, wifiOnly)
        observeExistingDownload()
    }

    private fun observeExistingDownload() {
        if (downloadObserverStarted) return // avoid a second collector if called twice (init + manual tap)
        downloadObserverStarted = true
        viewModelScope.launch {
            GemmaModelRepository.observeDownload(appContext).collect { workInfo ->
                if (workInfo == null) return@collect
                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                        _modelState.value = ModelState.Downloading(0)
                    WorkInfo.State.RUNNING -> {
                        val percent = workInfo.progress.getInt(
                            GemmaModelDownloadWorker.KEY_PROGRESS_PERCENT, 0
                        )
                        _modelState.value = ModelState.Downloading(percent)
                    }
                    WorkInfo.State.SUCCEEDED -> loadModel()
                    WorkInfo.State.FAILED -> {
                        val error = workInfo.outputData.getString(
                            GemmaModelDownloadWorker.KEY_ERROR
                        )
                        _modelState.value = ModelState.Failed(error ?: "ডাউনলোড ব্যর্থ হয়েছে।")
                    }
                    WorkInfo.State.CANCELLED -> _modelState.value = ModelState.NotDownloaded
                }
            }
        }
    }

    fun saveOnboarding(form: OnboardingFormState) {
        viewModelScope.launch {
            val profile = PatientProfile(
                age = form.age.toInt(),
                chronicConditions = form.chronicConditions,
                currentMedications = form.currentMedications,
                allergies = form.allergies
            )
            _patientId.value = db.patientProfileDao().insert(profile)
            _onboardingDone.value = true
        }
    }

    fun addMedication(input: NewMedicationInput) {
        val id = _patientId.value
        if (id < 0) return
        viewModelScope.launch {
            val schedule = MedicationSchedule(
                patientId = id,
                medicationName = input.name,
                dosageNote = input.dosageNote,
                hourOfDay = input.hour,
                minuteOfHour = input.minute
            )
            val newId = db.medicationScheduleDao().insert(schedule)
            MedicationReminderScheduler.schedule(appContext, schedule.copy(id = newId))
        }
    }

    fun deleteMedication(schedule: MedicationSchedule) {
        viewModelScope.launch {
            db.medicationScheduleDao().update(schedule.copy(isActive = false))
            MedicationReminderScheduler.cancel(appContext, schedule)
        }
    }

    fun addVitalLog(input: NewVitalLogInput) {
        val id = _patientId.value
        if (id < 0) return
        viewModelScope.launch {
            db.vitalLogDao().insert(
                VitalLog(
                    patientId = id,
                    bloodSugarMgDl = input.bloodSugar.toIntOrNull(),
                    systolicBp = input.systolic.toIntOrNull(),
                    diastolicBp = input.diastolic.toIntOrNull(),
                    weightKg = input.weight.toFloatOrNull(),
                    symptomNote = input.note.ifBlank { null }
                )
            )
        }
    }

    fun addHealthRecord(uri: Uri, title: String, recordType: String) {
        val id = _patientId.value
        if (id < 0) return
        viewModelScope.launch {
            val path = runCatching { FileStorageHelper.copyToPrivateStorage(appContext, uri) }
                .getOrElse { return@launch }
            db.healthRecordDao().insert(
                HealthRecord(
                    patientId = id,
                    title = title,
                    recordType = recordType,
                    filePath = path
                )
            )
        }
    }

    fun sendMessage(text: String) {
        if (_isReplying.value) return
        val patientId = _patientId.value
        if (patientId < 0) return  // No patient profile yet
        
        _isReplying.value = true

        viewModelScope.launch {
            val urgency = UrgencyClassifier.classify(text)
            
            // Save user message to DB
            db.chatMessageDao().insert(
                ChatMessage(
                    patientId = patientId,
                    text = text,
                    isUser = true,
                    urgency = urgency
                )
            )

            val reply = when (_modelState.value) {
                is ModelState.NotDownloaded, is ModelState.Downloading ->
                    "মডেল এখনো ডাউনলোড হয়নি। জরুরি উপসর্গ হলে সরাসরি ডাক্তারের সাথে যোগাযোগ করুন।"
                ModelState.Loading -> "মডেল এখনো লোড হচ্ছে। কিছুক্ষণ পর আবার চেষ্টা করুন।"
                is ModelState.Failed ->
                    "মডেল এই ডিভাইসে চালানো যাচ্ছে না। " +
                    "গুরুত্বপূর্ণ উপসর্গ হলে সরাসরি ডাক্তারের সাথে যোগাযোগ করুন।"
                ModelState.Ready -> runCatching { gemma.generateReply(text) }
                    .getOrElse {
                        "উত্তর দিতে সমস্যা হয়েছে। জরুরি উপসর্গ হলে ডাক্তারের সাথে যোগাযোগ করুন।"
                    }
            }

            // Save AI reply to DB
            db.chatMessageDao().insert(
                ChatMessage(
                    patientId = patientId,
                    text = reply,
                    isUser = false,
                    urgency = urgency
                )
            )
            
            _isReplying.value = false

            // Also log to vital logs for doctor's reference
            db.vitalLogDao().insert(
                VitalLog(
                    patientId = patientId,
                    symptomNote = text,
                    aiUrgencyLevel = urgency
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        gemma.close()
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(context.applicationContext) as T
        }
    }
}
