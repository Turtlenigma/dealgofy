package com.turtlenigma.dealgofy

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class InterceptActivity : AppCompatActivity() {

    private lateinit var coinManager: CoinManager
    private var targetPackage: String = ""
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intercept)

        coinManager = CoinManager(this)
        targetPackage = intent.getStringExtra("package_name") ?: ""

        val tvBalance = findViewById<TextView>(R.id.tvBalance)
        val tvQuestion = findViewById<TextView>(R.id.tvAppName)
        val btnEnter = findViewById<Button>(R.id.btnEnter)
        val btnBack = findViewById<Button>(R.id.btnBack)

        tvBalance.text = "⬡ ${"%.1f".format(coinManager.getBalance())} coins remaining"
        tvQuestion.text = "Do you really want to spend 1 coin to enter ${getFriendlyName(targetPackage)}?"

        btnEnter.setOnClickListener {
            btnEnter.isEnabled = false

            val spent = coinManager.spendCoin()
            if (spent) {
                updateNotification()

                val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                val appTask = activityManager.appTasks.firstOrNull {
                    it.taskInfo.baseActivity?.packageName == targetPackage
                }

                if (appTask != null) {
                    appTask.moveToFront()
                } else {
                    val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                    if (launchIntent != null) {
                        launchIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        )
                        startActivity(launchIntent)
                    }
                }

                handler.postDelayed({
                    moveTaskToBack(true)
                }, 800)

            } else {
                tvBalance.text = "No coins left"
                btnEnter.isEnabled = false
            }
        }

        btnBack.setOnClickListener {
            snoozeAndGoHome()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                snoozeAndGoHome()
            }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        val serviceIntent = Intent(this, AppMonitorService::class.java)
        serviceIntent.putExtra("intercept_dismissed", true)
        startService(serviceIntent)
        super.onDestroy()
    }

    private fun updateNotification() {
        val serviceIntent = Intent(this, AppMonitorService::class.java)
        serviceIntent.putExtra("update_notification", true)
        startService(serviceIntent)
    }

    private fun snoozeAndGoHome() {
        val serviceIntent = Intent(this, AppMonitorService::class.java)
        serviceIntent.putExtra("snooze_package", targetPackage)
        startService(serviceIntent)

        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(home)
        finish()
    }

    private fun getFriendlyName(pkg: String): String {
        return when (pkg) {
            "com.instagram.android" -> "Instagram"
            "com.zhiliaoapp.musically" -> "TikTok"
            "com.google.android.youtube" -> "YouTube"
            else -> pkg
        }
    }
}