package com.srgroup.healthassistant.ui.medication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srgroup.healthassistant.data.model.MedicationSchedule

data class NewMedicationInput(
    val name: String = "",
    val dosageNote: String = "",
    val hour: Int = 8,
    val minute: Int = 0
)

@Composable
fun MedicationScreen(
    schedules: List<MedicationSchedule>,
    onAdd: (NewMedicationInput) -> Unit,
    onDelete: (MedicationSchedule) -> Unit
) {
    var input by remember { mutableStateOf(NewMedicationInput()) }
    var showAddForm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ওষুধের রিমাইন্ডার", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(schedules) { schedule ->
                MedicationRow(schedule, onDelete = { onDelete(schedule) })
            }
        }

        if (showAddForm) {
            AddMedicationForm(
                input = input,
                onChange = { input = it },
                onSave = {
                    onAdd(input)
                    input = NewMedicationInput()
                    showAddForm = false
                },
                onCancel = { showAddForm = false }
            )
        } else {
            Button(onClick = { showAddForm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("+ নতুন ওষুধ যোগ করুন")
            }
        }
    }
}

@Composable
private fun MedicationRow(schedule: MedicationSchedule, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(schedule.medicationName, style = MaterialTheme.typography.titleMedium)
                Text(schedule.dosageNote, style = MaterialTheme.typography.bodySmall)
                Text(
                    "প্রতিদিন %02d:%02d".format(schedule.hourOfDay, schedule.minuteOfHour),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onDelete) { Text("মুছুন") }
        }
    }
}

@Composable
private fun AddMedicationForm(
    input: NewMedicationInput,
    onChange: (NewMedicationInput) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input.name,
            onValueChange = { onChange(input.copy(name = it)) },
            label = { Text("ওষুধের নাম") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = input.dosageNote,
            onValueChange = { onChange(input.copy(dosageNote = it)) },
            label = { Text("ডোজ (যেমন: ৫০০মিগ্রা, ১টি ট্যাবলেট)") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input.hour.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let { if (it in 0..23) onChange(input.copy(hour = it)) } },
                label = { Text("ঘণ্টা (0-23)") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = input.minute.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let { if (it in 0..59) onChange(input.copy(minute = it)) } },
                label = { Text("মিনিট") },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("বাতিল") }
            Button(
                onClick = onSave,
                enabled = input.name.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text("সংরক্ষণ") }
        }
    }
}
