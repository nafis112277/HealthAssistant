package com.srgroup.healthassistant.ui.logging

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srgroup.healthassistant.data.model.VitalLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NewVitalLogInput(
    val bloodSugar: String = "",
    val systolic: String = "",
    val diastolic: String = "",
    val weight: String = "",
    val note: String = ""
)

@Composable
fun VitalLogScreen(
    logs: List<VitalLog>,
    onSave: (NewVitalLogInput) -> Unit
) {
    var input by remember { mutableStateOf(NewVitalLogInput()) }
    val hasAnyValue = listOf(input.bloodSugar, input.systolic, input.diastolic, input.weight, input.note)
        .any { it.isNotBlank() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("দৈনিক লগ", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = input.bloodSugar,
            onValueChange = { input = input.copy(bloodSugar = it.filter { c -> c.isDigit() }) },
            label = { Text("ব্লাড সুগার (mg/dL)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input.systolic,
                onValueChange = { input = input.copy(systolic = it.filter { c -> c.isDigit() }) },
                label = { Text("সিস্টোলিক BP") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = input.diastolic,
                onValueChange = { input = input.copy(diastolic = it.filter { c -> c.isDigit() }) },
                label = { Text("ডায়াস্টোলিক BP") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input.weight,
            onValueChange = { input = input.copy(weight = it) },
            label = { Text("ওজন (kg)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input.note,
            onValueChange = { input = input.copy(note = it) },
            label = { Text("লক্ষণ / নোট (ঐচ্ছিক)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onSave(input)
                input = NewVitalLogInput()
            },
            enabled = hasAnyValue,
            modifier = Modifier.fillMaxWidth()
        ) { Text("সংরক্ষণ করুন") }

        Spacer(Modifier.height(16.dp))
        Text("ইতিহাস", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(logs) { log -> VitalLogRow(log) }
        }
    }
}

@Composable
private fun VitalLogRow(log: VitalLog) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(dateFormat.format(Date(log.loggedAtEpochMillis)), style = MaterialTheme.typography.bodySmall)
            log.bloodSugarMgDl?.let { Text("ব্লাড সুগার: $it mg/dL") }
            if (log.systolicBp != null && log.diastolicBp != null) {
                Text("BP: ${log.systolicBp}/${log.diastolicBp}")
            }
            log.weightKg?.let { Text("ওজন: $it kg") }
            log.symptomNote?.let { Text("নোট: $it") }
        }
    }
}
