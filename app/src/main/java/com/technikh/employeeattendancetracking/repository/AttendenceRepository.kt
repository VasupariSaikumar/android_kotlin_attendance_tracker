package com.technikh.employeeattendancetracking.repository

import android.util.Log
import com.technikh.employeeattendancetracking.data.database.daos.AttendanceDao
import com.technikh.employeeattendancetracking.data.database.daos.WorkReasonDao
import com.technikh.employeeattendancetracking.data.database.entities.AttendanceRecord
import com.technikh.employeeattendancetracking.data.database.entities.OfficeWorkReason
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow

class AttendanceRepository(
    private val attendanceDao: AttendanceDao,
    private val workReasonDao: WorkReasonDao,
    var supabase: SupabaseClient?
) {

    fun getDailyAttendance(employeeId: String): Flow<List<AttendanceRecord>> {
        return attendanceDao.getDailyAttendance(employeeId)
    }

    fun getAttendanceByEmployee(employeeId: String): Flow<List<AttendanceRecord>> {
        return attendanceDao.getAttendanceByEmployee(employeeId)
    }

    suspend fun insertAttendance(record: AttendanceRecord) {
        Log.d("REPO", "Saving record locally: ${record.punchType}")

        // A. Save to Local Phone (Room)
        attendanceDao.insert(record)

        try {
            supabase?.from("attendance")?.insert(record)
            Log.d("REPO", "Synced to Laptop Success!")
        } catch (e: Exception) {
            Log.e("REPO", "Offline mode: Could not sync to laptop. Error: ${e.message}")
            // This is fine. We saved it locally (Step A), so we are safe.
        }
    }

    suspend fun searchReasons(query: String): List<OfficeWorkReason> {
        return workReasonDao.searchReasons(query)
    }

    suspend fun insertReason(reason: OfficeWorkReason) {
        workReasonDao.insert(reason)
    }

    suspend fun incrementReasonUsage(reason: String, timestamp: Long) {
        workReasonDao.incrementUsage(reason, timestamp)
    }
}