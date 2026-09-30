package com.example.snowybottext.web

import android.webkit.JavascriptInterface
import java.io.File
import java.io.IOException

/**
 * Listener interface for handling streamed events from just-dice.com WebView DOM.
 */
interface JustDiceBridgeListener {
    fun onBalanceUpdated(balance: Double)
    fun onStatsUpdated(wins: Int, losses: Int)
    fun onWagerResult(wagerId: Long, betAmount: Double, rollResult: Double, isWin: Boolean, profit: Double, balanceAfter: Double)
    fun onLog(message: String)
    fun onLoginCompleted(success: Boolean)
    fun onSnowyBotStopped(reason: String) {}
    fun onBettingStall() {}
    fun logMessage(message: String) {
        onLog(message)
    }
}

/**
 * JavaScript interface injected into WebView as "AndroidBridge".
 */
class JustDiceJsBridge(
    private val listener: JustDiceBridgeListener,
    private val onLoginBalanceReady: () -> Unit = {},
    private val onLoginBalancePollStarted: () -> Unit = {},
    private val appFilesDir: File? = null,
) {

    @JavascriptInterface
    fun onBalanceUpdated(balanceStr: String) {
        val balance = balanceStr.toDoubleOrNull()
        if (balance == null || !balance.isFinite() || balance <= 0.0) {
            listener.onLog("[ERROR] Invalid or non-positive balance received from #pct_balance: '$balanceStr'")
            return
        }
        listener.onBalanceUpdated(balance)
    }

    @JavascriptInterface
    fun onStatsUpdated(winsStr: String, lossesStr: String) {
        val wins = winsStr.toIntOrNull() ?: 0
        val losses = lossesStr.toIntOrNull() ?: 0
        listener.onStatsUpdated(wins, losses)
    }

    @JavascriptInterface
    fun onWagerResult(
        wagerIdStr: String,
        betAmountStr: String,
        rollResultStr: String,
        isWin: Boolean,
        profitStr: String,
        balanceAfterStr: String
    ) {
        val wagerId = wagerIdStr.toLongOrNull() ?: 0L
        val betAmount = betAmountStr.toDoubleOrNull() ?: 0.0
        val rollResult = rollResultStr.toDoubleOrNull() ?: -1.0
        val profit = profitStr.toDoubleOrNull() ?: 0.0
        val balanceAfter = balanceAfterStr.toDoubleOrNull() ?: 0.0

        listener.onWagerResult(
            wagerId = wagerId,
            betAmount = betAmount,
            rollResult = rollResult,
            isWin = isWin,
            profit = profit,
            balanceAfter = balanceAfter
        )
    }

    @JavascriptInterface
    fun onLog(message: String) {
        listener.onLog(message)
    }

    @JavascriptInterface
    fun onLoginCompleted(success: Boolean) {
        listener.onLoginCompleted(success)
    }

    @JavascriptInterface
    fun onSnowyBotStopped(reason: String) {
        listener.onSnowyBotStopped(reason)
    }

    @JavascriptInterface
    fun onLoginBalanceReady() {
        onLoginBalanceReady()
    }

    @JavascriptInterface
    fun onLoginBalancePollStarted() {
        onLoginBalancePollStarted()
    }

    @JavascriptInterface
    fun onBettingStall() {
        listener.onBettingStall()
    }

    /**
     * Native bridge calls from page JavaScript may arrive off the UI thread. Serialize file
     * access to avoid overlapping read/write operations from periodic backup callbacks.
     */
    private val backupLock = Any()

    /** Returns the app-private snowybotbackup.json contents, or null if it does not exist. */
    @JavascriptInterface
    fun readSnowybotBackup(): String? {
        val file = backupFile() ?: return null
        return try {
            synchronized(backupLock) {
                if (file.isFile) file.readText(Charsets.UTF_8) else null
            }
        } catch (error: IOException) {
            listener.onLog("[Storage] Could not read app-private backup: ${error.message}")
            null
        }
    }

    /** Writes JSON text to app-private storage using an atomic temporary-file replacement. */
    @JavascriptInterface
    fun writeSnowybotBackup(json: String): Boolean {
        val file = backupFile() ?: return false
        return try {
            require(json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
                "Backup exceeds $MAX_BACKUP_BYTES bytes"
            }
            synchronized(backupLock) {
                val temporary = File(file.parentFile, "${file.name}.tmp")
                temporary.writeText(json, Charsets.UTF_8)
                if (!temporary.renameTo(file)) {
                    temporary.copyTo(file, overwrite = true)
                    temporary.delete()
                }
            }
            true
        } catch (error: Exception) {
            listener.onLog("[Storage] Could not write app-private backup: ${error.message}")
            false
        }
    }

    private fun backupFile(): File? = appFilesDir?.let { File(it, BACKUP_FILE_NAME) }

    companion object {
        const val BRIDGE_NAME = "AndroidBridge"
        private const val BACKUP_FILE_NAME = "snowybotbackup.json"
        private const val MAX_BACKUP_BYTES = 1_048_576
    }
}
