package com.example.snowybottext.service

import com.example.snowybottext.engine.PeanutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JustDiceBotServiceTest {

    @Test
    fun testBotStatusEnumValues() {
        assertEquals("Stopped", BotStatus.STOPPED.displayName)
        assertEquals("Logging in…", BotStatus.LOGGING_IN.displayName)
        assertEquals("Running", BotStatus.RUNNING.displayName)
        assertEquals("Stalled (Auto-reconnecting)", BotStatus.STALLED.displayName)
        assertEquals("Stopped", BotStatus.TARGET_REACHED.displayName)
    }

    @Test
    fun testServiceConstants() {
        assertEquals(30_000L, JustDiceBotService.STALL_TIMEOUT_MS)
        assertEquals(35_000L, JustDiceBotService.LOGIN_TIMEOUT_MS)
        assertEquals(0.00000001, JustDiceBotService.BALANCE_COMPLETION_TOLERANCE, 0.0)
        assertEquals("com.super.snowybot.action.START_BOT", JustDiceBotService.ACTION_START_BOT)
        assertEquals("com.super.snowybot.action.LOGIN", JustDiceBotService.ACTION_LOGIN)
        assertEquals("com.super.snowybot.action.READY", JustDiceBotService.ACTION_READY)
        assertEquals("com.super.snowybot.action.STOP_BOT", JustDiceBotService.ACTION_STOP_BOT)
        assertEquals("com.super.snowybot.action.RELOAD_PAGE", JustDiceBotService.ACTION_RELOAD_PAGE)
        assertEquals("com.super.snowybot.action.RESET_STATE", JustDiceBotService.ACTION_RESET_STATE)
        assertEquals("com.super.snowybot.action.RESET_ALL", JustDiceBotService.ACTION_RESET_ALL)
    }

    @Test
    fun loginCompletesOnFirstPositiveBalanceRatherThanTimeout() {
        val firstObservedPositiveBalance = 0.00000001
        assertTrue(LoginFlowPolicy.shouldComplete(firstObservedPositiveBalance))
        assertTrue(LoginFlowPolicy.retryableError(BotStatus.STOPPED, loginProgress = false))
        assertFalse(LoginFlowPolicy.retryableError(BotStatus.LOGGING_IN, loginProgress = true))
    }

    @Test
    fun fullResetIsASeparateActionFromStateOnlyReset() {
        assertTrue(JustDiceBotService.ACTION_RESET_ALL != JustDiceBotService.ACTION_RESET_STATE)
        assertTrue(JustDiceBotService.ACTION_RESET_ALL.endsWith("RESET_ALL"))
    }

    @Test
    fun resetGateRejectsPendingRecoveryResume() {
        assertTrue(JustDiceBotService.resetPreventsResume(fullResetRequested = true, generationMatches = true))
        assertTrue(JustDiceBotService.resetPreventsResume(fullResetRequested = false, generationMatches = false))
        assertFalse(JustDiceBotService.resetPreventsResume(fullResetRequested = false, generationMatches = true))
    }

    @Test
    fun testTargetReachedLogicWithPeanutEngine() {
        val engine = PeanutEngine()
        val initialBalance = 100.0
        engine.initWithBalance(initialBalance)

        // Balance below 10% profit should compute the next wager
        val nextWager = engine.processRollResult(
            shinyNewTicket = 12345L,
            rollOutcome = 12.3,
            winsCount = 1,
            lossesCount = 0,
            walletStash = 100.001,
            targetLimit = 144000.0
        )
        assertTrue(nextWager != null)
        assertFalse(engine.state.areWeRichYet)

        // A legacy engine target value remains disabled by default.
        val targetWager = engine.processRollResult(
            shinyNewTicket = 12346L,
            rollOutcome = 10.1,
            winsCount = 2,
            lossesCount = 0,
            walletStash = 144000.5,
            targetLimit = 144000.0
        )
        assertEquals(null, targetWager)
        assertTrue(engine.state.areWeRichYet)
    }

    @Test
    fun testWatchdogStallCalculation() {
        val lastActivityTime = System.currentTimeMillis() - 31_000L // 31 seconds ago
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastActivityTime

        assertTrue(elapsed >= JustDiceBotService.STALL_TIMEOUT_MS)
    }
}
