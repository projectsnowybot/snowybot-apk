package com.example.snowybottext.engine

import com.example.snowybottext.service.ExternalBalanceStabilizer
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor

/**
 * Pure Kotlin implementation of the Peanut Strategy engine.
 * Ports the wager progression, safety checkpoints, drawdown jumps, and wobble factor algorithms
 * directly from original Python bot script (snowybot.py).
 */
class PeanutEngine(
    initialState: PeanutStrategyState = PeanutStrategyState()
) {
    var state: PeanutStrategyState = initialState
        private set

    /**
     * Initializes engine state with starting balance scraped directly from #pct_balance on webpage.
     * Computes tinyPeanutSize = startingBalance / 1440000.0 immediately upon receiving the initial balance.
     */
    fun initialize(
        startingBalance: Double,
        restoredState: PeanutStrategyState? = null,
        onLog: ((String) -> Unit)? = null
    ) {
        require(startingBalance.isFinite() && startingBalance > 0.0) {
            "Starting balance must be a positive finite value"
        }
        if (restoredState != null && restoredState.startingPocketChange > 0.0) {
            state = restoredState.copy(
                walletStash = startingBalance,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            onLog?.invoke("POCKET CHANGE DETECTED: ${state.startingPocketChange}")
            onLog?.invoke("SAFETY CHECKPOINT ANCHORED AT: ${state.safetyCheckpoint}")
            return
        }

        val tinyPeanut = round8(startingBalance / 1_440_000.0)
        require(tinyPeanut.isFinite() && tinyPeanut > 0.0) {
            "Starting balance is too small to produce a positive wager at 8-decimal precision"
        }
        val backup = tinyPeanut
        val tenP = tinyPeanut * 10.0
        val checkpoint = floorCheckpoint(startingBalance, tenP)
        val juice = checkpoint

        state = PeanutStrategyState(
            startingPocketChange = startingBalance,
            tinyPeanutSize = tinyPeanut,
            backupPeanut = backup,
            tenPeanuts = tenP,
            walletStash = startingBalance,
            areWeRichYet = false,
            oopsieCounter = 0,
            previousWalletState = startingBalance,
            oldTicketStub = 0,
            shinyNewTicket = 0,
            totalSessionWins = 0,
            totalSessionLosses = 0,
            baseWinReference = 0.0,
            baseLossReference = 0.0,
            currentWagerAmount = backup,
            previousWagerAmount = backup,
            luckyCoinFlip = 0,
            checkpointJuice = juice,
            wobbleFactor = 1.0,
            safetyCheckpoint = checkpoint,
            lastSeenTimestamp = System.currentTimeMillis()
        )

        onLog?.invoke("POCKET CHANGE DETECTED: $startingBalance")
        onLog?.invoke("SAFETY CHECKPOINT ANCHORED AT: $checkpoint")
    }

    /**
     * Legacy/convenience method delegating to initialize.
     */
    fun initWithBalance(
        currentBalance: Double,
        restoredState: PeanutStrategyState? = null,
        configuredStartPocket: Double = 0.0,
        onLog: ((String) -> Unit)? = null
    ) {
        val startPocket = if (configuredStartPocket > 0.0) configuredStartPocket else currentBalance
        initialize(startPocket, restoredState, onLog)
    }

    /**
     * Calculates the next wager progression step given the incoming wager amount and current wallet balance.
     * Updates internal engine state (safetyCheckpoint, checkpointJuice, wobbleFactor, etc.).
     */
    fun calculateNextProgressionStep(
        incomingWager: Double,
        currentWalletStash: Double,
        onLog: ((String) -> Unit)? = null
    ): Double {
        require(currentWalletStash.isFinite() && currentWalletStash > 0.0) {
            "Wallet stash must be a positive finite value"
        }
        val walletStash = currentWalletStash
        var currentWager = incomingWager
        var wobble = state.wobbleFactor
        var checkpointJuice = state.checkpointJuice
        var safetyCheckpoint = state.safetyCheckpoint

        val tinyPeanutSize = state.tinyPeanutSize
        val backupPeanut = state.backupPeanut
        val tenPeanuts = if (state.tenPeanuts > 0.0) state.tenPeanuts else (tinyPeanutSize * 10.0)

        // Rule 1: Safety Checkpoint check
        if (walletStash >= (safetyCheckpoint + ((tinyPeanutSize * 10.0) * wobble))) {
            currentWager = backupPeanut
            wobble = 1.0
            checkpointJuice = floorCheckpoint(walletStash, tenPeanuts)
            safetyCheckpoint = floorCheckpoint(walletStash, tenPeanuts)
            onLog?.invoke("SAFETY RESET: Balance reached safety threshold. Wager reset to base $backupPeanut")
        }

        // Rule 2: Low wager gain progression
        if ((currentWager < (backupPeanut * 1.5)) && (walletStash > (checkpointJuice + (currentWager * 6.9)))) {
            currentWager = currentWager * 2.0
            checkpointJuice = walletStash
            onLog?.invoke("PROFIT JUMP: Increasing wager to ${round8(currentWager)}")
        }

        // Rule 3: Low wager drawdown progression
        if ((currentWager < (backupPeanut * 1.5)) && (walletStash < (checkpointJuice - (currentWager * 2.9)))) {
            currentWager = currentWager * 2.0
            checkpointJuice = walletStash
            onLog?.invoke("DRAWDOWN JUMP: Increasing wager to ${round8(currentWager)}")
        }

        // Rule 4: High wager gain progression
        if ((currentWager > (backupPeanut * 1.5)) && (walletStash > (checkpointJuice + (currentWager * 4.9)))) {
            currentWager = currentWager * 2.0
            checkpointJuice = walletStash
            onLog?.invoke("PROFIT JUMP: Increasing wager to ${round8(currentWager)}")
        }

        // Rule 5: High wager drawdown jump & wobble reset
        if ((currentWager > (backupPeanut * 1.5)) && (walletStash < (checkpointJuice - (currentWager * 4.9)))) {
            currentWager = currentWager * 2.0
            wobble = 0.0
            checkpointJuice = walletStash
            onLog?.invoke("DRAWDOWN JUMP: Increasing wager to ${round8(currentWager)}")
        }

        val roundedWager = round8(currentWager)
        require(roundedWager.isFinite() && roundedWager > 0.0) {
            "Calculated wager must be positive and finite at 8-decimal precision"
        }

        state = state.copy(
            walletStash = walletStash,
            currentWagerAmount = roundedWager,
            wobbleFactor = wobble,
            checkpointJuice = checkpointJuice,
            safetyCheckpoint = safetyCheckpoint,
            lastSeenTimestamp = System.currentTimeMillis()
        )

        return roundedWager
    }

    /**
     * Legacy diagnostic helper. Balance-shift resets must be decided by the external balance
     * stabilizer; a magnitude-only comparison can classify legitimate high-odds wins as deposits.
     */
    fun isUnexpectedBalanceShift(
        newBalance: Double,
        previousBalance: Double = if (state.previousWalletState > 0.0) state.previousWalletState else state.walletStash,
        expectedDelta: Double = if (state.oopsieCounter > 0) state.previousWagerAmount else 0.0,
        tolerance: Double = 1e-8
    ): Boolean {
        if (previousBalance <= 0.0 || newBalance <= 0.0 || !previousBalance.isFinite() || !newBalance.isFinite()) {
            return false
        }
        val actualDiff = abs(newBalance - previousBalance)
        return actualDiff > (expectedDelta + tolerance)
    }

    /**
     * Resets strategy state due to an unexpected balance shift:
     * - Recalculates base bet as newBalance / 1,440,000.0
     * - Re-initializes strategy state using newBalance as starting balance.
     */
    fun resetOnBalanceShift(newBalance: Double, onLog: ((String) -> Unit)? = null): Double {
        initialize(newBalance, restoredState = null, onLog = onLog)
        return state.tinyPeanutSize
    }

    /**
     * Checks for an unexpected balance shift, and if present, resets strategy state.
     * Returns true if reset occurred, false otherwise.
     */
    fun checkAndResetBalanceShift(
        newBalance: Double,
        previousBalance: Double = if (state.previousWalletState > 0.0) state.previousWalletState else state.walletStash,
        expectedDelta: Double = if (state.oopsieCounter > 0) state.previousWagerAmount else 0.0,
        onLog: ((String) -> Unit)? = null
    ): Boolean {
        if (!newBalance.isFinite() || newBalance <= 0.0) return false
        // Delegate this legacy API to the debounce classifier: magnitude alone is not evidence,
        // since a valid high-multiplier win can exceed the stake substantially.
        val filter = ExternalBalanceStabilizer()
        return filter.observe(
            balance = newBalance,
            nowMs = 0L,
            lastSettledBalance = previousBalance,
            lastWagerAmount = expectedDelta.takeIf { it > 0.0 },
        ).externalShift
    }

    /**
     * Evaluates a completed wager roll and determines whether a new wager should be placed.
     * Returns the next wager amount to place, or null for a duplicate ticket.
     */
    fun processRollResult(
        shinyNewTicket: Long,
        rollOutcome: Double,
        winsCount: Int,
        lossesCount: Int,
        walletStash: Double,
        targetLimit: Double = Double.POSITIVE_INFINITY,
        onLog: ((String) -> Unit)? = null
    ): Double? {
        require(walletStash.isFinite() && walletStash > 0.0) {
            "Wallet stash must be a positive finite value"
        }
        val oldTicket = state.oldTicketStub
        val oopsie = state.oopsieCounter

        if (shinyNewTicket <= oldTicket && oopsie > 0) {
            return null
        }

        // Calculate next progression step based on previous wager amount
        val computedNextBet = calculateNextProgressionStep(state.previousWagerAmount, walletStash, onLog)

        val targetReached = targetLimit.isFinite() && walletStash >= targetLimit

        val coinFlip = when {
            rollOutcome in 0.0..<49.5000 -> 1
            rollOutcome >= 49.5000 -> 0
            else -> state.luckyCoinFlip
        }

        var baseWinRef = state.baseWinReference
        var baseLossRef = state.baseLossReference

        if (oopsie == 0) {
            state = state.copy(
                shinyNewTicket = shinyNewTicket,
                oldTicketStub = shinyNewTicket,
                previousWagerAmount = computedNextBet,
                previousWalletState = walletStash,
                oopsieCounter = 1,
                luckyCoinFlip = coinFlip,
                totalSessionWins = winsCount,
                totalSessionLosses = lossesCount,
                areWeRichYet = targetReached,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        } else {
            if (coinFlip == 1) {
                baseWinRef += 1.0
            } else {
                baseLossRef += 1.0
            }
            state = state.copy(
                shinyNewTicket = shinyNewTicket,
                oldTicketStub = shinyNewTicket,
                previousWagerAmount = computedNextBet,
                previousWalletState = walletStash,
                oopsieCounter = oopsie + 1,
                luckyCoinFlip = coinFlip,
                baseWinReference = baseWinRef,
                baseLossReference = baseLossRef,
                totalSessionWins = winsCount,
                totalSessionLosses = lossesCount,
                areWeRichYet = targetReached,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        }

        return if (targetReached) null else computedNextBet
    }

    companion object {
        fun round8(value: Double): Double {
            return BigDecimal.valueOf(value)
                .setScale(8, RoundingMode.HALF_UP)
                .toDouble()
        }

        fun floorCheckpoint(balance: Double, tenPeanuts: Double): Double {
            if (tenPeanuts <= 0.0) return balance
            return floor(balance / tenPeanuts) * tenPeanuts
        }
    }
}
