package com.example.snowybottext

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.snowybottext.service.BotSessionCallbacks
import com.example.snowybottext.service.JustDiceBotService
import com.example.snowybottext.web.JustDiceWebSession
import android.os.IBinder
import com.example.snowybottext.web.JustDiceBridgeListener
import com.example.snowybottext.ui.navigation.MainAppScreen
import com.example.snowybottext.ui.theme.SnowybottextTheme

class MainActivity : ComponentActivity() {
    private var webSession: JustDiceWebSession? = null
    private val webSessionListener = object : JustDiceBridgeListener {
        override fun onBalanceUpdated(balance: Double) = BotSessionCallbacks.onBalanceUpdated(balance)
        override fun onCurrentWagerAmount(amount: Double) = BotSessionCallbacks.onCurrentWagerAmount(amount)
        override fun onStatsUpdated(wins: Int, losses: Int) = BotSessionCallbacks.onStatsUpdated(wins, losses)
        override fun onWagerResult(wagerId: Long, betAmount: Double, rollResult: Double, isWin: Boolean, profit: Double, balanceAfter: Double) =
            BotSessionCallbacks.onWagerResult(wagerId, betAmount, rollResult, isWin, profit, balanceAfter)
        override fun onLog(message: String) {
            if (message == "Loading page…" || message == "Page loaded") {
                JustDiceBotService.webPageStatusFlow.value = message
            }
            BotSessionCallbacks.onLog(message)
        }
        override fun onLoginCompleted(success: Boolean) = BotSessionCallbacks.onLoginCompleted(success)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        webSession = JustDiceWebSession.get(webSessionListener)
        webSession?.attach(this)
        webSession?.withBridge { bridge ->
            JustDiceBotService.publishInitialPageStatus(bridge.hasWebViewPage)
            JustDiceBotService.attachWebBridge(bridge)
        }
        JustDiceBotService.setWebSessionResetHandler { resetWebSession {} }
        serviceBound = bindService(Intent(this, JustDiceBotService::class.java), serviceConnection, BIND_AUTO_CREATE)
        setContent {
            SnowybottextTheme {
                MainAppScreen()
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceBound = true
            webSession?.withBridge { bridge -> JustDiceBotService.attachWebBridge(bridge) }
        }
        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    private var serviceBound = false

    fun resetWebSession(onComplete: () -> Unit) {
        runOnUiThread {
            webSession?.resetSession(this) {
                webSession?.withBridge { bridge ->
                    JustDiceBotService.attachWebBridge(bridge)
                    JustDiceBotService.notifyWebSessionResetComplete()
                }
                webSession?.loadFreshLoggedOutSession()
                onComplete()
            } ?: JustDiceBotService.notifyWebSessionResetComplete()
        }
    }

    override fun onStart() {
        super.onStart()
        webSession?.onActivityForegrounded(this)
    }

    override fun onStop() {
        webSession?.onActivityBackgrounded(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        webSession?.withBridge { bridge ->
            JustDiceBotService.publishInitialPageStatus(bridge.hasWebViewPage)
        }
    }

    override fun onDestroy() {
        if (serviceBound) unbindService(serviceConnection)
        webSession?.detach(this)
        webSession = null
        super.onDestroy()
    }
}
