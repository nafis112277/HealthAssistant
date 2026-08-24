package com.srgroup.healthassistant.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class OnboardingFormState(
    val age: String = "",
    val chronicConditions: String = "",
    val currentMedications: String = "",
    val allergies: String = ""
)

/**
 * Step 1 onboarding form: age, disease history, current medication, allergies.
 * onSubmit hands the raw form state to the ViewModel, which converts it into
 * a PatientProfile row and writes it via PatientProfileDao.
 */
@Composable
fun OnboardingScreen(onSubmit: (OnboardingFormState) -> Unit) {
    var form by remember { mutableStateOf(OnboardingFormState()) }
    val isValid = form.age.toIntOrNull()?.let { it in 0..120 } == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("আপনার তথ্য দিন", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = form.age,
            onValueChange = { form = form.copy(age = it.filter { c -> c.isDigit() }) },
            label = { Text("বয়স") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = form.chronicConditions,
            onValueChange = { form = form.copy(chronicConditions = it) },
            label = { Text("রোগের ইতিহাস (যেমন: ডায়াবেটিস, উচ্চ রক্তচাপ)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        OutlinedTextField(
            value = form.currentMedications,
            onValueChange = { form = form.copy(currentMedications = it) },
            label = { Text("বর্তমান ওষুধ") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        OutlinedTextField(
            value = form.allergies,
            onValueChange = { form = form.copy(allergies = it) },
            label = { Text("অ্যালার্জি (থাকলে লিখুন)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onSubmit(form) },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("সংরক্ষণ করুন")
        }

        if (!isValid && form.age.isNotEmpty()) {
            Text(
                "সঠিক বয়স দিন",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
