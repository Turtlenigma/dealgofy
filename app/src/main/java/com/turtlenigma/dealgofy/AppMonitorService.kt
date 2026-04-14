package com.turtlenigma.dealgofy

import android.app.*
import android.content.Intent
import android.os.*
import android.app.usage.UsageStatsManager
import androidx.core.app.NotificationCompat

class AppMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var coinManager: CoinManager
    private var isShowingIntercept = false

    private val guardedApps = listOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.google.android.youtube"
    )

    private val activeSessions = mutableSetOf<String>()
    private val snoozedApps = mutableSetOf<String>()
    private val exitTimestamps = mutableMapOf<String, Long>()
    private val graceUsedSessions = mutableSetOf<String>()

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        coinManager = CoinManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val snoozePackage = intent?.getStringExtra("snooze_package")
        if (snoozePackage != null) {
            isShowingIntercept = false
            snoozeApp(snoozePackage)
            return START_STICKY
        }

        val interceptDismissed = intent?.getBooleanExtra("intercept_dismissed", false) ?: false
        if (interceptDismissed) {
            isShowingIntercept = false
            return START_STICKY
        }

        val updateNotification = intent?.getBooleanExtra("update_notification", false) ?: false
        if (updateNotification) {
            updateNotification()
            return START_STICKY
        }

        startForeground(1, buildNotification())
        handler.post(checkRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun checkForegroundApp() {
        if (isShowingIntercept) return

        val ownPackage = packageName

        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, time - 5000, time
        )
        val foreground = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
            ?: return

        if (foreground == ownPackage) return

        if (foreground in guardedApps) {
            if (foreground in activeSessions) {
                if (foreground in exitTimestamps) {
                    exitTimestamps.remove(foreground)
                    graceUsedSessions.add(foreground)
                }
                return
            }

            if (foreground in snoozedApps) return

            isShowingIntercept = true
            activeSessions.add(foreground)
            showIntercept(foreground)
            return
        }

        val iterator = activeSessions.iterator()
        while (iterator.hasNext()) {
            val sessionApp = iterator.next()
            if (sessionApp != foreground) {
                if (sessionApp in graceUsedSessions) {
                    iterator.remove()
                    graceUsedSessions.remove(sessionApp)
                    exitTimestamps.remove(sessionApp)
                } else {
                    val exitTime = exitTimestamps[sessionApp]
                    if (exitTime == null) {
                        exitTimestamps[sessionApp] = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - exitTime > 30000) {
                        iterator.remove()
                        exitTimestamps.remove(sessionApp)
                    }
                }
            }
        }
    }

    private fun showIntercept(packageName: String) {
        val intent = Intent(this, InterceptActivity::class.java).apply {
            putExtra("package_name", packageName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_HISTORY
            )
        }
        startActivity(intent)
    }

    fun snoozeApp(packageName: String) {
        snoozedApps.add(packageName)
        activeSessions.remove(packageName)
        exitTimestamps.remove(packageName)
        graceUsedSessions.remove(packageName)
        handler.postDelayed({
            snoozedApps.remove(packageName)
        }, 5000)
    }

    fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, buildNotification())
    }

    private fun buildNotification(): Notification {
        val channelId = "monitor"
        val channel = NotificationChannel(
            channelId, "DeAlgofy Monitor", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("DeAlgofy is active")
            .setContentText("⬡ ${"%.1f".format(coinManager.getBalance())} coins remaining")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }
}