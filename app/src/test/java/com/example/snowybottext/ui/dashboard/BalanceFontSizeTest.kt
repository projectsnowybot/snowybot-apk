package com.example.snowybottext.ui.dashboard

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceFontSizeTest {
    @Test
    fun typicalBalanceKeepsOriginalTypography() {
        assertEquals(56.sp, balanceFontSize("0.01420000", 345.dp))
    }

    @Test
    fun shortBalanceGrowsBackAfterLongValueShrinks() {
        val longValueSize = balanceFontSize("12345678901234567890.12345678", 345.dp)
        val shortValueSize = balanceFontSize("0.01420000", 345.dp)

        assertTrue(longValueSize.value < 56f)
        assertEquals(56.sp, shortValueSize)
    }

    @Test
    fun balanceGrowsWhenAvailableWidthIncreases() {
        val narrowWidthSize = balanceFontSize("12345678901234567890.12345678", 220.dp)
        val wideWidthSize = balanceFontSize("12345678901234567890.12345678", 600.dp)

        assertTrue(wideWidthSize.value > narrowWidthSize.value)
        assertTrue(wideWidthSize.value <= 56f)
    }

    @Test
    fun longBalanceShrinksAndRemainsAboveMinimum() {
        val fontSize = balanceFontSize("12345678901234567890.12345678", 345.dp)
        assertTrue(fontSize.value < 56f)
        assertTrue(fontSize.value >= 12f)
    }

    @Test
    fun veryLongBalanceClampsAtMinimum() {
        assertEquals(12.sp, balanceFontSize("1".repeat(100), 345.dp))
    }

    @Test
    fun typicalBetKeepsIntendedTypographyWhenItFits() {
        assertEquals(28.sp, metricFontSize("0.00001000", 180.dp, 28.sp))
    }

    @Test
    fun shortBetShrinksWhenColumnIsTooNarrow() {
        assertTrue(metricFontSize("0.00001000", 160.dp, 28.sp).value < 28f)
    }

    @Test
    fun longBetShrinksAndRemainsAboveMinimum() {
        val fontSize = metricFontSize("12345678901234567890.12345678", 160.dp, 28.sp)
        assertTrue(fontSize.value < 28f)
        assertTrue(fontSize.value >= 12f)
    }

    @Test
    fun betGrowsWhenValueShortensAndWidthExpands() {
        val longValueSize = metricFontSize("12345678901234567890.12345678", 160.dp, 28.sp)
        val shortValueSize = metricFontSize("0.00001000", 180.dp, 28.sp)
        val widerSize = metricFontSize("12345678901234567890.12345678", 500.dp, 28.sp)

        assertTrue(shortValueSize.value > longValueSize.value)
        assertEquals(28.sp, shortValueSize)
        assertTrue(widerSize.value > longValueSize.value)
    }

    @Test
    fun veryLongBetClampsAtMinimum() {
        assertEquals(12.sp, metricFontSize("1".repeat(100), 160.dp, 28.sp))
    }

    @Test
    fun longProfitShrinksAndRemainsAboveMinimum() {
        val fontSize = metricFontSize("+12345678901234567890.12345678", 160.dp, 28.sp)
        assertTrue(fontSize.value < 28f)
        assertTrue(fontSize.value >= 12f)
    }

    @Test
    fun profitGrowsBackWhenValueShortens() {
        val longValueSize = metricFontSize("+12345678901234567890.12345678", 160.dp, 28.sp)
        val shortValueSize = metricFontSize("+0.00020000", 190.dp, 28.sp)

        assertTrue(shortValueSize.value > longValueSize.value)
        assertEquals(28.sp, shortValueSize)
    }

    @Test
    fun veryLongProfitClampsAtMinimum() {
        assertEquals(12.sp, metricFontSize("+" + "1".repeat(100), 160.dp, 28.sp))
    }
}
