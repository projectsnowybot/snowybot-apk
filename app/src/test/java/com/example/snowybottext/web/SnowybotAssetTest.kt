package com.example.snowybottext.web

import com.example.snowybottext.web.SnowybotScriptStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SnowybotAssetTest {
    @Test
    fun bundledAssetHasStrategyPlacementSettlementAndWatchdogHooks() {
        val source = assetSource()
        assertTrue("snowybot.js must be present", source.isNotBlank())
        assertTrue(source.contains("window.snowyBotRunning = true"))
        assertTrue(source.contains("initializeBotEngine"))
        assertTrue(source.contains("runPrimaryBettingLoop"))
        assertTrue(source.contains("async function calculateNextProgressionStep(incomingWager)"))
        assertTrue(source.contains("function resolveProgressedWager(computedWager)"))
        assertTrue(source.contains("currentWagerAmount = nextBet"))
        assertTrue(source.contains("executePlacementRoutine(nextBet, 49.5)"))
        assertTrue(source.contains("previousWagerAmount = nextBet"))
        assertTrue(source.contains("function reportCurrentWagerAmount()"))
        assertTrue(source.contains("reportCurrentWagerAmount();\n        wobbleFactor = 1;"))
        assertTrue(source.contains("currentWagerAmount = backupPeanut;\n        reportCurrentWagerAmount();"))
        assertTrue(source.contains("reportCurrentWagerAmount();\n                    placementAccepted = false;"))
        assertTrue(source.contains("reportCurrentWagerAmount();"))
        assertTrue(source.contains("window.AndroidBridge.onCurrentWagerAmount(String(Number(amount.toFixed(8))))"))
        assertTrue(source.contains("currentWagerAmount = backupPeanut;\n        reportCurrentWagerAmount();"))
        assertTrue(source.contains("reportCurrentWagerAmount();\n                    placementAccepted = false;"))
        assertTrue(source.contains("Placed stake does not match authoritative progression amount"))
        assertFalse(source.contains("AndroidBridge.onBettingStall()"))
        assertFalse(source.contains("readValidWatchdogBalance"))
        assertFalse(source.contains("watchdogBalanceLastChangeAt >= 30000"))
        assertTrue(source.contains("function stopBalanceWatchdog()"))
        assertFalse(source.contains("lastProgressTime"))
        assertFalse(source.contains("MutationObserver(inspectCompletedBetProgress"))
        val placementTimerStart = source.indexOf("placementCompletionTimer = setTimeout(function()")
        val placementTimerEnd = source.indexOf("}, 120000);", placementTimerStart)
        assertTrue(placementTimerStart >= 0 && placementTimerEnd > placementTimerStart)
        assertFalse(source.substring(placementTimerStart, placementTimerEnd).contains("onBettingStall"))
        assertFalse(source.contains("Placement completion timeout; requesting watchdog recovery."))
        assertFalse(source.contains("watchdogBalanceLastChangeAt >= 30000"))
        assertTrue(source.contains("#pct_bet="))
        assertTrue(source.contains("awaitPlacementCompletion(placed, priorId)"))
        assertTrue(source.contains("!betPlacementInFlight && ((shinyNewTicket > oldTicketStub)"))
        assertTrue(source.contains("function observePlacementSettlement()"))
        assertTrue(source.contains("settleInFlightPlacement(\"completed ticket \" + id)"))
        assertTrue(source.contains("placementCompletionTimer"))
        assertTrue(source.contains("window.__snowybotSettlePlacementForTest"))
        assertTrue(source.contains("exceeds available/site maximum"))
        assertTrue(source.contains("below site minimum"))
        listOf("pct_balance", "pct_bet", "pct_chance", "a_lo").forEach { assertTrue(source.contains(it)) }
    }

    @Test
    fun authAndMissingAmountBoundaryHandlingRemainExplicit() {
        val source = assetSource()
        assertTrue(source.contains("inspectSessionAuthentication"))
        assertTrue(source.contains("inspectCurrentWalletBalance"))
        assertTrue(source.contains("currentWalletBalance !== null"))
        assertTrue(source.contains("window.__snowybotLoginBalanceTimeout"))
        assertTrue(source.contains("window.__snowybotLoginBalancePoll"))
        assertTrue(source.contains("positive parsed #pct_balance"))
        assertTrue(source.contains("Authentication not confirmed; refusing runtime"))
        assertTrue(source.contains("stake is below 8-decimal precision"))
        assertTrue(source.contains("stake must be positive and finite"))
        assertFalse(source.contains("loginFormVisible"))
        assertFalse(source.contains("__snowybotAndroidConfirmedBalance"))
    }

    @Test
    fun nativeBridgeStillUsesOneStrategyAuthorityAndWaitsForExplicitPlacementResult() {
        val bridge = readFileContent("src/main/java/com/example/snowybottext/web/JustDiceWebBridge.kt")
        val service = readFileContent("src/main/java/com/example/snowybottext/service/JustDiceBotService.kt")
        assertTrue(bridge.contains("window.__snowybotPlacementPending = true"))
        assertTrue(bridge.contains("markWagerCompleted()"))
        assertTrue(service.contains("bundledScriptStrategyActive"))
        assertTrue(service.contains("phaseController.completeWager()"))
    }

    private fun assetSource(): String {
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
}
