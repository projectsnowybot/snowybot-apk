package com.example.snowybottext.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JustDicePlacementScriptTest {
    @Test
    fun placementScriptWritesProgressedEightDecimalStakeAfterMinimumAndBeforeRoll() {
        val source = bridgeWithoutInit().placementScriptForTest(0.00002468, 49.5)
        assertTrue(source.contains("getElementById('pct_bet')"))
        assertTrue(source.contains("var amount = '0.00002468'"))
        assertTrue(source.indexOf("minBtn.click()") < source.indexOf("betInput.value = amount"))
        assertTrue(source.indexOf("betInput.value = amount") < source.indexOf("rollBtn.click()"))
        assertTrue(source.contains("new Event('input', { bubbles: true })"))
        assertTrue(source.contains("#pct_bet normalized requested"))
        assertTrue(source.contains("exceeds available/site maximum"))
        assertTrue(source.contains("below site minimum"))
        assertTrue(source.contains("window.__snowybotPlacementPending = true"))
    }

    @Test
    fun placementRejectsSubPrecisionNonfiniteAndInvalidAmountsBeforeClick() {
        val bridge = bridgeWithoutInit()
        val tiny = bridge.placementScriptForTest(0.000000004, 49.5)
        assertTrue(tiny.contains("var amount = '0.00000000'"))
        assertTrue(tiny.contains("stake is below 8-decimal precision"))
        assertTrue(tiny.indexOf("stake is below 8-decimal precision") < tiny.indexOf("rollBtn.click()"))
        val invalid = bridge.placementScriptForTest(Double.NaN, 49.5)
        assertTrue(invalid.contains("var amount = 'NaN'") || invalid.contains("var amount = 'NaN"))
        assertTrue(invalid.contains("!isFinite(numericAmount)"))
    }

    @Test
    fun placementValidatesRequiredControlsDisabledStateAndMaintainsOneClick() {
        val source = bridgeWithoutInit().placementScriptForTest(0.00001, 49.5)
        assertTrue(source.contains("stale/in-flight pending gate is still set"))
        assertTrue(source.contains("!betInput || !chanceInput || !rollBtn || !balance || !minBtn"))
        assertTrue(source.contains("disabled (bet="))
        assertTrue(source.contains("window.__snowybotPlacementPending = true"))
        assertTrue(source.indexOf("window.__snowybotPlacementPending = true") < source.indexOf("rollBtn.click()"))
        assertEquals(1, Regex("rollBtn\\.click\\(\\)").findAll(source).count())
        assertFalse(source.contains("setInterval"))
        assertFalse(source.contains("runPrimaryBettingLoop"))
    }

    @Test
    fun formatUsesLocaleStableEightDecimalStakeAndFourDecimalChance() {
        val source = bridgeWithoutInit().placementScriptForTest(12.3, 24.25)
        assertTrue(source.contains("var amount = '12.30000000'"))
        assertTrue(source.contains("var chance = '24.2500'"))
    }

    private fun bridgeWithoutInit(): JustDiceWebBridge {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").also { it.isAccessible = true }.get(null)
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, JustDiceWebBridge::class.java) as JustDiceWebBridge
    }
}
