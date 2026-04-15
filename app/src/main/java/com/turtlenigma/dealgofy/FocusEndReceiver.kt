package com.turtlenigma.dealgofy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Triggered by the AlarmManager when a focus session ends.
 * Restores DND and brightness, then fires the completion notification.
 */
class FocusEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == FocusManager.ACTION_FOCUS_END) {
            FocusManager.endFocus(context)
        }
    }
}
