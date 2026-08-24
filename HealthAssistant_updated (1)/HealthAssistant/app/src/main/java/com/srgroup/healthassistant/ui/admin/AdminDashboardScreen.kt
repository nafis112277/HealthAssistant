package com.srgroup.healthassistant.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srgroup.healthassistant.data.model.PatientProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    vm: AdminViewModel,
    onBack: () -> Unit
) {
    val authState by vm.authState.collectAsState()

    when (authState) {
        AdminAuthState.CHECKING -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        AdminAuthState.NEEDS_SETUP -> AdminSetupScreen(vm = vm, onBack = onBack)
        AdminAuthState.NEEDS_LOGIN -> AdminLoginScreen(vm = vm, onBack = onBack)
        AdminAuthState.AUTHENTICATED -> AdminHomeScreen(vm = vm, onBack = { vm.logoutAdmin(); onBack() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminSetupScreen(vm: AdminViewModel, onBack: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val error by vm.authError.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("অ্যাডমিন পাসওয়ার্ড সেট করুন") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "ফিরে যান") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "এই ক্লিনিকের জন্য প্রথমবার — অ্যাডমিন প্যানেল সুরক্ষিত রাখতে একটি পাসওয়ার্ড সেট করুন।",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("নতুন পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirm, onValueChange = { confirm = it },
                label = { Text("পাসওয়ার্ড আবার লিখুন") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { vm.setupAdminPassword(password, confirm) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("সেট করুন") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminLoginScreen(vm: AdminViewModel, onBack: () -> Unit) {
    var password by remember { mutableStateOf("") }
    val error by vm.authError.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("অ্যাডমিন লগইন") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "ফিরে যান") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("পাসওয়ার্ড") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { vm.loginAdmin(password) }, modifier = Modifier.fillMaxWidth()) {
                Text("প্রবেশ করুন")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminHomeScreen(vm: AdminViewModel, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("অ্যাসাইনমেন্ট", "সাবস্ক্রিপশন", "অ্যানালিটিক্স")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ক্লিনিক অ্যাডমিন প্যানেল") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "ফিরে যান") } }
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
                0 -> AssignmentTab(vm)
                1 -> SubscriptionTab(vm)
                2 -> AnalyticsTab(vm)
            }
        }
    }
}

// ── Tab 1: Doctor ↔ patient assignment ───────────────────────────────────────

@Composable
private fun AssignmentTab(vm: AdminViewModel) {
    val rows by vm.assignmentRows.collectAsState()
    var addDoctorDialogOpen by remember { mutableStateOf(false) }
    var pickerForDoctorId by remember { mutableStateOf<Long?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো ডাক্তার নেই। নিচের + বাটনে যোগ করুন।")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rows, key = { it.doctor.id }) { row ->
                    DoctorAssignmentCard(
                        row = row,
                        onAddPatient = { pickerForDoctorId = row.doctor.id },
                        onRemovePatient = { patientId -> vm.unassignPatient(row.doctor.id, patientId) }
                    )
                }
                item { Spacer(Modifier.height(64.dp)) } // room for the FAB
            }
        }

        ExtendedFloatingActionButton(
            onClick = { addDoctorDialogOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Text("+ ডাক্তার যোগ করুন") }
    }

    if (addDoctorDialogOpen) {
        AddDoctorDialog(
            onDismiss = { addDoctorDialogOpen = false },
            onConfirm = { name, specialty, pin ->
                vm.addDoctor(name, specialty, pin)
                addDoctorDialogOpen = false
            }
        )
    }

    pickerForDoctorId?.let { doctorId ->
        val row = rows.firstOrNull { it.doctor.id == doctorId }
        if (row != null) {
            PatientPickerDialog(
                candidates = row.unassignedPatients,
                onDismiss = { pickerForDoctorId = null },
                onPick = { patientId ->
                    vm.assignPatient(doctorId, patientId)
                    pickerForDoctorId = null
                }
            )
        }
    }
}

@Composable
private fun DoctorAssignmentCard(
    row: DoctorAssignmentRow,
    onAddPatient: () -> Unit,
    onRemovePatient: (Long) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(row.doctor.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (row.doctor.specialty.isNotBlank()) {
                    Text(row.doctor.specialty, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            Divider()
            if (row.assignedPatients.isEmpty()) {
                Text("কোনো রোগী অ্যাসাইন করা নেই।", style = MaterialTheme.typography.bodySmall)
            } else {
                row.assignedPatients.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("বয়স ${p.age} — ${p.chronicConditions.take(40)}",
                            style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { onRemovePatient(p.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "সরান", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            OutlinedButton(onClick = onAddPatient, modifier = Modifier.fillMaxWidth()) {
                Text("রোগী অ্যাসাইন করুন")
            }
        }
    }
}

@Composable
private fun AddDoctorDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    val pinValid = pin.length in 4..8 && pin.all { it.isDigit() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন ডাক্তার যোগ করুন") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("নাম") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = specialty, onValueChange = { specialty = it },
                    label = { Text("বিশেষত্ব (ঐচ্ছিক)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8) pin = it.filter { c -> c.isDigit() } },
                    label = { Text("লগইন PIN (৪-৮ সংখ্যা)") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "এই PIN দিয়ে ডাক্তার নিজের ড্যাশবোর্ডে ঢুকবেন — তাকে জানিয়ে দিন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, specialty, pin) },
                enabled = name.isNotBlank() && pinValid
            ) { Text("যোগ করুন") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

@Composable
private fun PatientPickerDialog(
    candidates: List<PatientProfile>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("রোগী নির্বাচন করুন") },
        text = {
            if (candidates.isEmpty()) {
                Text("অ্যাসাইন করার মতো কোনো রোগী বাকি নেই।")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    candidates.forEach { p ->
                        Text(
                            "বয়স ${p.age} — ${p.chronicConditions.take(40)}",
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onPick(p.id) }
                                .padding(vertical = 10.dp)
                        )
                        Divider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("বন্ধ করুন") } }
    )
}

// ── Tab 2: Subscription / billing ────────────────────────────────────────────

private val planOptions = listOf(
    Triple("Trial", 0, 25),
    Triple("Basic", 1500, 100),
    Triple("Pro", 4000, 500)
)

@Composable
private fun SubscriptionTab(vm: AdminViewModel) {
    val sub by vm.subscription.collectAsState()
    val current = sub ?: return

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("বর্তমান প্ল্যান: ${current.planName}", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                StatusChip(current.status)
                Text("মাসিক ফি: ৳${current.monthlyFeeBdt}", style = MaterialTheme.typography.bodyMedium)
                Text("রোগী সীমা: ${current.patientCap} জন", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "* এটি একটি বেসিক প্ল্যান/স্ট্যাটাস স্ট্রাকচার — এখানে সরাসরি পেমেন্ট " +
                        "গেটওয়ে (bKash/Nagad/SSLCommerz ইত্যাদি) ইন্টিগ্রেট করা নেই।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Text("প্ল্যান পরিবর্তন করুন", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        planOptions.forEach { (name, fee, cap) ->
            OutlinedButton(
                onClick = { vm.updatePlan(name, fee, cap) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("$name — ৳$fee/মাস (সর্বোচ্চ $cap রোগী)")
            }
        }

        Text("স্ট্যাটাস", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("trial", "active", "past_due", "cancelled").forEach { status ->
                FilterChip(
                    selected = current.status == status,
                    onClick = { vm.setSubscriptionStatus(status) },
                    label = { Text(statusLabel(status)) }
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    AssistChip(onClick = {}, label = { Text(statusLabel(status)) })
}

private fun statusLabel(status: String) = when (status) {
    "trial" -> "ট্রায়াল"
    "active" -> "সক্রিয়"
    "past_due" -> "বকেয়া"
    "cancelled" -> "বাতিল"
    else -> status
}

// ── Tab 3: Clinic-level analytics ────────────────────────────────────────────

@Composable
private fun AnalyticsTab(vm: AdminViewModel) {
    val analytics by vm.analytics.collectAsState()

    LaunchedEffect(Unit) { vm.refreshAnalytics() }

    val a = analytics
    if (a == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("মোট রোগী", a.totalPatients.toString(), Modifier.weight(1f))
            MetricCard("মোট ডাক্তার", a.totalDoctors.toString(), Modifier.weight(1f))
        }
        MetricCard("খোলা উচ্চ-ঝুঁকি সতর্কতা", a.highRiskAlertsOpen.toString(), Modifier.fillMaxWidth())
        MetricCard(
            "ওষুধ সেবনের adherence rate (৭ দিন)",
            "${(a.medicationAdherenceRate7d * 100).toInt()}%",
            Modifier.fillMaxWidth(),
            note = "রোগী রিমাইন্ডার নোটিফিকেশনে \"নেওয়া হয়েছে\" চাপলে তা গণনায় ধরা হয় — " +
                "প্রত্যাশিত ডোজ = সক্রিয় ওষুধ সময়সূচি × ৭ দিন।"
        )
        MetricCard(
            "ফলো-আপ সম্পন্নের হার",
            "${(a.followUpCompletionRate * 100).toInt()}% (${a.totalFollowUpDrafts}টির মধ্যে)",
            Modifier.fillMaxWidth(),
            note = "ডাক্তার অনুমোদন/সম্পাদনা/বাতিল করেছেন এমন AI ড্রাফটের অনুপাত।"
        )
        OutlinedButton(onClick = vm::refreshAnalytics, modifier = Modifier.fillMaxWidth()) {
            Text("রিফ্রেশ করুন")
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, note: String? = null) {
    Card(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
