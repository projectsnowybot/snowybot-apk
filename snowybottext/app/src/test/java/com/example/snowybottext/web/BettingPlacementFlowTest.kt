package com.example.snowybottext.web

import com.example.snowybottext.web.SnowybotScriptStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Regression coverage for the asset-owned strategy's increase, placement, and settlement cycle. */
class BettingPlacementFlowTest {
    private class Flow(initialTicket: Long) {
        var previousTicket = initialTicket
        var inFlight = false
        var submittedStake: Double? = null
        var authoritativeWager: Double? = null
        var placementCount = 0

        fun progressionReturned(amount: Double): Double {
            authoritativeWager = amount
            return amount
        }

        fun placeAfterProgression(returnedAmount: Double) {
            check(!inFlight) { "Cannot place while prior wager is in flight" }
            check(returnedAmount == authoritativeWager) { "Stake must equal progression result" }
            submittedStake = returnedAmount
            inFlight = true
            placementCount++
        }

        fun complete(ticket: Long) {
            if (inFlight && (ticket > previousTicket)) {
                previousTicket = ticket
                inFlight = false
                submittedStake = null
            }
        }

        fun nextBet(amount: Double) {
            if (!inFlight) placeAfterProgression(progressionReturned(amount))
        }
    }

    private fun readScript(): String {
        val candidates = listOf(
            File("/home/snowy/AndroidStudioProjects/snowybottext/app/src/main/assets/${SnowybotScriptStore.FILE_NAME}"),
            File("app/src/main/assets/${SnowybotScriptStore.FILE_NAME}"),
            File("src/main/assets/${SnowybotScriptStore.FILE_NAME}"),
            File("/home/snowy/AndroidStudioProjects/snowybottext/src/main/assets/${SnowybotScriptStore.FILE_NAME}")
        )
        for (f in candidates) {
            if (f.exists() && f.isFile) return f.readText()
        }
        error("snowybot.js not found")
    }

    private fun readFileContent(path: String): String {
        val candidates = listOf(
            File("/home/snowy/AndroidStudioProjects/snowybottext/app/$path"),
            File("app/$path"),
            File(path),
            File("/home/snowy/AndroidStudioProjects/snowybottext/$path")
        )
        for (f in candidates) {
            if (f.exists() && f.isFile) return f.readText()
        }
        error("File not found: $path")
    }

    @Test
    fun progressionReturnBecomesStakeAndCompletesBeforeNextPlacement() {
        val flow = Flow(initialTicket = 40L)
        val returnedIncreasedAmount = flow.progressionReturned(0.00002468)
        flow.placeAfterProgression(returnedIncreasedAmount)
        assertEquals(0.00002468, flow.submittedStake!!, 0.0)
        assertTrue(flow.inFlight)
        assertEquals(1, flow.placementCount)

        flow.nextBet(0.00004936)
        assertEquals("next bet must wait for completion", 1, flow.placementCount)
        flow.complete(ticket = 41L)
        assertFalse(flow.inFlight)
        assertNull(flow.submittedStake)

        flow.nextBet(0.00004936)
        assertEquals(2, flow.placementCount)
        assertEquals(0.00004936, flow.submittedStake!!, 0.0)
        assertTrue(flow.inFlight)
    }

    @Test
    fun duplicateOrStaleTicketDoesNotSettlePlacement() {
        val flow = Flow(initialTicket = 9L)
        flow.progressionReturned(0.00002).also(flow::placeAfterProgression)
        flow.complete(ticket = 9L)
        assertTrue(flow.inFlight)
        flow.complete(ticket = 8L)
        assertTrue(flow.inFlight)
        assertEquals(1, flow.placementCount)
    }

    @Test
    fun bundledScriptUsesProgressionReturnAsStakeAndWaitsForTicketSettlement() {
        val source = readScript()
        val calculation = source.indexOf("const nextBet = resolveProgressedWager(await calculateNextProgressionStep(previousWagerAmount));")
        val stake = source.indexOf("executePlacementRoutine(nextBet, 49.5)", calculation)
        val completion = source.indexOf("awaitPlacementCompletion(placed, priorId)", stake)
        val nextInput = source.indexOf("previousWagerAmount = nextBet", completion)
        assertTrue("authoritative progression result must flow to stake input", calculation in (0 until stake))
        assertTrue("placement must be guarded by completion tracking", stake < completion)
        assertTrue("next progression input must store the accepted stake", completion < nextInput)
        assertTrue(source.contains("settleInFlightPlacement(\"completed ticket \" + id)"))
        assertFalse(source.contains("AndroidBridge.onBettingStall()"))
        assertFalse(source.contains("watchdogBalanceLastChangeAt >= 30000"))
        assertTrue(source.contains("function stopBalanceWatchdog()"))
        assertFalse(source.contains("Placement completion timeout; requesting watchdog recovery."))
        assertTrue(source.contains("if (placementAccepted && !betPlacementInFlight"))
        assertTrue(source.contains("window.__snowybotTryNextBetForTest"))
        assertTrue(source.contains("return Number(formatted);"))
    }

    @Test
    fun placementTimeoutCannotTriggerRecoveryAndWatchdogOwnsThirtySecondPolicy() {
        val source = readScript()
        val timerStart = source.indexOf("placementCompletionTimer = setTimeout(function()")
        val timerEnd = source.indexOf("}, 120000);", timerStart)
        assertTrue("bounded pending timer exists", timerStart >= 0 && timerEnd > timerStart)
        assertFalse(source.substring(timerStart, timerEnd).contains("onBettingStall"))
        assertFalse(source.contains("watchdogBalanceLastChangeAt >= 30000"))
        val bridge = readFileContent("src/main/java/com/example/snowybottext/web/JustDiceWebBridge.kt")
        val service = readFileContent("src/main/java/com/example/snowybottext/service/JustDiceBotService.kt")
        assertTrue(bridge.contains("window.__snowybotPlacementPending = true"))
        assertTrue(service.contains("webBridge?.markWagerCompleted()"))
        assertTrue(service.contains("stopSnowyBotAfterWatchdog()"))
        assertFalse(service.contains("WAGER_COMPLETION_TIMEOUT_MS"))
        val flow = Flow(initialTicket = 7L)
        flow.progressionReturned(0.00004).also(flow::placeAfterProgression)
        flow.complete(ticket = 8L)
        assertFalse("new ticket completion releases the in-flight gate", flow.inFlight)
    }

    @Test
    fun recoveryReloadReinjectsTheScriptAndReliesOnPlacementGateForNoDuplicateBets() {
        val bridge = readFileContent("src/main/java/com/example/snowybottext/web/JustDiceWebBridge.kt")
        val resume = bridge.substring(bridge.indexOf("fun resumeSnowyBotAfterWatchdog"), bridge.indexOf("fun dismissModal"))
        assertTrue(resume.contains("injectSnowyBot { result ->"))
        assertTrue(resume.contains("result.startsWith(\"STARTED:\")"))
        val script = readScript()
        assertTrue(script.contains("if (!placementAccepted || betPlacementInFlight) return false;"))
        assertTrue(script.contains("placementAccepted = true;"))
        assertTrue(script.contains("betPlacementInFlight = false;"))
        assertFalse(script.contains("void runPrimaryBettingLoop();"))
    }

    @Test
    fun bridgeScriptContainsPrecisionAndActualSiteBoundsGuards() {
        val source = JustDiceWebBridgeTestAccess.script(0.00002468)
        assertTrue(source.contains("String.format(Locale.US, \"%.8f\"" ) || source.contains("var amount = '0.00002468'"))
        assertTrue(source.contains("#pct_bet normalized requested"))
        assertTrue(source.contains("below site minimum"))
        assertTrue(source.contains("exceeds available/site maximum"))
        assertTrue(source.indexOf("minBtn.click()") < source.indexOf("betInput.value = amount"))
        assertTrue(source.indexOf("betInput.value = amount") < source.indexOf("rollBtn.click()"))
    }
}

private object JustDiceWebBridgeTestAccess {
    fun script(amount: Double): String {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").also { it.isAccessible = true }.get(null)
        val bridge = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, JustDiceWebBridge::class.java) as JustDiceWebBridge
        return bridge.placementScriptForTest(amount, 49.5)
    }
}
