package com.example.snowybottext.ui.dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snowybottext.data.credentials.CredentialRepository
import com.example.snowybottext.data.local.BotStateRepository
import com.example.snowybottext.engine.PeanutStrategyState
import com.example.snowybottext.service.BotPhaseController
import com.example.snowybottext.service.BotStatus
import com.example.snowybottext.service.JustDiceBotService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val botStateRepository = BotStateRepository(application)
    private val credentialRepository = CredentialRepository(application)
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadInitialCredentials()
        observeDataSources()
    }

    private fun loadInitialCredentials() {
        _uiState.update {
            it.copy(
                username = credentialRepository.getUsername(),
                password = credentialRepository.getPassword(),
                code2FA = credentialRepository.get2FACode(),
            )
        }
    }

    private fun observeDataSources() {
        viewModelScope.launch {
            combine(
                JustDiceBotService.statusFlow,
                JustDiceBotService.webPageStatusFlow,
                JustDiceBotService.loginProgressFlow,
                JustDiceBotService.loginErrorFlow,
                JustDiceBotService.runtimeErrorFlow,
                JustDiceBotService.readyFlow,
                JustDiceBotService.balanceFlow,
                JustDiceBotService.currentWagerFlow,
                botStateRepository.botStateFlow,
            ) { values ->
                val status = values[0] as BotStatus
                val pageStatus = values[1] as String
                val loginProgress = values[2] as Boolean
                val loginError = values[3] as String?
                val runtimeError = values[4] as String?
                val readyPhase = values[5] as BotPhaseController.Phase
                val activeBalance = values[6] as Double
                val activeWager = values[7] as Double
                val botState = values[8] as PeanutStrategyState
                val ready = when (readyPhase) {
                    BotPhaseController.Phase.CHECKING_READINESS -> ReadyState.CHECKING
                    BotPhaseController.Phase.READY -> ReadyState.READY
                    else -> ReadyState.NOT_READY
                }
                val balance = if (activeBalance > 0.0) activeBalance else botState.walletStash
                _uiState.value.copy(
                    status = if (loginProgress) BotStatus.LOGGING_IN else status,
                    pageStatus = pageStatus,
                    loginError = loginError,
                    runtimeError = runtimeError,
                    readyState = ready,
                    walletStash = balance,
                    startingPocketChange = botState.startingPocketChange.takeIf { it > 0.0 } ?: balance,
                    currentWager = activeWager.takeIf { it > 0.0 } ?: botState.currentWagerAmount,
                    currentWagerFromScript = activeWager > 0.0,
                    isLoading = false,
                )
            }.collect { new -> _uiState.value = new }
        }
    }

    fun onUsernameChange(value: String) { _uiState.update { it.copy(username = value) } }
    fun onPasswordChange(value: String) { _uiState.update { it.copy(password = value) } }
    fun on2FACodeChange(value: String) { _uiState.update { it.copy(code2FA = value) } }
    fun togglePasswordVisibility() { _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) } }

    fun saveCredentials() {
        val state = _uiState.value
        credentialRepository.saveCredentials(state.username, state.password, state.code2FA)
    }

    fun dismissSaveMessage() { _uiState.update { it.copy(saveMessage = null) } }
    fun showResetAllConfirmation(show: Boolean) { _uiState.update { it.copy(showResetAllConfirmation = show) } }

    fun resetAll(context: Context) {
        credentialRepository.clearCredentials()
        val intent = Intent(context, JustDiceBotService::class.java).apply { action = JustDiceBotService.ACTION_RESET_ALL }
        context.startService(intent)
        _uiState.update {
            it.copy(
                showResetAllConfirmation = false,
                readyState = ReadyState.NOT_READY,
                status = BotStatus.STOPPED,
                walletStash = 0.0,
                startingPocketChange = 0.0,
                currentWager = 0.0,
                username = "",
                password = "",
                code2FA = "",
                loginError = null,
                runtimeError = null,
                saveMessage = "Reset All complete: WebView data, bot progress, and saved credentials erased.",
            )
        }
    }

    fun startBot(context: Context) {
        if (!_uiState.value.canRun) {
            JustDiceBotService.logsFlow.tryEmit("[UI] Run refused; complete Login and explicit Ready first.")
            return
        }
        val intent = Intent(context, JustDiceBotService::class.java).apply { action = JustDiceBotService.ACTION_START_BOT }
        ContextCompat.startForegroundService(context, intent)
    }

    fun checkReady(context: Context) {
        val intent = Intent(context, JustDiceBotService::class.java).apply { action = JustDiceBotService.ACTION_READY }
        ContextCompat.startForegroundService(context, intent)
    }

    fun loginToJustDice(context: Context) {
        saveCredentials()
        val intent = Intent(context, JustDiceBotService::class.java).apply { action = JustDiceBotService.ACTION_LOGIN }
        ContextCompat.startForegroundService(context, intent)
    }

    fun reloadBot(context: Context) {
        val intent = Intent(context, JustDiceBotService::class.java).apply { action = JustDiceBotService.ACTION_RELOAD_PAGE }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopBot(context: Context) {
        val intent = Intent(context, JustDiceBotService::class.java).apply { action = JustDiceBotService.ACTION_STOP_BOT }
        ContextCompat.startForegroundService(context, intent)
    }
}
