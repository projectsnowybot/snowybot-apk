package com.example.snowybottext.service

import kotlin.math.abs

/**
 * Debounces candidate wallet shifts before deciding whether they are external. Balance events
 * consistent with the most recent wager's settlement envelope are accepted immediately and never
 * interpreted as deposits. Other changes must persist across multiple polls and the full window.
 */
internal class ExternalBalanceStabilizer(
    private val debounceWindowMs: Long = DEFAULT_DEBOUNCE_WINDOW_MS,
    private val requiredPolls: Int = DEFAULT_REQUIRED_POLLS,
    private val tolerance: Double = BALANCE_TOLERANCE,
) {
    data class Decision(val acceptedBalance: Double?, val externalShift: Boolean)

    private data class Candidate(
        val balance: Double,
        val firstSeenAtMs: Long,
        val polls: Int,
    )

    private var candidate: Candidate? = null

    init {
        require(debounceWindowMs >= 0L)
        require(requiredPolls >= 2)
        require(tolerance >= 0.0 && tolerance.isFinite())
    }

    /**
     * [lastSettledBalance] is the previous accepted balance; [lastWagerAmount] and [isWin]
     * describe a known completed wager, when available. A settlement delta has a tightly bounded
     * amount: losses are approximately the stake; wins are at most stake * (100/chance - 1).
     */
    fun observe(
        balance: Double,
        nowMs: Long,
        lastSettledBalance: Double,
        lastWagerAmount: Double? = null,
        isWin: Boolean? = null,
        winChancePercent: Double = DEFAULT_WIN_CHANCE_PERCENT,
    ): Decision {
        if (!balance.isFinite() || balance <= 0.0 || !lastSettledBalance.isFinite() || lastSettledBalance <= 0.0) {
            return Decision(null, false)
        }
        if (close(balance, lastSettledBalance)) {
            candidate = null
            return Decision(balance, false)
        }

        if (isExpectedSettlement(balance, lastSettledBalance, lastWagerAmount, isWin, winChancePercent)) {
            candidate = null
            return Decision(balance, false)
        }

        val current = candidate
        if (current == null || !close(current.balance, balance)) {
            candidate = Candidate(balance, nowMs, 1)
            return Decision(null, false)
        }

        val updated = current.copy(polls = current.polls + 1)
        candidate = updated
        if (updated.polls >= requiredPolls && nowMs - updated.firstSeenAtMs >= debounceWindowMs) {
            candidate = null
            return Decision(balance, true)
        }
        return Decision(null, false)
    }

    fun reset() {
        candidate = null
    }

    private fun isExpectedSettlement(
        balance: Double,
        previous: Double,
        wager: Double?,
        isWin: Boolean?,
        chancePercent: Double,
    ): Boolean {
        if (wager == null || !wager.isFinite() || wager <= 0.0 || chancePercent <= 0.0 || chancePercent >= 100.0) {
            return false
        }
        val delta = balance - previous
        val absDelta = abs(delta)
        val loss = isWin == false || (isWin == null && delta < 0.0)
        if (loss) return delta < 0.0 && abs(absDelta - wager) <= maxOf(tolerance, wager * SETTLEMENT_RELATIVE_TOLERANCE)
        if (delta <= 0.0) return false
        val maximumProfit = wager * (100.0 / chancePercent - 1.0)
        return delta <= maximumProfit + maxOf(tolerance, maximumProfit * SETTLEMENT_RELATIVE_TOLERANCE)
    }

    private fun close(first: Double, second: Double): Boolean = abs(first - second) <= maxOf(tolerance, maxOf(abs(first), abs(second)) * BALANCE_RELATIVE_TOLERANCE)

    companion object {
        const val DEFAULT_DEBOUNCE_WINDOW_MS = 4_000L
        const val DEFAULT_REQUIRED_POLLS = 3
        const val DEFAULT_WIN_CHANCE_PERCENT = 49.5
        private const val BALANCE_TOLERANCE = 1e-8
        private const val BALANCE_RELATIVE_TOLERANCE = 1e-10
        private const val SETTLEMENT_RELATIVE_TOLERANCE = 1e-6
    }
}
