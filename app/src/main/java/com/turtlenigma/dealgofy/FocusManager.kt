package com.turtlenigma.dealgofy

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * Manages the full focus-mode lifecycle:
 *  - startFocus: save brightness, enable DND, dim screen, arm alarm
 *  - endFocus: restore brightness, disable DND, fire completion notification
 */
object FocusManager {

    const val ACTION_FOCUS_END = "com.turtlenigma.dealgofy.FOCUS_END"

    private const val ALARM_REQUEST_CODE = 1001
    private const val NOTIF_CHANNEL_ID = "focus_end"
    private const val NOTIF_ID = 2

    private const val PREF_PRE_FOCUS_BRIGHTNESS = "pre_focus_brightness"
    private const val PREF_PRE_FOCUS_BRIGHTNESS_MODE = "pre_focus_brightness_mode"

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun startFocus(context: Context, durationMinutes: Int) {
        saveBrightness(context)
        applyDim(context)
        enableDnd(context)
        scheduleAlarm(context, durationMinutes)
    }

    fun endFocus(context: Context) {
        disableDnd(context)
        restoreBrightness(context)
        fireCompletionNotification(context)
    }

    // -------------------------------------------------------------------------
    // Brightness
    // -------------------------------------------------------------------------

    private fun saveBrightness(context: Context) {
        val cr = context.contentResolver
        val mode = Settings.System.getInt(
            cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        val level = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        context.getSharedPreferences(DeAlgofyAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_PRE_FOCUS_BRIGHTNESS, level)
            .putInt(PREF_PRE_FOCUS_BRIGHTNESS_MODE, mode)
            .apply()
    }

    private fun applyDim(context: Context) {
        if (!Settings.System.canWrite(context)) return
        val cr = context.contentResolver
        // Switch to manual so our brightness value takes effect
        Settings.System.putInt(
            cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 0)
    }

    private fun restoreBrightness(context: Context) {
        if (!Settings.System.canWrite(context)) return
        val prefs = context.getSharedPreferences(
            DeAlgofyAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE
        )
        val level = prefs.getInt(PREF_PRE_FOCUS_BRIGHTNESS, 128)
        val mode = prefs.getInt(
            PREF_PRE_FOCUS_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        val cr = context.contentResolver
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, level)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
    }

    // -------------------------------------------------------------------------
    // DND
    // -------------------------------------------------------------------------

    private fun enableDnd(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }
    }

    private fun disableDnd(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    // -------------------------------------------------------------------------
    // Alarm
    // -------------------------------------------------------------------------

    private fun scheduleAlarm(context: Context, durationMinutes: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = buildAlarmPendingIntent(context)
        val triggerAt = System.currentTimeMillis() + durationMinutes * 60_000L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // Exact alarms not permitted on this device; use inexact Doze-safe alarm
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** Cancel any pending focus alarm (e.g. if the user exits focus mode early). */
    fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(buildAlarmPendingIntent(context))
    }

    private fun buildAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(ACTION_FOCUS_END).apply {
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // -------------------------------------------------------------------------
    // Completion notification
    // -------------------------------------------------------------------------

    private fun fireCompletionNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(NOTIF_CHANNEL_ID, "Focus Mode", NotificationManager.IMPORTANCE_HIGH)
        )
        val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setContentTitle("Focus session complete")
            .setContentText("Nice work — your focus time is up.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, notif)
    }
}
