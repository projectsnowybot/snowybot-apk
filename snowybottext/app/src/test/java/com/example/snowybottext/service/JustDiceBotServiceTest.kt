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
        assertEquals("Target Reached", BotStatus.TARGET_REACHED.displayName)
    }

    @Test
    fun testServiceConstants() {
        assertEquals(144000.0, JustDiceBotService.DEFAULT_TARGET_LIMIT, 0.001)
        assertEquals(30_000L, JustDiceBotService.STALL_TIMEOUT_MS)
        assertEquals(0.00000001, JustDiceBotService.BALANCE_COMPLETION_TOLERANCE, 0.0)
        assertEquals("com.example.snowybottext.action.START_BOT", JustDiceBotService.ACTION_START_BOT)
        assertEquals("com.example.snowybottext.action.LOGIN", JustDiceBotService.ACTION_LOGIN)
        assertEquals("com.example.snowybottext.action.READY", JustDiceBotService.ACTION_READY)
        assertEquals("com.example.snowybottext.action.STOP_BOT", JustDiceBotService.ACTION_STOP_BOT)
        assertEquals("com.example.snowybottext.action.RELOAD_PAGE", JustDiceBotService.ACTION_RELOAD_PAGE)
        assertEquals("com.example.snowybottext.action.RESET_STATE", JustDiceBotService.ACTION_RESET_STATE)
    }

    @Test
    fun testTargetReachedLogicWithPeanutEngine() {
        val engine = PeanutEngine()
        val initialBalance = 100.0
        engine.initWithBalance(initialBalance)

        // Balance < 144000 -> Should compute next wager
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

        // Balance >= 144000 -> Target reached, next wager should be null
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
