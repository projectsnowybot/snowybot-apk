package com.example.snowybottext.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.webkit.WebView
import androidx.core.app.ServiceCompat
import com.example.snowybottext.data.credentials.CredentialRepository
import com.example.snowybottext.data.local.AppDatabase
import com.example.snowybottext.data.local.BotStateRepository
import com.example.snowybottext.data.local.RollEntity
import com.example.snowybottext.engine.PeanutEngine
import com.example.snowybottext.web.JustDiceBridgeListener
import com.example.snowybottext.web.JustDiceWebBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Foreground control service; the Activity owns the authenticated WebView. */
class JustDiceBotService : Service(), JustDiceBridgeListener {
    private val binder = BotBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)
    private lateinit var notificationManager: JustDiceNotificationManager
    private lateinit var engine: PeanutEngine
    private lateinit var botStateRepository: BotStateRepository
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var db: AppDatabase
    private val phaseController = BotPhaseController()
    private var watchdogJob: Job? = null
    private var loginJob: Job? = null
    private var readinessJob: Job? = null
    private var runReadinessCallbackPending = false
    private var runInitializationJob: Job? = null
    private var wagerBalanceAtSubmission: Double? = null
    private val wagerSnapshotGate = WagerSnapshotGate()
    private var bundledScriptStrategyActive = false
    private var loginInProgress = false
    private var balanceWatchdog = BalanceWatchdog()
    private var recoveryGeneration = 0L

    var webView: WebView? = null
        private set
    var webBridge: JustDiceWebBridge? = null
        private set
    var status: BotStatus = BotStatus.STOPPED
        private set
    var currentBalance: Double = 0.0
        private set
    var initialBalance: Double = 0.0
        private set
    var totalWins: Int = 0
        private set
    var totalLosses: Int = 0
        private set
    var targetLimit: Double = DEFAULT_TARGET_LIMIT
        private set

    inner class BotBinder : Binder() { fun getService(): JustDiceBotService = this@JustDiceBotService }

    override fun onCreate() {
        super.onCreate()
        notificationManager = JustDiceNotificationManager(this)
        notificationManager.createNotificationChannel()
        engine = PeanutEngine()
        botStateRepository = BotStateRepository(applicationContext)
        credentialRepository = CredentialRepository(applicationContext)
        db = AppDatabase.getInstance(applicationContext)
        BotSessionCallbacks.attach(this)
        activeService = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START_BOT) {
            ACTION_START_BOT -> {
                targetLimit = intent?.getDoubleExtra(EXTRA_TARGET_LIMIT, DEFAULT_TARGET_LIMIT) ?: DEFAULT_TARGET_LIMIT
                startForegroundServiceInternal()
                startBotExecution()
            }
            ACTION_LOGIN -> {
                startForegroundServiceInternal()
                startLoginExecution()
            }
            ACTION_READY -> startReadinessCheck()
            ACTION_STOP_BOT -> stopBotExecution()
            ACTION_RELOAD_PAGE -> reloadPageInternal()
            ACTION_RESET_STATE -> resetBotStateInternal()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun startForegroundServiceInternal() {
        val notification = notificationManager.buildNotification(status, currentBalance, totalWins, totalLosses, initialBalance, targetLimit)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, JustDiceNotificationManager.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(JustDiceNotificationManager.NOTIFICATION_ID, notification)
    }

    private fun startReadinessCheck() {
        checkMainThread()
        if (phaseController.shouldPlaceWager()) {
            onLog("Ready check ignored while wagering; stop the run before checking again.")
            return
        }
        if (readinessJob?.isActive == true) {
            onLog("Ready check ignored: a check is already in progress.")
            return
        }
        val bridge = webBridge
        if (bridge == null) {
            onLog("Ready check unavailable: open the app so its existing authenticated WebView is attached.")
            return
        }
        if (phaseController.phase == BotPhaseController.Phase.IDLE || phaseController.phase == BotPhaseController.Phase.LOGIN_PENDING_BALANCE) {
            onLog("Ready check unavailable until login authentication is confirmed.")
            return
        }
        if (!phaseController.beginReadinessCheck()) {
            onLog("Ready check ignored: complete Login and confirm the wallet balance first; duplicate checks are not started.")
            return
        }
        readyFlow.value = BotPhaseController.Phase.CHECKING_READINESS
        bridge.cancelPendingCallbacks()
        onLog("Checking Ready state in the existing WebView session; no bet will be submitted.")
        readinessJob = serviceScope.launch {
            bridge.checkReadiness { ready ->
                if (!phaseController.completeReadinessCheck(ready)) return@checkReadiness
                readyFlow.value = phaseController.phase
                onLog(if (ready) "Ready confirmed: authenticated page and wallet balance are available. Run is now enabled."
                else "Ready check failed: authentication, page load, or wallet balance was not confirmed. Run remains disabled; tap Ready to retry.")
            }
        }
    }

    private fun startBotExecution() {
        checkMainThread()
        val bridge = webBridge
        if (bridge == null) {
            onLog("Cannot Run: open the app first so the Activity can host the authenticated WebView.")
            return
        }
        if (phaseController.phase != BotPhaseController.Phase.READY) {
            onLog("Cannot Run: tap Ready and confirm the authenticated session first.")
            return
        }
        if (phaseController.isRunPreparationPending() || runInitializationJob?.isActive == true) {
            onLog("Cannot Run: preparation already active.")
            return
        }
        if (!phaseController.beginRunPreparation()) {
            onLog("Cannot Run: phase gate refused preparation.")
            return
        }
        loginJob?.cancel()
        runReadinessCallbackPending = false
        runInitializationJob = serviceScope.launch {
            try {
                val restoredState = botStateRepository.botStateFlow.firstOrNull()
                var balance = currentBalance
                if (!balance.isFinite() || balance <= 0.0) balance = restoredState?.walletStash ?: 100.0
                if (restoredState != null && restoredState.startingPocketChange > 0.0) {
                    engine.initialize(balance, restoredState)
                    initialBalance = restoredState.startingPocketChange
                    currentBalance = balance
                    totalWins = restoredState.totalSessionWins
                    totalLosses = restoredState.totalSessionLosses
                    balanceFlow.value = currentBalance
                } else {
                    engine.initialize(balance, onLog = ::onLog)
                    initialBalance = engine.state.startingPocketChange
                    botStateRepository.saveBotState(engine.state)
                }
                if (!phaseController.completeRunPreparation()) return@launch
                status = BotStatus.STALLED
                statusFlow.value = status
                readyFlow.value = phaseController.phase
                recoveryGeneration++
                runtimeErrorFlow.value = null
                bundledScriptStrategyActive = true
                onLog("[Bot] Loading bundled snowybot.js from app assets; native wager strategy is disabled for this run.")
                bridge.pauseLoginBalancePolling()
                bridge.readWalletStash { walletStash ->
                    if (!walletStash.isFinite() || walletStash <= 0.0 || !phaseController.shouldPlaceWager()) {
                        balanceWatchdog.stop()
                        phaseController.haltRunAfterRecoveryFailure()
                        stopBundledBotRuntime()
                        status = BotStatus.STOPPED
                        statusFlow.value = status
                        onLog("Cannot Run safely: current #pct_balance is missing, invalid, or not positive; betting halted.")
                        return@readWalletStash
                    }
                    currentBalance = walletStash
                    balanceFlow.value = walletStash
                    balanceWatchdog.start(walletStash, System.currentTimeMillis())
                    bridge.injectDomMonitor()
                    bridge.injectSnowyBot { result ->
                        if (!result.startsWith("STARTED:")) {
                            stopBundledBotRuntime()
                            phaseController.stopRun()
                            readyFlow.value = phaseController.phase
                            status = BotStatus.STOPPED
                            statusFlow.value = status
                            runtimeErrorFlow.value = "Runtime startup failed: $result"
                            onLog("snowybot.js runtime startup failed or was not confirmed: $result")
                        } else if (phaseController.shouldPlaceWager()) {
                            status = BotStatus.RUNNING
                            statusFlow.value = status
                            startWatchdog()
                            updateNotification()
                            onLog("snowybot.js asynchronous entrypoint confirmed and active.")
                        } else {
                            bridge.stopSnowyBot()
                            bundledScriptStrategyActive = false
                            phaseController.stopRun()
                            readyFlow.value = phaseController.phase
                            status = BotStatus.STOPPED
                            statusFlow.value = status
                            watchdogJob?.cancel()
                            balanceWatchdog.stop()
                            updateNotification()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                phaseController.cancelRunPreparation()
                readyFlow.value = phaseController.phase
                throw cancelled
            } catch (failure: Exception) {
                phaseController.cancelRunPreparation()
                readyFlow.value = phaseController.phase
                status = BotStatus.STOPPED
                statusFlow.value = status
                runtimeErrorFlow.value = failure.message ?: failure.javaClass.simpleName
                bundledScriptStrategyActive = false
                watchdogJob?.cancel()
                balanceWatchdog.stop()
                onLog("Cannot Run safely: ${failure.message ?: failure.javaClass.simpleName}. No wager was submitted.")
                updateNotification()
            }
        }
    }

    private fun stopBundledBotRuntime() {
        recoveryGeneration++
        bundledScriptStrategyActive = false
        status = BotStatus.STOPPED
        statusFlow.value = status
        watchdogJob?.cancel()
        balanceWatchdog.stop()
        updateNotification()
        webBridge?.stopSnowyBot()
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Bot service state must be changed on the main thread" }
    }

    private fun startLoginExecution() {
        checkMainThread()
        readinessJob?.cancel()
        webBridge?.cancelPendingCallbacks()
        runReadinessCallbackPending = false
        phaseController.invalidateReadiness()
        readyFlow.value = phaseController.phase
        if (phaseController.isWagerInFlight()) {
            loginErrorFlow.value = "Cannot login while a wager may still be processing."
            onLog("[Login] Login refused while a wager may still be processing; wait for completion or timeout recovery.")
            return
        }
        if (status == BotStatus.LOGGING_IN) return
        loginInProgress = true
        status = BotStatus.LOGGING_IN
        statusFlow.value = status
        loginProgressFlow.value = true
        loginErrorFlow.value = null
        runtimeErrorFlow.value = null
        onLog("[Login] Login started; waiting for authentication and a positive wallet balance.")
        val bridge = webBridge
        if (bridge == null) {
            phaseController.beginLogin()
            phaseController.failLogin()
            readyFlow.value = phaseController.phase
            loginInProgress = false
            loginProgressFlow.value = false
            loginErrorFlow.value = "Open the app to host the WebView, then retry login."
            status = BotStatus.STOPPED
            statusFlow.value = status
            onLog("[Login] Login stopped: open the app first so the Activity can host the WebView.")
            return
        }
        watchdogJob?.cancel()
        phaseController.beginLogin()
        readyFlow.value = phaseController.phase
        engine = PeanutEngine()
        initialBalance = 0.0
        loginJob = serviceScope.launch {
            val username = credentialRepository.getUsername()
            val password = credentialRepository.getPassword()
            val code2FA = credentialRepository.get2FACode()
            val targetUrl = credentialRepository.getTargetUrl()
            updateNotification()
            try {
                bridge.loadJustDice(targetUrl)
                delay(2.seconds)
                bridge.performLogin(username, password, code2FA)
                delay(LOGIN_TIMEOUT_MS.milliseconds)
                if (loginInProgress && phaseController.phase == BotPhaseController.Phase.LOGIN_PENDING_BALANCE) {
                    failLogin("Login timed out: no positive wallet balance was detected. Check your credentials/network and retry.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failLogin("Login failed: ${failure.message ?: failure.javaClass.simpleName}. Please retry.")
            }
        }
    }

    private fun failLogin(message: String) {
        checkMainThread()
        if (!loginInProgress) return
        loginInProgress = false
        phaseController.failLogin()
        readyFlow.value = phaseController.phase
        loginJob?.cancel()
        webBridge?.cancelLoginPolling()
        status = BotStatus.STOPPED
        statusFlow.value = status
        loginProgressFlow.value = false
        loginErrorFlow.value = message
        onLog("[Login] $message")
        updateNotification()
    }

    private fun stopBotExecution() {
        checkMainThread()
        recoveryGeneration++
        readinessJob?.cancel()
        webBridge?.cancelPendingCallbacks()
        runReadinessCallbackPending = false
        loginInProgress = false
        phaseController.invalidateReadiness()
        phaseController.stopRun()
        phaseController.cancelRunPreparation()
        webBridge?.stopSnowyBot()
        bundledScriptStrategyActive = false
        readyFlow.value = phaseController.phase
        runInitializationJob?.cancel()
        loginJob?.cancel()
        watchdogJob?.cancel()
        balanceWatchdog.stop()
        status = BotStatus.STOPPED
        statusFlow.value = status
        loginProgressFlow.value = false
        loginErrorFlow.value = null
        webBridge?.stopPageTimers()
        updateNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Keep the foreground Activity, WebView and browser cookies/session; only stop wagering.
    }

    private fun reloadPageInternal() {
        checkMainThread()
        readinessJob?.cancel()
        webBridge?.cancelPendingCallbacks()
        runReadinessCallbackPending = false
        phaseController.invalidateReadiness()
        readyFlow.value = phaseController.phase
        serviceScope.launch {
            webBridge?.stopPageTimers()
            webBridge?.reloadExistingPage()
        }
    }

    private fun recoverFromBettingStall() {
        checkMainThread()
        if (runReadinessCallbackPending || !phaseController.shouldPlaceWager() || !balanceWatchdog.isReloadPending()) return
        val generation = ++recoveryGeneration
        onLog("[Watchdog] #pct_balance was unchanged for 30s; reloading existing WebView.")
        if (bundledScriptStrategyActive) webBridge?.stopSnowyBotAfterWatchdog()
        status = BotStatus.STALLED
        statusFlow.value = status
        updateNotification()
        watchdogJob?.cancel()
        phaseController.beginRecovery()
        phaseController.resetPendingWagerForRecovery()
        wagerSnapshotGate.settleByCompletedResult()
        wagerBalanceAtSubmission = null
        webBridge?.markWagerCompleted()
        runReadinessCallbackPending = true
        val bridge = webBridge
        if (bridge == null) onWatchdogReloadFinished(generation, null)
        else bridge.reloadExistingPage { balance -> onWatchdogReloadFinished(generation, balance) }
    }

    private fun onWatchdogReloadFinished(generation: Long, authenticatedBalance: Double?) {
        checkMainThread()
        if (generation != recoveryGeneration || status != BotStatus.STALLED || !runReadinessCallbackPending) return
        runReadinessCallbackPending = false
        if (authenticatedBalance == null || !balanceWatchdog.recoverySucceeded(authenticatedBalance, System.currentTimeMillis())) {
            balanceWatchdog.recoveryFailed()
            phaseController.haltRunAfterRecoveryFailure()
            bundledScriptStrategyActive = false
            status = BotStatus.STOPPED
            statusFlow.value = status
            runtimeErrorFlow.value = "Watchdog recovery failed: authenticated wallet unavailable."
            updateNotification()
            onLog("[Watchdog] Recovery failed: reload/authentication did not provide a positive wallet balance. Betting halted; sign in and tap Run.")
            return
        }
        currentBalance = authenticatedBalance
        balanceFlow.value = authenticatedBalance
        if (!phaseController.resumeRunAfterRecovery()) {
            bundledScriptStrategyActive = false
            status = BotStatus.STOPPED
            statusFlow.value = status
            onLog("[Watchdog] Recovery cancelled because the run is no longer active; no wager submitted.")
            updateNotification()
            return
        }
        bundledScriptStrategyActive = true
        status = BotStatus.STALLED
        statusFlow.value = status
        updateNotification()
        val bridge = webBridge
        if (bridge == null) {
            failWatchdogResume(generation, "WebView bridge detached during recovery")
            return
        }
        bridge.resumeSnowyBotAfterWatchdog { resumed ->
            if (generation != recoveryGeneration || status != BotStatus.STALLED) return@resumeSnowyBotAfterWatchdog
            if (!resumed) failWatchdogResume(generation, "positive balance confirmed but bot runtime did not resume")
            else {
                status = BotStatus.RUNNING
                statusFlow.value = status
                onLog("[Watchdog] Existing session authenticated; betting loop resumed with one active strategy.")
                startWatchdog()
            }
            updateNotification()
        }
    }

    private fun failWatchdogResume(generation: Long, reason: String) {
        if (generation != recoveryGeneration) return
        balanceWatchdog.recoveryFailed()
        bundledScriptStrategyActive = false
        phaseController.haltRunAfterRecoveryFailure()
        status = BotStatus.STOPPED
        statusFlow.value = status
        runtimeErrorFlow.value = "Watchdog recovery failed: $reason"
        onLog("[Watchdog] Recovery failed: $reason. Betting halted; sign in and tap Run.")
        updateNotification()
    }

    override fun onBettingStall() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            serviceScope.launch { onBettingStall() }
            return
        }
        if (balanceWatchdog.shouldReload(System.currentTimeMillis())) recoverFromBettingStall()
    }

    private fun resetBotStateInternal() {
        onLog("Resetting app-private DataStore bot state and PeanutEngine state...")
        serviceScope.launch {
            botStateRepository.clearBotState()
            engine = PeanutEngine()
            currentBalance = 0.0
            initialBalance = 0.0
            totalWins = 0
            totalLosses = 0
            balanceFlow.value = 0.0
            updateNotification()
        }
    }

    private fun placeWager(amount: Double) {
        checkMainThread()
        val bridge = webBridge ?: run {
            onLog("Wager blocked: WebView bridge detached before placement; run stopped without submitting.")
            phaseController.failRun()
            status = BotStatus.STOPPED
            statusFlow.value = status
            runtimeErrorFlow.value = "WebView bridge detached before wager placement."
            return
        }
        if (!phaseController.tryBeginWager()) return
        if (!wagerSnapshotGate.trySubmit(currentBalance)) {
            phaseController.completeWager()
            onLog("Wager blocked: balance snapshot $currentBalance is invalid or already reserved.")
            return
        }
        wagerBalanceAtSubmission = currentBalance
        onLog("Submitting wager $amount against balance snapshot $currentBalance; waiting for settlement.")
        if (!amount.isFinite() || amount <= 0.0) {
            phaseController.completeWager()
            wagerSnapshotGate.settleByCompletedResult()
            wagerBalanceAtSubmission = null
            phaseController.failRun()
            status = BotStatus.STOPPED
            statusFlow.value = status
            runtimeErrorFlow.value = "Invalid wager amount; strategy stopped safely."
            updateNotification()
            return
        }
        try {
            bridge.executeRoll(amount)
        } catch (failure: RuntimeException) {
            phaseController.failRun()
            watchdogJob?.cancel()
            status = BotStatus.STOPPED
            statusFlow.value = status
            runtimeErrorFlow.value = failure.message ?: failure.javaClass.simpleName
            onLog("WebView rejected wager execution: ${failure.message ?: failure.javaClass.simpleName}; strategy stopped safely.")
            updateNotification()
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive && phaseController.shouldPlaceWager() && balanceWatchdog.isRunning()) {
                delay(WATCHDOG_POLL_MS.milliseconds)
                if (balanceWatchdog.shouldReload(System.currentTimeMillis())) {
                    recoverFromBettingStall()
                    return@launch
                }
            }
        }
    }

    private fun handleTargetReached(finalBalance: Double) {
        onLog("TARGET REACHED: $finalBalance >= $targetLimit. Stopping bot.")
        phaseController.completeWager()
        wagerSnapshotGate.settleByCompletedResult()
        wagerBalanceAtSubmission = null
        webBridge?.markWagerCompleted()
        phaseController.stopRun()
        balanceWatchdog.stop()
        readyFlow.value = phaseController.phase
        status = BotStatus.TARGET_REACHED
        statusFlow.value = status
        watchdogJob?.cancel()
        serviceScope.launch { botStateRepository.clearBotState() }
        updateNotification()
    }

    private fun updateNotification() {
        notificationManager.updateNotification(status, currentBalance, totalWins, totalLosses, initialBalance, targetLimit)
    }

    override fun onBalanceUpdated(balance: Double) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            serviceScope.launch { onBalanceUpdated(balance) }
            return
        }
        currentBalance = balance
        balanceFlow.value = balance
        balanceWatchdog.observeBalance(balance, System.currentTimeMillis())
        if (phaseController.phase == BotPhaseController.Phase.LOGIN_PENDING_BALANCE) {
            if (LoginFlowPolicy.shouldComplete(balance) && loginInProgress && status == BotStatus.LOGGING_IN) {
                webBridge?.cancelLoginPolling()
                loginInProgress = false
                loginJob?.cancel()
                currentBalance = balance
                balanceFlow.value = balance
                phaseController.balanceArrivedAfterLogin()
                loginProgressFlow.value = false
                loginErrorFlow.value = null
                status = BotStatus.STOPPED
                statusFlow.value = status
                readyFlow.value = phaseController.phase
                onLog("[Login] Authentication confirmed from positive current wallet balance; login-complete. Session remains idle until explicit Ready, then Run.")
            }
            updateNotification()
            return
        }
        if (phaseController.isRunPreparationPending()) {
            updateNotification()
            return
        }
        if (status == BotStatus.STALLED || !phaseController.shouldPlaceWager() || status == BotStatus.TARGET_REACHED) {
            updateNotification()
            return
        }
        val submittedBalance = wagerBalanceAtSubmission
        val completedByBalance = phaseController.isWagerInFlight() && submittedBalance != null && wagerSnapshotGate.settleByChangedBalance(balance)
        if (completedByBalance && phaseController.completeWager()) {
            wagerBalanceAtSubmission = null
            webBridge?.markWagerCompleted()
            serviceScope.launch {
                if (engine.state.startingPocketChange == 0.0) {
                    engine.initialize(balance, onLog = ::onLog)
                    initialBalance = engine.state.startingPocketChange
                    botStateRepository.saveBotState(engine.state)
                }
                if (balance >= targetLimit || engine.state.areWeRichYet) handleTargetReached(balance)
                else if (engine.state.currentWagerAmount > 0.0 && phaseController.shouldPlaceWager()) placeWager(engine.state.currentWagerAmount)
            }
        }
        serviceScope.launch {
            if (engine.state.startingPocketChange == 0.0) {
                engine.initialize(balance, onLog = ::onLog)
                initialBalance = engine.state.startingPocketChange
                botStateRepository.saveBotState(engine.state)
            }
            if (balance >= targetLimit || engine.state.areWeRichYet) handleTargetReached(balance)
            else updateNotification()
        }
    }

    override fun onStatsUpdated(wins: Int, losses: Int) {
        totalWins = wins
        totalLosses = losses
        updateNotification()
    }

    override fun onWagerResult(wagerId: Long, betAmount: Double, rollResult: Double, isWin: Boolean, profit: Double, balanceAfter: Double) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            serviceScope.launch { onWagerResult(wagerId, betAmount, rollResult, isWin, profit, balanceAfter) }
            return
        }
        serviceScope.launch {
            val roll = RollEntity(
                wagerId = wagerId,
                betAmount = betAmount,
                rollResult = rollResult,
                isWin = isWin,
                profit = profit,
                balanceAfter = balanceAfter,
                timestamp = System.currentTimeMillis(),
            )
            db.rollDao().insertRoll(roll)
            val next = engine.processRollResult(wagerId, rollResult, totalWins, totalLosses, balanceAfter, targetLimit, ::onLog)
            currentBalance = balanceAfter
            balanceFlow.value = balanceAfter
            botStateRepository.saveBotState(engine.state)
            if (balanceAfter >= targetLimit || engine.state.areWeRichYet) handleTargetReached(balanceAfter)
            else if (next != null && phaseController.shouldPlaceWager()) {
                updateNotification()
                placeWager(next)
            } else updateNotification()
        }
    }

    override fun onLog(message: String) {
        serviceScope.launch { logsFlow.emit(message) }
    }

    override fun logMessage(message: String) = onLog(message)

    override fun onLoginCompleted(success: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            serviceScope.launch { onLoginCompleted(success) }
            return
        }
        if (success && loginInProgress && phaseController.phase == BotPhaseController.Phase.LOGIN_PENDING_BALANCE) {
            status = BotStatus.LOGGING_IN
            statusFlow.value = status
            loginProgressFlow.value = true
        } else if (!success && loginInProgress && phaseController.phase == BotPhaseController.Phase.LOGIN_PENDING_BALANCE) {
            loginInProgress = false
            phaseController.failLogin()
            readyFlow.value = phaseController.phase
            status = BotStatus.STOPPED
            statusFlow.value = status
            loginProgressFlow.value = false
            loginErrorFlow.value = "Login form could not be submitted or authentication was rejected. Check your credentials and retry."
            webBridge?.cancelLoginPolling()
            loginJob?.cancel()
        }
        onLog(if (success) "[Login] Credentials submitted; waiting for positive wallet balance confirmation." else "[Login] Login form could not be submitted; status stopped.")
    }

    override fun onSnowyBotStopped(reason: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            serviceScope.launch { onSnowyBotStopped(reason) }
            return
        }
        if (!bundledScriptStrategyActive || status == BotStatus.STOPPED) return
        recoveryGeneration++
        bundledScriptStrategyActive = false
        watchdogJob?.cancel()
        balanceWatchdog.stop()
        val isTarget = reason.contains("target", ignoreCase = true)
        if (!isTarget) phaseController.failRun()
        readyFlow.value = phaseController.phase
        status = if (isTarget) BotStatus.TARGET_REACHED else BotStatus.STOPPED
        runtimeErrorFlow.value = if (isTarget) null else reason
        statusFlow.value = status
        if (isTarget) serviceScope.launch { botStateRepository.clearBotState() }
        updateNotification()
        onLog("snowybot.js stopped: $reason")
    }

    override fun onDestroy() {
        recoveryGeneration++
        webBridge?.stopSnowyBot()
        bundledScriptStrategyActive = false
        balanceWatchdog.stop()
        status = BotStatus.STOPPED
        statusFlow.value = status
        loginProgressFlow.value = false
        readinessJob?.cancel()
        webBridge?.cancelPendingCallbacks()
        webBridge?.stopPageTimers()
        BotSessionCallbacks.detach(this)
        if (activeService === this) activeService = null
        loginJob?.cancel()
        runInitializationJob?.cancel()
        watchdogJob?.cancel()
        serviceScope.cancel()
        webView = null
        webBridge = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_BOT = "com.example.snowybottext.action.START_BOT"
        const val ACTION_LOGIN = "com.example.snowybottext.action.LOGIN"
        const val ACTION_READY = "com.example.snowybottext.action.READY"
        const val ACTION_STOP_BOT = "com.example.snowybottext.action.STOP_BOT"
        const val ACTION_RELOAD_PAGE = "com.example.snowybottext.action.RELOAD_PAGE"
        const val ACTION_RESET_STATE = "com.example.snowybottext.action.RESET_STATE"
        const val EXTRA_TARGET_LIMIT = "extra_target_limit"

        @Volatile private var activeService: JustDiceBotService? = null

        fun markLoginReady() {
            val service = activeService ?: return
            val transition: () -> Unit = {
                if (service.phaseController.phase == BotPhaseController.Phase.LOGIN_PENDING_BALANCE &&
                    service.loginInProgress && service.status == BotStatus.LOGGING_IN &&
                    LoginFlowPolicy.shouldComplete(service.currentBalance)
                ) {
                    service.webBridge?.cancelLoginPolling()
                    service.loginInProgress = false
                    service.loginJob?.cancel()
                    service.phaseController.balanceArrivedAfterLogin()
                    service.status = BotStatus.STOPPED
                    loginProgressFlow.value = false
                    loginErrorFlow.value = null
                    statusFlow.value = BotStatus.STOPPED
                    readyFlow.value = service.phaseController.phase
                    service.onLog("[Login] Authentication confirmed from positive wallet balance; login-complete. Session remains idle until explicit Ready, then Run.")
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) transition()
            else service.serviceScope.launch { transition() }
        }

        fun attachWebBridge(bridge: JustDiceWebBridge) {
            val service = activeService ?: return
            bridge.setReadinessInvalidationCallback {
                val wasRunning = service.phaseController.shouldPlaceWager()
                service.phaseController.invalidateReadiness()
                readyFlow.value = service.phaseController.phase
                if (wasRunning) {
                    service.phaseController.stopRun()
                    service.balanceWatchdog.stop()
                    service.recoveryGeneration++
                    service.webBridge?.stopSnowyBot()
                    service.bundledScriptStrategyActive = false
                    service.status = BotStatus.STOPPED
                    statusFlow.value = BotStatus.STOPPED
                    runtimeErrorFlow.value = "WebView navigation interrupted the active run."
                    service.watchdogJob?.cancel()
                    service.onLog("WebView navigation halted wagering and invalidated Ready confirmation; tap Ready, then Run again.")
                } else {
                    service.onLog("WebView navigation invalidated Ready confirmation; tap Ready to check this page again.")
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) service.webBridge = bridge
            else service.serviceScope.launch { service.webBridge = bridge }
        }

        const val DEFAULT_TARGET_LIMIT = 144000.0
        const val STALL_TIMEOUT_MS = 30_000L
        const val LOGIN_TIMEOUT_MS = 35_000L
        const val BALANCE_COMPLETION_TOLERANCE = 0.00000001
        const val WATCHDOG_POLL_MS = 250L
        val statusFlow = MutableStateFlow(BotStatus.STOPPED)
        val loginProgressFlow = MutableStateFlow(false)
        val loginErrorFlow = MutableStateFlow<String?>(null)
        val runtimeErrorFlow = MutableStateFlow<String?>(null)
        val readyFlow = MutableStateFlow(BotPhaseController.Phase.IDLE)
        val balanceFlow = MutableStateFlow(0.0)
        val logsFlow = MutableSharedFlow<String>(replay = 50, extraBufferCapacity = 100)
    }
}
