package com.turtlenigma.dealgofy

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class DeAlgofyAccessibilityService : AccessibilityService() {

    // True while InterceptActivity is on screen — suppresses re-triggering.
    private var isInterceptShowing = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dateChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val today = LocalDate.now().toString()
            serviceScope.launch {
                AppDatabase.get(context).circleTapCountDao().deleteOldEntries(today)
            }
        }
    }

    companion object {
        // Weak reference used by InterceptActivity to signal dismissal.
        var instance: DeAlgofyAccessibilityService? = null

        const val PREFS_NAME = "dealgofy_prefs"
        const val PREFS_KEY_GUARDED_APPS = "guarded_apps"

        // SharedPreferences keys for per-circle configuration
        fun circleNameKey(i: Int) = "circle_${i}_name"
        fun circleTypeKey(i: Int) = "circle_${i}_type"
        fun circleAppKey(i: Int) = "circle_${i}_app"

        /** Lifetime intercept count; drives text-reveal speed after 10 views. */
        const val PREFS_KEY_SEEN_COUNT = "intercept_seen_count"
    }

    override fun onServiceConnected() {
        instance = this
        registerReceiver(dateChangedReceiver, IntentFilter(Intent.ACTION_DATE_CHANGED))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Ignore our own windows (MainActivity, InterceptActivity, etc.)
        if (pkg == packageName) return

        // Don't stack intercepts.
        if (isInterceptShowing) return

        val guardedApps = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(PREFS_KEY_GUARDED_APPS, emptySet()) ?: emptySet()

        if (pkg !in guardedApps) return

        isInterceptShowing = true
        showIntercept(pkg)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        runCatching { unregisterReceiver(dateChangedReceiver) }
        instance = null
        super.onDestroy()
    }

    /** Called by InterceptActivity when it is fully dismissed. */
    fun onInterceptDismissed() {
        isInterceptShowing = false
    }

    /**
     * Lock the device via the accessibility global action.
     * Requires API 28+; silently does nothing on older devices.
     */
    fun lockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    private fun showIntercept(packageName: String) {
        val intent = Intent(this, InterceptActivity::class.java).apply {
            putExtra(InterceptActivity.EXTRA_PACKAGE_NAME, packageName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            )
        }
        startActivity(intent)
    }
}
