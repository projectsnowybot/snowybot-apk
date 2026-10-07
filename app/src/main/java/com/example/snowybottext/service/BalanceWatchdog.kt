package com.example.snowybottext.service

/** Clock policy driven exclusively by valid observed website-balance changes. */
internal class BalanceWatchdog(
    private val timeoutMs: Long = JustDiceBotService.STALL_TIMEOUT_MS,
) {
    private var running = false
    private var lastBalance: Double? = null
    private var lastChangeAtMs = 0L
    private var reloadPending = false

    fun start(balance: Double, nowMs: Long) {
        running = true
        lastBalance = balance.takeIf { it.isFinite() && it > 0.0 }
        // An invalid baseline cannot itself prove a change; keep the timer anchored to Run.
        lastChangeAtMs = nowMs
        reloadPending = false
    }

    /** Returns true only when a valid positive walletStash differs from its baseline. */
    fun observeBalance(balance: Double, nowMs: Long): Boolean {
        if (!running || !balance.isFinite() || balance <= 0.0) return false
        val prior = lastBalance ?: return false
        if (prior == balance) return false
        lastBalance = balance
        lastChangeAtMs = nowMs
        return true
    }

    fun shouldReload(nowMs: Long): Boolean {
        if (!running || reloadPending || nowMs - lastChangeAtMs < timeoutMs) return false
        reloadPending = true
        return true
    }

    fun recoverySucceeded(balance: Double, nowMs: Long): Boolean {
        if (!running || !reloadPending || !balance.isFinite() || balance <= 0.0) return false
        reloadPending = false
        lastBalance = balance
        lastChangeAtMs = nowMs
        return true
    }

    fun recoveryFailed() {
        running = false
        reloadPending = false
    }

    fun stop() {
        running = false
        reloadPending = false
    }

    fun isRunning(): Boolean = running
    fun isReloadPending(): Boolean = reloadPending
}
