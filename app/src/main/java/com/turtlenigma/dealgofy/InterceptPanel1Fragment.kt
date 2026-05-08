package com.turtlenigma.dealgofy

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class InterceptPanel1Fragment : Fragment() {

    companion object {
        private const val ARG_TARGET_PACKAGE = "target_package"

        fun newInstance(targetPackage: String) = InterceptPanel1Fragment().apply {
            arguments = Bundle().apply { putString(ARG_TARGET_PACKAGE, targetPackage) }
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    // ── Floating-circle physics ──────────────────────────────────────────────
    private data class Body(
        val view: View,
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float
    )

    private val bodies = mutableListOf<Body>()
    private val choreographer = Choreographer.getInstance()
    private var lastFrameNs = 0L
    private var floatRunning = false
    private var playArea: FrameLayout? = null

    private val floatTick = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!floatRunning || !isAdded) return
            if (lastFrameNs != 0L) {
                val dt = ((frameTimeNanos - lastFrameNs) / 1_000_000_000f)
                    .coerceAtMost(1f / 30f)
                stepFloat(dt)
            }
            lastFrameNs = frameTimeNanos
            choreographer.postFrameCallback(this)
        }
    }

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

        // ── Float physics setup (positions become valid after layout) ─────────
        val container = view.findViewById<FrameLayout>(R.id.circlesContainer)
        playArea = container
        val circles = circleViews.map { (id, _, _) -> view.findViewById<View>(id) }
        container.doOnLayout { setupFloatBodies(circles) }

        // ── Seen-count → animation speed ──────────────────────────────────────
        val seenCount = prefs.getInt(DeAlgofyAccessibilityService.PREFS_KEY_SEEN_COUNT, 0)
        prefs.edit()
            .putInt(DeAlgofyAccessibilityService.PREFS_KEY_SEEN_COUNT, seenCount + 1)
            .apply()
        val pauseMs = if (seenCount >= 10) 500L else 2000L

        startRevealSequence(view, circleViews, pauseMs)
    }

    override fun onDestroyView() {
        stopFloating()
        handler.removeCallbacksAndMessages(null)
        playArea = null
        bodies.clear()
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

        // 3 — circles plop in one by one, then start floating
        circles.forEachIndexed { i, circle ->
            later(t + i * circleStagger) { popCircleIn(circle) }
        }
        val circlesCompleteAt = t + (circles.size - 1) * circleStagger + circlePopMs
        later(circlesCompleteAt) { startFloating() }
        t = circlesCompleteAt + tailPause

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

    // ── Floating physics ─────────────────────────────────────────────────────

    /**
     * Place each circle in the same compact triangle as before (top-center,
     * bottom-left, bottom-right) sitting in the middle of the play area, and
     * seed each with a small random velocity so they begin to drift outward.
     * Called once after the play area has been measured.
     */
    private fun setupFloatBodies(circles: List<View>) {
        val container = playArea ?: return
        val w = container.width.toFloat()
        val h = container.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val s = circles[0].width.toFloat().takeIf { it > 0f } ?: return
        val density = resources.displayMetrics.density

        // Compact triangle, centered in the play area. Matches the previous
        // static layout: 12 dp vertical gap, 20 dp gap between bottom circles.
        val vGap = 12f * density
        val hGap = 20f * density
        val triH = 2f * s + vGap
        val triW = 2f * s + hGap
        val cx = w / 2f
        val cy = h / 2f
        val topX = cx - s / 2f
        val topY = cy - triH / 2f
        val botY = topY + s + vGap
        val botLX = cx - triW / 2f
        val botRX = botLX + s + hGap
        val starts = listOf(topX to topY, botLX to botY, botRX to botY)

        bodies.clear()
        starts.forEachIndexed { i, (px, py) ->
            // 12–20 dp/s — a slow, drifty pace that reads as "floating on water".
            val speed = (12f + Random.nextFloat() * 8f) * density
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            bodies.add(
                Body(
                    view = circles[i],
                    x = px,
                    y = py,
                    vx = speed * cos(angle),
                    vy = speed * sin(angle)
                )
            )
        }
        bodies.forEach { b ->
            b.view.translationX = b.x
            b.view.translationY = b.y
        }
    }

    private fun startFloating() {
        if (floatRunning || bodies.isEmpty()) return
        floatRunning = true
        lastFrameNs = 0L
        choreographer.postFrameCallback(floatTick)
    }

    private fun stopFloating() {
        floatRunning = false
        choreographer.removeFrameCallback(floatTick)
    }

    private fun stepFloat(dt: Float) {
        val container = playArea ?: return
        val w = container.width.toFloat()
        val h = container.height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Tiny random angle nudge per body so paths curve gently instead of
        // tracing perfectly straight lines until they hit a wall.
        val driftRadPerSec = 0.6f  // ~34°/s of slow wandering

        // 1. Integrate position with a small random angle drift.
        bodies.forEach { b ->
            val da = (Random.nextFloat() * 2f - 1f) * driftRadPerSec * dt
            val cosA = cos(da)
            val sinA = sin(da)
            val nvx = b.vx * cosA - b.vy * sinA
            val nvy = b.vx * sinA + b.vy * cosA
            b.vx = nvx
            b.vy = nvy

            b.x += b.vx * dt
            b.y += b.vy * dt
        }

        // 2. Circle-circle collisions (equal-mass elastic, treating each
        //    100 dp square as a circle of radius s/2 around its center).
        for (i in 0 until bodies.size) {
            for (j in i + 1 until bodies.size) {
                val a = bodies[i]
                val b = bodies[j]
                val r = a.view.width.toFloat() / 2f
                val acx = a.x + r
                val acy = a.y + r
                val bcx = b.x + r
                val bcy = b.y + r
                val dx = bcx - acx
                val dy = bcy - acy
                val minDist = 2f * r
                val distSq = dx * dx + dy * dy
                if (distSq < minDist * minDist && distSq > 0.0001f) {
                    val dist = sqrt(distSq)
                    val nx = dx / dist
                    val ny = dy / dist

                    // Push them apart so they don't sit overlapped.
                    val overlap = (minDist - dist) / 2f
                    a.x -= nx * overlap
                    a.y -= ny * overlap
                    b.x += nx * overlap
                    b.y += ny * overlap

                    // Swap the normal-component of velocity (equal masses).
                    val vrx = b.vx - a.vx
                    val vry = b.vy - a.vy
                    val vn = vrx * nx + vry * ny
                    if (vn < 0f) {
                        a.vx += vn * nx
                        a.vy += vn * ny
                        b.vx -= vn * nx
                        b.vy -= vn * ny
                    }
                }
            }
        }

        // 3. Wall collisions + commit translation.
        bodies.forEach { b ->
            val s = b.view.width.toFloat()
            if (b.x < 0f) { b.x = 0f; b.vx = -b.vx }
            if (b.y < 0f) { b.y = 0f; b.vy = -b.vy }
            if (b.x + s > w) { b.x = w - s; b.vx = -b.vx }
            if (b.y + s > h) { b.y = h - s; b.vy = -b.vy }

            b.view.translationX = b.x
            b.view.translationY = b.y
        }
    }
}
