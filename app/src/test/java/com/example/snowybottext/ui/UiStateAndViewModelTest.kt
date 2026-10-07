package com.example.snowybottext.ui

import com.example.snowybottext.service.BotStatus
import com.example.snowybottext.service.LoginFlowPolicy
import com.example.snowybottext.ui.dashboard.DashboardUiState
import com.example.snowybottext.ui.dashboard.ReadyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateAndViewModelTest {
    @Test
    fun initialPageStatusTransitionsFromLoadingToLoadedWithoutAuthentication() {
        val loading = DashboardUiState(pageStatus = "Loading page…", walletStash = 0.0)
        val loaded = loading.copy(pageStatus = "Page loaded")

        assertEquals("Loading page…", loading.pageStatus)
        assertEquals("Page loaded", loaded.pageStatus)
        assertFalse(loaded.isSessionAuthenticated)
        assertFalse(loaded.canRun)
    }

    @Test
    fun dashboardDisplaysScriptReportedWagerOverNativeState() {
        val state = DashboardUiState(
            currentWager = 1_440_000.0,
            currentWagerFromScript = true,
        )

        assertEquals(1_440_000.0, state.currentWager, 0.0)
        assertTrue(state.currentWagerFromScript)
    }

    @Test
    fun runningServiceStatusSelectsRunControl() {
        val state = DashboardUiState(status = BotStatus.RUNNING, readyState = ReadyState.READY)
        assertTrue(state.isRunning)
        assertTrue(state.isRunButtonSelected)
        assertFalse(state.canRun)
    }

    @Test
    fun loginReadyAndRunUiStatusTransitions() {
        val loggingIn = DashboardUiState(status = BotStatus.LOGGING_IN)
        assertTrue(loggingIn.isLoggingIn)
        assertFalse(loggingIn.canLogin)
        assertFalse(loggingIn.isRunButtonSelected)
        assertFalse(loggingIn.canRun)

        val notReady = loggingIn.copy(status = BotStatus.STOPPED, readyState = ReadyState.NOT_READY)
        assertFalse(notReady.canRun)
        val authenticated = notReady.copy(walletStash = 2.5)
        assertTrue(authenticated.canLogin)
        assertTrue(authenticated.isSessionAuthenticated)
        assertFalse(authenticated.canRun)
        assertEquals(ReadyState.NOT_READY, authenticated.readyState)
        val sessionReady = authenticated.copy(readyState = ReadyState.CHECKING)
        assertTrue(sessionReady.isSessionAuthenticated)
        val ready = notReady.copy(readyState = ReadyState.READY)
        assertTrue(ready.canRun)
        assertFalse(ready.isRunButtonSelected)
        val running = ready.copy(status = BotStatus.RUNNING)
        assertTrue(running.isRunButtonSelected)
        assertFalse(running.canRun)
    }

    @Test
    fun loginInProgressDisablesLoginAndFailureRestoresRetry() {
        val inProgress = DashboardUiState(status = BotStatus.LOGGING_IN)
        assertEquals("Logging in…", inProgress.status.displayName)
        assertFalse(inProgress.canLogin)

        val failed = inProgress.copy(
            status = BotStatus.STOPPED,
            loginError = "Network error",
            walletStash = 0.0,
            readyState = ReadyState.NOT_READY,
        )
        assertTrue(failed.canLogin)
        assertFalse(failed.isLoggingIn)
        assertFalse(failed.isSessionAuthenticated)
        assertFalse(failed.canRun)
        assertEquals("Network error", failed.loginError)
    }

    @Test
    fun acceptedLoginShowsBalanceButRequiresExplicitReadyBeforeRun() {
        val completedLogin = DashboardUiState(
            status = BotStatus.STOPPED,
            walletStash = 12.5,
            readyState = ReadyState.NOT_READY,
        )

        assertTrue(completedLogin.canLogin)
        assertTrue(completedLogin.isSessionAuthenticated)
        assertFalse(completedLogin.isLoggingIn)
        assertFalse(completedLogin.canRun)
        assertEquals(12.5, completedLogin.walletStash, 0.0)
    }

    @Test
    fun fullResetUiClearsSessionStateAndKeepsCredentialFieldsAvailable() {
        val reset = DashboardUiState(
            status = BotStatus.STOPPED,
            readyState = ReadyState.NOT_READY,
            walletStash = 0.0,
            startingPocketChange = 0.0,
            currentWager = 0.0,
            username = "",
            password = "",
        )
        assertFalse(reset.isSessionAuthenticated)
        assertFalse(reset.canRun)
        assertEquals("", reset.username)
        assertEquals("", reset.password)
    }

    @Test
    fun positiveBalanceIsTheLoginCompletionCriterion() {
        assertTrue(LoginFlowPolicy.shouldComplete(0.0001))
        assertFalse(LoginFlowPolicy.shouldComplete(0.0))
        assertFalse(LoginFlowPolicy.shouldComplete(Double.NaN))
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
    fun profitUsesCurrentLiveBalanceAndBaselineOnlyWhenFinite() {
        val state = DashboardUiState(walletStash = 120.0, startingPocketChange = 100.0)
        assertEquals(20.0, state.profitLoss, 0.0001)
        assertEquals(0.0, state.copy(startingPocketChange = Double.NaN).profitLoss, 0.0)
    }
}
