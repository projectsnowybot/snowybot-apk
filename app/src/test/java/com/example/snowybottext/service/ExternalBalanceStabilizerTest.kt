package com.example.snowybottext.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalBalanceStabilizerTest {
    private fun stabilizer() = ExternalBalanceStabilizer(debounceWindowMs = 4_000L, requiredPolls = 3)

    @Test
    fun spikeThenRevertIsRejectedWithoutExternalShift() {
        val filter = stabilizer()
        assertFalse(filter.observe(110.0, 0L, 100.0).externalShift)
        val reverted = filter.observe(100.0, 1_000L, 100.0)
        assertEquals(100.0, reverted.acceptedBalance!!, 0.0)
        assertFalse(reverted.externalShift)
        assertFalse(filter.observe(110.0, 2_000L, 100.0).externalShift)
        assertFalse(filter.observe(100.0, 3_000L, 100.0).externalShift)
    }

    @Test
    fun persistentDepositAcceptedOnlyAfterWindowAndMultiplePolls() {
        val filter = stabilizer()
        assertNull(filter.observe(125.0, 0L, 100.0).acceptedBalance)
        assertNull(filter.observe(125.0, 1_000L, 100.0).acceptedBalance)
        val confirmed = filter.observe(125.0, 4_000L, 100.0)
        assertEquals(125.0, confirmed.acceptedBalance!!, 0.0)
        assertTrue(confirmed.externalShift)
    }

    @Test
    fun normalWinsAndLossesAreAcceptedWithoutExternalReset() {
        val filter = stabilizer()
        val winning = filter.observe(
            balance = 100.5,
            nowMs = 100L,
            lastSettledBalance = 100.0,
            lastWagerAmount = 1.0,
            isWin = true,
        )
        assertEquals(100.5, winning.acceptedBalance!!, 0.0)
        assertFalse(winning.externalShift)

        val losing = filter.observe(
            balance = 99.0,
            nowMs = 200L,
            lastSettledBalance = 100.0,
            lastWagerAmount = 1.0,
            isWin = false,
        )
        assertEquals(99.0, losing.acceptedBalance!!, 0.0)
        assertFalse(losing.externalShift)
    }

    @Test
    fun debounceRequiresBothElapsedWindowAndPollCount() {
        val filter = stabilizer()
        assertNull(filter.observe(101.0, 0L, 100.0).acceptedBalance)
        assertNull(filter.observe(101.0, 1_000L, 100.0).acceptedBalance) // only two samples and window incomplete
        val afterWindow = filter.observe(101.0, 4_000L, 100.0)
        assertTrue(afterWindow.externalShift)
        assertEquals(101.0, afterWindow.acceptedBalance!!, 0.0)
    }

    @Test
    fun expectedWinCanExceedStakeWithoutBeingMistakenForDeposit() {
        val filter = stabilizer()
        val result = filter.observe(
            balance = 110.0,
            nowMs = 1L,
            lastSettledBalance = 100.0,
            lastWagerAmount = 1.0,
            isWin = true,
            winChancePercent = 9.0,
        )
        assertEquals(110.0, result.acceptedBalance!!, 0.0)
        assertFalse(result.externalShift)
    }
}
