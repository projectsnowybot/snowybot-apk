package com.example.snowybottext.ui.dashboard

import com.example.snowybottext.data.local.RollEntity
import com.example.snowybottext.service.BotStatus
import com.example.snowybottext.service.LoginFlowPolicy

/**
 * UI State for the single-page Dashboard screen.
 */
enum class ReadyState { NOT_READY, CHECKING, READY }

data class DashboardUiState(
    val status: BotStatus = BotStatus.STOPPED,
    val loginError: String? = null,
    val runtimeError: String? = null,
    val walletStash: Double = 0.0,
    val startingPocketChange: Double = 0.0,
    val targetLimit: Double = 144000.0,
    val currentWager: Double = 0.0,
    val safetyCheckpoint: Double = 0.0,
    val checkpointJuice: Double = 0.0,
    val wobbleFactor: Double = 1.0,
    val oopsieCounter: Int = 0,
    val totalWins: Int = 0,
    val totalLosses: Int = 0,
    val recentRolls: List<RollEntity> = emptyList(),
    val activityLogs: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val showResetConfirmation: Boolean = false,
    val readyState: ReadyState = ReadyState.NOT_READY,
    // Login / Credential inputs
    val username: String = "",
    val password: String = "",
    val code2FA: String = "",
    val isPasswordVisible: Boolean = false,
    val saveMessage: String? = null
) {
    val profitLoss: Double
        get() = if (startingPocketChange > 0.0) walletStash - startingPocketChange else 0.0

    val winRate: Double
        get() {
            val total = totalWins + totalLosses
            return if (total > 0) (totalWins.toDouble() / total) * 100.0 else 0.0
        }

    val isRunning: Boolean get() = status == BotStatus.RUNNING
    val isRunButtonSelected: Boolean get() = isRunning
    val isLoggingIn: Boolean get() = LoginFlowPolicy.isAuthenticating(status, status == BotStatus.LOGGING_IN)
    val canLogin: Boolean get() = !isLoggingIn
    val isSessionAuthenticated: Boolean get() = walletStash.isFinite() && walletStash > 0.0
    val canRun: Boolean get() = readyState == ReadyState.READY && status != BotStatus.RUNNING && status != BotStatus.STALLED && status != BotStatus.LOGGING_IN

    val targetProgress: Float
        get() {
            if (targetLimit <= startingPocketChange || targetLimit <= 0.0) return 0f
            val gain = walletStash - startingPocketChange
            val needed = targetLimit - startingPocketChange
            return (gain / needed).coerceIn(0.0, 1.0).toFloat()
        }
}
