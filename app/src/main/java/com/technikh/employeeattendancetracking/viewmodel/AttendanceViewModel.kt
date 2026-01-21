package com.technikh.employeeattendancetracking.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// CORRECT IMPORTS FOR YOUR NEW FOLDER STRUCTURE
import com.technikh.employeeattendancetracking.data.database.daos.AttendanceDao
import com.technikh.employeeattendancetracking.data.database.daos.WorkReasonDao
import com.technikh.employeeattendancetracking.data.database.entities.AttendanceRecord
import com.technikh.employeeattendancetracking.data.database.entities.OfficeWorkReason
import com.technikh.employeeattendancetracking.data.database.entities.DailyAttendance


import com.technikh.employeeattendancetracking.data.database.SupabaseManager
import com.technikh.employeeattendancetracking.data.database.AppPreferences
import com.technikh.employeeattendancetracking.repository.AttendanceRepository
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
class AttendanceViewModel(
    private val repository: AttendanceRepository,
    private val preferences: AppPreferences,
) : ViewModel() {
    private val _connectionStatus = MutableStateFlow("Initializing...")
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline = _isOnline.asStateFlow()

    var supabaseClient: io.github.jan.supabase.SupabaseClient? = null

    init {
        monitorConnection()
    }

    // --- UI STATES ---
    var showPunchOutDialog by mutableStateOf(false)

    // Holds the "In/Out" status for the Button color
    private val _isPunchedIn = MutableStateFlow(false)
    val isPunchedIn = _isPunchedIn.asStateFlow()

    // Holds the Reports Data (Fixed: Now returns grouped DailyAttendance)
    private val _dailyReports = MutableStateFlow<List<DailyAttendance>>(emptyList())
    val dailyReports = _dailyReports.asStateFlow()

    // --- MAIN LOGIC ---

    // Called when the screen opens to load data
    fun loadEmployeeState(employeeId: String) {
        viewModelScope.launch {
            // 1. Check if user is currently punched in
            val historyList = repository.getAttendanceByEmployee(employeeId).firstOrNull()
            val lastRecord = historyList?.firstOrNull()
            _isPunchedIn.value = lastRecord?.punchType == "IN"

            // 2. Load and Group the Reports Data
            repository.getDailyAttendance(employeeId).collect { records ->
                // This logic transforms the "Raw List" into "Grouped by Date"
                val groupedData = records.groupBy { record ->
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(record.timestamp))
                }.map { (date, dayRecords) ->
                    DailyAttendance(date, dayRecords)
                }
                _dailyReports.value = groupedData
            }
        }
    }

    fun onBiometricSuccess(employeeId: String) {
        viewModelScope.launch {
            // Double check state to avoid duplicate punches
            val historyList = repository.getAttendanceByEmployee(employeeId).firstOrNull()
            val lastRecord = historyList?.firstOrNull()
            if (lastRecord?.punchType == "IN") {
                showPunchOutDialog = true
            } else {
                repository.insertAttendance(AttendanceRecord(
                    employeeId = employeeId,
                    punchType = "IN"
                ))
                _isPunchedIn.value = true
            }
        }
    }

    fun punchOut(employeeId: String, reason: String, isOfficeWork: Boolean, workReason: String?) {
        viewModelScope.launch {
            repository.insertAttendance(AttendanceRecord(
                employeeId = employeeId,
                punchType = "OUT",
                reason = reason,
                isOfficeWork = isOfficeWork,
                workReason = workReason
            ))
            _isPunchedIn.value = false // Switch button back to "PUNCH IN" (Green)

            if (isOfficeWork && !workReason.isNullOrBlank()) {
                saveNewReason(workReason)
            }
        }
    }

    private suspend fun saveNewReason(reasonText: String) {
        val existing = repository.searchReasons("%$reasonText%")
        if (existing.isEmpty()) {
            repository.insertReason(OfficeWorkReason(reason = reasonText, usageCount = 1))
        } else {
            repository.incrementReasonUsage(reasonText, System.currentTimeMillis())
        }
    }

    suspend fun searchWorkReasons(query: String): List<String> {
        return withContext(Dispatchers.IO) {
            repository.searchReasons("%$query%").map { it.reason }
        }
    }
    private fun monitorConnection() {
        viewModelScope.launch {
            preferences.supabaseConfig.collect { (url, key) ->
                android.util.Log.d("AttendanceVM", "Config received - URL: '$url', Key length: ${key.length}")
                if (url.isNotBlank() && key.isNotBlank()) {
                    android.util.Log.d("AttendanceVM", "Creating Supabase client for URL: $url")
                    supabaseClient = SupabaseManager.getClient(url, key)
                    repository.supabase = supabaseClient
                    startHeartbeat()
                } else {
                    android.util.Log.w("AttendanceVM", "Config is blank - URL blank: ${url.isBlank()}, Key blank: ${key.isBlank()}")
                    _connectionStatus.value = "Setup Required in Settings"
                    _isOnline.value = false
                }
            }
        }
    }
    private suspend fun startHeartbeat() {
        while (true) {
            try {
                val client = supabaseClient
                if (client == null) {
                    android.util.Log.w("AttendanceVM", "Supabase client is null, cannot check connection")
                    _connectionStatus.value = "Offline: No Client"
                    _isOnline.value = false
                } else {
                    android.util.Log.d("AttendanceVM", "Attempting to connect to Supabase...")
                    try {
                        client.from("employees").select { limit(1) }
                        android.util.Log.d("AttendanceVM", "Connection successful!")
                        _connectionStatus.value = "Online (WiFi Mode)"
                        _isOnline.value = true
                    } catch (e: io.github.jan.supabase.exceptions.NotFoundRestException) {
                        // Table not found means server IS reachable, just table doesn't exist
                        android.util.Log.d("AttendanceVM", "Server reachable but table not found - treating as connected")
                        _connectionStatus.value = "Online (WiFi Mode)"
                        _isOnline.value = true
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AttendanceVM", "Connection failed: ${e.javaClass.simpleName} - ${e.message}", e)
                _connectionStatus.value = "Offline: ${e.javaClass.simpleName}"
                _isOnline.value = false
            }
            delay(5000)
        }
    }

    class Factory(
        private val repository: AttendanceRepository,
        private val preferences: AppPreferences
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AttendanceViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AttendanceViewModel(repository, preferences) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}