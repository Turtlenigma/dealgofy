package com.turtlenigma.dealgofy

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppPickerAdapter(
    private val onAppSelected: (packageName: String, label: String) -> Unit
) : RecyclerView.Adapter<AppPickerAdapter.VH>() {

    data class AppItem(val packageName: String, val label: String, val icon: Drawable)

    private var fullList: List<AppItem> = emptyList()
    private var filteredList: List<AppItem> = emptyList()

    fun setApps(apps: List<AppItem>) {
        fullList = apps
        filteredList = apps
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isBlank()) fullList
        else fullList.filter {
            it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = filteredList[position]
        holder.ivIcon.setImageDrawable(item.icon)
        holder.tvName.text = item.label
        holder.tvPackage.text = item.packageName
        holder.itemView.setOnClickListener { onAppSelected(item.packageName, item.label) }
    }

    override fun getItemCount() = filteredList.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivIcon: ImageView  = v.findViewById(R.id.ivAppIcon)
        val tvName: TextView   = v.findViewById(R.id.tvAppName)
        val tvPackage: TextView = v.findViewById(R.id.tvPackageName)
    }
}
