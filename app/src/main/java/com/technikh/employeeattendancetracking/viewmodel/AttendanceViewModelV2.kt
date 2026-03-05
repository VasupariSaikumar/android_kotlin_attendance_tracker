package com.technikh.employeeattendancetracking.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.technikh.employeeattendancetracking.data.database.daos.*
import com.technikh.employeeattendancetracking.data.database.entities.*
import com.technikh.employeeattendancetracking.repository.AttendanceRepository
import com.technikh.employeeattendancetracking.data.database.entities.ApprovalItem
import com.technikh.employeeattendancetracking.data.database.entities.ApprovalType

class AttendanceViewModelV2(
    private val attendanceDao: AttendanceDao,
    private val workReasonDao: WorkReasonDao,
    private val employeeDao: EmployeeDao,
    initialRepository: AttendanceRepository? = null  // Optional for Supabase sync - can be updated later
) : ViewModel() {
    var repository: AttendanceRepository? = initialRepository
        set(value) {
            field = value
            android.util.Log.d("V2-VM", "Repository updated: ${value != null}")
        }


    val todayTimeline = attendanceDao.getTodayAttendance(getStartOfDay())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Employee List
    private val _employees = MutableStateFlow<List<Employee>>(emptyList())
    val employees = _employees.asStateFlow()

    // Current Status (For Punch Button Color)
    private val _isPunchedIn = MutableStateFlow(false)
    val isPunchedIn = _isPunchedIn.asStateFlow()

    private val _currentEmployeeName = MutableStateFlow("")
    val currentEmployeeName = _currentEmployeeName.asStateFlow()


    // 1. The Single Source of Truth for "Selected Time" (Used for both Day and Month views)
    private val _selectedDate = MutableStateFlow(Calendar.getInstance())

    // 2. Formatted Strings for UI Headers
    val currentDateText = _selectedDate.map { cal ->
        SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(cal.time)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val currentMonthText = _selectedDate.map { cal ->
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // 3. Raw Records for the Employee (Fetched from DB)
    private val _allRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())

    // 4. DAILY REPORT: Filters records to show ONLY the selected Day
    val currentDayRecords = combine(_allRecords, _selectedDate) { records, cal ->
        val targetDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        records.filter {
            val recordDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.employeeTimeMillis))
            recordDay == targetDay
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 5. CSV EXPORT DATA: Gets ALL records for the selected MONTH
    val currentMonthRecords = combine(_allRecords, _selectedDate) { records, cal ->
        val targetMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)
        records.filter {
            val recordMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(it.employeeTimeMillis))
            recordMonth == targetMonth
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 6. Compatibility Flow (Keeps old code working if referenced)
    val dailyReports = combine(_allRecords, _selectedDate) { records, cal ->
        val selectedMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)
        val filtered = records.filter {
            val recordMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(it.employeeTimeMillis))
            recordMonth == selectedMonthStr
        }
        filtered.groupBy {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.employeeTimeMillis))
        }.map { (date, dailyRecs) -> DailyAttendance(date, dailyRecs) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 7. Monthly Chart Data
    private val _monthlyReport = MutableStateFlow<List<DayOfficeHours>>(emptyList())
    val monthlyReport = _monthlyReport.asStateFlow()

    private var activeEmployeeId: String? = null

    // 1. All records in the entire database (loaded when Global Screen opens)
    private val _globalRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())

    // 2. The IDs currently selected in the filter (Empty = Select All)
    private val _selectedEmployeeIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedEmployeeIds = _selectedEmployeeIds.asStateFlow()

    fun toggleEmployeeSelection(id: String) {
        val current = _selectedEmployeeIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedEmployeeIds.value = current
    }

    fun selectAllEmployees(allIds: List<String>) {
        _selectedEmployeeIds.value = allIds.toSet()
    }

    // 3. GLOBAL DAILY REPORT (Filtered by Date AND Selected Employees)
    val globalDailyReports = combine(_globalRecords, _selectedDate, _selectedEmployeeIds) { records, cal, selectedIds ->
        val targetDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

        records.filter { record ->
            val isDateMatch = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(record.employeeTimeMillis
            )) == targetDay
            val isEmpMatch = if (selectedIds.isEmpty()) true else selectedIds.contains(record.employeeId)
            isDateMatch && isEmpMatch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. GLOBAL MONTHLY SUMMARY (Aggregated Hours per Employee for the Month)
    val globalMonthlySummary = combine(_globalRecords, _selectedDate, _selectedEmployeeIds) { records, cal, selectedIds ->
        val targetMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)

        // Filter by Month & Employee
        val monthlyRecords = records.filter { record ->
            val isMonthMatch = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(record.employeeTimeMillis
            )) == targetMonth
            val isEmpMatch = if (selectedIds.isEmpty()) true else selectedIds.contains(record.employeeId)
            isMonthMatch && isEmpMatch
        }

        // Group by Employee and Calculate Hours
        monthlyRecords.groupBy { it.employeeId }.map { (empId, empRecords) ->
            val hours = calculateHoursInternal(empRecords)
            empId to hours
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // --- WORK REASONS ---
    private val _workReasonSuggestions = MutableStateFlow<List<String>>(emptyList())
    val workReasonSuggestions = _workReasonSuggestions.asStateFlow()

    // --- EMPLOYER: PENDING APPROVALS ---
    private val _pendingApprovals = MutableStateFlow<List<ApprovalItem>>(emptyList())
    val pendingApprovals = _pendingApprovals.asStateFlow()

    private val _approvalsLoading = MutableStateFlow(false)
    val approvalsLoading = _approvalsLoading.asStateFlow()

    private val _approvalsError = MutableStateFlow<String?>(null)
    val approvalsError = _approvalsError.asStateFlow()

    init {
        viewModelScope.launch {
            employeeDao.getAllEmployees().collect { list -> _employees.value = list }
        }
    }

    // --- ACTIONS ---

    fun loadDashboardData(employeeId: String) {
        viewModelScope.launch {
            activeEmployeeId = employeeId
            val emp = employeeDao.getEmployeeById(employeeId)
            _currentEmployeeName.value = emp?.name ?: "Unknown"

            // Update Punch Button Status
            val lastRecord = attendanceDao.getLastRecord(employeeId)
            _isPunchedIn.value = lastRecord?.punchType == "IN"

            // Fetch All Records
            val all = attendanceDao.getAttendanceByEmployee(employeeId)
            _allRecords.value = all

            refreshMonthlyChart()
        }
    }

    // --- NEW: Load Global Data ---
    fun loadGlobalReportData() {
        viewModelScope.launch {
            val all = attendanceDao.getAllRecordsList() // Fetch ALL records
            _globalRecords.value = all

            // FIX: Use .first() on the Flow to get the list without needing a new DAO function
            val allEmps = employeeDao.getAllEmployees().first()

            _selectedEmployeeIds.value = allEmps.map { it.employeeId }.toSet()
        }
    }


    // Move Day by Day (For Daily Report Tab)
    fun incrementDay(amount: Int) {
        val current = _selectedDate.value.clone() as Calendar
        current.add(Calendar.DAY_OF_YEAR, amount)
        _selectedDate.value = current
        refreshMonthlyChart()
    }

    // Move Month by Month (For Monthly Chart Tab)
    fun incrementMonth(amount: Int) {
        val current = _selectedDate.value.clone() as Calendar
        current.add(Calendar.MONTH, amount)
        _selectedDate.value = current
        refreshMonthlyChart()
    }

    // Helper for backward compatibility with old 'changeMonth' calls
    fun changeMonth(monthsToAdd: Int) {
        incrementMonth(monthsToAdd)
    }

    // Set Specific Date from DatePicker
    fun setDate(timestamp: Long) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        _selectedDate.value = cal
        refreshMonthlyChart()
    }

    private fun refreshMonthlyChart() {
        activeEmployeeId?.let { id ->
            viewModelScope.launch {
                val format = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                val monthStr = format.format(_selectedDate.value.time)
                _monthlyReport.value = attendanceDao.getMonthlyOfficeHours(id, monthStr)
            }
        }
    }

    // --- STANDARD PUNCH ACTIONS ---

    fun registerEmployee(name: String, id: String, password: String) {
        viewModelScope.launch {
            employeeDao.insertEmployee(Employee(name = name, employeeId = id, password = password))
        }
    }

    fun getLiveStatus(employeeId: String): Flow<AttendanceRecord?> {
        return attendanceDao.getLastRecordFlow(employeeId)
    }

    fun punchIn(employeeId: String, selfiePath: String?, systemTimeMillis: Long, employeeTimeMillis: Long) {
        android.util.Log.d("V2-PUNCH", "punchIn called for $employeeId, selfiePath=$selfiePath")
        viewModelScope.launch {
            val normalizedSystemTime = normalizeToMinute(systemTimeMillis)
            val normalizedEmployeeTime = normalizeToMinute(employeeTimeMillis)
            val isManuallyEdited = normalizedSystemTime != normalizedEmployeeTime

            attendanceDao.insert(AttendanceRecord(employeeId = employeeId, punchType = "IN", systemTimeMillis = systemTimeMillis, employeeTimeMillis = employeeTimeMillis, isManuallyEdited = isManuallyEdited, selfiePath = selfiePath))
            android.util.Log.d("V2-PUNCH", "Local DB insert done. Repository is null: ${repository == null}")
            
            // Sync to Supabase if repository is available
            repository?.let { repo ->
                android.util.Log.d("V2-PUNCH", "Calling syncAttendanceToSupabase...")
                repo.syncAttendanceToSupabase(employeeId, "IN", employeeTimeMillis, selfiePath)
                android.util.Log.d("V2-PUNCH", "syncAttendanceToSupabase completed")
            } ?: run {
                android.util.Log.w("V2-PUNCH", "Repository is NULL - cannot sync to Supabase!")
            }
            
            _isPunchedIn.value = true
            loadDashboardData(employeeId)
        }
    }

    fun punchOut(employeeId: String, reason: String, isOfficeWork: Boolean, workReason: String?, systemTimeMillis: Long, employeeTimeMillis: Long, selfiePath: String?) {
        viewModelScope.launch {
            val normalizedSystemTime = normalizeToMinute(systemTimeMillis)
            val normalizedEmployeeTime = normalizeToMinute(employeeTimeMillis)
            val isManuallyEdited = normalizedSystemTime != normalizedEmployeeTime

            attendanceDao.insert(AttendanceRecord(employeeId = employeeId, punchType = "OUT", systemTimeMillis = systemTimeMillis, employeeTimeMillis = employeeTimeMillis, isManuallyEdited = isManuallyEdited, reason = reason, isOfficeWork = isOfficeWork, workReason = workReason, selfiePath = selfiePath))
            
            // Sync to Supabase if repository is available
            repository?.syncAttendanceToSupabase(employeeId, "OUT", employeeTimeMillis, selfiePath)
            
            if (isOfficeWork && !workReason.isNullOrBlank()) saveNewReason(workReason)
            _isPunchedIn.value = false
            loadDashboardData(employeeId)
        }
    }

    fun searchReasons(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) _workReasonSuggestions.value = emptyList()
            else _workReasonSuggestions.value = workReasonDao.searchReasons("%$query%").map { it.reason }
        }
    }

    // =============================================================
    // EMPLOYER: Approval Actions
    // =============================================================

    fun loadPendingApprovals() {
        viewModelScope.launch {
            _approvalsLoading.value = true
            _approvalsError.value = null
            try {
                val repo = repository
                if (repo == null) {
                    _approvalsError.value = "Supabase not configured. Go to Settings and save your server config."
                    _pendingApprovals.value = emptyList()
                } else {
                    _pendingApprovals.value = repo.fetchPendingApprovals()
                    if (_pendingApprovals.value.isEmpty()) {
                        _approvalsError.value = null  // No error, just empty
                    }
                }
            } catch (e: Exception) {
                _approvalsError.value = "Failed to load approvals: ${e.message}"
            } finally {
                _approvalsLoading.value = false
            }
        }
    }

    fun approveRequest(item: ApprovalItem) {
        viewModelScope.launch {
            repository?.reviewRequest(item.type, item.id, "approved")
            loadPendingApprovals()  // Refresh after action
        }
    }

    fun rejectRequest(item: ApprovalItem) {
        viewModelScope.launch {
            repository?.reviewRequest(item.type, item.id, "rejected")
            loadPendingApprovals()  // Refresh after action
        }
    }

    private fun normalizeToMinute(timeMillis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private suspend fun saveNewReason(reasonText: String) {
        val existing = workReasonDao.searchReasons(reasonText)
        if (existing.isEmpty()) workReasonDao.insert(OfficeWorkReason(reason = reasonText, usageCount = 1))
        else workReasonDao.incrementUsage(reasonText, System.currentTimeMillis())
    }

    private fun getStartOfDay(): Long {
        val cal = Calendar.getInstance(); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // Helper logic for hours calculation
    private fun calculateHoursInternal(records: List<AttendanceRecord>): Double {
        var totalHours = 0.0
        var lastPunchIn: Long? = null
        records.sortedBy { it.employeeTimeMillis }.forEach { record ->
            if (record.punchType == "IN") lastPunchIn = record.employeeTimeMillis
            else if (record.punchType == "OUT" && lastPunchIn != null) {
                totalHours += (record.employeeTimeMillis - lastPunchIn!!) / (1000.0 * 60 * 60)
                lastPunchIn = null
            }
        }
        return totalHours
    }

    class Factory(
        val ad: AttendanceDao, 
        val wd: WorkReasonDao, 
        val ed: EmployeeDao,
        val repo: AttendanceRepository? = null
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AttendanceViewModelV2(ad, wd, ed, repo) as T
    }
}