package com.technikh.employeeattendancetracking.ui.screens.attendance

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

import com.technikh.employeeattendancetracking.data.database.AppDatabase
import com.technikh.employeeattendancetracking.viewmodel.AttendanceViewModelV2
import com.technikh.employeeattendancetracking.viewmodel.AttendanceViewModel
import com.technikh.employeeattendancetracking.utils.rememberBiometricPrompt
import com.technikh.employeeattendancetracking.utils.launchBiometric
import com.technikh.employeeattendancetracking.utils.takePhoto
import com.technikh.employeeattendancetracking.utils.SettingsManager

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainAttendanceScreen(
    employeeId: String,
    viewModel: AttendanceViewModel,
    onNavigateToDashboard: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val database = AppDatabase.getDatabase(context)

    // Connection status from the main ViewModel
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()


    val settingsManager = remember { SettingsManager(context) }

    // Create repository without Supabase client initially
    val repository = remember {
        com.technikh.employeeattendancetracking.repository.AttendanceRepository(
            database.attendanceDao(),
            database.workReasonDao(),
            null  // Will be updated by LaunchedEffect
        )
    }
    
    // Keep repository's supabase client in sync with parent viewModel
    LaunchedEffect(viewModel.supabaseClient) {
        repository.supabase = viewModel.supabaseClient
        android.util.Log.d("MainAttendance", "Updated repository.supabase = ${viewModel.supabaseClient != null}")
    }

    val viewModelV2: AttendanceViewModelV2 = viewModel(
        factory = AttendanceViewModelV2.Factory(
            database.attendanceDao(),
            database.workReasonDao(),
            database.employeeDao(),
            repository  // Pass repository for Supabase sync
        )
    )
    
    // CRITICAL: Update the ViewModel's repository field in case it was cached from another screen
    LaunchedEffect(repository) {
        viewModelV2.repository = repository
        android.util.Log.d("MainAttendance", "Set viewModelV2.repository = ${repository != null}")
    }

    BackHandler { onNavigateHome() }

    val lastRecord by viewModelV2.getLiveStatus(employeeId).collectAsState(initial = null)


    val isPunchedIn = lastRecord?.punchType == "IN"


    var employeeName by remember { mutableStateOf("Loading...") }
    var savedPassword by remember { mutableStateOf("") }

    LaunchedEffect(employeeId) {
        val emp = database.employeeDao().getEmployeeById(employeeId)
        employeeName = emp?.name ?: employeeId
        savedPassword = emp?.password ?: ""
    }


    var showPunchOutDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf("") }

    var tempSelfiePath by remember { mutableStateOf<String?>(null) }
    var tempGoogleEmail by remember { mutableStateOf<String?>(null) }
    var tempDeviceIdHash by remember { mutableStateOf<String?>(null) }
    var isCapturingPhoto by remember { mutableStateOf(false) }

    var currentMode by remember { mutableStateOf("SHARED_DEVICE") }


    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val systemTimeMillis = remember { System.currentTimeMillis() }
    var selectedTimeMillis by remember { mutableStateOf(systemTimeMillis) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )
    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            bindToLifecycle(lifecycleOwner)
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }


    val biometricPrompt = rememberBiometricPrompt(
        onSuccess = {
            isCapturingPhoto = true
            Toast.makeText(context, "Verifying... Smile! 📸", Toast.LENGTH_SHORT).show()

            takePhoto(
                controller = cameraController,
                context = context,
                onPhotoTaken = { photoFile ->
                    isCapturingPhoto = false
                    val path = photoFile.absolutePath
                    tempSelfiePath = path

                    if (pendingAction == "OUT") {
                        showPunchOutDialog = true
                    } else {
                        viewModelV2.punchIn(
                            employeeId = employeeId,
                            systemTimeMillis = System.currentTimeMillis(),
                            employeeTimeMillis = selectedTimeMillis,
                            selfiePath = path
                        )
                        Toast.makeText(context, "Punch In Successful!", Toast.LENGTH_SHORT).show()
                        onNavigateHome()
                    }
                },
                onError = {
                    isCapturingPhoto = false
                    Toast.makeText(context, "Photo failed", Toast.LENGTH_SHORT).show()
                }
            )
        },
        onError = { _, errString ->
            Toast.makeText(context, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
        }
    )


    val startPunchProcess = {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            biometricPrompt?.let { launchBiometric(it) }
        }
    }

    fun openDateTimePicker(onTimeSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        val now = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        calendar.set(year, month, dayOfMonth, hour, minute)
                        onTimeSelected(calendar.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.datePicker.maxDate = now.timeInMillis

        datePicker.show()
    }

    fun continuePunchAfterDateSelection() {
        if (settingsManager.isPasswordFeatureEnabled && savedPassword.isNotBlank()) {
            passwordInput = ""
            showPasswordDialog = true
        } else {
            startPunchProcess()
        }
    }

    Scaffold(
        topBar = {
            TabRow(
                selectedTabIndex = if (currentMode == "SHARED_DEVICE") 0 else 1,
                containerColor = Color(0xFF1E1E2E), // Match dark theme
                contentColor = Color.White
            ) {
                Tab(
                    selected = currentMode == "SHARED_DEVICE",
                    onClick = { currentMode = "SHARED_DEVICE" },
                    text = { Text("Shared Device") }
                )
                Tab(
                    selected = currentMode == "PERSONAL_PHONE",
                    onClick = { currentMode = "PERSONAL_PHONE" },
                    text = { Text("Personal Phone") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (currentMode == "PERSONAL_PHONE") {
                PersonalPhonePunchScreen(
                    employeeId = employeeId,
                    viewModel = viewModelV2,
                    onPunchIn = { selfiePath, googleEmail, deviceIdHash ->
                        val currentMillis = System.currentTimeMillis()
                        viewModelV2.punchIn(
                            employeeId = employeeId,
                            selfiePath = selfiePath,
                            systemTimeMillis = currentMillis,
                            employeeTimeMillis = currentMillis,
                            punchSource = "personal_phone",
                            googleEmail = googleEmail,
                            deviceIdHash = deviceIdHash
                        )
                        Toast.makeText(context, "Punch In Successful!", Toast.LENGTH_SHORT).show()
                        onNavigateHome()
                    },
                    onPunchOut = { selfiePath, googleEmail, deviceIdHash ->
                        tempSelfiePath = selfiePath
                        tempGoogleEmail = googleEmail
                        tempDeviceIdHash = deviceIdHash
                        pendingAction = "OUT"
                        selectedTimeMillis = System.currentTimeMillis() // Capture live time for out
                        showPunchOutDialog = true
                    }
                )
            } else {
                if (hasCameraPermission) {

                    val size = if (settingsManager.showCameraPreview) 150.dp else 1.dp
                    val alpha = if (settingsManager.showCameraPreview) 1f else 0f

                    AndroidView(
                        factory = { ctx -> PreviewView(ctx).apply { controller = cameraController } },
                        modifier = Modifier.size(size).alpha(alpha).align(Alignment.TopCenter)
                    )
                }

            // --- CONNECTION STATUS INDICATOR ---
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(
                        color = if (isOnline) Color(0xFF4CAF50) else Color(0xFFE53935),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isOnline) "● Online" else "● $connectionStatus",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = employeeName, fontSize = 28.sp, fontWeight = FontWeight.Bold)

                Text(
                    text = "ID: $employeeId",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            Toast.makeText(context, "Live Status: ${if(isPunchedIn) "IN" else "OUT"}", Toast.LENGTH_SHORT).show()
                        }
                    )
                )

                Spacer(modifier = Modifier.height(40.dp))


                Button(
                    onClick = {
                        pendingAction = if (isPunchedIn) "OUT" else "IN"

                        if (settingsManager.allowSelfCorrection) {
                            openDateTimePicker { pickedMillis ->
                                selectedTimeMillis = pickedMillis
                                continuePunchAfterDateSelection()
                            }
                        } else {
                            selectedTimeMillis = System.currentTimeMillis()
                            continuePunchAfterDateSelection()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPunchedIn) Color(0xFFC62828) else Color(0xFF2E7D32)
                    ),
                    enabled = !isCapturingPhoto,
                    modifier = Modifier.size(220.dp).padding(16.dp)
                ) {
                    if (isCapturingPhoto) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Text(
                            text = if (isPunchedIn) "PUNCH OUT" else "PUNCH IN",
                            fontSize = 24.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = { onNavigateToDashboard() },
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text("View My Reports")
                }
            } // End of Column
            } // End of SHARED_DEVICE mode else block

            FloatingActionButton(
                onClick = onNavigateHome,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }


            if (showPasswordDialog) {
                AlertDialog(
                    onDismissRequest = { showPasswordDialog = false },
                    title = { Text("Enter Password") },
                    text = {
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("User Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (passwordInput == savedPassword) {
                                showPasswordDialog = false
                                startPunchProcess() // Auth Success -> Proceed
                            } else {
                                Toast.makeText(context, "Wrong Password", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Confirm") }
                    },
                    dismissButton = {
                        Button(onClick = { showPasswordDialog = false }) { Text("Cancel") }
                    }
                )
            }

            if (showPunchOutDialog) {
                PunchOutReasonDialog(
                    onDismiss = { showPunchOutDialog = false },
                    onConfirm = { reason, isOffice, workReason ->
                        val source = if (currentMode == "PERSONAL_PHONE") "personal_phone" else "shared_device"
                        viewModelV2.punchOut(
                            employeeId = employeeId, 
                            reason = reason, 
                            isOfficeWork = isOffice, 
                            workReason = workReason, 
                            systemTimeMillis = System.currentTimeMillis(), 
                            employeeTimeMillis = selectedTimeMillis, 
                            selfiePath = tempSelfiePath,
                            punchSource = source,
                            googleEmail = tempGoogleEmail,
                            deviceIdHash = tempDeviceIdHash
                        )
                        showPunchOutDialog = false
                        Toast.makeText(context, "Punch Out Successful!", Toast.LENGTH_SHORT).show()
                        onNavigateHome()
                    }
                )
            }
        }
    }
}