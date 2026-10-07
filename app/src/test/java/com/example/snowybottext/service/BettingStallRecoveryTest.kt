package com.example.snowybottext.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BettingStallRecoveryTest {
    @Test
    fun runStartsClockWithCurrentWalletBaselineAndDoesNotReloadAt29Seconds() {
        val watchdog = BalanceWatchdog()
        watchdog.start(balance = 10.0, nowMs = 8_000L)
        assertFalse(watchdog.shouldReload(37_999L))
        assertTrue(watchdog.shouldReload(38_000L))
    }

    @Test
    fun balanceChangeAtTwentyNineSecondsStartsFreshThirtySecondWindow() {
        val watchdog = BalanceWatchdog()
        watchdog.start(balance = 12.0, nowMs = 0L)
        assertTrue(watchdog.observeBalance(balance = 12.5, nowMs = 29_000L))
        assertFalse(watchdog.shouldReload(58_999L))
        assertTrue(watchdog.shouldReload(59_000L))
    }

    @Test
    fun invalidBaselineAndInvalidObservationsCannotPretendBalanceChanged() {
        val watchdog = BalanceWatchdog()
        watchdog.start(balance = Double.NaN, nowMs = 500L)
        assertFalse(watchdog.observeBalance(15.0, 20_000L))
        assertFalse(watchdog.observeBalance(Double.POSITIVE_INFINITY, 21_000L))
        assertFalse(watchdog.observeBalance(0.0, 22_000L))
        assertTrue(watchdog.shouldReload(30_500L))
    }

    @Test
    fun positiveBalanceReloadAutomaticallyResumesRunningPhaseExactlyOnce() {
        val phases = runningPhases()
        val recovery = BalanceWatchdog()
        recovery.start(balance = 8.0, nowMs = 0L)
        assertTrue(recovery.shouldReload(30_000L))
        assertTrue(phases.beginRecovery())
        assertTrue(phases.shouldPlaceWager()) // strategy phase remains active while UI reports STALLED
        assertTrue(recovery.recoverySucceeded(8.25, nowMs = 31_000L))
        assertFalse(recovery.recoverySucceeded(8.25, nowMs = 31_001L))
        assertTrue(phases.resumeRunAfterRecovery())
        assertFalse(phases.resumeRunAfterRecovery())
        assertTrue(phases.shouldPlaceWager())
        assertFalse(phases.isWagerInFlight())
        assertFalse(recovery.shouldReload(60_999L))
        assertTrue(recovery.shouldReload(61_000L))
    }

    @Test
    fun recoveryClearsUncertainWagerAndAllowsOnlyOneNextSubmission() {
        val phases = runningPhases()
        val gate = WagerSnapshotGate()
        assertTrue(phases.tryBeginWager())
        assertTrue(gate.trySubmit(10.0))
        val recovery = BalanceWatchdog()
        recovery.start(balance = 10.0, nowMs = 0L)
        assertTrue(recovery.shouldReload(30_000L))
        assertTrue(phases.beginRecovery())
        assertTrue(phases.resetPendingWagerForRecovery())
        assertTrue(gate.settleByCompletedResult())
        assertTrue(recovery.recoverySucceeded(10.0, nowMs = 31_000L))
        assertTrue(phases.resumeRunAfterRecovery())
        assertTrue(phases.tryBeginWager())
        assertFalse(phases.tryBeginWager())
    }

    @Test
    fun authenticationFailureHaltsWithoutAllowingResume() {
        val phases = runningPhases()
        val recovery = BalanceWatchdog()
        recovery.start(balance = 10.0, nowMs = 0L)
        assertTrue(recovery.shouldReload(30_000L))
        assertTrue(phases.beginRecovery())
        assertFalse(recovery.recoverySucceeded(0.0, nowMs = 31_000L))
        recovery.recoveryFailed()
        assertFalse(recovery.isRunning())
        assertTrue(phases.haltRunAfterRecoveryFailure())
        assertFalse(phases.shouldPlaceWager())
        assertFalse(phases.resumeRunAfterRecovery())
        assertFalse(recovery.shouldReload(90_000L))
    }

    @Test
    fun balanceChangeResetsTheThirtySecondCountdown() {
        val watchdog = BalanceWatchdog()
        watchdog.start(balance = 20.0, nowMs = 1_000L)
        assertFalse(watchdog.shouldReload(30_999L))
        assertTrue(watchdog.observeBalance(balance = 21.0, nowMs = 20_000L))
        assertFalse(watchdog.shouldReload(49_999L))
        assertTrue(watchdog.shouldReload(50_000L))
    }

    @Test
    fun unchangedBalanceDoesNotReloadBeforeThirtySecondsAndReloadsAtDeadline() {
        val watchdog = BalanceWatchdog()
        watchdog.start(balance = 20.0, nowMs = 100L)
        assertFalse(watchdog.shouldReload(30_099L))
        assertTrue(watchdog.shouldReload(30_100L))
    }

    @Test
    fun invalidOrSameBalanceDoesNotResetTheClock() {
        val watchdog = BalanceWatchdog()
        watchdog.start(balance = 20.0, nowMs = 0L)
        assertFalse(watchdog.observeBalance(20.0, 10_000L))
        assertFalse(watchdog.observeBalance(0.0, 20_000L))
        assertFalse(watchdog.observeBalance(Double.NaN, 25_000L))
        assertTrue(watchdog.shouldReload(30_000L))
    }

    @Test
    fun successfulRecoveryWaitsForNextBalanceTimeoutAndRequiresAnotherReload() {
        val recovery = BalanceWatchdog()
        recovery.start(balance = 10.0, nowMs = 0L)
        assertTrue(recovery.shouldReload(30_000L))
        assertFalse(recovery.shouldReload(30_001L))
        assertTrue(recovery.recoverySucceeded(10.0, nowMs = 30_000L))
        assertTrue(recovery.shouldReload(60_000L))
        recovery.recoveryFailed()
        assertFalse(recovery.isRunning())
    }

    @Test
    fun duplicateRunTicksCannotRequestConcurrentReloads() {
        val recovery = BalanceWatchdog()
        recovery.start(balance = 10.0, nowMs = 0L)
        assertFalse(recovery.shouldReload(29_999L))
        assertTrue(recovery.shouldReload(30_000L))
        assertFalse(recovery.shouldReload(30_001L))
        assertTrue(recovery.isReloadPending())
    }

    private fun runningPhases(): BotPhaseController = BotPhaseController().apply {
        beginLogin()
        balanceArrivedAfterLogin()
        check(beginReadinessCheck())
        check(completeReadinessCheck(ready = true))
        check(startRun())
    }
}
