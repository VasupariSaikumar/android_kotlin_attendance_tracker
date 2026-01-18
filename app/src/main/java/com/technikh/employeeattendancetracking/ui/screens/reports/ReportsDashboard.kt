package com.technikh.employeeattendancetracking.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Database Imports
import com.technikh.employeeattendancetracking.data.database.AppDatabase
import com.technikh.employeeattendancetracking.viewmodel.AttendanceViewModel
import com.technikh.employeeattendancetracking.data.database.entities.AttendanceRecord
import com.technikh.employeeattendancetracking.data.database.entities.DailyAttendance
import com.technikh.employeeattendancetracking.data.database.entities.DayOfficeHours

// Chart Imports (Vico)
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.technikh.employeeattendancetracking.data.database.AppPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsDashboard(
    employeeId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val preferences = remember { AppPreferences(context) }

    val repository = remember {
        com.technikh.employeeattendancetracking.repository.AttendanceRepository(
            database.attendanceDao(),
            database.workReasonDao(),
            null
        )
    }
    val viewModel: AttendanceViewModel = viewModel(
        factory = AttendanceViewModel.Factory(
            repository ,
            preferences
        )
    )

    LaunchedEffect(employeeId) {
        viewModel.loadEmployeeState(employeeId)
    }

    val dailyReportsState = viewModel.dailyReports.collectAsState(initial = emptyList())
    val dailyReports = dailyReportsState.value
    val monthlyReport = emptyList<DayOfficeHours>() // Logic for this can be added later

    // --- SCAFFOLD ADDS THE TOP BAR WITH BACK BUTTON ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance Reports") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Handle top bar padding
                .padding(16.dp)
        ) {
            var selectedTab by remember { mutableIntStateOf(0) }

            TabRow(selectedTabIndex = selectedTab) {
                listOf("Daily Report", "Monthly Report").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> DailyReportView(dailyReports)
                1 -> MonthlyReportView(monthlyReport)
            }
        }
    }
}

@Composable
fun DailyReportView(reports: List<DailyAttendance>) {
    LazyColumn {
        items(reports) { daily ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = daily.date, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    daily.records.forEach { record ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                            Text(
                                text = timeFormat.format(Date(record.timestamp)),
                                color = if (record.punchType == "IN") Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                            Text(record.punchType)
                            if (record.reason != null) Text(record.reason, fontStyle = FontStyle.Italic)
                        }
                    }
                    val totalHours = calculateDailyHoursLocal(daily.records)
                    if (totalHours > 0.0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Total: ${"%.2f".format(totalHours)} hours",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyReportView(monthlyData: List<DayOfficeHours>) {
    if (monthlyData.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text("No monthly data available yet.")
        }
        return
    }

    val chartProducer = remember(monthlyData) {
        val entries = monthlyData.mapIndexed { index, dayData ->
            FloatEntry(x = index.toFloat(), y = dayData.officeHours.toFloat())
        }
        ChartEntryModelProducer(entries)
    }

    Column {
        Chart(
            chart = columnChart(),
            chartModelProducer = chartProducer,
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(monthlyData) { dayData ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Text(dayData.day)
                    Text("${"%.2f".format(dayData.officeHours)} hours")
                }
                Divider()
            }
        }
    }
}

fun calculateDailyHoursLocal(records: List<AttendanceRecord>): Double {
    var totalHours = 0.0
    var lastPunchIn: Long? = null
    records.sortedBy { it.timestamp }.forEach { record ->
        if (record.punchType == "IN") lastPunchIn = record.timestamp
        else if (record.punchType == "OUT" && lastPunchIn != null) {
            totalHours += (record.timestamp - lastPunchIn!!) / (1000.0 * 60 * 60)
            lastPunchIn = null
        }
    }
    return totalHours
}