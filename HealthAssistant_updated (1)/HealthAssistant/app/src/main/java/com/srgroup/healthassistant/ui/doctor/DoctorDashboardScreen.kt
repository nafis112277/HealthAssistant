package com.srgroup.healthassistant.ui.doctor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srgroup.healthassistant.data.model.Doctor
import com.srgroup.healthassistant.data.model.VitalLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDashboardScreen(
    vm: DoctorViewModel,
    onBack: () -> Unit
) {
    val activeDoctorId by vm.activeDoctorId.collectAsState()
    val pendingDoctorId by vm.pendingDoctorId.collectAsState()
    val allDoctors by vm.allDoctors.collectAsState()
    val selectedPatient by vm.selectedPatient.collectAsState()
    val loginError by vm.loginError.collectAsState()

    // PIN entry step — doctor picked but not verified yet.
    if (activeDoctorId == null && pendingDoctorId != null) {
        val doctor = allDoctors.firstOrNull { it.id == pendingDoctorId }
        DoctorPinScreen(
            doctorName = doctor?.name ?: "",
            error = loginError,
            onSubmit = vm::attemptLogin,
            onBack = vm::cancelPendingLogin
        )
        return
    }

    // Doctor not selected yet → show doctor picker
    if (activeDoctorId == null) {
        DoctorPickerScreen(doctors = allDoctors, onSelect = vm::selectDoctor, onBack = onBack)
        return
    }

    // Patient selected → show detail screen
    if (selectedPatient != null) {
        PatientDetailScreen(vm = vm, onBack = vm::clearSelectedPatient)
        return
    }

    // Default: patient list + alerts
    DoctorHomeScreen(vm = vm, onBack = { vm.logout(); onBack() })
}

// ── PIN entry ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoctorPinScreen(
    doctorName: String,
    error: String?,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doctorName) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "ফিরে যান") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("আপনার PIN দিন", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 8) pin = it.filter { c -> c.isDigit() } },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                ),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { onSubmit(pin) }, enabled = pin.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("প্রবেশ করুন")
            }
        }
    }
}

// ── Doctor picker ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoctorPickerScreen(
    doctors: List<Doctor>,
    onSelect: (Long) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ডাক্তার নির্বাচন") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "ফিরে যান") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (doctors.isEmpty()) {
                item { Text("কোনো ডাক্তার পাওয়া যায়নি। অ্যাডমিন প্যানেল থেকে যোগ করুন।") }
            }
            items(doctors) { doctor ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(doctor.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(doctor.name, style = MaterialTheme.typography.titleMedium)
                            if (doctor.specialty.isNotBlank())
                                Text(doctor.specialty, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// ── Doctor home: tabs for Patient List / Escalation Alerts ──────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoctorHomeScreen(vm: DoctorViewModel, onBack: () -> Unit) {
    val patientList by vm.patientRiskList.collectAsState()
    val alerts by vm.escalationAlerts.collectAsState()
    val pendingDrafts by vm.pendingDrafts.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("রোগী তালিকা", "জরুরি সতর্কতা", "ফলো-আপ ড্রাফট")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ডাক্তার ড্যাশবোর্ড") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "ফিরে যান") } },
                actions = {
                    if (alerts.isNotEmpty()) {
                        BadgedBox(badge = { Badge { Text(alerts.size.toString()) } }) {
                            Icon(Icons.Filled.Notifications, contentDescription = "সতর্কতা")
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            when (selectedTab) {
                0 -> PatientListTab(patientList, onSelect = { vm.selectPatient(it.profile.id) })
                1 -> EscalationAlertsTab(alerts)
                2 -> PendingDraftsTab(pendingDrafts, onApprove = { draft, text -> vm.approveDraft(draft, text) }, onReject = vm::rejectDraft)
            }
        }
    }
}

// ── Tab 1: Patient list sorted by risk ──────────────────────────────────────

@Composable
private fun PatientListTab(
    patients: List<PatientRiskItem>,
    onSelect: (PatientRiskItem) -> Unit
) {
    if (patients.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("কোনো রোগী নেই। রোগী অ্যাপ থেকে অনবোর্ড করুন।",
                style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(patients) { item -> PatientRiskCard(item, onClick = { onSelect(item) }) }
    }
}

@Composable
private fun PatientRiskCard(item: PatientRiskItem, onClick: () -> Unit) {
    val (borderColor, riskLabel) = when (item.riskLevel) {
        "High"    -> Color(0xFFD32F2F) to "⚠ উচ্চ ঝুঁকি"
        "Medium"  -> Color(0xFFF9A825) to "মধ্যম ঝুঁকি"
        "Low"     -> Color(0xFF388E3C) to "কম ঝুঁকি"
        else      -> Color.Gray       to "ঝুঁকি অজানা"
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("বয়স: ${item.profile.age} বছর", style = MaterialTheme.typography.titleMedium)
                Text(item.profile.chronicConditions.take(60),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1)
                item.latestLog?.symptomNote?.let {
                    Text("সর্বশেষ: $it", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(riskLabel, color = borderColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Tab 2: Escalation alerts ─────────────────────────────────────────────────

@Composable
private fun EscalationAlertsTab(alerts: List<VitalLog>) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    if (alerts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("কোনো জরুরি সতর্কতা নেই।")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(alerts) { log ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("⚠ উচ্চ ঝুঁকি — রোগী ID: ${log.patientId}",
                        fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                    log.symptomNote?.let { Text("লক্ষণ: $it") }
                    Text(dateFormat.format(Date(log.loggedAtEpochMillis)),
                        style = MaterialTheme.typography.bodySmall)
                    log.bloodSugarMgDl?.let { Text("ব্লাড সুগার: $it mg/dL") }
                    if (log.systolicBp != null)
                        Text("BP: ${log.systolicBp}/${log.diastolicBp}")
                }
            }
        }
    }
}

// ── Tab 3: Pending follow-up drafts ─────────────────────────────────────────

@Composable
private fun PendingDraftsTab(
    drafts: List<com.srgroup.healthassistant.data.model.FollowUpDraft>,
    onApprove: (com.srgroup.healthassistant.data.model.FollowUpDraft, String) -> Unit,
    onReject: (com.srgroup.healthassistant.data.model.FollowUpDraft) -> Unit
) {
    if (drafts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("কোনো পেন্ডিং ফলো-আপ ড্রাফট নেই।")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(drafts) { draft -> DraftReviewCard(draft, onApprove, onReject) }
    }
}

@Composable
private fun DraftReviewCard(
    draft: com.srgroup.healthassistant.data.model.FollowUpDraft,
    onApprove: (com.srgroup.healthassistant.data.model.FollowUpDraft, String) -> Unit,
    onReject: (com.srgroup.healthassistant.data.model.FollowUpDraft) -> Unit
) {
    var editText by remember(draft.id) { mutableStateOf(draft.aiGeneratedText) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("রোগী ID: ${draft.patientId} — AI ড্রাফট",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("বার্তা সম্পাদনা করুন") },
                minLines = 3
            )
            Text("*পাঠানোর আগে সম্পাদনা করুন ও নিশ্চিত করুন।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onReject(draft) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("বাতিল") }
                Button(
                    onClick = { onApprove(draft, editText) },
                    modifier = Modifier.weight(1f)
                ) { Text("অনুমোদন") }
            }
        }
    }
}
