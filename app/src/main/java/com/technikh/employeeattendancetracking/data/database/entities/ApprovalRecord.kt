package com.technikh.employeeattendancetracking.data.database.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single pending approval item shown in the employer dashboard.
 * Can be either a Google Email approval or a Device ID approval.
 */
data class ApprovalItem(
    val id: String,
    val employeeId: String,
    val type: ApprovalType,        // EMAIL or DEVICE
    val value: String,             // The email address or device_id_hash
    val status: String,            // "pending", "approved", "rejected"
    val requestedAt: String,
    val reviewedAt: String? = null,
    val reviewedBy: String? = null
)

enum class ApprovalType { EMAIL, DEVICE }

// ------- Supabase DTOs -------

/**
 * DTO matching the `employee_google_access` Supabase table.
 */
@Serializable
data class GoogleAccessRequest(
    @SerialName("id")           val id: String = "",
    @SerialName("employee_id")  val employeeId: String,
    @SerialName("google_email") val googleEmail: String,
    @SerialName("status")       val status: String = "pending",
    @SerialName("requested_at") val requestedAt: String = "",
    @SerialName("reviewed_at")  val reviewedAt: String? = null,
    @SerialName("reviewed_by")  val reviewedBy: String? = null
)

/**
 * DTO matching the `employee_device_access` Supabase table.
 */
@Serializable
data class DeviceAccessRequest(
    @SerialName("id")             val id: String = "",
    @SerialName("employee_id")    val employeeId: String,
    @SerialName("device_id_hash") val deviceIdHash: String,
    @SerialName("status")         val status: String = "pending",
    @SerialName("requested_at")   val requestedAt: String = "",
    @SerialName("reviewed_at")    val reviewedAt: String? = null,
    @SerialName("reviewed_by")    val reviewedBy: String? = null
)

/**
 * DTO used to PATCH the status of an approval row.
 */
@Serializable
data class ApprovalStatusUpdate(
    @SerialName("status")      val status: String,
    @SerialName("reviewed_at") val reviewedAt: String,
    @SerialName("reviewed_by") val reviewedBy: String = "employer"
)
