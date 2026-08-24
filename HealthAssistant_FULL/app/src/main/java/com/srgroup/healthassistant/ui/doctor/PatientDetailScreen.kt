package com.srgroup.healthassistant.ui.doctor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srgroup.healthassistant.data.model.VitalLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailScreen(vm: DoctorViewModel, onBack: () -> Unit) {
    val patient by vm.selectedPatient.collectAsState()
    val logs by vm.selectedPatientLogs.collectAsState()
    val aiWorking by vm.aiWorking.collectAsState()
    val aiOutput by vm.aiOutput.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("তথ্য ও সারাংশ", "লগ ইতিহাস", "ফলো-আপ")

    val p = patient ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("রোগী বিস্তারিত") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "ফিরে যান") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Patient header chip
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("বয়স: ${p.age} বছর", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text("রোগ: ${p.chronicConditions}", style = MaterialTheme.typography.bodySmall)
                    Text("অ্যালার্জি: ${p.allergies.ifBlank { "নেই" }}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(title, style = MaterialTheme.typography.labelMedium) })
                }
            }

            when (selectedTab) {
                0 -> SummaryTab(p, aiWorking, aiOutput, onGenerate = { vm.generatePatientSummary(p) })
                1 -> LogHistoryTab(logs)
                2 -> FollowUpTab(aiWorking, onGenerate = { vm.generateFollowUpDraft(p) })
            }
        }
    }
}

// ── Tab 0: AI Summary ────────────────────────────────────────────────────────

@Composable
private fun SummaryTab(
    patient: com.srgroup.healthassistant.data.model.PatientProfile,
    aiWorking: Boolean,
    aiOutput: String?,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("বর্তমান ওষুধ", style = MaterialTheme.typography.labelLarge)
                Text(patient.currentMedications.ifBlank { "তথ্য নেই" })
            }
        }

        Button(
            onClick = onGenerate,
            enabled = !aiWorking,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (aiWorking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("সারাংশ তৈরি হচ্ছে...")
            } else {
                Text("AI সারাংশ তৈরি করুন")
            }
        }

        if (aiOutput != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("AI সারাংশ", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.height(6.dp))
                    Text(aiOutput, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠ এই সারাংশ AI-জেনারেটেড। চিকিৎসা সিদ্ধান্তের আগে মূল রেকর্ড যাচাই করুন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ── Tab 1: Log history ───────────────────────────────────────────────────────

@Composable
private fun LogHistoryTab(logs: List<VitalLog>) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    if (logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("কোনো লগ পাওয়া যায়নি।")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(logs) { log ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(dateFormat.format(Date(log.loggedAtEpochMillis)),
                        style = MaterialTheme.typography.labelSmall)
                    log.bloodSugarMgDl?.let { Text("ব্লাড সুগার: $it mg/dL") }
                    if (log.systolicBp != null && log.diastolicBp != null)
                        Text("BP: ${log.systolicBp}/${log.diastolicBp}")
                    log.weightKg?.let { Text("ওজন: $it kg") }
                    log.symptomNote?.let { Text("লক্ষণ: $it") }
                    log.aiUrgencyLevel?.let { level ->
                        val color = when (level) {
                            "High" -> Color(0xFFD32F2F)
                            "Medium" -> Color(0xFFF9A825)
                            else -> Color(0xFF388E3C)
                        }
                        Text("ঝুঁকি: $level", color = color,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Tab 2: Follow-up draft generation ───────────────────────────────────────

@Composable
private fun FollowUpTab(aiWorking: Boolean, onGenerate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "AI একটি ফলো-আপ বার্তার ড্রাফট তৈরি করবে। " +
            "ডাক্তার review ও edit করার পরেই এটি পাঠানো হবে।",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = onGenerate,
            enabled = !aiWorking,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (aiWorking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("ড্রাফট তৈরি হচ্ছে...")
            } else {
                Text("ফলো-আপ ড্রাফট তৈরি করুন")
            }
        }
        Text(
            "তৈরি হলে ড্রাফটটি 'ফলো-আপ ড্রাফট' ট্যাবে দেখা যাবে।",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
