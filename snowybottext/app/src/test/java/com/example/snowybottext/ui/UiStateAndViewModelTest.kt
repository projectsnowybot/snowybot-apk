package com.example.snowybottext.ui

import com.example.snowybottext.service.BotStatus
import com.example.snowybottext.ui.dashboard.DashboardUiState
import com.example.snowybottext.ui.dashboard.ReadyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateAndViewModelTest {
    @Test
    fun runningServiceStatusSelectsRunControl() {
        val state = DashboardUiState(status = BotStatus.RUNNING, readyState = ReadyState.READY)
        assertTrue(state.isRunning)
        assertTrue(state.isRunButtonSelected)
        assertFalse(state.canRun)
    }

    @Test
    fun loginReadyAndRunUiStatusTransitions() {
        val loggingIn = DashboardUiState(status = BotStatus.LOGGING_IN, activityLogs = listOf("[Login] Login started"))
        assertTrue(loggingIn.isLoggingIn)
        assertFalse(loggingIn.isRunButtonSelected)
        assertFalse(loggingIn.canRun)
        assertEquals("[Login] Login started", loggingIn.activityLogs.single())

        val notReady = loggingIn.copy(status = BotStatus.STOPPED, readyState = ReadyState.NOT_READY)
        assertFalse(notReady.canRun)
        val ready = notReady.copy(readyState = ReadyState.READY)
        assertTrue(ready.canRun)
        assertFalse(ready.isRunButtonSelected)
        val running = ready.copy(status = BotStatus.RUNNING)
        assertTrue(running.isRunButtonSelected)
        assertFalse(running.canRun)
    }

    @Test
    fun stalledAndRecoveredStatusTransitions() {
        val running = DashboardUiState(status = BotStatus.RUNNING, readyState = ReadyState.READY)
        val stalled = running.copy(status = BotStatus.STALLED)
        assertFalse(stalled.isRunButtonSelected)
        assertFalse(stalled.canRun)
        val recovered = stalled.copy(status = BotStatus.RUNNING)
        assertTrue(recovered.isRunButtonSelected)
    }

    @Test
    fun stoppedAndRuntimeFailureClearRunSelectionAndExposeDetails() {
        val stopped = DashboardUiState(status = BotStatus.STOPPED, runtimeError = "runtime loop error")
        assertEquals("runtime loop error", stopped.runtimeError)
        assertFalse(stopped.isRunning)
        assertFalse(stopped.isRunButtonSelected)
        assertFalse(stopped.canRun)
    }

    @Test
    fun targetReachedClearsRunSelection() {
        val state = DashboardUiState(status = BotStatus.TARGET_REACHED)
        assertFalse(state.isRunButtonSelected)
        assertFalse(state.isRunning)
    }

    @Test
    fun testDashboardUiState_profitLossAndWinRate() {
        val state = DashboardUiState(
            status = BotStatus.RUNNING,
            walletStash = 120.0,
            startingPocketChange = 100.0,
            targetLimit = 144000.0,
            totalWins = 8,
            totalLosses = 2
        )
        assertEquals(20.0, state.profitLoss, 0.0001)
        assertEquals(80.0, state.winRate, 0.0001)
        assertEquals(20.0 / 143900.0, state.targetProgress.toDouble(), 0.0001)
        assertFalse(state.showResetConfirmation)
    }
}
