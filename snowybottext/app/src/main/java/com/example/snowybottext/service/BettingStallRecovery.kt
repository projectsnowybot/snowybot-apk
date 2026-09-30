package com.example.snowybottext.service

/** Legacy wager-progress watchdog policy retained only for source compatibility. */
@Deprecated("Use BalanceWatchdog, which observes only valid #pct_balance changes")
internal class BettingStallRecovery(
    private val timeoutMs: Long = JustDiceBotService.STALL_TIMEOUT_MS,
    private val maxReloadAttempts: Int = DEFAULT_MAX_RELOAD_ATTEMPTS,
    initialTimeMs: Long,
) {
    enum class Action { WAIT, RELOAD_EXISTING_WEBVIEW, HALT }

    private var lastCompletedBetAtMs = initialTimeMs
    private var runStarted = true
    private var reloadPending = false
    private var haltForLogin = false
    private var reloadAttempts = 0

    fun pauseUntilRun() { runStarted = false }
    fun onRunStarted(nowMs: Long) { runStarted = true; lastCompletedBetAtMs = nowMs }
    fun onCompletedBet(nowMs: Long) { if (runStarted) lastCompletedBetAtMs = nowMs }
    fun onBalanceUpdate(nowMs: Long) = timeUntilStall(nowMs)

    fun timeUntilStall(nowMs: Long): Long? {
        if (!runStarted || reloadPending || haltForLogin || reloadAttempts >= maxReloadAttempts) return null
        return (timeoutMs - (nowMs - lastCompletedBetAtMs)).coerceAtLeast(0L)
    }

    fun onTick(nowMs: Long): Action {
        if (!runStarted || reloadPending || haltForLogin) return Action.WAIT
        if (nowMs - lastCompletedBetAtMs < timeoutMs) return Action.WAIT
        if (reloadAttempts >= maxReloadAttempts) { haltForLogin = true; return Action.HALT }
        reloadAttempts++
        reloadPending = true
        return Action.RELOAD_EXISTING_WEBVIEW
    }

    fun onReloadComplete(balance: Double?, nowMs: Long = lastCompletedBetAtMs): Boolean {
        if (!reloadPending) return false
        reloadPending = false
        val authenticated = balance != null && balance.isFinite() && balance > 0.0
        if (authenticated) { runStarted = true; lastCompletedBetAtMs = nowMs; return true }
        haltForLogin = true
        return false
    }

    fun isReloadPending(): Boolean = reloadPending
    fun isHaltedForLogin(): Boolean = haltForLogin

    companion object { const val DEFAULT_MAX_RELOAD_ATTEMPTS = 3 }
}
