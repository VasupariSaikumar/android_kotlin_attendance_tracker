package com.technikh.employeeattendancetracking.utils

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Generates a stable, privacy-safe device fingerprint.
 * Uses ANDROID_ID hashed with SHA-256 — never stores the raw ID.
 */
object DeviceUtils {

    /**
     * Returns a SHA-256 hash of the device's ANDROID_ID.
     * This is stable across app reinstalls (on same device) and unique per device.
     */
    fun getDeviceIdHash(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"

        return MessageDigest.getInstance("SHA-256")
            .digest(androidId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns a shortened 16-char version for display purposes only.
     */
    fun getShortDeviceId(context: Context): String {
        return getDeviceIdHash(context).take(16)
    }
}
