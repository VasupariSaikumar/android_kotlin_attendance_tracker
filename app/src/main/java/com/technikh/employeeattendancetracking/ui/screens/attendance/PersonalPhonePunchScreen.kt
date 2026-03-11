package com.technikh.employeeattendancetracking.ui.screens.attendance

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.technikh.employeeattendancetracking.data.database.entities.ApprovalStatus
import com.technikh.employeeattendancetracking.data.database.entities.PunchEligibility
import com.technikh.employeeattendancetracking.utils.DeviceUtils
import com.technikh.employeeattendancetracking.viewmodel.AttendanceViewModelV2
// Replace with your actual Web Client ID from google-services.json
private const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID"

/**
 * Personal phone punch screen.
 * Guards punch with 4 checks: Google signed in, intranet reachable,
 * email approved, device approved.
 */
@Composable
fun PersonalPhonePunchScreen(
    employeeId: String,
    viewModel: AttendanceViewModelV2,
    onPunchIn: (selfieUri: String?, googleEmail: String, deviceIdHash: String) -> Unit,
    onPunchOut: (selfieUri: String?, googleEmail: String, deviceIdHash: String) -> Unit
) {
    val context = LocalContext.current
    val deviceIdHash = remember { DeviceUtils.getDeviceIdHash(context) }
    val shortDevice = remember { DeviceUtils.getShortDeviceId(context) }

    val eligibility by viewModel.punchEligibility.collectAsState()
    val eligibilityLoading by viewModel.eligibilityLoading.collectAsState()
    val firebaseUser by viewModel.currentFirebaseUser.collectAsState()
    val submitResult by viewModel.approvalSubmitResult.collectAsState()

    // Google Sign-In launcher
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            viewModel.checkPersonalPhonePunchEligibility(deviceIdHash)
                        } else {
                            Toast.makeText(context, "Sign-in failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } catch (e: ApiException) {
                Toast.makeText(context, "Google Sign-In error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Load eligibility on first open / user changes
    LaunchedEffect(firebaseUser) {
        viewModel.checkPersonalPhonePunchEligibility(deviceIdHash)
    }

    // Show submit result as toast
    LaunchedEffect(submitResult) {
        submitResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearApprovalSubmitResult()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // ── Header ──────────────────────────────────────────────
        Icon(
            Icons.Default.PhoneAndroid,
            contentDescription = null,
            tint = Color(0xFF6C63FF),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Personal Phone Punch", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Your own device — verified access required", fontSize = 13.sp, color = Color(0xFFAAAAAA))

        Spacer(modifier = Modifier.height(24.dp))

        // ── Google Sign-in Card ──────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (firebaseUser == null) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Sign in to continue", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { signInLauncher.launch(googleSignInClient.signInIntent) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF4285F4))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign in with Google", color = Color(0xFF333333), fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF6C63FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (firebaseUser?.displayName?.firstOrNull() ?: "?").toString(),
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(firebaseUser?.displayName ?: "", color = Color.White, fontWeight = FontWeight.Medium)
                        Text(firebaseUser?.email ?: "", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    }
                    TextButton(onClick = { viewModel.signOutGoogle() }) {
                        Text("Sign out", color = Color(0xFFFF6B6B), fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Eligibility Checklist ────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Access Checklist", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                if (eligibilityLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF6C63FF), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking access...", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                    }
                } else {
                    EligibilityRow(
                        icon = Icons.Default.AccountCircle, label = "Google sign-in",
                        detail = eligibility.googleEmail ?: "Not signed in",
                        ok = eligibility.isSignedIn
                    )
                    EligibilityRow(
                        icon = Icons.Default.Wifi, label = "Office server reachable",
                        detail = if (eligibility.isIntranetReachable) "Connected" else "Offline",
                        ok = eligibility.isIntranetReachable
                    )
                    EligibilityRow(
                        icon = Icons.Default.Email, label = "Email approved",
                        detail = eligibility.emailApprovalStatus.label(),
                        ok = eligibility.emailApprovalStatus == ApprovalStatus.APPROVED,
                        pending = eligibility.emailApprovalStatus == ApprovalStatus.PENDING
                    )
                    EligibilityRow(
                        icon = Icons.Default.Devices, label = "Device approved",
                        detail = "...${shortDevice}",
                        ok = eligibility.deviceApprovalStatus == ApprovalStatus.APPROVED,
                        pending = eligibility.deviceApprovalStatus == ApprovalStatus.PENDING
                    )
                }
            }
        }

        // ── Block reason ─────────────────────────────────────────
        eligibility.blockReason?.let { reason ->
            if (!eligibilityLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1A1A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(reason, color = Color(0xFFFFB74D), fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Request Approval Button ───────────────────────────────
        val needsRequest = firebaseUser != null && (
            eligibility.emailApprovalStatus == ApprovalStatus.NONE ||
            eligibility.deviceApprovalStatus == ApprovalStatus.NONE
        )
        if (needsRequest) {
            OutlinedButton(
                onClick = { viewModel.submitApprovalRequests(employeeId, deviceIdHash) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6C63FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Request Approval from Employer", fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Refresh Button ────────────────────────────────────────
        TextButton(onClick = { viewModel.checkPersonalPhonePunchEligibility(deviceIdHash) }) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF6C63FF), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Refresh Status", color = Color(0xFF6C63FF), fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Punch Buttons ─────────────────────────────────────────
        val canPunch = eligibility.canPunch && !eligibilityLoading
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    onPunchIn(null, eligibility.googleEmail ?: "", deviceIdHash)
                },
                enabled = canPunch,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canPunch) Color(0xFF4CAF50) else Color(0xFF333333),
                    disabledContainerColor = Color(0xFF2A2A2A)
                )
            ) {
                Icon(Icons.Default.Login, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Punch In", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    onPunchOut(null, eligibility.googleEmail ?: "", deviceIdHash)
                },
                enabled = canPunch,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canPunch) Color(0xFFE53935) else Color(0xFF333333),
                    disabledContainerColor = Color(0xFF2A2A2A)
                )
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Punch Out", fontWeight = FontWeight.Bold)
            }
        }

        if (!canPunch && !eligibilityLoading && firebaseUser != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Punch buttons are locked until all checks pass.",
                color = Color(0xFF888888), fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Helper composables ────────────────────────────────────────────

@Composable
private fun EligibilityRow(
    icon: ImageVector,
    label: String,
    detail: String,
    ok: Boolean,
    pending: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = Color(0xFFAAAAAA), fontSize = 11.sp)
        }
        val (statusIcon, statusColor) = when {
            ok      -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
            pending -> Icons.Default.HourglassEmpty to Color(0xFFFFC107)
            else    -> Icons.Default.Cancel to Color(0xFFEF5350)
        }
        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
    }
}

private fun ApprovalStatus.label() = when (this) {
    ApprovalStatus.APPROVED -> "Approved ✓"
    ApprovalStatus.PENDING  -> "Pending employer review"
    ApprovalStatus.REJECTED -> "Rejected — contact manager"
    ApprovalStatus.NONE     -> "Not submitted yet"
    ApprovalStatus.UNKNOWN  -> "Unable to check (offline?)"
}
