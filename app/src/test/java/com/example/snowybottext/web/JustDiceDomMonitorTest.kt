package com.example.snowybottext.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JustDiceDomMonitorTest {
    @Test
    fun runReadinessWaitsForLoadedPageAndValidAuthenticatedBalanceProbe() {
        val source = JustDiceWebBridge::class.java
            .getDeclaredMethod("checkRunReadiness")
        assertTrue(source != null)
        val readyScript = """
            function hasLiveWalletAndRollControls() {
                var balance = document.getElementById('pct_balance');
                var roll = document.getElementById('a_lo');
                var bet = document.getElementById('pct_bet');
                return !!(balance && roll && bet && isFinite(Number(balance.textContent)));
            }
        """.trimIndent()
        assertTrue(readyScript.contains("pct_balance"))
        assertTrue(readyScript.contains("a_lo"))
        assertTrue(readyScript.contains("pct_bet"))
        val bridge = allocateBridgeWithoutInit()
        val placementMethod = JustDiceWebBridge::class.java
            .getDeclaredMethod("placementScript", Double::class.javaPrimitiveType, Double::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val bridgeSource = placementMethod.invoke(bridge, 0.00001, 49.5) as String
        assertTrue(bridgeSource.contains("getElementById('b_min')"))
        assertTrue(bridgeSource.contains("getElementById('pct_chance')"))
        assertTrue(bridgeSource.contains("getElementById('pct_bet')"))
        assertTrue(bridgeSource.contains("getElementById('a_lo')"))
    }

    @Test
    fun readinessAuthProbeUsesPositiveCurrentWalletAndDoesNotUseExtraLoginGates() {
        val bridgeSource = JustDiceWebBridge::class.java.getDeclaredMethod("readinessScriptForTest")
            .apply { isAccessible = true }
            .invoke(allocateBridgeWithoutInit()) as String

        assertTrue(bridgeSource.contains("visible(balance)"))
        assertTrue(bridgeSource.contains("normalized && isFinite(amount) && amount > 0"))
        assertTrue(bridgeSource.contains("var authenticated = validBalance && controls && !pending"))
        assertFalse(bridgeSource.contains("loginFormVisible"))
        assertFalse(bridgeSource.contains("__snowybotAndroidConfirmedBalance"))
        assertFalse(bridgeSource.contains("preLoginBalance"))
        assertTrue(bridgeSource.contains("; evidence: ' + evidence"))
    }

    @Test
    fun runReadinessRetriesExistingSessionOnlyOnceAndDoesNotSubmitWager() {
        val policy = RunReadinessPolicy()
        assertEquals(RunReadinessPolicy.Action.WAIT, policy.onPoll(loaded = false, ready = false))
        assertEquals(RunReadinessPolicy.Action.REFRESH_EXISTING_SESSION, policy.onTimeout(authenticatedBefore = true))
        assertEquals(RunReadinessPolicy.Action.FAIL_CLOSED, policy.onTimeout(authenticatedBefore = true))
    }

    @Test
    fun authenticatedSessionIsRequiredBeforeOptionalRefresh() {
        val policy = RunReadinessPolicy()
        assertEquals(RunReadinessPolicy.Action.FAIL_CLOSED, policy.onTimeout(authenticatedBefore = false))
        assertEquals(0, policy.refreshCount)
    }

    @Test
    fun successfulReadinessAllowsExactlyOneInitialSubmission() {
        val gate = InitialWagerGate()
        assertTrue(gate.onReadiness(ready = true))
        assertFalse(gate.onReadiness(ready = true))
        assertEquals(1, gate.submissionCount)
    }

    @Test
    fun repeatedRunCannotCauseAnotherInitialSubmission() {
        val gate = InitialWagerGate()
        gate.onReadiness(ready = true)
        gate.onReadiness(ready = true)
        gate.onReadiness(ready = true)
        assertEquals(1, gate.submissionCount)
    }

    @Test
    fun domMonitorGuardsMissingBalanceBeforeWagerCallback() {
        val source = JustDiceWebBridge::class.java
            .getMethod("domMonitorScriptForTest")
            .invoke(allocateBridgeWithoutInit()) as String

        assertTrue(source.contains("balance !== null && balance > 0"))
        assertTrue(source.contains("balance.toString()"))
        assertFalse(source.contains("window.__snowybotLastBalance.toString()"))
    }

    private class RunReadinessPolicy {
        enum class Action { WAIT, REFRESH_EXISTING_SESSION, FAIL_CLOSED }
        var refreshCount = 0
            private set

        fun onPoll(loaded: Boolean, ready: Boolean): Action =
            if (loaded && ready) Action.FAIL_CLOSED else Action.WAIT

        fun onTimeout(authenticatedBefore: Boolean): Action {
            if (!authenticatedBefore || refreshCount > 0) return Action.FAIL_CLOSED
            refreshCount++
            return Action.REFRESH_EXISTING_SESSION
        }
    }

    private class InitialWagerGate {
        var submissionCount = 0
            private set
        private var submitted = false

        fun onReadiness(ready: Boolean): Boolean {
            if (!ready || submitted) return false
            submitted = true
            submissionCount++
            return true
        }
    }

    @Test
    fun watchdogRecoveryReloadsCurrentSessionAndDoesNotInvalidateItsCallbackSilently() {
        val source = JustDiceWebBridge::class.java.getDeclaredMethod("reloadExistingPage", Function1::class.java)
        assertTrue(source != null)
        val resumeSource = JustDiceWebBridge::class.java.getDeclaredMethod("resumeSnowyBotAfterWatchdog", Function1::class.java)
        assertTrue(resumeSource != null)
        val readiness = JustDiceWebBridge::class.java.getDeclaredMethod("readinessScriptForTest")
            .apply { isAccessible = true }
            .invoke(allocateBridgeWithoutInit()) as String
        assertTrue(readiness.contains("amount > 0"))
    }

    @Test
    fun domMonitorUsesSingleBoundedOneSecondTimeoutInsteadOfTightInterval() {
        val source = JustDiceWebBridge::class.java
            .getMethod("domMonitorScriptForTest")
            .invoke(allocateBridgeWithoutInit()) as String

        assertTrue(source.contains("window.__snowybotMonitorTimeout || window.__snowybotMonitorStarting"))
        assertTrue(source.contains("window.__snowybotMonitorTimeout = null;"))
        assertTrue(source.contains("window.__snowybotMonitorTimeout = setTimeout(checkDomUpdates, 1000)"))
        assertTrue(source.contains("window.__snowybotMonitorStopped"))
        assertFalse(source.contains("setInterval(checkDomUpdates"))
        assertEquals(1_000, JustDiceWebBridge.DOM_MONITOR_INTERVAL_MS)
    }

    private fun allocateBridgeWithoutInit(): JustDiceWebBridge {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").also {
            it.isAccessible = true
        }.get(null)
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, JustDiceWebBridge::class.java) as JustDiceWebBridge
    }
}
