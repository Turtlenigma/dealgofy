package com.turtlenigma.dealgofy

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class InterceptPagerAdapter(
    activity: FragmentActivity,
    private val targetPackage: String
) : FragmentStateAdapter(activity) {

    override fun getItemCount() = 2

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> InterceptPanel1Fragment.newInstance(targetPackage)
        1 -> InterceptPanel2Fragment.newInstance()
        else -> throw IllegalArgumentException("Unknown page $position")
    }
}
