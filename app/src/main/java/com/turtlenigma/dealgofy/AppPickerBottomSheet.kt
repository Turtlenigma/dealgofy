package com.turtlenigma.dealgofy

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppPickerBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val ARG_RESULT_KEY = "result_key"
        const val RESULT_PACKAGE = "package"
        const val RESULT_LABEL   = "label"

        fun newInstance(resultKey: String) = AppPickerBottomSheet().apply {
            arguments = Bundle().apply { putString(ARG_RESULT_KEY, resultKey) }
        }
    }

    override fun getTheme() = R.style.DarkBottomSheetTheme

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_app_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv        = view.findViewById<RecyclerView>(R.id.rvApps)
        val etSearch  = view.findViewById<EditText>(R.id.etSearch)
        val resultKey = requireArguments().getString(ARG_RESULT_KEY)!!

        val adapter = AppPickerAdapter { pkg, label ->
            parentFragmentManager.setFragmentResult(resultKey, bundleOf(
                RESULT_PACKAGE to pkg,
                RESULT_LABEL   to label
            ))
            dismiss()
        }

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { loadApps() }
            adapter.setApps(apps)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { adapter.filter(s?.toString() ?: "") }
        })
    }

    private fun loadApps(): List<AppPickerAdapter.AppItem> {
        val pm = requireContext().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != requireContext().packageName }
            .map { ri ->
                AppPickerAdapter.AppItem(
                    packageName = ri.activityInfo.packageName,
                    label       = ri.loadLabel(pm).toString(),
                    icon        = ri.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
