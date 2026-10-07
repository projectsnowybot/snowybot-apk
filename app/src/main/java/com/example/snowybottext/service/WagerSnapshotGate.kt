package com.example.snowybottext.service

/**
 * Serializes wager submissions against wallet snapshots. A result callback can settle a wager
 * even when the displayed balance is unchanged; a balance-only settlement must be a real change.
 */
internal class WagerSnapshotGate {
    private var submittedSnapshot: Double? = null

    fun trySubmit(balanceSnapshot: Double): Boolean {
        if ((!balanceSnapshot.isFinite()) || (submittedSnapshot != null)) return false
        submittedSnapshot = balanceSnapshot
        return true
    }

    fun settleByChangedBalance(balance: Double): Boolean {
        val submitted = submittedSnapshot ?: return false
        if (!balance.isFinite() || balance == submitted) return false
        submittedSnapshot = null
        return true
    }

    fun settleByCompletedResult(): Boolean {
        if (submittedSnapshot == null) return false
        submittedSnapshot = null
        return true
    }
}
