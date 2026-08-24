package com.srgroup.healthassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.srgroup.healthassistant.ui.admin.AdminDashboardScreen
import com.srgroup.healthassistant.ui.admin.AdminViewModel
import com.srgroup.healthassistant.ui.chat.ChatScreen
import com.srgroup.healthassistant.ui.doctor.DoctorDashboardScreen
import com.srgroup.healthassistant.ui.doctor.DoctorViewModel
import com.srgroup.healthassistant.ui.history.HealthHistoryScreen
import com.srgroup.healthassistant.ui.logging.VitalLogScreen
import com.srgroup.healthassistant.ui.medication.MedicationScreen
import com.srgroup.healthassistant.ui.onboarding.OnboardingScreen
import com.srgroup.healthassistant.ui.theme.HealthAssistantTheme

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val bottomTabs = listOf(
    BottomTab("chat",       "চ্যাট",        Icons.Filled.Chat),
    BottomTab("medication", "ওষুধ",         Icons.Filled.MedicalServices),
    BottomTab("logging",    "দৈনিক লগ",     Icons.Filled.MonitorHeart),
    BottomTab("history",    "রেকর্ড",       Icons.Filled.History),
    BottomTab("doctor",     "ডাক্তার",      Icons.Filled.LocalHospital),
    BottomTab("admin",      "অ্যাডমিন",     Icons.Filled.AdminPanelSettings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthAssistantTheme {
                val vm: MainViewModel = viewModel(factory = MainViewModel.factory(applicationContext))
                val doctorVm: DoctorViewModel = viewModel(factory = DoctorViewModel.factory(applicationContext))
                val adminVm: AdminViewModel = viewModel(factory = AdminViewModel.factory(applicationContext))
                val onboardingDone by vm.onboardingDone.collectAsState()

                // Splash while checking DB for existing patient
                if (onboardingDone == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@HealthAssistantTheme
                }

                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination
                val modelState by vm.modelState.collectAsState()
                val isReplying by vm.isReplying.collectAsState()

                Scaffold(
                    bottomBar = {
                        val hideBar = currentRoute?.hierarchy?.any { it.route == "onboarding" } == true
                        if (!hideBar) {
                            NavigationBar {
                                bottomTabs.forEach { tab ->
                                    NavigationBarItem(
                                        selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true,
                                        onClick = {
                                            navController.navigate(tab.route) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                                        label = { Text(tab.label) }
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = if (onboardingDone == true) "chat" else "onboarding",
                        modifier = Modifier.padding(padding)
                    ) {
                        composable("onboarding") {
                            OnboardingScreen { form ->
                                vm.saveOnboarding(form)
                                navController.navigate("chat") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        }
                        composable("chat") {
                            val messages by vm.messages.collectAsState()
                            ChatScreen(
                                messages = messages,
                                modelState = modelState,
                                isReplying = isReplying,
                                onSend = vm::sendMessage,
                                onDownloadModel = { vm.startModelDownload() }
                            )
                        }
                        composable("medication") {
                            val schedules by vm.medicationSchedules.collectAsState()
                            MedicationScreen(
                                schedules = schedules,
                                onAdd = vm::addMedication,
                                onDelete = vm::deleteMedication
                            )
                        }
                        composable("logging") {
                            val logs by vm.vitalLogs.collectAsState()
                            VitalLogScreen(logs = logs, onSave = vm::addVitalLog)
                        }
                        composable("history") {
                            val records by vm.healthRecords.collectAsState()
                            HealthHistoryScreen(
                                records = records,
                                onAddRecord = { uri, title, type -> vm.addHealthRecord(uri, title, type) }
                            )
                        }
                        composable("doctor") {
                            DoctorDashboardScreen(
                                vm = doctorVm,
                                onBack = {
                                    navController.navigate("chat") {
                                        popUpTo(navController.graph.findStartDestination().id)
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable("admin") {
                            AdminDashboardScreen(
                                vm = adminVm,
                                onBack = {
                                    navController.navigate("chat") {
                                        popUpTo(navController.graph.findStartDestination().id)
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
