package com.example.snowybottext.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JustDiceJsBridgeTest {
    @Test
    fun loginScriptSubmitsOnceOnlyAfterControlsExistAndSupportsOptionalTwoFactor() {
        val bridgeFile = File("src/main/java/com/example/snowybottext/web/JustDiceWebBridge.kt")
        val source = bridgeFile.readText()
        val loginBlock = source.substringAfter("fun performLogin(").substringBefore("/** Cancels all login-specific")

        assertTrue(loginBlock.contains("var submitted = false"))
        assertTrue(loginBlock.contains("if (submitted) return"))
        assertTrue(loginBlock.contains("document.getElementById('myuser')"))
        assertTrue(loginBlock.contains("document.getElementById('mypass')"))
        assertTrue(loginBlock.contains("document.getElementById('myok')"))
        assertTrue(loginBlock.contains("codeEl) {"))
        assertTrue(loginBlock.contains("okBtn.click()"))
        assertTrue(loginBlock.contains("onLoginCompleted(false)"))
    }

    @Test
    fun loginUsesLoadedPageAndDoesNotDependOnWagerReadinessSelectors() {
        val serviceFile = File("src/main/java/com/example/snowybottext/service/JustDiceBotService.kt")
        val source = serviceFile.readText()
        val loginBlock = source.substringAfter("private fun startLoginExecution()")

        assertTrue(loginBlock.contains("bridge.performLogin(username, password, code)"))
        assertFalse(loginBlock.contains("awaitRunReadiness"))
    }

    @Test
    fun currentWagerCallbackAcceptsPositiveScriptWagerAndRejectsInvalidAmounts() {
        val wagers = mutableListOf<Double>()
        val listener = object : JustDiceBridgeListener {
            override fun onBalanceUpdated(balance: Double) = Unit
            override fun onCurrentWagerAmount(amount: Double) { wagers += amount }
            override fun onStatsUpdated(wins: Int, losses: Int) = Unit
            override fun onWagerResult(wagerId: Long, betAmount: Double, rollResult: Double, isWin: Boolean, profit: Double, balanceAfter: Double) = Unit
            override fun onLog(message: String) = Unit
            override fun onLoginCompleted(success: Boolean) = Unit
        }
        val bridge = JustDiceJsBridge(listener)

        bridge.onCurrentWagerAmount("1440000")
        bridge.onCurrentWagerAmount("2880000")
        bridge.onCurrentWagerAmount("0")
        bridge.onCurrentWagerAmount("NaN")

        assertEquals(listOf(1440000.0, 2880000.0), wagers)
    }

    @Test
    fun loginBalancePollStartedInvokesConstructorCallbackExactlyOnce() {
        var callbackCalls = 0
        val listener = object : JustDiceBridgeListener {
            override fun onBalanceUpdated(balance: Double) = Unit
            override fun onCurrentWagerAmount(amount: Double) = Unit
            override fun onStatsUpdated(wins: Int, losses: Int) = Unit
            override fun onWagerResult(
                wagerId: Long,
                betAmount: Double,
                rollResult: Double,
                isWin: Boolean,
                profit: Double,
                balanceAfter: Double,
            ) = Unit
            override fun onLog(message: String) = Unit
            override fun onLoginCompleted(success: Boolean) = Unit
        }
        val bridge = JustDiceJsBridge(
            listener = listener,
            onLoginBalancePollStartedCallback = { callbackCalls++ },
        )

        bridge.onLoginBalancePollStarted()

        assertEquals(1, callbackCalls)
    }
}
