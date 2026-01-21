package com.technikh.employeeattendancetracking.repository

import android.util.Log
import com.technikh.employeeattendancetracking.data.database.daos.AttendanceDao
import com.technikh.employeeattendancetracking.data.database.daos.WorkReasonDao
import com.technikh.employeeattendancetracking.data.database.entities.AttendanceRecord
import com.technikh.employeeattendancetracking.data.database.entities.OfficeWorkReason
import com.technikh.employeeattendancetracking.data.database.entities.SupabaseAttendanceRecord
import com.technikh.employeeattendancetracking.data.database.entities.PunchOutUpdate
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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

    /**
     * Upload selfie image to Supabase Storage bucket 'selfies'
     * @param filePath Local file path of the selfie image
     * @return Public URL of the uploaded image, or null if upload fails
     */
    suspend fun uploadImageToSupabase(filePath: String): String? {
        val client = supabase ?: return null
        
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e("REPO", "Selfie file does not exist: $filePath")
                return null
            }
            
            val fileBytes = file.readBytes()
            val fileName = "selfie_${System.currentTimeMillis()}_${file.name}"
            
            // Upload to 'selfies' bucket
            val bucket = client.storage.from("selfies")
            bucket.upload(fileName, fileBytes, upsert = true)
            
            // Get public URL
            val publicUrl = bucket.publicUrl(fileName)
            Log.d("REPO", "Image uploaded successfully: $publicUrl")
            publicUrl
        } catch (e: Exception) {
            Log.e("REPO", "Failed to upload image: ${e.message}")
            null
        }
    }

    /**
     * Sync punch record to Supabase with proper mapping to existing table structure.
     * - For PUNCH IN: Creates new row with employee_id, punch_in_time, and image_url
     * - For PUNCH OUT: Updates existing row's punch_out_time (or creates new if none for today)
     */
    suspend fun syncAttendanceToSupabase(
        employeeId: String,
        punchType: String,
        timestamp: Long,
        selfiePath: String?
    ) {
        Log.d("REPO", "syncAttendanceToSupabase called - employeeId: $employeeId, punchType: $punchType, selfiePath: $selfiePath")
        
        val client = supabase ?: run {
            Log.w("REPO", "Supabase client is null, skipping sync")
            return
        }
        
        Log.d("REPO", "Supabase client is available, proceeding with sync...")

        try {
            // Format timestamp as ISO 8601 string for Supabase TEXT column
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val timeString = dateFormat.format(Date(timestamp))

            // Upload image if available
            Log.d("REPO", "selfiePath received: $selfiePath")
            val imageUrl = if (selfiePath != null) {
                Log.d("REPO", "Attempting to upload image from: $selfiePath")
                val url = uploadImageToSupabase(selfiePath)
                Log.d("REPO", "Image upload result: $url")
                url
            } else {
                Log.w("REPO", "No selfiePath provided - image_url will be null")
                null
            }

            if (punchType == "IN") {
                // Create new attendance record for punch in
                val record = SupabaseAttendanceRecord(
                    employeeId = employeeId,
                    punchInTime = timeString,
                    punchOutTime = null,
                    imageUrl = imageUrl,
                    isSynced = true
                )
                try {
                    client.from("attendance").insert(record)
                    Log.d("REPO", "PUNCH IN synced to Supabase for $employeeId")
                } catch (insertError: Exception) {
                    Log.e("REPO", "CRITICAL: PUNCH IN Insert Failed for $employeeId", insertError)
                    insertError.printStackTrace()
                }
            } else {
                // For punch out, update the most recent record that has punch_in but no punch_out
                // Get today's date to find the matching record
                val todayStart = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
                
                // Create serializable update record for punch_out_time
                val updateData = PunchOutUpdate(
                    punchOutTime = timeString,
                    isSynced = true
                )
                
                try {
                    client.from("attendance").update(updateData) {
                        filter {
                            eq("employee_id", employeeId)
                            ilike("punch_in_time", "$todayStart%")
                            exact("punch_out_time", null)
                        }
                    }
                    Log.d("REPO", "PUNCH OUT synced to Supabase for $employeeId")
                } catch (updateError: Exception) {
                    Log.e("REPO", "CRITICAL: PUNCH OUT Update Failed for $employeeId", updateError)
                    updateError.printStackTrace()
                }
            }
        } catch (e: Exception) {
            Log.e("REPO", "Failed to sync to Supabase (General Error): ${e.message}", e)
            e.printStackTrace()
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