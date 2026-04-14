package com.turtlenigma.dealgofy

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri

class MainActivity : AppCompatActivity() {

    private lateinit var coinManager: CoinManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        coinManager = CoinManager(this)

        val tvBalance = findViewById<TextView>(R.id.tvBalance)
        val btnRequestPermission = findViewById<Button>(R.id.btnRequestPermission)
        val btnStartService = findViewById<Button>(R.id.btnStartService)
        val btnRequestOverlay = findViewById<Button>(R.id.btnRequestOverlay)

        tvBalance.text = "⬡ ${coinManager.getBalance()} coins remaining"

        btnRequestPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
        }

        btnRequestOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        btnStartService.setOnClickListener {
            if (hasUsagePermission()) {
                val serviceIntent = Intent(this, AppMonitorService::class.java)
                startForegroundService(serviceIntent)
                btnStartService.text = "DeAlgofy is running"
                btnStartService.isEnabled = false
            } else {
                btnRequestPermission.text = "Grant permission first"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val tvBalance = findViewById<TextView>(R.id.tvBalance)
        tvBalance.text = "⬡ ${coinManager.getBalance()} coins remaining"
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }
    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}