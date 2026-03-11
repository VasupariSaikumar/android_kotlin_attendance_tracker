package com.technikh.employeeattendancetracking.data.database.entities

/**
 * Represents the full eligibility result before a personal-phone punch.
 * All four conditions must pass for a punch to be allowed.
 */
data class PunchEligibility(
    val isSignedIn: Boolean = false,
    val isIntranetReachable: Boolean = false,
    val emailApprovalStatus: ApprovalStatus = ApprovalStatus.UNKNOWN,
    val deviceApprovalStatus: ApprovalStatus = ApprovalStatus.UNKNOWN,
    val googleEmail: String? = null,
    val deviceIdHash: String? = null
) {
    val canPunch: Boolean
        get() = isSignedIn
                && isIntranetReachable
                && emailApprovalStatus == ApprovalStatus.APPROVED
                && deviceApprovalStatus == ApprovalStatus.APPROVED

    val blockReason: String?
        get() = when {
            !isSignedIn                                          -> "Not signed in with Google. Please sign in first."
            !isIntranetReachable                                 -> "Cannot reach the office server. Connect to the office WiFi and try again."
            emailApprovalStatus == ApprovalStatus.PENDING        -> "Your Google email is pending employer approval. Please wait."
            emailApprovalStatus == ApprovalStatus.REJECTED       -> "Your Google email was rejected by the employer. Contact your manager."
            emailApprovalStatus == ApprovalStatus.NONE           -> "Your Google email has not been submitted for approval. Tap 'Request Approval'."
            deviceApprovalStatus == ApprovalStatus.PENDING       -> "Your device is pending employer approval. Please wait."
            deviceApprovalStatus == ApprovalStatus.REJECTED      -> "Your device was rejected by the employer. Contact your manager."
            deviceApprovalStatus == ApprovalStatus.NONE          -> "Your device has not been submitted for approval. Tap 'Request Approval'."
            else                                                 -> null
        }
}

enum class ApprovalStatus {
    UNKNOWN,   // Not checked yet
    NONE,      // No request submitted
    PENDING,   // Request submitted, awaiting review
    APPROVED,  // Approved
    REJECTED   // Rejected by employer
}
