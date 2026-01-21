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
import com.technikh.employeeattendancetracking.utils.rememberBiometricPrompt
import com.technikh.employeeattendancetracking.utils.launchBiometric
import com.technikh.employeeattendancetracking.utils.takePhoto
import com.technikh.employeeattendancetracking.utils.SettingsManager
import com.technikh.employeeattendancetracking.viewmodel.AttendanceViewModel
import com.technikh.employeeattendancetracking.repository.AttendanceRepository

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainAttendanceScreen(
    employeeId: String,
    viewModel: AttendanceViewModel,
    onNavigateToDashboard: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val status by viewModel.connectionStatus.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val database = AppDatabase.getDatabase(context)


    val settingsManager = remember { SettingsManager(context) }
    
    // Create repository for Supabase sync
    val repository = remember {
        AttendanceRepository(
            attendanceDao = database.attendanceDao(),
            workReasonDao = database.workReasonDao(),
            supabase = viewModel.supabaseClient
        )
    }

    val viewModelV2: AttendanceViewModelV2 = viewModel(
        factory = AttendanceViewModelV2.Factory(
            database.attendanceDao(),
            database.workReasonDao(),
            database.employeeDao()
        )
    )
    
    // Set repository on ViewModel and update when Supabase client changes
    LaunchedEffect(viewModel.supabaseClient, isOnline) {
        android.util.Log.d("MainScreen", "Setting repository - Supabase client: ${viewModel.supabaseClient != null}, isOnline: $isOnline")
        repository.supabase = viewModel.supabaseClient
        viewModelV2.setRepository(repository)
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
    var isCapturingPhoto by remember { mutableStateOf(false) }


    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
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
                        viewModelV2.punchIn(employeeId, path)
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

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (hasCameraPermission) {

                val size = if (settingsManager.showCameraPreview) 150.dp else 1.dp
                val alpha = if (settingsManager.showCameraPreview) 1f else 0f

                AndroidView(
                    factory = { ctx -> PreviewView(ctx).apply { controller = cameraController } },
                    modifier = Modifier.size(size).alpha(alpha).align(Alignment.TopCenter)
                )
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336)) // Green or Red
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = status, color = Color.White, fontWeight = FontWeight.Bold)
                }
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
                    onClick = { /* Punch Logic */ },
                    enabled = isOnline, // <--- DISABLE IF OFFLINE
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("PUNCH IN")
                }
                Button(
                    onClick = {
                        pendingAction = if (isPunchedIn) "OUT" else "IN"


                        if (settingsManager.isPasswordFeatureEnabled && savedPassword.isNotBlank()) {
                            passwordInput = ""
                            showPasswordDialog = true
                        } else {
                            startPunchProcess()
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
            }

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
                        viewModelV2.punchOut(employeeId, reason, isOffice, workReason, tempSelfiePath)
                        showPunchOutDialog = false
                        Toast.makeText(context, "Punch Out Successful!", Toast.LENGTH_SHORT).show()
                        onNavigateHome()
                    }
                )
            }
        }
    }
}