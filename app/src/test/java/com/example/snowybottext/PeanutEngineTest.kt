package com.example.snowybottext

import com.example.snowybottext.engine.PeanutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeanutEngineTest {

    @Test
    fun testInitializationCalculation() {
        val logs = mutableListOf<String>()
        val engine = PeanutEngine()
        val initialBalance = 14.4 // 14.4 balance units
        engine.initialize(initialBalance, onLog = { logs.add(it) })

        val state = engine.state
        assertEquals(14.4, state.startingPocketChange, 0.00000001)

        // tinyPeanutSize = round8(14.4 / 1440000.0) = 0.00001000
        assertEquals(0.00001, state.tinyPeanutSize, 0.00000001)
        assertEquals(0.00001, state.backupPeanut, 0.00000001)
        assertEquals(0.0001, state.tenPeanuts, 0.00000001)
        assertEquals(14.4, state.safetyCheckpoint, 0.00000001)
        assertEquals(14.4, state.checkpointJuice, 0.00000001)
        assertEquals(1.0, state.wobbleFactor, 0.00001)

        assertEquals(2, logs.size)
        assertEquals("POCKET CHANGE DETECTED: 14.4", logs[0])
        assertEquals("SAFETY CHECKPOINT ANCHORED AT: 14.4", logs[1])
    }

    @Test
    fun initializationRejectsWalletAmountsThatRoundTheOpeningWagerToZero() {
        val engine = PeanutEngine()
        try {
            engine.initialize(0.000001)
            throw AssertionError("An unplaceable rounded wager must be rejected before Run starts")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Starting balance is too small to produce a positive wager at 8-decimal precision", expected.message)
        }
    }

    @Test
    fun progressionRejectsWagersThatRoundDownToZeroInsteadOfReturningAnInvalidAmount() {
        val engine = PeanutEngine()
        engine.initialize(1.0)

        try {
            engine.calculateNextProgressionStep(0.000000001, 0.999999)
            throw AssertionError("Progression must not return a zero wager")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Calculated wager must be positive and finite at 8-decimal precision", expected.message)
        }
    }

    @Test
    fun testLowWagerGainProgression() {
        val engine = PeanutEngine()
        val initialBalance = 10.0
        engine.initialize(initialBalance)

        val tinyPeanut = engine.state.tinyPeanutSize
        val initialWager = tinyPeanut

        // Rule 2: (currentWager < backupPeanut * 1.5) and (walletStash > checkpointJuice + currentWager * 6.9)
        // Ensure walletStash remains below safetyCheckpoint + tenPeanuts (10.00006944)
        val gainBalance = 10.0 + (initialWager * 6.95)
        val doubledWager = engine.calculateNextProgressionStep(initialWager, gainBalance)
        assertEquals(initialWager * 2.0, doubledWager, 0.00000001)
    }

    @Test
    fun testLowWagerDrawdownProgression() {
        val engine = PeanutEngine()
        val initialBalance = 10.0
        engine.initialize(initialBalance)

        val tinyPeanut = engine.state.tinyPeanutSize
        val initialWager = tinyPeanut

        // Rule 3: (currentWager < backupPeanut * 1.5) and (walletStash < checkpointJuice - currentWager * 2.9)
        val droppedBalance = engine.state.checkpointJuice - (initialWager * 3.0)
        val doubledWager = engine.calculateNextProgressionStep(initialWager, droppedBalance)
        assertEquals(initialWager * 2.0, doubledWager, 0.00000001)
    }

    @Test
    fun testHighWagerGainProgression() {
        val engine = PeanutEngine()
        val initialBalance = 10.0
        engine.initialize(initialBalance)

        val tinyPeanut = engine.state.tinyPeanutSize
        val highWager = tinyPeanut * 2.0 // > backupPeanut * 1.5

        // First simulate a drawdown below checkpoint Juice (e.g. at 9.9000000)
        val lowBalance = 9.9000000
        engine.calculateNextProgressionStep(highWager, lowBalance) // updates checkpointJuice to 9.9000000

        // Rule 4: (currentWager > backupPeanut * 1.5) and (walletStash > checkpointJuice + currentWager * 4.9)
        // Keep gain balance below safetyCheckpoint so Rule 1 does not reset it
        val gainBalance = 9.9000000 + (highWager * 5.0)
        val doubledWager = engine.calculateNextProgressionStep(highWager, gainBalance)
        assertEquals(highWager * 2.0, doubledWager, 0.00000001)
    }

    @Test
    fun testDrawdownJumpAndWobbleReset() {
        val engine = PeanutEngine()
        val initialBalance = 10.0
        engine.initialize(initialBalance)

        val tinyPeanut = engine.state.tinyPeanutSize
        val highWager = tinyPeanut * 2.0 // > backupPeanut * 1.5

        // Rule 5: (currentWager > backupPeanut * 1.5) and (walletStash < checkpointJuice - highWager * 4.9)
        val droppedBalance = engine.state.checkpointJuice - (highWager * 5.0)
        val nextWager = engine.calculateNextProgressionStep(highWager, droppedBalance)

        // Should double wager and reset wobbleFactor to 0.0
        assertEquals(highWager * 2.0, nextWager, 0.00000001)
        assertEquals(0.0, engine.state.wobbleFactor, 0.00001)
    }

    @Test
    fun progressionOutputBecomesTheExactNextPlacementAfterWinOrDrawdown() {
        val winEngine = PeanutEngine()
        winEngine.initialize(10.0)
        val base = winEngine.state.backupPeanut
        val winBalance = winEngine.state.checkpointJuice + base * 6.95
        val winNext = winEngine.processRollResult(101L, 20.0, 1, 0, winBalance)
        assertEquals(base * 2.0, winNext!!, 0.00000001)
        assertEquals(winNext, winEngine.state.currentWagerAmount, 0.0)
        assertEquals(winNext, winEngine.state.previousWagerAmount, 0.0) // next placement stake

        val drawdownEngine = PeanutEngine()
        drawdownEngine.initialize(10.0)
        val drawdownBalance = drawdownEngine.state.checkpointJuice - base * 3.0
        val drawdownNext = drawdownEngine.processRollResult(201L, 70.0, 0, 1, drawdownBalance)
        assertEquals(base * 2.0, drawdownNext!!, 0.00000001)
        assertEquals(drawdownNext, drawdownEngine.state.currentWagerAmount, 0.0)
        assertEquals(drawdownNext, drawdownEngine.state.previousWagerAmount, 0.0)
    }

    @Test
    fun safetyCheckpointResetIsTheNextPlacementAndRestoresWobble() {
        val engine = PeanutEngine()
        engine.initialize(10.0)
        val expectedBaseWager = engine.state.backupPeanut
        val safeBalance = engine.state.safetyCheckpoint + engine.state.tenPeanuts + 0.0001

        val nextWager = engine.processRollResult(301L, 20.0, 1, 0, safeBalance)

        assertEquals(expectedBaseWager, nextWager!!, 0.00000001)
        assertEquals(expectedBaseWager, engine.state.currentWagerAmount, 0.0)
        assertEquals(1.0, engine.state.wobbleFactor, 0.0)
        assertTrue(engine.state.safetyCheckpoint >= safeBalance - engine.state.tenPeanuts)
    }

    @Test
    fun testSafetyCheckpointTrigger() {
        val engine = PeanutEngine()
        val initialBalance = 10.0
        engine.initialize(initialBalance)

        val tenPeanuts = engine.state.tenPeanuts
        // Rule 1: walletStash >= safetyCheckpoint + tenPeanuts * wobble
        val safeBalance = 10.0 + tenPeanuts + 0.0001
        val nextWager = engine.calculateNextProgressionStep(0.001, safeBalance)

        assertEquals(engine.state.backupPeanut, nextWager, 0.00000001)
        assertEquals(1.0, engine.state.wobbleFactor, 0.00001)
    }

    @Test
    fun testProcessRollResult() {
        val engine = PeanutEngine()
        val initialBalance = 10.0
        engine.initialize(initialBalance)

        val wager = engine.processRollResult(
            shinyNewTicket = 1001L,
            rollOutcome = 25.4, // Win (< 49.5)
            winsCount = 1,
            lossesCount = 0,
            walletStash = 10.00001
        )

        assertNotNull(wager)
        assertEquals(1001L, engine.state.shinyNewTicket)
        assertEquals(1, engine.state.oopsieCounter)
        assertEquals(1, engine.state.luckyCoinFlip)
        assertEquals(1, engine.state.totalSessionWins)

        // Duplicate ticket should be ignored
        val dupWager = engine.processRollResult(
            shinyNewTicket = 1001L,
            rollOutcome = 25.4,
            winsCount = 1,
            lossesCount = 0,
            walletStash = 10.00001
        )
        assertNull(dupWager)
    }
}
