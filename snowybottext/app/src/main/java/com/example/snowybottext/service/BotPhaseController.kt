package com.example.snowybottext.service

/** Tracks login, explicit readiness, bot execution, and recovery as separate phases. */
class BotPhaseController {
    enum class Phase { IDLE, LOGIN_PENDING_BALANCE, SESSION_READY, CHECKING_READINESS, READY, PREPARING_RUN, RUNNING }

    var phase: Phase = Phase.IDLE
        private set

    private var wagerInFlight = false
    private var recoveryResumed = false

    fun tryBeginWager(): Boolean {
        if (!shouldPlaceWager() || wagerInFlight) return false
        wagerInFlight = true
        return true
    }

    fun completeWager(): Boolean {
        if (!wagerInFlight) return false
        wagerInFlight = false
        return true
    }

    fun isWagerInFlight(): Boolean = wagerInFlight

    fun resetPendingWagerForRecovery(): Boolean {
        val wasInFlight = wagerInFlight
        wagerInFlight = false
        return wasInFlight
    }

    fun beginRecovery(): Boolean {
        if (phase != Phase.RUNNING || recoveryResumed) return false
        recoveryResumed = true
        return true
    }

    fun resumeRunAfterRecovery(): Boolean {
        if (phase != Phase.RUNNING || !recoveryResumed) return false
        recoveryResumed = false
        wagerInFlight = false
        return true
    }

    fun haltRunAfterRecoveryFailure(): Boolean {
        if (phase != Phase.RUNNING) return false
        phase = Phase.SESSION_READY
        recoveryResumed = false
        wagerInFlight = false
        return true
    }

    fun beginLogin() {
        check(phase != Phase.RUNNING) { "Stop wagering before starting a new login" }
        phase = Phase.LOGIN_PENDING_BALANCE
        recoveryResumed = false
    }

    fun balanceArrivedAfterLogin() {
        if (phase == Phase.LOGIN_PENDING_BALANCE) phase = Phase.SESSION_READY
    }

    /** Reset an unsuccessful login to an idle, retryable phase. */
    fun failLogin() {
        if (phase == Phase.LOGIN_PENDING_BALANCE) phase = Phase.IDLE
    }

    fun beginReadinessCheck(): Boolean {
        if (phase !in setOf(Phase.SESSION_READY, Phase.READY)) return false
        phase = Phase.CHECKING_READINESS
        return true
    }

    fun completeReadinessCheck(ready: Boolean): Boolean {
        if (phase != Phase.CHECKING_READINESS) return false
        phase = if (ready) Phase.READY else Phase.SESSION_READY
        recoveryResumed = false
        return true
    }

    fun invalidateReadiness() {
        if (phase == Phase.READY || phase == Phase.CHECKING_READINESS || phase == Phase.RUNNING) {
            phase = Phase.SESSION_READY
        }
    }

    fun beginRunPreparation(): Boolean {
        if (phase != Phase.READY || wagerInFlight) return false
        phase = Phase.PREPARING_RUN
        recoveryResumed = false
        return true
    }

    fun completeRunPreparation(): Boolean {
        if (phase != Phase.PREPARING_RUN) return false
        phase = Phase.RUNNING
        return true
    }

    fun cancelRunPreparation() {
        if (phase == Phase.PREPARING_RUN) phase = Phase.READY
    }

    fun startRun(): Boolean {
        if (!beginRunPreparation()) return false
        return completeRunPreparation()
    }

    fun stopRun() {
        if (phase == Phase.RUNNING || phase == Phase.PREPARING_RUN) phase = Phase.READY
        recoveryResumed = false
        wagerInFlight = false
    }

    fun failRun(): Boolean {
        if (phase != Phase.RUNNING) return false
        phase = Phase.SESSION_READY
        recoveryResumed = false
        return true
    }

    fun shouldPlaceWager(): Boolean = phase == Phase.RUNNING
    fun isRunPreparationPending(): Boolean = phase == Phase.PREPARING_RUN
}
