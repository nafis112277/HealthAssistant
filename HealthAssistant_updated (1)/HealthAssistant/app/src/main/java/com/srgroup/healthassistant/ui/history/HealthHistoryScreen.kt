package com.srgroup.healthassistant.ui.history

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srgroup.healthassistant.data.model.HealthRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HealthHistoryScreen(
    records: List<HealthRecord>,
    onAddRecord: (uri: Uri, title: String, recordType: String) -> Unit
) {
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var recordType by remember { mutableStateOf("prescription") }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> pendingUri = uri }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("হেলথ রেকর্ড", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(records) { record -> RecordRow(record) }
        }

        Spacer(Modifier.height(12.dp))

        if (pendingUri != null) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("শিরোনাম (যেমন: প্রেসক্রিপশন - ডা. রহিম)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = recordType == "prescription",
                    onClick = { recordType = "prescription" },
                    label = { Text("প্রেসক্রিপশন") }
                )
                FilterChip(
                    selected = recordType == "lab_report",
                    onClick = { recordType = "lab_report" },
                    label = { Text("রিপোর্ট") }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pendingUri = null; title = "" }, modifier = Modifier.weight(1f)) {
                    Text("বাতিল")
                }
                Button(
                    onClick = {
                        onAddRecord(pendingUri!!, title.ifBlank { "রেকর্ড" }, recordType)
                        pendingUri = null
                        title = ""
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("সংরক্ষণ") }
            }
        } else {
            Button(
                onClick = { pickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ প্রেসক্রিপশন/রিপোর্ট যোগ করুন") }
        }
    }
}

@Composable
private fun RecordRow(record: HealthRecord) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(record.title, style = MaterialTheme.typography.titleMedium)
            Text(
                if (record.recordType == "prescription") "প্রেসক্রিপশন" else "রিপোর্ট",
                style = MaterialTheme.typography.bodySmall
            )
            Text(dateFormat.format(Date(record.addedAtEpochMillis)), style = MaterialTheme.typography.bodySmall)
        }
    }
}
