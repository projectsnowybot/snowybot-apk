package com.example.snowybottext.ui.dashboard

import com.example.snowybottext.service.BotStatus
import com.example.snowybottext.service.LoginFlowPolicy

enum class ReadyState { NOT_READY, CHECKING, READY }

data class DashboardUiState(
    val status: BotStatus = BotStatus.STOPPED,
    val pageStatus: String = "Loading page…",
    val loginError: String? = null,
    val runtimeError: String? = null,
    val walletStash: Double = 0.0,
    val startingPocketChange: Double = 0.0,
    val currentWager: Double = 0.0,
    val currentWagerFromScript: Boolean = false,
    val isLoading: Boolean = false,
    val showResetAllConfirmation: Boolean = false,
    val readyState: ReadyState = ReadyState.NOT_READY,
    val username: String = "",
    val password: String = "",
    val code2FA: String = "",
    val isPasswordVisible: Boolean = false,
    val saveMessage: String? = null,
) {
    val profitLoss: Double
        get() = if (startingPocketChange.isFinite() && startingPocketChange > 0.0 && walletStash.isFinite()) walletStash - startingPocketChange else 0.0

    val isRunning: Boolean get() = status == BotStatus.RUNNING
    val isRunButtonSelected: Boolean get() = isRunning
    val isLoggingIn: Boolean get() = LoginFlowPolicy.isAuthenticating(status, status == BotStatus.LOGGING_IN)
    val canLogin: Boolean get() = !isLoggingIn
    val isSessionAuthenticated: Boolean get() = walletStash.isFinite() && walletStash > 0.0
    val canRun: Boolean get() = readyState == ReadyState.READY && status != BotStatus.RUNNING && status != BotStatus.STALLED && status != BotStatus.LOGGING_IN
}
