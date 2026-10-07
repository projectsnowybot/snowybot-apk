package com.example.snowybottext.service

import com.example.snowybottext.engine.PeanutEngine

/** Deterministic wallet threshold and once-only event gate. */
internal object ProfitResetPolicy {
    const val TARGET_FRACTION = 0.10

    fun isTargetReached(startingPocketChange: Double, walletStash: Double): Boolean {
        if (!startingPocketChange.isFinite() || startingPocketChange <= 0.0 || !walletStash.isFinite()) return false
        val threshold = PeanutEngine.round8(startingPocketChange * 1.10)
        return walletStash >= threshold - 1e-9
    }

    fun freshBaseBet(balance: Double): Double? {
        if (!balance.isFinite() || balance <= 0.0) return null
        val amount = PeanutEngine.round8(balance / 1_440_000.0)
        return amount.takeIf { it.isFinite() && it > 0.0 }
    }

    class Once {
        private var triggered = false
        fun reset() { triggered = false }
        fun hasTriggered(): Boolean = triggered
        fun triggerIfTargetReached(startingPocketChange: Double, walletStash: Double): Boolean {
            if (triggered || !isTargetReached(startingPocketChange, walletStash)) return false
            triggered = true
            return true
        }
    }

    class RefreshGate {
        private var refreshing = false
        fun begin(): Boolean {
            if (refreshing) return false
            refreshing = true
            return true
        }
        fun complete() { refreshing = false }
    }
}
