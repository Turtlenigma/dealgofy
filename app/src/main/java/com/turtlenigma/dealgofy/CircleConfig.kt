package com.turtlenigma.dealgofy

import android.content.SharedPreferences

/**
 * Runtime snapshot of one circle's configuration loaded from SharedPreferences.
 * Persisted by the main screen (step 8); replaced by Room-backed config in step 5.
 */
data class CircleConfig(
    val name: String,
    val actionType: CircleActionType,
    /** Package name of the app to launch — only meaningful for PRODUCTIVE_APP. */
    val linkedApp: String?
) {
    companion object {
        fun load(prefs: SharedPreferences, index: Int): CircleConfig {
            val name = prefs.getString(
                DeAlgofyAccessibilityService.circleNameKey(index), "Goal ${index + 1}"
            ) ?: "Goal ${index + 1}"

            val typeStr = prefs.getString(
                DeAlgofyAccessibilityService.circleTypeKey(index),
                CircleActionType.LOCK_SCREEN.name
            ) ?: CircleActionType.LOCK_SCREEN.name

            val type = try {
                CircleActionType.valueOf(typeStr)
            } catch (_: IllegalArgumentException) {
                CircleActionType.LOCK_SCREEN
            }

            val linkedApp = prefs.getString(
                DeAlgofyAccessibilityService.circleAppKey(index), null
            )

            return CircleConfig(name, type, linkedApp)
        }
    }
}
