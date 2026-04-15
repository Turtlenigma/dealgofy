package com.turtlenigma.dealgofy

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class InterceptActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
    }

    private lateinit var viewPager: ViewPager2
    var targetPackage: String = ""
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intercept)

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""

        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = InterceptPagerAdapter(this, targetPackage)

        findViewById<LinearLayout>(R.id.tabAppUsage).setOnClickListener {
            viewPager.currentItem = 1
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewPager.currentItem > 0) viewPager.currentItem = 0 else goHome()
            }
        })
    }

    override fun onDestroy() {
        DeAlgofyAccessibilityService.instance?.onInterceptDismissed()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Public exit paths — called by fragments
    // -------------------------------------------------------------------------

    /** "Go back to home screen" button — deflection, not entering the app. */
    fun goHome() {
        recordExit(ExitType.GO_HOME)
        dismissAndNavigateHome()
    }

    /** "I want to use [AppName] right now" — the user chose to enter the guarded app. */
    fun enterApp() {
        recordExit(ExitType.ENTER_APP)
        DeAlgofyAccessibilityService.instance?.onInterceptDismissed()
        launchPackage(targetPackage)
        finish()
    }

    /**
     * A circle was tapped. Writes the tap count increment + exit event to Room
     * (sequentially, on IO), then dispatches the configured action.
     * Focus mode is the exception: the exit event is recorded later by
     * onFocusModeConfirmed(), when the user actually confirms the duration.
     */
    fun onCircleTapped(circleIndex: Int, config: CircleConfig) {
        val exitType = circleExitType(circleIndex)
        val today = LocalDate.now().toString()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.get(applicationContext)
                db.circleTapCountDao().incrementTap(today, circleIndex)
                if (config.actionType != CircleActionType.FOCUS_MODE) {
                    db.interceptEventDao().insert(
                        InterceptEvent(triggeredPackage = targetPackage, exitType = exitType)
                    )
                }
            }

            when (config.actionType) {
                CircleActionType.PRODUCTIVE_APP -> {
                    DeAlgofyAccessibilityService.instance?.onInterceptDismissed()
                    config.linkedApp?.let { launchPackage(it) }
                    finish()
                }
                CircleActionType.FOCUS_MODE -> {
                    FocusBottomSheet.newInstance(circleIndex)
                        .show(supportFragmentManager, "focus_sheet")
                }
                CircleActionType.LOCK_SCREEN -> {
                    DeAlgofyAccessibilityService.instance?.onInterceptDismissed()
                    DeAlgofyAccessibilityService.instance?.lockScreen()
                    finish()
                }
            }
        }
    }

    /**
     * Called by FocusBottomSheet when the user confirms a duration.
     * Records the exit event with the chosen duration then navigates home.
     */
    fun onFocusModeConfirmed(circleIndex: Int, durationMinutes: Int) {
        recordExit(circleExitType(circleIndex), focusDuration = durationMinutes)
        dismissAndNavigateHome()
    }

    /** Human-readable display name for the intercepted package. */
    fun getAppName(): String = try {
        val info = packageManager.getApplicationInfo(targetPackage, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        targetPackage
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun recordExit(exitType: ExitType, focusDuration: Int? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.get(applicationContext).interceptEventDao().insert(
                InterceptEvent(
                    triggeredPackage = targetPackage,
                    exitType = exitType,
                    focusDuration = focusDuration
                )
            )
        }
    }

    private fun dismissAndNavigateHome() {
        DeAlgofyAccessibilityService.instance?.onInterceptDismissed()
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }

    private fun launchPackage(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.also {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(it)
        }
    }

    private fun circleExitType(index: Int) = when (index) {
        0 -> ExitType.CIRCLE_1
        1 -> ExitType.CIRCLE_2
        else -> ExitType.CIRCLE_3
    }
}
