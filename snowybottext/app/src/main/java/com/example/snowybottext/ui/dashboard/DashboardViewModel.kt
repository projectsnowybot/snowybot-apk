package com.example.snowybottext.ui.dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snowybottext.data.credentials.CredentialRepository
import com.example.snowybottext.data.local.AppDatabase
import com.example.snowybottext.data.local.BotStateRepository
import com.example.snowybottext.data.local.RollEntity
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
    private val db = AppDatabase.getInstance(application)
    private val rollDao = db.rollDao()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadInitialCredentials()
        observeDataSources()
    }

    private fun loadInitialCredentials() {
        val uname = credentialRepository.getUsername()
        val pwd = credentialRepository.getPassword()
        val code = credentialRepository.get2FACode()
        _uiState.update { it.copy(username = uname, password = pwd, code2FA = code) }
    }

    private fun observeDataSources() {
        viewModelScope.launch {
            JustDiceBotService.logsFlow.collect { message ->
                _uiState.update { state ->
                    state.copy(activityLogs = (listOf(message) + state.activityLogs).take(MAX_ACTIVITY_LOGS))
                }
            }
        }
        viewModelScope.launch {
            combine(
                JustDiceBotService.statusFlow,
                JustDiceBotService.loginProgressFlow,
                JustDiceBotService.loginErrorFlow,
                JustDiceBotService.runtimeErrorFlow,
                JustDiceBotService.readyFlow,
                JustDiceBotService.balanceFlow,
                botStateRepository.botStateFlow,
                rollDao.getLatestRolls(10),
            ) { values ->
                val status = values[0] as BotStatus
                val loginProgress = values[1] as Boolean
                val loginError = values[2] as String?
                val runtimeError = values[3] as String?
                val readyPhase = values[4] as BotPhaseController.Phase
                val activeBalance = values[5] as Double
                val botState = values[6] as PeanutStrategyState
                @Suppress("UNCHECKED_CAST")
                val latestRolls = values[7] as List<RollEntity>
                val targetLimit = credentialRepository.getTargetLimit()
                val startPocket = if (botState.startingPocketChange > 0.0) botState.startingPocketChange else activeBalance
                val dashboardReadyState = when (readyPhase) {
                    BotPhaseController.Phase.CHECKING_READINESS -> ReadyState.CHECKING
                    BotPhaseController.Phase.READY -> ReadyState.READY
                    else -> ReadyState.NOT_READY
                }
                val currentBalance = if (activeBalance > 0.0) activeBalance else botState.walletStash
                _uiState.value.copy(
                    status = if (loginProgress) BotStatus.LOGGING_IN else status,
                    loginError = loginError,
                    runtimeError = runtimeError,
                    readyState = dashboardReadyState,
                    walletStash = currentBalance,
                    startingPocketChange = startPocket,
                    targetLimit = targetLimit,
                    currentWager = botState.currentWagerAmount,
                    safetyCheckpoint = botState.safetyCheckpoint,
                    checkpointJuice = botState.checkpointJuice,
                    wobbleFactor = botState.wobbleFactor,
                    oopsieCounter = botState.oopsieCounter,
                    totalWins = botState.totalSessionWins,
                    totalLosses = botState.totalSessionLosses,
                    recentRolls = latestRolls,
                    isLoading = false,
                )
            }.collect { newState -> _uiState.value = newState }
        }
    }

    private companion object {
        const val MAX_ACTIVITY_LOGS = 40
    }

    fun onUsernameChange(value: String) { _uiState.update { it.copy(username = value) } }
    fun onPasswordChange(value: String) { _uiState.update { it.copy(password = value) } }
    fun on2FACodeChange(value: String) { _uiState.update { it.copy(code2FA = value) } }
    fun togglePasswordVisibility() { _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) } }

    fun saveCredentials() {
        val state = _uiState.value
        credentialRepository.saveCredentials(state.username, state.password, state.code2FA)
        _uiState.update { it.copy(saveMessage = "Credentials saved securely!") }
    }

    fun dismissSaveMessage() { _uiState.update { it.copy(saveMessage = null) } }
    fun showResetConfirmation(show: Boolean) { _uiState.update { it.copy(showResetConfirmation = show) } }

    fun resetBotState(context: Context) {
        viewModelScope.launch {
            botStateRepository.clearBotState()
            credentialRepository.clearCredentials()
        }
        val intent = Intent(context, JustDiceBotService::class.java).apply { action = JustDiceBotService.ACTION_RESET_STATE }
        context.startService(intent)
        _uiState.update {
            it.copy(
                username = "",
                password = "",
                code2FA = "",
                showResetConfirmation = false,
                saveMessage = "Bot state and credentials reset.",
            )
        }
    }

    fun startBot(context: Context) {
        val current = _uiState.value
        if (!current.canRun) {
            val reason = when {
                current.readyState != ReadyState.READY -> "Ready confirmation is ${current.readyState}; complete Login, wallet detection, and Ready before Run."
                current.status == BotStatus.RUNNING -> "Run is already active."
                current.status == BotStatus.STALLED -> "Watchdog recovery is still in progress."
                else -> "Run is currently unavailable (phase/status changed); no wager was submitted."
            }
            JustDiceBotService.logsFlow.tryEmit("[UI] Run refused: $reason")
            return
        }
        val intent = Intent(context, JustDiceBotService::class.java).apply {
            action = JustDiceBotService.ACTION_START_BOT
            putExtra(JustDiceBotService.EXTRA_TARGET_LIMIT, credentialRepository.getTargetLimit())
        }
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
