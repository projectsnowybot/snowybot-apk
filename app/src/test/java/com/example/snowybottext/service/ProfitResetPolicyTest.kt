package com.example.snowybottext.service

import com.example.snowybottext.engine.PeanutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProfitResetPolicyTest {
    @Test fun belowThresholdDoesNotTrigger() { assertFalse(ProfitResetPolicy.isTargetReached(100.0, 109.99)) }
    @Test fun exactTenPercentTriggers() { assertTrue(ProfitResetPolicy.isTargetReached(100.0, 110.0)) }
    @Test fun aboveThresholdTriggers() { assertTrue(ProfitResetPolicy.isTargetReached(100.0, 110.01)) }

    @Test fun duplicateProfitEventIsIgnoredUntilNewCycle() {
        val once = ProfitResetPolicy.Once()
        assertTrue(once.triggerIfTargetReached(100.0, 110.0))
        assertFalse(once.triggerIfTargetReached(100.0, 120.0))
        once.reset()
        assertTrue(once.triggerIfTargetReached(110.0, 121.0))
    }

    @Test fun invalidBaselinesAndBalancesAreSafe() {
        assertFalse(ProfitResetPolicy.isTargetReached(0.0, 1.0))
        assertFalse(ProfitResetPolicy.isTargetReached(-100.0, 1.0))
        assertFalse(ProfitResetPolicy.isTargetReached(Double.NaN, 1.0))
        assertFalse(ProfitResetPolicy.isTargetReached(Double.POSITIVE_INFINITY, 1.0))
        assertFalse(ProfitResetPolicy.isTargetReached(100.0, Double.NaN))
        assertNull(ProfitResetPolicy.freshBaseBet(Double.NaN))
    }

    @Test fun freshCycleBaseBetIsCurrentBalanceDividedBy1440000() {
        val balance = 1_440.0
        assertEquals(PeanutEngine.round8(balance / 1_440_000.0), ProfitResetPolicy.freshBaseBet(balance)!!, 0.0)
    }

    @Test fun profitRefreshBalanceMustBeConfirmedBeforeFreshCycleCanStart() {
        val service = File("src/main/java/com/example/snowybottext/service/JustDiceBotService.kt").readText()
        val refreshIndex = service.indexOf("bridge.resetStrategyAndRefresh")
        val readWalletIndex = service.indexOf("webBridge?.readWalletStash", refreshIndex)
        val startCycleIndex = service.indexOf("startFreshProfitCycle(refreshedBalance, generation)", refreshIndex)
        assertTrue(refreshIndex >= 0 && readWalletIndex > refreshIndex && startCycleIndex > readWalletIndex)
        assertTrue(service.contains("manualStopGeneration != profitResetManualStopSnapshot"))
        assertTrue(service.contains("profitRestartPending"))
    }

    @Test fun dashboardShowsOnlyBalanceBetAndProfitMetrics() {
        val dashboard = File("src/main/java/com/example/snowybottext/ui/dashboard/DashboardComponents.kt").readText()
        assertTrue(dashboard.contains("Text(\"Balance\""))
        assertTrue(dashboard.contains("Text(\"Current Bet Amount\""))
        assertTrue(dashboard.contains("Text(\"Profit:\""))
        assertFalse(dashboard.contains("Text(\"Wins\""))
        assertFalse(dashboard.contains("Text(\"Losses\""))
        assertFalse(dashboard.contains("Checkpoint"))
    }

    @Test fun profitRefreshGateSuppressesDuplicatesAndAllowsNextCycle() {
        val gate = ProfitResetPolicy.RefreshGate()
        assertTrue(gate.begin())
        assertFalse(gate.begin())
        gate.complete()
        assertTrue(gate.begin())
    }

    @Test fun resetAllSourceOrdersStopClearCredentialsAndFullWebViewData() {
        val service = readProjectFile("app/src/main/java/com/example/snowybottext/service/JustDiceBotService.kt")
        val bridge = readProjectFile("app/src/main/java/com/example/snowybottext/web/JustDiceWebBridge.kt")
        val resetIndex = service.indexOf("private fun resetAllInternal()")
        val stopIndex = service.indexOf("stopBotExecution()", resetIndex)
        val clearProgressIndex = service.indexOf("clearAllProgress()", resetIndex)
        val resetBodyEnd = service.indexOf("\n    private fun startWatchdog()", resetIndex)
        val resetBody = service.substring(resetIndex, resetBodyEnd)
        assertTrue(resetIndex >= 0 && stopIndex > resetIndex)
        assertTrue(resetBody.contains("performCompleteAppWipe()"))
        assertTrue(resetBody.contains("clearAllProgress()"))
        assertTrue(resetBody.contains("clearCredentials()"))
        assertTrue(service.contains("manualStopGeneration++"))
        assertTrue(service.contains("watchdogJob?.cancel()"))
        assertTrue(bridge.contains("removeAllCookies"))
        assertTrue(bridge.contains("cookieManager.flush()"))
        assertTrue(bridge.contains("WebStorage.getInstance().deleteAllData()"))
        assertTrue(bridge.contains("webView.clearCache(true)"))
        assertTrue(bridge.contains("webView.clearHistory()"))
        assertTrue(bridge.contains("webView.clearFormData()"))
        assertTrue(bridge.contains("localStorage.clear();sessionStorage.clear();"))
        assertTrue(bridge.contains("onCompleteForRefresh != null") || bridge.contains("onCompleteForRefresh"))
        assertTrue(bridge.contains("Duplicate refresh suppressed"))
    }

    private fun readProjectFile(path: String): String {
        val candidates = listOf(
            File(path),
            File("/home/snowy/AndroidStudioProjects/snowybottext/$path"),
            File("../$path"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Project source not found: $path")
    }
}
