package com.turtlenigma.dealgofy

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

class InterceptPanel1Fragment : Fragment() {

    companion object {
        private const val ARG_TARGET_PACKAGE = "target_package"

        fun newInstance(targetPackage: String) = InterceptPanel1Fragment().apply {
            arguments = Bundle().apply { putString(ARG_TARGET_PACKAGE, targetPackage) }
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_intercept_panel1, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = requireActivity() as InterceptActivity
        val prefs = requireContext()
            .getSharedPreferences(DeAlgofyAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()

        // ── Exit hierarchy ────────────────────────────────────────────────────
        view.findViewById<TextView>(R.id.tvEnterApp).apply {
            text = "I want to use ${host.getAppName()} right now"
            setOnClickListener { host.enterApp() }
        }
        view.findViewById<Button>(R.id.btnGoHome).setOnClickListener { host.goHome() }

        // ── Circle names ──────────────────────────────────────────────────────
        val circleViews = listOf(
            Triple(R.id.circle1, R.id.tvCircle1Name, R.id.tvCircle1Count),
            Triple(R.id.circle2, R.id.tvCircle2Name, R.id.tvCircle2Count),
            Triple(R.id.circle3, R.id.tvCircle3Name, R.id.tvCircle3Count)
        )

        circleViews.forEachIndexed { i, (_, nameId, _) ->
            view.findViewById<TextView>(nameId).text = CircleConfig.load(prefs, i).name
        }

        // ── Today's tap counts (Room) ─────────────────────────────────────────
        lifecycleScope.launch {
            val db = AppDatabase.get(requireContext())
            circleViews.forEachIndexed { i, (_, _, countId) ->
                val count = withContext(Dispatchers.IO) {
                    db.circleTapCountDao().getCount(today, i) ?: 0
                }
                view.findViewById<TextView>(countId).text = count.toString()
            }
        }

        // ── Circle tap dispatch ───────────────────────────────────────────────
        circleViews.forEachIndexed { i, (circleId, _, countId) ->
            view.findViewById<LinearLayout>(circleId).setOnClickListener {
                val tv = view.findViewById<TextView>(countId)
                tv.text = ((tv.text.toString().toIntOrNull() ?: 0) + 1).toString()
                host.onCircleTapped(i, CircleConfig.load(prefs, i))
            }
        }

        // ── Usage stats ───────────────────────────────────────────────────────
        loadUsageStats(view, host.targetPackage, host.getAppName())

        // ── Seen-count → animation speed ──────────────────────────────────────
        val seenCount = prefs.getInt(DeAlgofyAccessibilityService.PREFS_KEY_SEEN_COUNT, 0)
        prefs.edit()
            .putInt(DeAlgofyAccessibilityService.PREFS_KEY_SEEN_COUNT, seenCount + 1)
            .apply()
        val pauseMs = if (seenCount >= 10) 500L else 2000L

        startRevealSequence(view, circleViews, pauseMs)
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    private fun loadUsageStats(view: View, pkg: String, appName: String) {
        val tvOpened = view.findViewById<TextView>(R.id.tvOpenedToday)
        val tvTime   = view.findViewById<TextView>(R.id.tvTimeToday)

        lifecycleScope.launch {
            val dayStartMs = dayStartMillis()

            // Open count from Room
            val openCount = withContext(Dispatchers.IO) {
                AppDatabase.get(requireContext())
                    .interceptEventDao()
                    .enterAppCountToday(pkg, dayStartMs)
            }

            // Screen time from UsageStatsManager
            val timeStr = withContext(Dispatchers.IO) {
                usageTimeString(pkg, dayStartMs)
            }

            tvOpened.text = "$appName opened today: $openCount"
            tvTime.text   = "Time spent on $appName today: $timeStr"
        }
    }

    private fun dayStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun usageTimeString(pkg: String, dayStartMs: Long): String {
        val usm = requireContext()
            .getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            dayStartMs,
            System.currentTimeMillis()
        )
        val totalMs = stats?.find { it.packageName == pkg }?.totalTimeInForeground ?: 0L
        val totalMinutes = (totalMs / 60_000).toInt()
        val hours = totalMinutes / 60
        val mins  = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            totalMinutes > 0 -> "${mins}m"
            else -> "0m"
        }
    }

    // ── Animation ─────────────────────────────────────────────────────────────

    private fun startRevealSequence(
        view: View,
        circleViews: List<Triple<Int, Int, Int>>,
        pauseMs: Long
    ) {
        val tvWait     = view.findViewById<TextView>(R.id.tvWait)
        val tvGoals    = view.findViewById<TextView>(R.id.tvGoals)
        val tvOpened   = view.findViewById<TextView>(R.id.tvOpenedToday)
        val tvTime     = view.findViewById<TextView>(R.id.tvTimeToday)
        val btnGoHome  = view.findViewById<Button>(R.id.btnGoHome)
        val tvEnterApp = view.findViewById<TextView>(R.id.tvEnterApp)
        val circles    = circleViews.map { (id, _, _) ->
            view.findViewById<LinearLayout>(id)
        }

        listOf(tvWait, tvGoals, tvOpened, tvTime, btnGoHome, tvEnterApp)
            .forEach { it.alpha = 0f }
        circles.forEach { c -> c.alpha = 0f; c.scaleX = 0.6f; c.scaleY = 0.6f }

        // Tunables
        val initialDelay  = 400L
        val waitFadeMs    = 800L
        val fadeMs        = 600L
        val circleStagger = 220L
        val circlePopMs   = 400L
        val tailPause     = 700L  // pause between circles → stats → button → enter

        fun fadeIn(v: View, durationMs: Long = fadeMs) {
            ObjectAnimator.ofFloat(v, "alpha", 0f, 1f).apply {
                duration = durationMs
                start()
            }
        }

        fun later(delay: Long, block: () -> Unit) {
            handler.postDelayed({ if (isAdded) block() }, delay)
        }

        var t = initialDelay

        // 1 — "hey, wait a second!"
        later(t) { fadeIn(tvWait, waitFadeMs) }
        t += waitFadeMs + pauseMs

        // 2 — "have you worked towards your goals today?"
        later(t) { fadeIn(tvGoals) }
        t += fadeMs + pauseMs

        // 3 — circles plop in one by one
        circles.forEachIndexed { i, circle ->
            later(t + i * circleStagger) { popCircleIn(circle) }
        }
        t += (circles.size - 1) * circleStagger + circlePopMs + tailPause

        // 4 — stats lines + "Go back to home screen" all pop up together
        later(t) {
            fadeIn(tvOpened)
            fadeIn(tvTime)
            fadeIn(btnGoHome)
        }
        t += fadeMs + tailPause

        // 5 — "I want to use [App] right now"
        later(t) { fadeIn(tvEnterApp) }
    }

    private fun popCircleIn(circle: View) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(circle, "scaleX", 0.6f, 1f),
                ObjectAnimator.ofFloat(circle, "scaleY", 0.6f, 1f),
                ObjectAnimator.ofFloat(circle, "alpha",  0f,   1f)
            )
            duration     = 400
            interpolator = OvershootInterpolator(1.5f)
            start()
        }
    }
}
