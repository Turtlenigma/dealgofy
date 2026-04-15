package com.turtlenigma.dealgofy

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
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
                // Optimistic display increment
                val tv = view.findViewById<TextView>(countId)
                tv.text = ((tv.text.toString().toIntOrNull() ?: 0) + 1).toString()
                host.onCircleTapped(i, CircleConfig.load(prefs, i))
            }
        }

        // ── Seen-count → animation speed ──────────────────────────────────────
        val seenCount = prefs.getInt(DeAlgofyAccessibilityService.PREFS_KEY_SEEN_COUNT, 0)
        prefs.edit()
            .putInt(DeAlgofyAccessibilityService.PREFS_KEY_SEEN_COUNT, seenCount + 1)
            .apply()
        // First 10 intercepts: 2 s pause between lines. After that: 0.5 s.
        val pauseMs = if (seenCount >= 10) 500L else 2000L

        startRevealSequence(view, circleViews, pauseMs)
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }

    // ── Animation ─────────────────────────────────────────────────────────────

    private fun startRevealSequence(
        view: View,
        circleViews: List<Triple<Int, Int, Int>>,
        pauseMs: Long
    ) {
        val tvWait  = view.findViewById<TextView>(R.id.tvWait)
        val tvGoals = view.findViewById<TextView>(R.id.tvGoals)
        val circles = circleViews.map { (id, _, _) ->
            view.findViewById<LinearLayout>(id)
        }

        // Set initial hidden state for all animated elements
        tvWait.alpha  = 0f
        tvGoals.alpha = 0f
        circles.forEach { c -> c.alpha = 0f; c.scaleX = 0.6f; c.scaleY = 0.6f }

        // 1 — "hey, wait a second!" fades in over 800 ms
        ObjectAnimator.ofFloat(tvWait, "alpha", 0f, 1f).apply {
            duration = 800
            start()
        }

        // 2 — pause, then "have you worked…" fades in over 600 ms
        handler.postDelayed({
            if (!isAdded) return@postDelayed
            ObjectAnimator.ofFloat(tvGoals, "alpha", 0f, 1f).apply {
                duration = 600
                start()
            }

            // 3 — circles pop in with 150 ms stagger once goals text is mostly visible
            handler.postDelayed({
                if (!isAdded) return@postDelayed
                circles.forEachIndexed { i, circle ->
                    handler.postDelayed({
                        if (!isAdded) return@postDelayed
                        popCircleIn(circle)
                    }, i * 150L)
                }
            }, 500L)
        }, 800L + pauseMs)
    }

    /**
     * Scale the circle from 0.6 → 1.0 with an overshoot bounce while fading
     * it in from invisible.
     */
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
