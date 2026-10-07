package com.example.snowybottext.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SnowybotRuntimeStateTest {
    @Test
    fun bundledRuntimeRemainsActiveDuringAsyncLoopAndStopTerminatesIt() {
        val source = File("src/main/assets/snowybot.js").readText()
        val runtimeLoop = extractFunction(source, "async function runPrimaryBettingLoop()")
        assertTrue(source.contains("window.__snowybotRuntimeTask = (async function initializeBotEngine()"))
        assertTrue(source.contains("await runPrimaryBettingLoop();"))

        val runtime = AsyncBundledRuntime(runtimeLoop)
        runtime.start()
        assertTrue("Bundled async loop did not remain running", runtime.awaitEntered(5, TimeUnit.SECONDS))
        assertFalse("Runtime unexpectedly stopped while its loop was suspended", runtime.isStopped())

        runtime.stop()
        assertTrue("Async runtime did not terminate after Stop", runtime.awaitStopped(5, TimeUnit.SECONDS))
        assertTrue(runtime.isStopped())
    }

    @Test
    fun startupHandshakeWaitsForActiveLoopAndReportsFailureAndStopSeparately() {
        val bridgeSource = File("src/main/java/com/example/snowybottext/web/JustDiceWebBridge.kt").readText()
        val assetSource = File("src/main/assets/snowybot.js").readText()

        assertTrue(bridgeSource.contains("probe.phase === 'active'"))
        assertTrue(bridgeSource.contains("probe.phase === 'failed'"))
        assertTrue(bridgeSource.contains("probe.phase === 'stopped'"))
        assertTrue(bridgeSource.contains("probeState.startsWith(\"stopped:\")"))
        assertTrue(bridgeSource.contains("sourceStarted: false, loopActive: false"))
        assertTrue(bridgeSource.contains("SNOWYBOT_STARTUP_MAX_POLLS"))
        assertTrue(assetSource.contains("window.__snowybotStartupProbe.phase = 'source-started'"))
        assertTrue(assetSource.contains("window.__snowybotStartupProbe.phase = 'active'"))
        assertTrue(assetSource.contains("window.__snowybotLoginConfirmed = true;\n    window.__snowybotStopRequested = false;\n    window.snowyBotRunning = true;"))
        assertTrue(assetSource.contains("Authentication not confirmed; refusing runtime"))
        assertTrue(assetSource.contains("authentication not confirmed: ' + sessionAuth.reason"))
        assertTrue(assetSource.contains("window.__snowybotLoginConfirmed = true;\n    window.__snowybotStopRequested = false;\n    window.snowyBotRunning = true;\n    if (window.AndroidBridge) {"))
        assertTrue(assetSource.contains("Authentication not confirmed; refusing runtime. reason=' + sessionAuth.reason + '; evidence: ' + sessionAuth.evidence"))
        assertTrue(assetSource.contains("window.__snowybotStartupProbe.phase = 'failed'"))
        assertTrue(bridgeSource.contains("Startup handshake cancelled by Stop."))
    }

    @Test
    fun delayedAsyncInitializationIsPendingUntilTheLoopEntryHandshake() {
        val probe = StartupProbeHarness()
        probe.sourceStarted()
        assertEquals("PENDING", probe.result())
        Thread.sleep(25)
        assertEquals("PENDING", probe.result())
        probe.activeLoopEntered()
        assertEquals("STARTED", probe.result())
    }

    @Test
    fun startupHandshakeDoesNotTreatInjectionAloneAsActiveAndStopIsNotSuccess() {
        val probe = StartupProbeHarness()
        probe.sourceStarted()
        assertEquals("PENDING", probe.result())
        probe.stop("stopped by Android")
        assertEquals("STOPPED", probe.result())
        assertFalse(probe.result() == "STARTED")
    }

    @Test
    fun authAndSourceFailuresAreReportedWithReasonInsteadOfWaitingForTimeout() {
        val probe = StartupProbeHarness()
        val bridgeProbe = File("src/main/java/com/example/snowybottext/web/JustDiceWebBridge.kt").readText()
        assertTrue(bridgeProbe.contains("source evaluation failed:"))
        assertTrue(bridgeProbe.contains("probeState.startsWith(\"stopped:\")"))
        assertTrue(bridgeProbe.contains("sourceStarted: false, loopActive: false"))
        probe.fail("authentication not confirmed: balance is not positive")
        assertEquals("FAILED:authentication not confirmed: balance is not positive", probe.result())
        val sourceFailure = StartupProbeHarness().apply { fail("source evaluation error: SyntaxError") }
        assertEquals("FAILED:source evaluation error: SyntaxError", sourceFailure.result())
    }

    @Test
    fun profitResetChecksThresholdClearsOnlyProgressionAndRefreshesExistingWebView() {
        val source = File("src/main/assets/snowybot.js").readText()
        val bridge = File("src/main/java/com/example/snowybottext/web/JustDiceWebBridge.kt").readText()
        val service = File("src/main/java/com/example/snowybottext/service/JustDiceBotService.kt").readText()
        assertTrue(source.contains("(current - baseline) / baseline >= 0.10"))
        assertTrue(source.contains("!Number.isFinite(baseline) || baseline <= 0"))
        assertTrue(source.contains("window.__snowybotProfitResetTriggered"))
        assertTrue(source.contains("if (window.__snowybotProfitResetTriggered) return false"))
        assertTrue(source.contains("localStorage.removeItem('snowybotbackup')"))
        assertTrue(source.contains("AndroidBridge.clearSnowybotBackup()"))
        assertTrue(File("src/main/java/com/example/snowybottext/web/JustDiceJsBridge.kt").readText().contains("synchronized(backupLock) { if (file.exists()) file.delete() else true }"))
        assertTrue(source.contains("10% PROFIT TARGET REACHED..."))
        assertTrue(bridge.contains("webView.reload()"))
        assertTrue(bridge.contains("CookieManager"))
        assertTrue(bridge.contains("Duplicate refresh suppressed"))
        assertTrue(service.contains("botStateRepository.clearBotState()"))
        assertTrue(service.contains("completeProfitReset()"))
        assertTrue(service.contains("resetStrategyAndRefresh"))
        assertTrue(service.contains("clearCredentials()"))
        assertTrue(service.contains("ProfitResetPolicy.freshBaseBet(balance)"))
        assertTrue(service.contains("setFreshProfitBaseBet(baseBet, balance)"))
        assertTrue(bridge.contains("freshProfitBaseBet"))
        assertTrue(source.contains("__snowybotFreshCycleBaseBet"))
        assertTrue(source.contains("window.__snowybotProfitCycleBaseline"))
        assertFalse(source.contains("walletStash >= 144000"))
        assertTrue(service.contains("BotStatus.STOPPED"))
    }

    @Test
    fun runtimeFailureNotifiesNativeServiceAndClearsRunningFlags() {
        val source = File("src/main/assets/snowybot.js").readText()
        assertTrue(source.contains("window.snowyBotRunning = false"))
        assertTrue(source.contains("window.__snowybotStopRequested = true"))
        assertTrue(source.contains("AndroidBridge.onSnowyBotStopped"))
    }

    private fun extractFunction(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("Bundled runtime function is missing", start >= 0)
        val bodyStart = source.indexOf('{', start)
        var depth = 0
        var inSingle = false
        var inDouble = false
        var inTemplate = false
        var escaped = false
        for (index in bodyStart until source.length) {
            val character = source[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (character == '\\') {
                escaped = true
                continue
            }
            if (!inDouble && !inTemplate && character == '\'') inSingle = !inSingle
            else if (!inSingle && !inTemplate && character == '"') inDouble = !inDouble
            else if (!inSingle && !inDouble && character == '`') inTemplate = !inTemplate
            if (inSingle || inDouble || inTemplate) continue
            if (character == '{') depth++
            if (character == '}') {
                depth--
                if (depth == 0) return source.substring(start, index + 1)
            }
        }
        throw AssertionError("Bundled runtime function braces are unbalanced")
    }

    private class StartupProbeHarness {
        private var phase = "evaluating"
        private var reason: String? = null

        fun sourceStarted() { phase = "source-started" }
        fun activeLoopEntered() { phase = "active" }
        fun fail(detail: String) { phase = "failed"; reason = detail }
        fun stop(detail: String) { phase = "stopped"; reason = detail }

        fun result(): String = when (phase) {
            "active" -> "STARTED"
            "failed" -> "FAILED:${reason.orEmpty()}"
            "stopped" -> "STOPPED"
            else -> "PENDING"
        }
    }

    private class AsyncBundledRuntime(bundledLoop: String) {
        private val running = AtomicBoolean(true)
        private val stopRequested = AtomicBoolean(false)
        private val entered = CountDownLatch(1)
        private val stopped = CountDownLatch(1)
        private val worker = Thread {
            entered.countDown()
            try {
                while (running.get() && !stopRequested.get()) Thread.sleep(10)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                stopped.countDown()
            }
        }

        init {
            require(bundledLoop.contains("while (window.snowyBotRunning && !window.__snowybotStopRequested)"))
            require(bundledLoop.contains("await pauseExecution(50);"))
        }

        fun start() = worker.start()
        fun stop() {
            stopRequested.set(true)
            running.set(false)
        }
        fun awaitEntered(timeout: Long, unit: TimeUnit) = entered.await(timeout, unit)
        fun awaitStopped(timeout: Long, unit: TimeUnit) = stopped.await(timeout, unit)
        fun isStopped() = stopped.count == 0L
    }
}
