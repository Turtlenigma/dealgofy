package com.turtlenigma.dealgofy

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val circleCardViews = arrayOfNulls<View>(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(DeAlgofyAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)

        // Register fragment result listeners before onStart
        for (i in 0..2) {
            supportFragmentManager.setFragmentResultListener("circle_app_$i", this) { _, bundle ->
                val pkg = bundle.getString(AppPickerBottomSheet.RESULT_PACKAGE) ?: return@setFragmentResultListener
                prefs.edit()
                    .putString(DeAlgofyAccessibilityService.circleAppKey(i), pkg)
                    .apply()
                refreshCircleCard(i)
            }
        }
        supportFragmentManager.setFragmentResultListener("guarded_app", this) { _, bundle ->
            val pkg = bundle.getString(AppPickerBottomSheet.RESULT_PACKAGE) ?: return@setFragmentResultListener
            val current = prefs.getStringSet(
                DeAlgofyAccessibilityService.PREFS_KEY_GUARDED_APPS, emptySet()
            )!!.toMutableSet()
            current.add(pkg)
            prefs.edit().putStringSet(DeAlgofyAccessibilityService.PREFS_KEY_GUARDED_APPS, current).apply()
            refreshGuardedApps()
        }

        setupCircleCards()
        setupGuardedApps()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    private fun refreshPermissions() {
        bindPermissionRow(R.id.dotAccessibility, R.id.btnGrantAccessibility, isAccessibilityEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        bindPermissionRow(R.id.dotUsage, R.id.btnGrantUsage, hasUsagePermission()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        bindPermissionRow(R.id.dotDnd, R.id.btnGrantDnd, hasDndPermission()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        bindPermissionRow(R.id.dotWriteSettings, R.id.btnGrantWriteSettings, hasWriteSettingsPermission()) {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        }

        val cardAlarm = findViewById<View>(R.id.cardAlarm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cardAlarm.visibility = View.VISIBLE
            bindPermissionRow(R.id.dotAlarm, R.id.btnGrantAlarm, hasExactAlarmPermission()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        } else {
            cardAlarm.visibility = View.GONE
        }
    }

    private fun bindPermissionRow(dotId: Int, btnId: Int, granted: Boolean, onGrant: () -> Unit) {
        val dot = findViewById<View>(dotId)
        val btn = findViewById<Button>(btnId)
        val color = if (granted) Color.parseColor("#c8f542") else Color.parseColor("#ff4444")
        (dot.background.mutate() as GradientDrawable).setColor(color)
        btn.visibility = if (granted) View.GONE else View.VISIBLE
        btn.setOnClickListener { onGrant() }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        return am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun hasDndPermission(): Boolean =
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted

    private fun hasWriteSettingsPermission(): Boolean =
        Settings.System.canWrite(this)

    private fun hasExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return (getSystemService(ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }

    // ── Circle configuration ─────────────────────────────────────────────────

    private fun setupCircleCards() {
        val container = findViewById<LinearLayout>(R.id.circlesContainer)
        val inflater  = LayoutInflater.from(this)
        for (i in 0..2) {
            val card = inflater.inflate(R.layout.item_circle_config, container, false)
            circleCardViews[i] = card
            container.addView(card)
            bindCircleCard(i, card)
        }
    }

    private fun refreshCircleCard(index: Int) {
        val card = circleCardViews[index] ?: return
        bindCircleCard(index, card)
    }

    private fun bindCircleCard(index: Int, card: View) {
        val config = CircleConfig.load(prefs, index)

        card.findViewById<TextView>(R.id.tvCircleLabel).text = "CIRCLE ${index + 1}"

        // Name — save on text change
        val et = card.findViewById<EditText>(R.id.etCircleName)
        et.setText(config.name)
        et.setSelection(et.text.length)
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.edit()
                    .putString(DeAlgofyAccessibilityService.circleNameKey(index), s?.toString()?.trim() ?: "")
                    .apply()
            }
        })

        val tvTypeApp   = card.findViewById<TextView>(R.id.tvTypeApp)
        val tvTypeFocus = card.findViewById<TextView>(R.id.tvTypeFocus)
        val tvTypeLock  = card.findViewById<TextView>(R.id.tvTypeLock)
        val rowLinkedApp     = card.findViewById<View>(R.id.rowLinkedApp)
        val tvLinkedAppName  = card.findViewById<TextView>(R.id.tvLinkedAppName)
        val btnPickApp       = card.findViewById<Button>(R.id.btnPickApp)

        val lime = Color.parseColor("#c8f542")
        val grey = Color.parseColor("#666666")

        fun applyTypeSelection(type: CircleActionType) {
            tvTypeApp.setTextColor(if (type == CircleActionType.PRODUCTIVE_APP) lime else grey)
            tvTypeFocus.setTextColor(if (type == CircleActionType.FOCUS_MODE) lime else grey)
            tvTypeLock.setTextColor(if (type == CircleActionType.LOCK_SCREEN) lime else grey)
            rowLinkedApp.visibility =
                if (type == CircleActionType.PRODUCTIVE_APP) View.VISIBLE else View.GONE
        }

        applyTypeSelection(config.actionType)

        // Linked app display name
        val linkedLabel = config.linkedApp?.let { pkg ->
            try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { pkg }
        }
        tvLinkedAppName.text = linkedLabel ?: "Tap to pick an app"

        tvTypeApp.setOnClickListener {
            prefs.edit()
                .putString(DeAlgofyAccessibilityService.circleTypeKey(index), CircleActionType.PRODUCTIVE_APP.name)
                .apply()
            applyTypeSelection(CircleActionType.PRODUCTIVE_APP)
        }
        tvTypeFocus.setOnClickListener {
            prefs.edit()
                .putString(DeAlgofyAccessibilityService.circleTypeKey(index), CircleActionType.FOCUS_MODE.name)
                .apply()
            applyTypeSelection(CircleActionType.FOCUS_MODE)
        }
        tvTypeLock.setOnClickListener {
            prefs.edit()
                .putString(DeAlgofyAccessibilityService.circleTypeKey(index), CircleActionType.LOCK_SCREEN.name)
                .apply()
            applyTypeSelection(CircleActionType.LOCK_SCREEN)
        }

        btnPickApp.setOnClickListener {
            AppPickerBottomSheet.newInstance("circle_app_$index")
                .show(supportFragmentManager, "app_picker_$index")
        }
        tvLinkedAppName.setOnClickListener {
            AppPickerBottomSheet.newInstance("circle_app_$index")
                .show(supportFragmentManager, "app_picker_$index")
        }
    }

    // ── Guarded apps ─────────────────────────────────────────────────────────

    private fun setupGuardedApps() {
        findViewById<Button>(R.id.btnAddGuardedApp).setOnClickListener {
            AppPickerBottomSheet.newInstance("guarded_app")
                .show(supportFragmentManager, "guarded_app_picker")
        }
        refreshGuardedApps()
    }

    private fun refreshGuardedApps() {
        val container = findViewById<LinearLayout>(R.id.guardedAppsContainer)
        val tvEmpty   = findViewById<TextView>(R.id.tvNoGuardedApps)
        val guardedApps = prefs.getStringSet(
            DeAlgofyAccessibilityService.PREFS_KEY_GUARDED_APPS, emptySet()
        )!!

        container.removeAllViews()

        if (guardedApps.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            return
        }
        tvEmpty.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        guardedApps.sorted().forEach { pkg ->
            val row       = inflater.inflate(R.layout.item_guarded_app, container, false)
            val tvName    = row.findViewById<TextView>(R.id.tvGuardedAppName)
            val tvPkg     = row.findViewById<TextView>(R.id.tvGuardedPackage)
            val ivIcon    = row.findViewById<ImageView>(R.id.ivGuardedAppIcon)
            val btnRemove = row.findViewById<View>(R.id.btnRemoveGuardedApp)

            try {
                val info = packageManager.getApplicationInfo(pkg, 0)
                tvName.text = packageManager.getApplicationLabel(info).toString()
                ivIcon.setImageDrawable(packageManager.getApplicationIcon(info))
            } catch (_: Exception) {
                tvName.text = pkg
            }
            tvPkg.text = pkg

            btnRemove.setOnClickListener {
                val current = prefs.getStringSet(
                    DeAlgofyAccessibilityService.PREFS_KEY_GUARDED_APPS, emptySet()
                )!!.toMutableSet()
                current.remove(pkg)
                prefs.edit()
                    .putStringSet(DeAlgofyAccessibilityService.PREFS_KEY_GUARDED_APPS, current)
                    .apply()
                refreshGuardedApps()
            }

            container.addView(row)
        }
    }
}
