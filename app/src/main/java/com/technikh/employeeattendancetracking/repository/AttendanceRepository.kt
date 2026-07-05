package com.technikh.employeeattendancetracking.repository

import android.util.Log
import com.technikh.employeeattendancetracking.data.database.daos.AttendanceDao
import com.technikh.employeeattendancetracking.data.database.daos.WorkReasonDao
import com.technikh.employeeattendancetracking.data.database.entities.AttendanceRecord
import com.technikh.employeeattendancetracking.data.database.entities.OfficeWorkReason
import com.technikh.employeeattendancetracking.data.database.entities.SupabaseAttendanceRecord
import com.technikh.employeeattendancetracking.data.database.entities.PunchOutUpdate
import com.technikh.employeeattendancetracking.data.database.entities.ApprovalItem
import com.technikh.employeeattendancetracking.data.database.entities.ApprovalType
import com.technikh.employeeattendancetracking.data.database.entities.GoogleAccessRequest
import com.technikh.employeeattendancetracking.data.database.entities.DeviceAccessRequest
import com.technikh.employeeattendancetracking.data.database.entities.ApprovalStatusUpdate
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
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

    suspend fun getAttendanceByEmployee(employeeId: String): List<AttendanceRecord> {
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
        }
    }
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
    suspend fun syncAttendanceToSupabase(
        employeeId: String,
        punchType: String,
        timestamp: Long,
        selfiePath: String?,
        punchSource: String = "shared_device",
        googleEmail: String? = null,
        deviceIdHash: String? = null
    ) {
        Log.d("REPO", "syncAttendanceToSupabase called - employeeId: $employeeId, punchType: $punchType, selfiePath: $selfiePath")
        
        val client = supabase ?: run {
            Log.w("REPO", "Supabase client is null, skipping sync")
            return
        }
        
        Log.d("REPO", "Supabase client is available, proceeding with sync...")

        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
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
                    punchSource = punchSource,
                    googleEmail = googleEmail,
                    deviceIdHash = deviceIdHash,
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
                Log.d("REPO", "Preparing PUNCH OUT update. ImageURL: $imageUrl")

                // Create serializable update record for punch_out_time
                val updateData = PunchOutUpdate(
                    punchOutTime = timeString,
                    punchOutImageUrl = imageUrl,
                    punchSource = punchSource,
                    googleEmail = googleEmail,
                    deviceIdHash = deviceIdHash,
                    isSynced = true
                )
                
                try {
                    client.from("attendance").update(updateData) {
                        filter {
                            eq("employee_id", employeeId)
                            filter("punch_out_time", FilterOperator.IS, null)
                        }
                    }
                    Log.d("REPO", "PUNCH OUT synced to Supabase for $employeeId. Payload: $updateData")
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

    // =============================================================
    // EMPLOYER: Approval Workflow Methods
    // =============================================================

    /**
     * Fetches all PENDING approval requests from both Supabase tables.
     * Returns a combined list of [ApprovalItem] for the employer dashboard.
     */
    suspend fun fetchPendingApprovals(): List<ApprovalItem> {
        val client = supabase ?: run {
            Log.w("REPO", "Supabase not configured – cannot fetch approvals")
            return emptyList()
        }

        val results = mutableListOf<ApprovalItem>()

        try {
            val emailRequests = client
                .from("employee_google_access")
                .select {
                    filter { eq("status", "pending") }
                }
                .decodeList<GoogleAccessRequest>()

            emailRequests.forEach { req ->
                results.add(
                    ApprovalItem(
                        id = req.id,
                        employeeId = req.employeeId,
                        type = ApprovalType.EMAIL,
                        value = req.googleEmail,
                        status = req.status,
                        requestedAt = req.requestedAt,
                        reviewedAt = req.reviewedAt,
                        reviewedBy = req.reviewedBy
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("REPO", "Failed to fetch email approvals: ${e.message}")
        }

        try {
            val deviceRequests = client
                .from("employee_device_access")
                .select {
                    filter { eq("status", "pending") }
                }
                .decodeList<DeviceAccessRequest>()

            deviceRequests.forEach { req ->
                results.add(
                    ApprovalItem(
                        id = req.id,
                        employeeId = req.employeeId,
                        type = ApprovalType.DEVICE,
                        value = req.deviceIdHash,
                        status = req.status,
                        requestedAt = req.requestedAt,
                        reviewedAt = req.reviewedAt,
                        reviewedBy = req.reviewedBy
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("REPO", "Failed to fetch device approvals: ${e.message}")
        }

        Log.d("REPO", "Fetched ${results.size} pending approvals")
        return results
    }

    /**
     * Updates the status of an approval request (approve or reject).
     * @param approvalType "email" → `employee_google_access` | "device" → `employee_device_access`
     * @param requestId    UUID of the row to update
     * @param newStatus    "approved" or "rejected"
     */
    suspend fun reviewRequest(approvalType: ApprovalType, requestId: String, newStatus: String) {
        val client = supabase ?: run {
            Log.w("REPO", "Supabase not configured – cannot review request")
            return
        }

        val table = when (approvalType) {
            ApprovalType.EMAIL  -> "employee_google_access"
            ApprovalType.DEVICE -> "employee_device_access"
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        val now = dateFormat.format(Date())

        try {
            client.from(table).update(
                ApprovalStatusUpdate(status = newStatus, reviewedAt = now)
            ) {
                filter { eq("id", requestId) }
            }
            Log.d("REPO", "Request $requestId in $table set to $newStatus")
        } catch (e: Exception) {
            Log.e("REPO", "Failed to review request $requestId: ${e.message}")
        }
    }

    // =============================================================
    // EMPLOYEE: Submit Approval Requests
    // =============================================================

    /**
     * Employee submits their Google email for employer approval.
     * Only submits if no pending/approved request already exists.
     */
    suspend fun submitEmailApprovalRequest(employeeId: String, googleEmail: String): Boolean {
        val client = supabase ?: return false
        return try {
            // Check if already submitted
            val existing = client.from("employee_google_access")
                .select { filter { eq("google_email", googleEmail) } }
                .decodeList<GoogleAccessRequest>()
            if (existing.any { it.status == "approved" || it.status == "pending" }) {
                Log.d("REPO", "Email request already exists: ${existing.first().status}")
                return false
            }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            client.from("employee_google_access").insert(
                GoogleAccessRequest(
                    employeeId = employeeId,
                    googleEmail = googleEmail,
                    status = "pending",
                    requestedAt = dateFormat.format(Date())
                )
            )
            Log.d("REPO", "Email approval request submitted for $googleEmail")
            true
        } catch (e: Exception) {
            Log.e("REPO", "Failed to submit email request: ${e.message}")
            false
        }
    }

    /**
     * Employee submits their device ID hash for employer approval.
     */
    suspend fun submitDeviceApprovalRequest(employeeId: String, deviceIdHash: String): Boolean {
        val client = supabase ?: return false
        return try {
            val existing = client.from("employee_device_access")
                .select { filter { eq("device_id_hash", deviceIdHash) } }
                .decodeList<DeviceAccessRequest>()
            if (existing.any { it.status == "approved" || it.status == "pending" }) {
                Log.d("REPO", "Device request already exists: ${existing.first().status}")
                return false
            }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            client.from("employee_device_access").insert(
                DeviceAccessRequest(
                    employeeId = employeeId,
                    deviceIdHash = deviceIdHash,
                    status = "pending",
                    requestedAt = dateFormat.format(Date())
                )
            )
            Log.d("REPO", "Device approval request submitted for hash $deviceIdHash")
            true
        } catch (e: Exception) {
            Log.e("REPO", "Failed to submit device request: ${e.message}")
            false
        }
    }

    /**
     * Checks the approval status of a given Google email.
     * Returns: "approved", "pending", "rejected", or "none" (never submitted).
     */
    suspend fun checkEmailApprovalStatus(googleEmail: String): String {
        val client = supabase ?: return "none"
        return try {
            val results = client.from("employee_google_access")
                .select { filter { eq("google_email", googleEmail) } }
                .decodeList<GoogleAccessRequest>()
            results.maxByOrNull { it.requestedAt }?.status ?: "none"
        } catch (e: Exception) {
            Log.e("REPO", "Failed to check email status: ${e.message}")
            "none"
        }
    }

    /**
     * Checks the approval status of a given device ID hash.
     */
    suspend fun checkDeviceApprovalStatus(deviceIdHash: String): String {
        val client = supabase ?: return "none"
        return try {
            val results = client.from("employee_device_access")
                .select { filter { eq("device_id_hash", deviceIdHash) } }
                .decodeList<DeviceAccessRequest>()
            results.maxByOrNull { it.requestedAt }?.status ?: "none"
        } catch (e: Exception) {
            Log.e("REPO", "Failed to check device status: ${e.message}")
            "none"
        }
    }

    /**
     * Checks if the Supabase server is reachable (used as intranet check).
     */
    suspend fun isIntranetReachable(): Boolean {
        val client = supabase ?: return false
        return try {
            // Simple ping: count rows in attendance table
            client.from("attendance").select { limit(1) }
            true
        } catch (e: Exception) {
            Log.w("REPO", "Intranet not reachable: ${e.message}")
            false
        }
    }
}
//This file works as a mediator between viewModel and dataSources like room and supabase 