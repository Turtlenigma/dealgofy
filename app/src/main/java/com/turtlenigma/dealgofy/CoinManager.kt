package com.turtlenigma.dealgofy

import android.content.Context

class CoinManager(context: Context) {

    private val prefs = context.getSharedPreferences("dealgofy_prefs", Context.MODE_PRIVATE)

    companion object {
        const val STARTING_COINS = 100f
        private const val KEY_COINS = "coin_balance"
    }

    fun getBalance(): Float {
        return prefs.getFloat(KEY_COINS, STARTING_COINS)
    }

    fun spendCoin(): Boolean {
        val current = getBalance()
        if (current < 1f) return false
        prefs.edit().putFloat(KEY_COINS, current - 1f).apply()
        return true
    }

    fun resetWeekly() {
        prefs.edit().putFloat(KEY_COINS, STARTING_COINS).apply()
    }
}