package com.example.snowybottext.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.core.app.ServiceCompat
import com.example.snowybottext.data.credentials.CredentialRepository
import com.example.snowybottext.data.local.AppDatabase
import com.example.snowybottext.data.local.BotStateRepository
import com.example.snowybottext.data.local.RollEntity
import com.example.snowybottext.engine.PeanutEngine
import com.example.snowybottext.web.JustDiceBridgeListener
import com.example.snowybottext.web.JustDiceWebBridge
import java.io.File
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
    private var runInitializationJob: Job? = null
    private var wagerBalanceAtSubmission: Double? = null
    private data class WagerResult(val betAmount: Double, val isWin: Boolean)
    private var latestWagerResult: WagerResult? = null
    private val wagerSnapshotGate = WagerSnapshotGate()
    private var bundledScriptStrategyActive = false
    private val currentWagerState = CurrentWagerState()
    private var loginInProgress = false
    private var balanceWatchdog = BalanceWatchdog()
    private val balanceStabilizer = ExternalBalanceStabilizer()
    private var recoveryGeneration = 0L
    private var manualStopGeneration = 0L
    private var fullResetRequested = false
    private var profitRestartPending = false
    private var profitResetManualStopSnapshot = 0L
    private var profitCycleBaselineForJs = 0.0
    private var pendingProfitBalance: Double? = null
    private var intentionalProfitRefresh = false
    private val profitResetPolicy = ProfitResetPolicy.Once()

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
            ACTION_START_BOT -> { startForegroundServiceInternal(); startBotExecution() }
            ACTION_LOGIN -> { startForegroundServiceInternal(); startLoginExecution() }
            ACTION_INITIAL_PAGE_STATUS -> publishInitialPageStatus(intent?.getBooleanExtra(EXTRA_PAGE_LOADED, false) == true)
            ACTION_READY -> startReadinessCheck()
            ACTION_STOP_BOT -> stopBotExecution()
            ACTION_RELOAD_PAGE -> reloadPageInternal()
            ACTION_RESET_STATE -> resetBotStateInternal()
            ACTION_RESET_ALL -> resetAllInternal()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun startForegroundServiceInternal() {
        val notification = notificationManager.buildNotification(status, currentBalance, totalWins, totalLosses, initialBalance, currentWagerState.amount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, JustDiceNotificationManager.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(JustDiceNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    private fun startReadinessCheck() {
        checkMainThread()
        if (fullResetRequested || profitRestartPending || phaseController.shouldPlaceWager()) return
        if (readinessJob?.isActive == true) return
        val bridge = webBridge ?: return
        if (phaseController.phase !in setOf(BotPhaseController.Phase.SESSION_READY, BotPhaseController.Phase.READY)) return
        if (currentBalance.isFinite() && currentBalance > 0 && initialBalance == 0.0) initialBalance = currentBalance
        if (!phaseController.beginReadinessCheck()) return
        readyFlow.value = BotPhaseController.Phase.CHECKING_READINESS
        bridge.cancelPendingCallbacks()
        readinessJob = serviceScope.launch {
            bridge.checkReadiness { ready ->
                if (!phaseController.completeReadinessCheck(ready)) return@checkReadiness
                readyFlow.value = phaseController.phase
                onLog(if (ready) "Ready confirmed. Run is enabled." else "Ready check failed; confirm the session and try again.")
            }
        }
    }

    private fun startBotExecution() {
        checkMainThread()
        if (fullResetRequested || profitRestartPending || intentionalProfitRefresh) return
        val bridge = webBridge ?: return
        if (!bridge.hasWebViewPage || phaseController.phase != BotPhaseController.Phase.READY) return
        if (runInitializationJob?.isActive == true || !phaseController.beginRunPreparation()) return
        loginJob?.cancel()
        runInitializationJob = serviceScope.launch {
            try {
                val restoredState = botStateRepository.botStateFlow.firstOrNull()
                val balance = currentBalance.takeIf { it.isFinite() && it > 0.0 } ?: restoredState?.walletStash ?: 0.0
                if (!balance.isFinite() || balance <= 0.0) {
                    phaseController.cancelRunPreparation()
                    return@launch
                }
                if (restoredState != null && restoredState.startingPocketChange > 0.0) engine.initialize(balance, restoredState)
                else {
                    engine.initialize(balance, onLog = ::onLog)
                    botStateRepository.saveBotState(engine.state)
                }
                initialBalance = engine.state.startingPocketChange
                profitCycleBaselineForJs = initialBalance
                currentBalance = balance
                balanceFlow.value = balance
                if (!phaseController.completeRunPreparation()) return@launch
                status = BotStatus.STALLED
                statusFlow.value = status
                readyFlow.value = phaseController.phase
                recoveryGeneration++
                bundledScriptStrategyActive = true
                currentWagerState.beginBundledScript(engine.state.currentWagerAmount)
                currentWagerFlow.value = currentWagerState.amount
                bridge.pauseLoginBalancePolling()
                bridge.readWalletStash { wallet ->
                    if (fullResetRequested || !phaseController.shouldPlaceWager() || !wallet.isFinite() || wallet <= 0) return@readWalletStash
                    currentBalance = wallet
                    balanceFlow.value = wallet
                    balanceWatchdog.start(wallet, System.currentTimeMillis())
                    bridge.injectDomMonitor()
                    bridge.injectSnowyBot { result ->
                        if (fullResetRequested || !phaseController.shouldPlaceWager()) return@injectSnowyBot
                        if (!result.startsWith("STARTED:")) {
                            stopBundledBotRuntime()
                            phaseController.stopRun()
                            readyFlow.value = phaseController.phase
                        } else {
                            status = BotStatus.RUNNING
                            statusFlow.value = status
                            startWatchdog()
                            updateNotification()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                phaseController.cancelRunPreparation()
                throw cancelled
            } catch (failure: Exception) {
                phaseController.cancelRunPreparation()
                status = BotStatus.STOPPED
                statusFlow.value = status
                runtimeErrorFlow.value = failure.message ?: failure.javaClass.simpleName
                bundledScriptStrategyActive = false
                currentWagerState.endBundledScript()
                currentWagerFlow.value = 0.0
                balanceWatchdog.stop()
            }
        }
    }

    private fun cancelActiveOperationsForProfitReset() {
        profitResetManualStopSnapshot = manualStopGeneration
        readinessJob?.cancel(); readinessJob = null
        runInitializationJob?.cancel(); runInitializationJob = null
        loginJob?.cancel(); loginJob = null
        watchdogJob?.cancel(); watchdogJob = null
        loginInProgress = false
        bundledScriptStrategyActive = false
        currentWagerState.endBundledScript()
        currentWagerFlow.value = 0.0
        balanceWatchdog.stop()
        phaseController.completeProfitReset()
        phaseController.beginReadinessCheck()
        readyFlow.value = BotPhaseController.Phase.CHECKING_READINESS
        webBridge?.stopSnowyBot()
    }

    private fun startFreshProfitCycle(balance: Double, generation: Long) {
        if (fullResetRequested || generation != recoveryGeneration || manualStopGeneration != profitResetManualStopSnapshot) return
        engine = PeanutEngine()
        val baseBet = ProfitResetPolicy.freshBaseBet(balance)
        if (baseBet == null) {
            profitRestartPending = false
            pendingProfitBalance = null
            intentionalProfitRefresh = false
            return
        }
        val freshState = PeanutEngine().let { freshEngine ->
            freshEngine.initialize(balance, onLog = ::onLog)
            freshEngine.state.copy(
                tinyPeanutSize = baseBet,
                backupPeanut = baseBet,
                tenPeanuts = baseBet * 10.0,
                currentWagerAmount = baseBet,
                previousWagerAmount = baseBet,
            )
        }
        engine = PeanutEngine(freshState)
        initialBalance = balance
        profitCycleBaselineForJs = balance
        currentBalance = balance
        balanceFlow.value = balance
        currentWagerState.beginBundledScript(baseBet)
        currentWagerFlow.value = baseBet
        serviceScope.launch {
            botStateRepository.saveBotState(engine.state)
            if (fullResetRequested || generation != recoveryGeneration || manualStopGeneration != profitResetManualStopSnapshot) return@launch
            if (!phaseController.beginRunPreparation() || !phaseController.completeRunPreparation()) {
                profitRestartPending = false
                return@launch
            }
            bundledScriptStrategyActive = true
            status = BotStatus.STALLED
            statusFlow.value = status
            webBridge?.readWalletStash { actualBalance ->
                if (fullResetRequested || generation != recoveryGeneration || manualStopGeneration != profitResetManualStopSnapshot || !phaseController.shouldPlaceWager()) return@readWalletStash
                if (!actualBalance.isFinite() || actualBalance <= 0.0) { stopBotExecution(); return@readWalletStash }
                currentBalance = actualBalance
                balanceFlow.value = actualBalance
                balanceWatchdog.start(actualBalance, System.currentTimeMillis())
                webBridge?.injectDomMonitor()
                webBridge?.setFreshProfitBaseBet(baseBet, balance)
                webBridge?.injectSnowyBot { result ->
                    if (fullResetRequested || generation != recoveryGeneration || manualStopGeneration != profitResetManualStopSnapshot) return@injectSnowyBot
                    if (!result.startsWith("STARTED:")) {
                        stopBundledBotRuntime()
                        phaseController.stopRun()
                        readyFlow.value = phaseController.phase
                    } else {
                        profitRestartPending = false
                        status = BotStatus.RUNNING
                        statusFlow.value = status
                        readyFlow.value = phaseController.phase
                        startWatchdog()
                        updateNotification()
                        onLog("[Profit reset] Fresh strategy saved and automatically restarted from refreshed wallet.")
                    }
                }
            }
        }
    }

    private fun stopBundledBotRuntime() {
        recoveryGeneration++
        bundledScriptStrategyActive = false
        currentWagerState.endBundledScript()
        currentWagerFlow.value = 0.0
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
        if (fullResetRequested || profitRestartPending || intentionalProfitRefresh) return
        readinessJob?.cancel()
        webBridge?.cancelPendingCallbacks()
        phaseController.invalidateReadiness()
        if (phaseController.isWagerInFlight()) return
        if (status == BotStatus.LOGGING_IN) return
        loginInProgress = true
        status = BotStatus.LOGGING_IN
        statusFlow.value = status
        loginProgressFlow.value = true
        loginErrorFlow.value = null
        runtimeErrorFlow.value = null
        val bridge = webBridge
        if (bridge == null) {
            failLogin("Open the app to host the WebView, then retry login.")
            return
        }
        phaseController.beginLogin()
        readyFlow.value = phaseController.phase
        engine = PeanutEngine()
        initialBalance = 0.0
        loginJob = serviceScope.launch {
            val username = credentialRepository.getUsername()
            val password = credentialRepository.getPassword()
            val code = credentialRepository.get2FACode()
            bridge.performLogin(username, password, code)
            delay(LOGIN_TIMEOUT_MS.milliseconds)
            if (loginInProgress && phaseController.phase == BotPhaseController.Phase.LOGIN_PENDING_BALANCE) failLogin("Login timed out; verify credentials and retry.")
        }
    }

    private fun failLogin(message: String) {
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
        onLog(message)
    }

    private fun stopBotExecution() {
        checkMainThread()
        manualStopGeneration++
        profitRestartPending = false
        pendingProfitBalance = null
        intentionalProfitRefresh = false
        recoveryGeneration++
        readinessJob?.cancel()
        webBridge?.cancelPendingCallbacks()
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
        balanceStabilizer.reset()
        status = BotStatus.STOPPED
        statusFlow.value = status
        loginProgressFlow.value = false
        loginErrorFlow.value = null
        webBridge?.stopPageTimers()
        updateNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun reloadPageInternal() {
        checkMainThread()
        if (fullResetRequested || profitRestartPending || intentionalProfitRefresh) return
        readinessJob?.cancel()
        webBridge?.cancelPendingCallbacks()
        phaseController.invalidateReadiness()
        readyFlow.value = phaseController.phase
        serviceScope.launch { webBridge?.stopPageTimers(); webBridge?.reloadExistingPage() }
    }

    private fun recoverFromBettingStall() {
        checkMainThread()
        if (fullResetRequested || profitRestartPending || !phaseController.shouldPlaceWager() || !balanceWatchdog.isReloadPending()) return
        val generation = ++recoveryGeneration
        if (bundledScriptStrategyActive) webBridge?.stopSnowyBotAfterWatchdog()
        status = BotStatus.STALLED
        statusFlow.value = status
        watchdogJob?.cancel()
        phaseController.beginRecovery()
        phaseController.resetPendingWagerForRecovery()
        wagerSnapshotGate.settleByCompletedResult()
        wagerBalanceAtSubmission = null
        webBridge?.markWagerCompleted()
        val bridge = webBridge ?: return
        bridge.reloadExistingPage { balance -> onWatchdogReloadFinished(generation, balance) }
    }

    private fun onWatchdogReloadFinished(generation: Long, authenticatedBalance: Double?) {
        checkMainThread()
        if (fullResetRequested || generation != recoveryGeneration || status != BotStatus.STALLED) return
        if (authenticatedBalance == null || !balanceWatchdog.recoverySucceeded(authenticatedBalance, System.currentTimeMillis())) {
            phaseController.haltRunAfterRecoveryFailure()
            bundledScriptStrategyActive = false
            status = BotStatus.STOPPED
            statusFlow.value = status
            updateNotification()
            return
        }
        currentBalance = authenticatedBalance
        balanceFlow.value = authenticatedBalance
        if (!phaseController.resumeRunAfterRecovery()) return
        bundledScriptStrategyActive = true
        currentWagerState.beginBundledScript(currentWagerState.amount)
        webBridge?.resumeSnowyBotAfterWatchdog { resumed ->
            if (fullResetRequested || generation != recoveryGeneration || status != BotStatus.STALLED) return@resumeSnowyBotAfterWatchdog
            if (!resumed) stopBundledBotRuntime() else { status = BotStatus.RUNNING; statusFlow.value = status; startWatchdog() }
            updateNotification()
        }
    }

    override fun onBettingStall() {
        if (Looper.myLooper() != Looper.getMainLooper()) serviceScope.launch { onBettingStall() }
        else if (balanceWatchdog.shouldReload(System.currentTimeMillis())) recoverFromBettingStall()
    }

    private fun resetBotStateInternal() {
        stopBotExecution()
        serviceScope.launch {
            botStateRepository.clearBotState()
            webBridge?.clearSnowybotBackup()
            engine = PeanutEngine()
            currentBalance = 0.0
            initialBalance = 0.0
            currentWagerFlow.value = 0.0
            balanceFlow.value = 0.0
        }
    }

    private fun performCompleteAppWipe() {
        try {
            deleteRecursive(cacheDir)
            deleteRecursive(codeCacheDir)
            filesDir?.listFiles()?.forEach { file ->
                deleteRecursive(file)
            }
            val dataDir = File(applicationInfo.dataDir)
            if (dataDir.exists()) {
                File(dataDir, "shared_prefs").let { if (it.exists()) deleteRecursive(it) }
                File(dataDir, "datastore").let { if (it.exists()) deleteRecursive(it) }
                File(dataDir, "app_webview").let { if (it.exists()) deleteRecursive(it) }
                dataDir.listFiles()?.forEach { file ->
                    if (file.isDirectory && (file.name == "shared_prefs" || file.name == "datastore" || file.name == "app_webview")) {
                        deleteRecursive(file)
                    }
                }
            }
            try {
                db.clearAllTables()
            } catch (e: Exception) {
            }
            deleteDatabase("snowybot_database")
            deleteDatabase("webview.db")
            deleteDatabase("webviewCache.db")
            try {
                val cookieManager = CookieManager.getInstance()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.removeAllCookies(null)
            } else {
                @Suppress("DEPRECATION")
                cookieManager.removeAllCookie()
            }
            cookieManager.flush()
                WebStorage.getInstance().deleteAllData()
            } catch (e: Exception) {
            }
        } catch (e: Exception) {
            onLog("[Reset All] Error during complete wipe: ${e.message}")
        }
    }

    private fun deleteRecursive(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        file.delete()
    }

    private fun resetAllInternal() {
        checkMainThread()
        if (fullResetRequested) return
        fullResetRequested = true
        stopBotExecution()
        watchdogJob?.cancel()
        fullResetRequested = true
        recoveryGeneration++
        phaseController.stopRun()
        phaseController.invalidateReadiness()
        loginInProgress = false
        status = BotStatus.STOPPED
        statusFlow.value = status
        loginProgressFlow.value = false
        loginErrorFlow.value = null
        runtimeErrorFlow.value = null
        serviceScope.launch {
            performCompleteAppWipe()
            botStateRepository.clearAllProgress()
            webBridge?.clearSnowybotBackup()
            credentialRepository.clearCredentials()
            db.rollDao().clearAllRolls()
            engine = PeanutEngine()
            currentBalance = 0.0
            initialBalance = 0.0
            currentWagerFlow.value = 0.0
            balanceFlow.value = 0.0
            totalWins = 0
            totalLosses = 0
            readyFlow.value = phaseController.phase
            updateNotification()
            val finishReset = {
                if (webBridge == null) {
                    webView = null
                }
                fullResetRequested = false
                profitRestartPending = false
                pendingProfitBalance = null
                intentionalProfitRefresh = false
                recoveryGeneration++
                manualStopGeneration++
                profitResetPolicy.reset()
                webPageStatusFlow.value = "Loading page…"
                onLog("[Reset All] Complete app cache, storage, SharedPreferences, DataStore, WebView data, database, and credentials cleared; fresh WebView initialized and reloaded.")
            }
            pendingFullResetCompletion = finishReset
            webSessionResetHandler?.invoke() ?: webBridge?.clearForFullReset(finishReset) ?: finishReset()
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive && phaseController.shouldPlaceWager() && balanceWatchdog.isRunning()) {
                delay(WATCHDOG_POLL_MS.milliseconds)
                if (balanceWatchdog.shouldReload(System.currentTimeMillis())) { recoverFromBettingStall(); return@launch }
            }
        }
    }

    private fun updateNotification() {
        notificationManager.updateNotification(status, currentBalance, totalWins, totalLosses, initialBalance, currentWagerState.amount)
    }

    override fun onCurrentWagerAmount(amount: Double) {
        if (Looper.myLooper() != Looper.getMainLooper()) { serviceScope.launch { onCurrentWagerAmount(amount) }; return }
        if (fullResetRequested || !bundledScriptStrategyActive) return
        if (currentWagerState.acceptScriptAmount(amount)) {
            currentWagerFlow.value = currentWagerState.amount
            updateNotification()
        }
    }

    override fun onBalanceUpdated(balance: Double) {
        if (Looper.myLooper() != Looper.getMainLooper()) { serviceScope.launch { onBalanceUpdated(balance) }; return }
        if (fullResetRequested || !balance.isFinite() || balance <= 0.0) return
        currentBalance = balance
        balanceFlow.value = balance
        if (intentionalProfitRefresh) {
            if (balance > 0.0) {
                intentionalProfitRefresh = false
                pendingProfitBalance = balance
                val generation = recoveryGeneration
                profitResetPolicy.reset()
                startFreshProfitCycle(balance, generation)
            }
            return
        }
        val baseline = profitCycleBaselineForJs.takeIf { it > 0.0 } ?: initialBalance
        if (bundledScriptStrategyActive && profitResetPolicy.triggerIfTargetReached(baseline, balance)) {
            profitRestartPending = true
            pendingProfitBalance = balance
            val generation = ++recoveryGeneration
            cancelActiveOperationsForProfitReset()
            status = BotStatus.STOPPED
            statusFlow.value = status
            updateNotification()
            botStateRepositorySafeClear { cleared ->
                if (!cleared || fullResetRequested || generation != recoveryGeneration || manualStopGeneration != profitResetManualStopSnapshot || !profitRestartPending) return@botStateRepositorySafeClear
                intentionalProfitRefresh = true
                val bridge = webBridge
                if (bridge == null) {
                    intentionalProfitRefresh = false
                    pendingProfitBalance = null
                    profitRestartPending = false
                    return@botStateRepositorySafeClear
                }
                bridge.resetStrategyAndRefresh { refreshSucceeded ->
                    if (!refreshSucceeded || fullResetRequested || generation != recoveryGeneration || manualStopGeneration != profitResetManualStopSnapshot || !profitRestartPending) {
                        intentionalProfitRefresh = false
                        pendingProfitBalance = null
                        profitRestartPending = false
                        return@resetStrategyAndRefresh
                    }
                    webBridge?.readWalletStash { refreshedBalance ->
                        if (fullResetRequested || generation != recoveryGeneration || manualStopGeneration != profitResetManualStopSnapshot || !profitRestartPending) return@readWalletStash
                        if (!refreshedBalance.isFinite() || refreshedBalance <= 0.0) {
                            intentionalProfitRefresh = false
                            pendingProfitBalance = null
                            profitRestartPending = false
                            return@readWalletStash
                        }
                        intentionalProfitRefresh = false
                        pendingProfitBalance = refreshedBalance
                        profitResetPolicy.reset()
                        startFreshProfitCycle(refreshedBalance, generation)
                    }
                }
            }
            return
        }
        val observedAt = System.currentTimeMillis()
        val wagerResult = latestWagerResult
        val decision = balanceStabilizer.observe(
            balance = balance,
            nowMs = observedAt,
            lastSettledBalance = currentBalance.takeIf { it.isFinite() && it > 0.0 } ?: balance,
            lastWagerAmount = wagerResult?.betAmount,
            isWin = wagerResult?.isWin,
        )
        if (decision.acceptedBalance == null) return
        val priorBalance = currentBalance
        balanceWatchdog.observeBalance(decision.acceptedBalance, observedAt)
        if (decision.externalShift && phaseController.shouldPlaceWager() && status != BotStatus.STALLED && !profitRestartPending) {
            val stableBalance = decision.acceptedBalance
            onLog("[Balance stabilization] Persistent external balance shift confirmed from $priorBalance to $stableBalance; resetting Peanut state and base wager.")
            engine = PeanutEngine()
            runCatching { engine.initialize(stableBalance, onLog = ::onLog) }
                .onSuccess {
                    latestWagerResult = null
                    balanceStabilizer.reset()
                    currentBalance = stableBalance
                    balanceFlow.value = stableBalance
                    currentWagerState.acceptScriptAmount(engine.state.currentWagerAmount)
                    currentWagerFlow.value = engine.state.currentWagerAmount
                    serviceScope.launch { botStateRepository.saveBotState(engine.state) }
                    webBridge?.setFreshProfitBaseBet(engine.state.currentWagerAmount, stableBalance)
                }
                .onFailure { onLog("[Balance stabilization] Could not reset strategy for confirmed balance: ${it.message}") }
            return
        }
        currentBalance = decision.acceptedBalance
        balanceFlow.value = decision.acceptedBalance
        if (phaseController.phase == BotPhaseController.Phase.LOGIN_PENDING_BALANCE && loginInProgress && LoginFlowPolicy.shouldComplete(balance)) {
            webBridge?.cancelLoginPolling()
            loginInProgress = false
            loginJob?.cancel()
            phaseController.balanceArrivedAfterLogin()
            initialBalance = balance
            profitCycleBaselineForJs = balance
            balanceStabilizer.reset()
            loginProgressFlow.value = false
            status = BotStatus.STOPPED
            statusFlow.value = status
            readyFlow.value = phaseController.phase
            loginErrorFlow.value = null
            engine = PeanutEngine()
            try {
                engine.initialize(balance, onLog = ::onLog)
                serviceScope.launch { botStateRepository.clearBotState(); botStateRepository.saveBotState(engine.state) }
            } catch (_: IllegalArgumentException) { }
        }
        if (!phaseController.shouldPlaceWager() || status == BotStatus.STALLED || profitRestartPending) return
        if (phaseController.isWagerInFlight() && wagerBalanceAtSubmission != null && wagerSnapshotGate.settleByChangedBalance(balance)) {
            phaseController.completeWager()
            wagerBalanceAtSubmission = null
            webBridge?.markWagerCompleted()
        }
        updateNotification()
    }

    private fun botStateRepositorySafeClear(onComplete: (Boolean) -> Unit) {
        serviceScope.launch {
            runCatching { botStateRepository.clearBotState(); webBridge?.clearSnowybotBackup() }
                .onSuccess { onComplete(true) }
                .onFailure { onComplete(false) }
        }
    }

    override fun onStatsUpdated(wins: Int, losses: Int) {
        if (fullResetRequested) return
        totalWins = wins
        totalLosses = losses
        updateNotification()
    }

    override fun onWagerResult(wagerId: Long, betAmount: Double, rollResult: Double, isWin: Boolean, profit: Double, balanceAfter: Double) {
        if (fullResetRequested) return
        if (Looper.myLooper() != Looper.getMainLooper()) { serviceScope.launch { onWagerResult(wagerId, betAmount, rollResult, isWin, profit, balanceAfter) }; return }
        latestWagerResult = WagerResult(betAmount, isWin)
        if (betAmount.isFinite() && betAmount > 0.0) {
            currentWagerState.acceptScriptAmount(betAmount)
            currentWagerFlow.value = currentWagerState.amount
        }
        serviceScope.launch {
            db.rollDao().insertRoll(RollEntity(wagerId = wagerId, betAmount = betAmount, rollResult = rollResult, isWin = isWin, profit = profit, balanceAfter = balanceAfter, timestamp = System.currentTimeMillis()))
            if (balanceAfter.isFinite() && balanceAfter > 0.0) {
                val preWagerBalance = wagerBalanceAtSubmission?.takeIf { it.isFinite() && it > 0.0 }
                    ?: currentBalance.takeIf { it.isFinite() && it > 0.0 }
                    ?: balanceAfter
                val settlement = balanceStabilizer.observe(
                    balance = balanceAfter,
                    nowMs = System.currentTimeMillis(),
                    lastSettledBalance = preWagerBalance,
                    lastWagerAmount = betAmount,
                    isWin = isWin,
                )
                if (!settlement.externalShift) {
                    currentBalance = balanceAfter
                    balanceFlow.value = balanceAfter
                }
                latestWagerResult = null
                wagerBalanceAtSubmission = null
                wagerSnapshotGate.settleByCompletedResult()
                if (phaseController.isWagerInFlight()) phaseController.completeWager()
                webBridge?.markWagerCompleted()
            }
            if (engine.state.startingPocketChange == 0.0 && balanceAfter > 0.0) engine.initialize(balanceAfter, onLog = ::onLog)
            val savedState = engine.state.copy(
                walletStash = balanceAfter.takeIf { it.isFinite() && it > 0.0 } ?: engine.state.walletStash,
                currentWagerAmount = betAmount.takeIf { it.isFinite() && it > 0.0 } ?: engine.state.currentWagerAmount,
                previousWagerAmount = engine.state.currentWagerAmount,
            )
            botStateRepository.saveBotState(savedState)
            updateNotification()
        }
    }

    override fun onLog(message: String) { serviceScope.launch { logsFlow.emit(message) } }
    override fun logMessage(message: String) = onLog(message)

    override fun onLoginCompleted(success: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) { serviceScope.launch { onLoginCompleted(success) }; return }
        if (!success && loginInProgress) failLogin("Login form could not be submitted or authentication was rejected.")
    }

    override fun onProfitTargetReached() {
        if (Looper.myLooper() != Looper.getMainLooper()) { serviceScope.launch { onProfitTargetReached() }; return }
        onLog("[System] 10% profit threshold reported; native coordinator handles the reset cycle.")
    }

    override fun onSnowyBotStopped(reason: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) { serviceScope.launch { onSnowyBotStopped(reason) }; return }
        if (!bundledScriptStrategyActive || status == BotStatus.STOPPED) return
        recoveryGeneration++
        bundledScriptStrategyActive = false
        currentWagerState.endBundledScript()
        currentWagerFlow.value = 0.0
        watchdogJob?.cancel()
        balanceWatchdog.stop()
        if (reason.contains("target", ignoreCase = true)) {
            // A script-side profit report is handled by onBalanceUpdated; never signal a target stop.
            status = BotStatus.STOPPED
        } else {
            phaseController.failRun()
            status = BotStatus.STOPPED
            runtimeErrorFlow.value = reason
        }
        statusFlow.value = status
        updateNotification()
    }

    override fun onDestroy() {
        recoveryGeneration++
        webBridge?.stopSnowyBot()
        bundledScriptStrategyActive = false
        currentWagerState.endBundledScript()
        currentWagerFlow.value = 0.0
        balanceWatchdog.stop()
        readinessJob?.cancel()
        webBridge?.cancelPendingCallbacks()
        webBridge?.stopPageTimers()
        BotSessionCallbacks.detach(this)
        if (activeService === this) activeService = null
        loginJob?.cancel(); runInitializationJob?.cancel(); watchdogJob?.cancel()
        serviceScope.cancel()
        webView = null
        webBridge = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_BOT = "com.super.snowybot.action.START_BOT"
        const val ACTION_LOGIN = "com.super.snowybot.action.LOGIN"
        const val ACTION_READY = "com.super.snowybot.action.READY"
        const val ACTION_STOP_BOT = "com.super.snowybot.action.STOP_BOT"
        const val ACTION_RELOAD_PAGE = "com.super.snowybot.action.RELOAD_PAGE"
        const val ACTION_RESET_STATE = "com.super.snowybot.action.RESET_STATE"
        const val ACTION_RESET_ALL = "com.super.snowybot.action.RESET_ALL"
        @Volatile private var activeService: JustDiceBotService? = null
        @Volatile private var webSessionResetHandler: (() -> Unit)? = null
        @Volatile private var pendingFullResetCompletion: (() -> Unit)? = null
        fun setWebSessionResetHandler(handler: (() -> Unit)?) { webSessionResetHandler = handler }
        fun notifyWebSessionResetComplete() { pendingFullResetCompletion?.invoke(); pendingFullResetCompletion = null }
        fun resetPreventsResume(fullResetRequested: Boolean, generationMatches: Boolean): Boolean = fullResetRequested || !generationMatches
        fun markLoginReady() {
            val service = activeService ?: return
            val transition = {
                if (service.phaseController.phase == BotPhaseController.Phase.LOGIN_PENDING_BALANCE && service.loginInProgress && LoginFlowPolicy.shouldComplete(service.currentBalance)) {
                    service.loginInProgress = false
                    service.loginJob?.cancel()
                    service.phaseController.balanceArrivedAfterLogin()
                    service.initialBalance = service.currentBalance
                    service.profitCycleBaselineForJs = service.currentBalance
                    service.status = BotStatus.STOPPED
                    loginProgressFlow.value = false
                    loginErrorFlow.value = null
                    statusFlow.value = BotStatus.STOPPED
                    readyFlow.value = service.phaseController.phase
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) transition() else service.serviceScope.launch { transition() }
        }
        fun attachWebBridge(bridge: JustDiceWebBridge) {
            val service = activeService ?: return
            bridge.setReadinessInvalidationCallback {
                val running = service.phaseController.shouldPlaceWager()
                service.phaseController.invalidateReadiness()
                readyFlow.value = service.phaseController.phase
                if (running) {
                    service.phaseController.stopRun()
                    service.balanceWatchdog.stop()
                    service.recoveryGeneration++
                    service.webBridge?.stopSnowyBot()
                    service.bundledScriptStrategyActive = false
                    service.currentWagerState.endBundledScript()
                    currentWagerFlow.value = 0.0
                    service.status = BotStatus.STOPPED
                    statusFlow.value = BotStatus.STOPPED
                    runtimeErrorFlow.value = "WebView navigation interrupted the active run."
                    service.watchdogJob?.cancel()
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) service.webBridge = bridge else service.serviceScope.launch { service.webBridge = bridge }
        }
        const val STALL_TIMEOUT_MS = 30_000L
        const val LOGIN_TIMEOUT_MS = 35_000L
        const val BALANCE_COMPLETION_TOLERANCE = 0.00000001
        const val WATCHDOG_POLL_MS = 250L
        val statusFlow = MutableStateFlow(BotStatus.STOPPED)
        val currentWagerFlow = MutableStateFlow(0.0)
        val webPageStatusFlow = MutableStateFlow("Loading page…")
        fun publishInitialPageStatus(loaded: Boolean) { webPageStatusFlow.value = if (loaded) "Page loaded" else "Loading page…" }
        const val ACTION_INITIAL_PAGE_STATUS = "com.super.snowybot.action.INITIAL_PAGE_STATUS"
        const val EXTRA_PAGE_LOADED = "extra_page_loaded"
        val loginProgressFlow = MutableStateFlow(false)
        val loginErrorFlow = MutableStateFlow<String?>(null)
        val runtimeErrorFlow = MutableStateFlow<String?>(null)
        val readyFlow = MutableStateFlow(BotPhaseController.Phase.IDLE)
        val balanceFlow = MutableStateFlow(0.0)
        val logsFlow = MutableSharedFlow<String>(replay = 50, extraBufferCapacity = 100)
    }
}
