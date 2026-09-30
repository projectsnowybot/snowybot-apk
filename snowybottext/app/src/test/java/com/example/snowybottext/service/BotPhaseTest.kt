package com.example.snowybottext.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotPhaseTest {
    @Test
    fun loginRequiresExplicitReadyBeforeRun() {
        val phases = BotPhaseController()
        phases.beginLogin()
        assertEquals(BotPhaseController.Phase.LOGIN_PENDING_BALANCE, phases.phase)
        phases.balanceArrivedAfterLogin()
        assertEquals(BotPhaseController.Phase.SESSION_READY, phases.phase)
        assertFalse(phases.beginRunPreparation())
        assertTrue(phases.beginReadinessCheck())
        assertTrue(phases.completeReadinessCheck(ready = true))
        assertEquals(BotPhaseController.Phase.READY, phases.phase)
        assertTrue(phases.startRun())
        assertTrue(phases.shouldPlaceWager())
    }

    @Test
    fun failedReadinessDoesNotEnableRun() {
        val phases = readyCheckPhases()
        assertTrue(phases.completeReadinessCheck(ready = false))
        assertEquals(BotPhaseController.Phase.SESSION_READY, phases.phase)
        assertFalse(phases.beginRunPreparation())
    }

    @Test
    fun watchdogRecoveryPreservesRunningPhase() {
        val phases = runningPhases()
        assertTrue(phases.tryBeginWager())
        assertTrue(phases.beginRecovery())
        assertTrue(phases.shouldPlaceWager()) // allows the authenticated recovery path to continue the same run
        assertTrue(phases.resetPendingWagerForRecovery())
        assertTrue(phases.resumeRunAfterRecovery())
        assertEquals(BotPhaseController.Phase.RUNNING, phases.phase)
        assertFalse(phases.isWagerInFlight())
        assertTrue(phases.tryBeginWager())
    }

    @Test
    fun stopHaltsWageringAndLeavesReadySession() {
        val phases = runningPhases()
        assertTrue(phases.tryBeginWager())
        phases.stopRun()
        assertFalse(phases.shouldPlaceWager())
        assertEquals(BotPhaseController.Phase.READY, phases.phase)
        assertFalse(phases.isWagerInFlight())
    }

    @Test
    fun runtimeFailureLeavesNonRunningSessionAndRequiresReadyAgain() {
        val phases = runningPhases()
        assertTrue(phases.failRun())
        assertFalse(phases.shouldPlaceWager())
        assertEquals(BotPhaseController.Phase.SESSION_READY, phases.phase)
        assertFalse(phases.beginRunPreparation())
    }

    @Test
    fun cancelledRunPreparationReturnsToReady() {
        val phases = readyPhases()
        assertTrue(phases.beginRunPreparation())
        phases.cancelRunPreparation()
        assertEquals(BotPhaseController.Phase.READY, phases.phase)
        assertFalse(phases.tryBeginWager())
    }

    @Test
    fun wagerReservationIsSingleAndCompletedResultReleasesIt() {
        val phases = runningPhases()
        assertTrue(phases.tryBeginWager())
        assertFalse(phases.tryBeginWager())
        assertTrue(phases.completeWager())
        assertFalse(phases.isWagerInFlight())
        assertTrue(phases.tryBeginWager())
    }

    @Test
    fun recoveryFailureHaltsRunAndPreservesUncertainReservation() {
        val phases = runningPhases()
        assertTrue(phases.tryBeginWager())
        assertTrue(phases.beginRecovery())
        assertTrue(phases.haltRunAfterRecoveryFailure())
        assertFalse(phases.shouldPlaceWager())
        assertEquals(BotPhaseController.Phase.SESSION_READY, phases.phase)
        assertFalse(phases.isWagerInFlight())
    }

    private fun readyCheckPhases() = BotPhaseController().apply {
        beginLogin()
        balanceArrivedAfterLogin()
        check(beginReadinessCheck())
    }

    private fun readyPhases() = readyCheckPhases().apply {
        check(completeReadinessCheck(ready = true))
    }

    private fun runningPhases() = readyPhases().apply {
        check(startRun())
    }
}
