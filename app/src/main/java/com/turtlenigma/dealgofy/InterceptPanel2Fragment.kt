package com.turtlenigma.dealgofy

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class InterceptPanel2Fragment : Fragment() {

    companion object {
        fun newInstance() = InterceptPanel2Fragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_intercept_panel2, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = requireActivity() as InterceptActivity

        val rv      = view.findViewById<RecyclerView>(R.id.rvUsage)
        val tvEmpty = view.findViewById<TextView>(R.id.tvUsageEmpty)

        rv.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<Button>(R.id.btnGoHome2).setOnClickListener { host.goHome() }

        loadUsageStats(rv, tvEmpty)
    }

    private fun loadUsageStats(rv: RecyclerView, tvEmpty: TextView) {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { queryTodayStats() }
            if (items.isEmpty()) {
                rv.visibility    = View.GONE
                tvEmpty.visibility = View.VISIBLE
            } else {
                rv.adapter       = UsageStatsAdapter(items)
                rv.visibility    = View.VISIBLE
                tvEmpty.visibility = View.GONE
            }
        }
    }

    /**
     * Returns guarded-app usage for the current calendar day, sorted by total
     * time descending. Runs on a background thread — call inside Dispatchers.IO.
     */
    private fun queryTodayStats(): List<Pair<String, Long>> {
        val context = requireContext()
        val prefs = context.getSharedPreferences(
            DeAlgofyAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE
        )
        val guardedApps = prefs.getStringSet(
            DeAlgofyAccessibilityService.PREFS_KEY_GUARDED_APPS, emptySet()
        ) ?: emptySet()

        if (guardedApps.isEmpty()) return emptyList()

        // Calendar day start in local time
        val dayStart = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, dayStart, System.currentTimeMillis()
        ) ?: return emptyList()

        return stats
            .filter { it.packageName in guardedApps && it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .mapNotNull { stat ->
                val label = try {
                    val info = context.packageManager.getApplicationInfo(stat.packageName, 0)
                    context.packageManager.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    null // app not installed; skip row
                }
                label?.let { Pair(it, stat.totalTimeInForeground) }
            }
    }
}
