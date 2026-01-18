package com.technikh.employeeattendancetracking

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.technikh.employeeattendancetracking.data.database.AppDatabase
import com.technikh.employeeattendancetracking.data.database.AppPreferences
import com.technikh.employeeattendancetracking.repository.AttendanceRepository
import com.technikh.employeeattendancetracking.ui.screens.attendance.MainAttendanceScreen
import com.technikh.employeeattendancetracking.ui.screens.dashboard.ReportsDashboardV2
import com.technikh.employeeattendancetracking.ui.screens.login.LoginScreen
import com.technikh.employeeattendancetracking.ui.screens.login.RegisterEmployeeScreen
import com.technikh.employeeattendancetracking.ui.screens.settings.SettingsScreen
import com.technikh.employeeattendancetracking.ui.screens.reports.GlobalReportsScreen // <--- IMPORT THIS
import com.technikh.employeeattendancetracking.viewmodel.AttendanceViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var currentScreen by remember { mutableStateOf("login") }
            var currentEmployeeId by remember { mutableStateOf("") }
            val context = LocalContext.current
            val database = AppDatabase.getDatabase(context)
            val preferences = remember { AppPreferences(context) }
            val repository = remember {
                AttendanceRepository(database.attendanceDao(), database.workReasonDao(), null)
            }
            val attendanceViewModel: AttendanceViewModel = viewModel(
                factory = AttendanceViewModel.Factory(repository, preferences)
            )
            when (currentScreen) {

                "login" -> {
                    LoginScreen(
                        onLoginSuccess = { enteredId ->
                            currentEmployeeId = enteredId
                            attendanceViewModel.loadEmployeeState(enteredId)
                            currentScreen = "attendance"
                        },
                        onNavigateToRegister = { currentScreen = "register" },
                        onNavigateToSettings = { currentScreen = "settings" },
                        // --- NEW: CONNECT GLOBAL REPORTS ---
                        onNavigateToGlobalReports = { currentScreen = "global_reports" }
                    )
                }

                "register" -> {
                    RegisterEmployeeScreen(onRegistered = { currentScreen = "login" })
                }

                "attendance" -> {
                    MainAttendanceScreen(
                        employeeId = currentEmployeeId,
                        onNavigateToDashboard = { currentScreen = "reports" },
                        viewModel = attendanceViewModel,
                        onNavigateHome = { currentScreen = "login" }
                    )
                }

                "reports" -> {
                    ReportsDashboardV2(
                        employeeId = currentEmployeeId,
                        onBack = { currentScreen = "attendance" }
                    )
                }

                "settings" -> {
                    SettingsScreen(
                        viewModel = attendanceViewModel,
                        onBack = { currentScreen = "login" })
                }

                // --- NEW SCREEN CASE ---
                "global_reports" -> {
                    GlobalReportsScreen(
                        onBack = { currentScreen = "login" }
                    )
                }
            }
        }
    }
}