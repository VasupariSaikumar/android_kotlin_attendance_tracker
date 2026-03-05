package com.technikh.employeeattendancetracking.ui.screens.reports

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.technikh.employeeattendancetracking.data.database.AppDatabase
import com.technikh.employeeattendancetracking.data.database.entities.ApprovalItem
import com.technikh.employeeattendancetracking.data.database.entities.ApprovalType
import com.technikh.employeeattendancetracking.data.database.entities.AttendanceRecord
import com.technikh.employeeattendancetracking.utils.CsvUtils
import com.technikh.employeeattendancetracking.viewmodel.AttendanceViewModelV2
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalReportsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val viewModel: AttendanceViewModelV2 = viewModel(
        factory = AttendanceViewModelV2.Factory(database.attendanceDao(), database.workReasonDao(), database.employeeDao())
    )

    // Load ALL data when screen opens
    LaunchedEffect(Unit) { viewModel.loadGlobalReportData() }

    BackHandler { onBack() }

    // States
    val employees by viewModel.employees.collectAsState()
    val selectedIds by viewModel.selectedEmployeeIds.collectAsState()
    val globalDailyReports by viewModel.globalDailyReports.collectAsState()
    val globalMonthlySummary by viewModel.globalMonthlySummary.collectAsState()

    val currentDateText by viewModel.currentDateText.collectAsState()
    val currentMonthText by viewModel.currentMonthText.collectAsState()

    // Approvals state
    val pendingApprovals by viewModel.pendingApprovals.collectAsState()
    val approvalsLoading by viewModel.approvalsLoading.collectAsState()
    val approvalsError by viewModel.approvalsError.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0=Daily, 1=Monthly, 2=Approvals
    var showFilterDialog by remember { mutableStateOf(false) }

    // Map for easy ID -> Name lookup
    val empMap = remember(employees) { employees.associate { it.employeeId to it.name } }

    // Calendar
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            val newDate = Calendar.getInstance().apply { set(year, month, day) }
            viewModel.setDate(newDate.timeInMillis)
        },
        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manager Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // --- EXPORT FILTERED CSV ---
                    IconButton(onClick = {
                        if (selectedTab == 0) {
                            // Export Daily List
                            if (globalDailyReports.isNotEmpty()) {
                                CsvUtils.generateAndShareCsv(context, "Filtered_Daily_Report", globalDailyReports, empMap)
                            }
                        } else {
                            // Export Monthly Summary (Fallback to daily data for CSV detail)
                            if (globalDailyReports.isNotEmpty()) {
                                CsvUtils.generateAndShareCsv(context, "Filtered_Report", globalDailyReports, empMap)
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Share, "Export Filtered CSV")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            // --- 1. FILTERS & NAVIGATION ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filter Button
                Button(
                    onClick = { showFilterDialog = true },
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    // FIXED ICON: Use 'List' instead of 'FilterList' to avoid import errors
                    Icon(Icons.Filled.List, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Filter Staff (${selectedIds.size})")
                }
            }

            // Date Navigation
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { if (selectedTab == 0) viewModel.incrementDay(-1) else viewModel.incrementMonth(-1) }) { Icon(Icons.Filled.KeyboardArrowLeft, null) }
                Text(
                    text = if (selectedTab == 0) currentDateText else currentMonthText,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = { if (selectedTab == 0) viewModel.incrementDay(1) else viewModel.incrementMonth(1) }) { Icon(Icons.Filled.KeyboardArrowRight, null) }
                    IconButton(onClick = { datePickerDialog.show() }) { Icon(Icons.Filled.DateRange, null) }
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- 2. TABS ---
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Daily Log") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Monthly Summary") })
                Tab(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        viewModel.loadPendingApprovals()
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Approvals")
                            if (pendingApprovals.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Badge { Text(pendingApprovals.size.toString()) }
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // --- 3. CONTENT ---
            if (selectedTab == 0) {
                // DAILY VIEW
                if (globalDailyReports.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No records for selected criteria.") }
                } else {
                    LazyColumn {
                        items(globalDailyReports) { record ->
                            GlobalLogItem(record, empMap[record.employeeId] ?: record.employeeId)
                        }
                    }
                }
            } else if (selectedTab == 1) {
                // MONTHLY VIEW
                if (globalMonthlySummary.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No data for selected criteria.") }
                } else {
                    LazyColumn {
                        // Header
                        item {
                            Row(Modifier.fillMaxWidth().padding(8.dp).background(Color.LightGray)) {
                                Text("Employee", Modifier.weight(1f).padding(8.dp), fontWeight = FontWeight.Bold)
                                Text("Total Hours", Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                            }
                        }
                        // Summary Items
                        items(globalMonthlySummary) { (empId, hours) ->
                            Row(Modifier.fillMaxWidth().padding(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(empMap[empId] ?: empId, fontWeight = FontWeight.Bold)
                                    Text(empId, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Text("${"%.2f".format(hours)} hrs", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Divider()
                        }
                    }
                }
            } else {
                // APPROVALS VIEW
                when {
                    approvalsLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("Loading pending approvals...")
                            }
                        }
                    }
                    approvalsError != null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(approvalsError ?: "", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { viewModel.loadPendingApprovals() }) { Text("Retry") }
                            }
                        }
                    }
                    pendingApprovals.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No pending approvals", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(16.dp))
                                OutlinedButton(onClick = { viewModel.loadPendingApprovals() }) { Text("Refresh") }
                            }
                        }
                    }
                    else -> {
                        LazyColumn {
                            item {
                                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${pendingApprovals.size} Pending", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    TextButton(onClick = { viewModel.loadPendingApprovals() }) { Text("Refresh") }
                                }
                            }
                            items(pendingApprovals) { item ->
                                ApprovalRequestCard(
                                    item = item,
                                    onApprove = { viewModel.approveRequest(item) },
                                    onReject  = { viewModel.rejectRequest(item) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- FILTER DIALOG ---
        if (showFilterDialog) {
            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = { Text("Select Employees") },
                text = {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().clickable { viewModel.selectAllEmployees(employees.map { it.employeeId }) }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Select All", fontWeight = FontWeight.Bold)
                            }
                            Divider()
                        }
                        items(employees) { emp ->
                            val isSelected = selectedIds.contains(emp.employeeId)
                            Row(
                                Modifier.fillMaxWidth().clickable { viewModel.toggleEmployeeSelection(emp.employeeId) }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isSelected, onCheckedChange = { viewModel.toggleEmployeeSelection(emp.employeeId) })
                                Column {
                                    Text(emp.name)
                                    Text(emp.employeeId, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                },
                confirmButton = { Button(onClick = { showFilterDialog = false }) { Text("Done") } }
            )
        }
    }
}

@Composable
fun GlobalLogItem(record: AttendanceRecord, empName: String) {
    val timeFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(empName, fontWeight = FontWeight.Bold)
                Text(
                    text = "Recorded on ${dateFormat.format(Date(record.systemTimeMillis))} at ${timeFormat.format(Date(record.systemTimeMillis))}",
                    color = if (record.punchType == "IN") Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontWeight = FontWeight.Bold
                )
                if (record.isManuallyEdited) {
                    Text(
                        "Recorded at ${timeFormat.format(Date(record.systemTimeMillis))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (record.punchType == "OUT" && record.reason != null) {
                Text(
                    record.reason,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

/**
 * Card shown in the Employer Approvals tab.
 * Displays employee ID, request type badge, value, timestamp, and Approve/Reject buttons.
 */
@Composable
fun ApprovalRequestCard(
    item: ApprovalItem,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val typeLabel = if (item.type == ApprovalType.EMAIL) "Email" else "Device"
    val typeBadgeColor = if (item.type == ApprovalType.EMAIL) Color(0xFF1565C0) else Color(0xFF6A1B9A)

    // Parse ISO timestamp to readable format
    val displayTime = try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        formatter.format(parser.parse(item.requestedAt.take(19)) ?: Date())
    } catch (e: Exception) {
        item.requestedAt
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: type badge + employee ID
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = typeBadgeColor,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = typeLabel,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.employeeId,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(6.dp))

            // Value (email address or device hash)
            Text(
                text = item.value,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            // Requested at
            Text(
                text = "Requested: $displayTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Reject", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reject")
                }
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Approve", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        }
    }
}
