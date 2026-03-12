package com.technikh.employeeattendancetracking.data.database.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for syncing attendance to Supabase.
 * Matches the existing Supabase 'attendance' table structure.
 */
@Serializable
data class SupabaseAttendanceRecord(
    @SerialName("employee_id") val employeeId: String,
    @SerialName("punch_in_time") val punchInTime: String? = null,
    @SerialName("punch_out_time") val punchOutTime: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("punch_out_image_url") val punchOutImageUrl: String? = null,
    @SerialName("punch_source") val punchSource: String = "shared_device",
    @SerialName("google_email") val googleEmail: String? = null,
    @SerialName("device_id_hash") val deviceIdHash: String? = null,
    @SerialName("is_synced") val isSynced: Boolean = true
)

/**
 * DTO for updating punch_out_time in Supabase.
 * Used instead of mapOf() to ensure proper serialization.
 */
@Serializable
data class PunchOutUpdate(
    @SerialName("punch_out_time") val punchOutTime: String,
    @SerialName("punch_out_image_url") val punchOutImageUrl: String? = null,
    @SerialName("punch_source") val punchSource: String = "shared_device",
    @SerialName("google_email") val googleEmail: String? = null,
    @SerialName("device_id_hash") val deviceIdHash: String? = null,
    @SerialName("is_synced") val isSynced: Boolean = true
)
