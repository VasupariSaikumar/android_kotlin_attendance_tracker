package com.technikh.employeeattendancetracking.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.technikh.employeeattendancetracking.data.database.AppPreferences
import com.technikh.employeeattendancetracking.utils.SettingsManager
import com.technikh.employeeattendancetracking.viewmodel.AttendanceViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: AttendanceViewModel,
    onBack: () -> Unit) {
    var ip by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }

    val settingsManager = remember { SettingsManager(context) }

    var isAuthenticated by remember { mutableStateOf(false) }
    var inputPass by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    val supabaseConfig by prefs.supabaseConfig.collectAsState(initial = Pair("", ""))

    var ipInput by remember { mutableStateOf("") }
    var keyInput by remember { mutableStateOf("") }

    LaunchedEffect(supabaseConfig) {
        if (ipInput.isBlank()) ipInput = supabaseConfig.first
        if (keyInput.isBlank()) keyInput = supabaseConfig.second
    }
    if (!isAuthenticated) {

        Column(modifier = Modifier.padding(16.dp)) {
            Text("Admin Access Required", style = MaterialTheme.typography.headlineMedium)
            Text("Default Password: admin123", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = inputPass,
                onValueChange = { inputPass = it },
                label = { Text("Enter Admin Password") },
                visualTransformation = PasswordVisualTransformation(),
                isError = errorMsg.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
            if (errorMsg.isNotEmpty()) {
                Text(errorMsg, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (inputPass == settingsManager.adminPassword) {
                    isAuthenticated = true
                } else {
                    errorMsg = "Wrong Password"
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Login")
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    } else {

        var isPassEnabled by remember { mutableStateOf(settingsManager.isPasswordFeatureEnabled) }
        var isPreviewEnabled by remember { mutableStateOf(settingsManager.showCameraPreview) }
        var maxEmp by remember { mutableStateOf(settingsManager.maxHomeEmployees.toString()) }
        var newAdminPass by remember { mutableStateOf("") }

        Column(modifier = Modifier.padding(16.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))

            // 1. Toggle Password Feature
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Require User Password for Punch")
                Switch(checked = isPassEnabled, onCheckedChange = {
                    isPassEnabled = it
                    settingsManager.isPasswordFeatureEnabled = it
                })
            }
            Divider(Modifier.padding(vertical = 8.dp))


            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Show Selfie Camera Preview")
                Switch(checked = isPreviewEnabled, onCheckedChange = {
                    isPreviewEnabled = it
                    settingsManager.showCameraPreview = it
                })
            }
            Divider(Modifier.padding(vertical = 8.dp))


            OutlinedTextField(
                value = maxEmp,
                onValueChange = {
                    maxEmp = it
                    it.toIntOrNull()?.let { num -> settingsManager.maxHomeEmployees = num }
                },
                label = { Text("Max Employees List on Home") },
                modifier = Modifier.fillMaxWidth()
            )
            Divider(Modifier.padding(vertical = 8.dp))

            Text(
                "Local Server Configuration (WiFi)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                label = { Text("Supabase URL (e.g. http://192.168.x.x:54321)") },
                placeholder = { Text("http://192.168.1.5:54321") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("Anon Key") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        prefs.saveSupabaseConfig(ipInput, keyInput)
                        Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) // Green to distinguish
            ) {
                Text("Save Server Config")
            }
            Divider(Modifier.padding(vertical = 16.dp))

            OutlinedTextField(
                value = newAdminPass,
                onValueChange = { newAdminPass = it },
                label = { Text("Set New Admin Password") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                if(newAdminPass.isNotBlank()) {
                    settingsManager.adminPassword = newAdminPass
                    newAdminPass = ""
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Update Admin Password") }

            Spacer(Modifier.height(20.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Close Settings") }
        }
    }
}