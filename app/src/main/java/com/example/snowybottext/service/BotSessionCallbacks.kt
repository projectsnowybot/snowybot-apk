package com.example.snowybottext.service

import com.example.snowybottext.web.JustDiceBridgeListener

/** Routes callbacks from the activity-owned WebView to the currently bound service. */
object BotSessionCallbacks : JustDiceBridgeListener {
    @Volatile private var delegate: JustDiceBridgeListener? = null

    fun attach(listener: JustDiceBridgeListener) { delegate = listener }
    fun detach(listener: JustDiceBridgeListener) { if (delegate === listener) delegate = null }

    override fun onBalanceUpdated(balance: Double) { delegate?.onBalanceUpdated(balance) }
    override fun onCurrentWagerAmount(amount: Double) { delegate?.onCurrentWagerAmount(amount) }
    override fun onStatsUpdated(wins: Int, losses: Int) { delegate?.onStatsUpdated(wins, losses) }
    override fun onWagerResult(wagerId: Long, betAmount: Double, rollResult: Double, isWin: Boolean, profit: Double, balanceAfter: Double) {
        delegate?.onWagerResult(wagerId, betAmount, rollResult, isWin, profit, balanceAfter)
    }
    override fun onLog(message: String) { delegate?.onLog(message) }
    override fun onLoginCompleted(success: Boolean) { delegate?.onLoginCompleted(success) }
    override fun logMessage(message: String) { delegate?.logMessage(message) }
}
