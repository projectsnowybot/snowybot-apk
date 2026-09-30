package com.example.snowybottext.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.snowybottext.service.JustDiceBotService
import org.json.JSONObject
import java.util.Locale

/**
 * Configures the single Just-Dice WebView and coordinates DOM-based authentication,
 * readiness checks, and safe wager placement without replacing the browsing session.
 */
class JustDiceWebBridge(
    private val webView: WebView,
    private val listener: JustDiceBridgeListener,
) {
    private val jsBridge = JustDiceJsBridge(
        object : JustDiceBridgeListener {
            override fun onBalanceUpdated(balance: Double) = listener.onBalanceUpdated(balance)
            override fun onStatsUpdated(wins: Int, losses: Int) = listener.onStatsUpdated(wins, losses)
            override fun onWagerResult(
                wagerId: Long,
                betAmount: Double,
                rollResult: Double,
                isWin: Boolean,
                profit: Double,
                balanceAfter: Double,
            ) = listener.onWagerResult(wagerId, betAmount, rollResult, isWin, profit, balanceAfter)
            override fun onLog(message: String) = listener.onLog(message)
            override fun onLoginCompleted(success: Boolean) = listener.onLoginCompleted(success)
            override fun onSnowyBotStopped(reason: String) = listener.onSnowyBotStopped(reason)
        },
        onLoginBalanceReady = { JustDiceBotService.markLoginReady() },
        onLoginBalancePollStarted = { loginBalancePollStartedCallback?.invoke() },
        appFilesDir = webView.context.filesDir,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val readinessCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var pageLoaded = false
    private var destroyed = false
    private var pageLoadFailed = false
    private var readinessRefreshAttempted = false
    private var readinessPollHandler: Handler? = null
    private var readinessPollCount = 0
    private var isAuthenticated = false
    private var readinessInvalidationCallback: (() -> Unit)? = null
    private var readinessRefreshAttemptedForCurrentCheck = false
    private var loginSubmissionPending = false
    private var confirmedLoginBalance: Double? = null
    private val loginStabilizationPolicy = LoginStabilizationPolicy()
    private var loginAuthConfirmed = false
    private var loginAuthPollInProgress = false
    private var loginTimedOut = false
    private var loginCallbackGeneration = 0L
    private var loginBalancePollStartedCallback: (() -> Unit)? = null
    private var loginAuthPollHandler: Handler? = null
    private var loginAuthPollCount = 0
    private var devConsoleOutput: ((String) -> Unit)? = null
    private var snowyBotInjectionInFlight = false
    private var snowyBotScriptStarted = false
    private var snowyBotStartupProbeCount = 0
    private var snowyBotStartupProbe: Runnable? = null
    private val watchdogReloadCallbacks = mutableListOf<(Double?) -> Unit>()
    private var watchdogReloadPending = false
    private var watchdogReloadRecoveryInProgress = false
    private var watchdogReloadAuthPollCount = 0
    private var watchdogReloadAuthPoll: Runnable? = null
    private var watchdogRecoveryInProgress = false
    private var watchdogResumeIssuedForRecovery = false
    private var intentionalWatchdogResumePending = false
    private var watchdogResumeCallbacks = mutableListOf<(Boolean) -> Unit>()

    private fun onMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "WebView operations must be performed on the main thread"
        }
    }

    fun setReadinessInvalidationCallback(callback: (() -> Unit)?) {
        readinessInvalidationCallback = callback
    }

    private fun invalidateReadinessForNavigation() {
        if (loginSubmissionPending) {
            loginCallbackGeneration++
            loginSubmissionPending = false
            // A submit-caused page load doesn't determine auth; the new DOM balance does.
            return
        }
        isAuthenticated = false
        readinessRefreshAttempted = false
        cancelPendingCallbacks()
        readinessInvalidationCallback?.invoke()
    }

    /** Cancels bridge-owned timers and prevents later callbacks from reviving them. */
    fun destroy() = onMainThread {
        requireMainThread()
        loginCallbackGeneration++
        loginAuthPollHandler?.removeCallbacksAndMessages(null)
        loginAuthPollHandler = null
        loginAuthPollInProgress = false
        destroyed = true
        readinessPollHandler?.removeCallbacksAndMessages(null)
        readinessPollHandler = null
        watchdogReloadAuthPoll?.let(mainHandler::removeCallbacks)
        watchdogReloadAuthPoll = null
        finishReadiness(ready = false)
        snowyBotStartupProbe?.let(mainHandler::removeCallbacks)
        snowyBotStartupProbe = null
        watchdogReloadCallbacks.forEach { it(null) }
        watchdogReloadCallbacks.clear()
        watchdogResumeCallbacks.forEach { it(false) }
        watchdogResumeCallbacks.clear()
        webView.evaluateJavascript(cancelPageTimersScript(), null)
        loginBalancePollStartedCallback = null
        readinessInvalidationCallback = null
        watchdogRecoveryInProgress = false
    }

    /** Waits for a positive current DOM balance and available controls on the existing page. */
    fun awaitRunReadiness(onReady: (Boolean) -> Unit) = startReadinessCheck(onReady)

    /** Explicit user-driven readiness check. Never submits a wager. */
    fun checkReadiness(onReady: (Boolean) -> Unit) = startReadinessCheck(onReady)

    private fun startReadinessCheck(onReady: (Boolean) -> Unit) {
        requireMainThread()
        if (destroyed) return
        if (readinessCallbacks.isNotEmpty()) {
            readinessCallbacks += onReady
            listener.onLog("A readiness check is already active; this request will share its result.")
            return
        }
        readinessCallbacks += onReady
        readinessPollCount = 0
        readinessRefreshAttemptedForCurrentCheck = false
        readinessPollHandler?.removeCallbacksAndMessages(null)
        readinessPollHandler = mainHandler
        listener.onLog("Checking positive #pct_balance and wager controls in this WebView.")
        checkRunReadiness()
    }

    private fun checkRunReadiness() {
        requireMainThread()
        if (destroyed || readinessCallbacks.isEmpty()) return
        if (pageLoadFailed) {
            listener.onLog("[Ready probe] Refused: main-frame page load failed.")
            finishReadiness(ready = false)
            return
        }
        if (!pageLoaded) {
            if (readinessPollCount == 0) listener.onLog("[Ready probe] Waiting for current WebView page load.")
            scheduleReadinessCheck()
            return
        }
        webView.evaluateJavascript(readinessScript()) { result ->
            if (destroyed || readinessCallbacks.isEmpty()) return@evaluateJavascript
            if (result == "true") {
                isAuthenticated = true
                finishReadiness(ready = true)
            } else {
                if (readinessPollCount == 0) listener.onLog("[Ready probe] Waiting for a valid positive #pct_balance and wager controls.")
                scheduleReadinessCheck()
            }
        }
    }

    private fun readinessScript(): String = """
        (function() {
          var balance = document.getElementById('pct_balance');
          var roll = document.getElementById('a_lo');
          var bet = document.getElementById('pct_bet');
          var chance = document.getElementById('pct_chance');
          function visible(el) {
            if (!el) return false;
            var style = window.getComputedStyle ? window.getComputedStyle(el) : null;
            var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
            return (!style || (style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0')) &&
              (!rect || (rect.width > 0 && rect.height > 0));
          }
          var raw = balance ? ((('value' in balance && balance.value) ? balance.value :
            (balance.innerText || balance.textContent || '')).trim()) : '';
          var normalized = raw.replace(/[^0-9+.-]/g, '');
          var amount = Number(normalized);
          var validBalance = !!(balance && visible(balance) && normalized && isFinite(amount) && amount > 0);
          var controls = !!(roll && bet && chance);
          var pending = !!window.__snowybotPlacementPending;
          var authenticated = validBalance && controls && !pending;
          var evidence = 'balancePresent=' + !!balance + ', balanceVisible=' + visible(balance) +
            ', balanceRaw="' + raw + '", controls=' + controls + ', placementPending=' + pending;
          if (!authenticated && window.AndroidBridge) {
            var reason = !balance ? '#pct_balance missing' :
              !visible(balance) ? '#pct_balance hidden' :
              !normalized || !isFinite(amount) ? '#pct_balance invalid' :
              amount <= 0 ? '#pct_balance is not positive' :
              !controls ? 'wager controls missing' : 'placement pending';
            window.AndroidBridge.onLog('[Ready probe] Refused: ' + reason + '; evidence: ' + evidence);
          }
          if (authenticated && window.AndroidBridge) {
            window.AndroidBridge.onLog('[Ready probe] Authenticated from current DOM; evidence: ' + evidence);
            window.AndroidBridge.onBalanceUpdated(amount.toString());
          }
          return authenticated;
        })();
    """.trimIndent()

    fun readinessScriptForTest(): String = readinessScript()

    private fun scheduleReadinessCheck() {
        readinessPollCount++
        if (readinessPollCount >= RUN_READINESS_MAX_POLLS) {
            if (!readinessRefreshAttemptedForCurrentCheck && isAuthenticated && !pageLoadFailed) {
                readinessRefreshAttemptedForCurrentCheck = true
                readinessRefreshAttempted = true
                pageLoaded = false
                pageLoadFailed = false
                listener.onLog("Ready timed out; refreshing the existing authenticated WebView once, without submitting a wager.")
                webView.reload()
                readinessPollCount = 0
            } else {
                listener.onLog("[Ready probe] Refused: no positive ready DOM found; authenticated=$isAuthenticated.")
                finishReadiness(ready = false)
            }
            return
        }
        if (!destroyed && readinessCallbacks.isNotEmpty()) {
            readinessPollHandler?.postDelayed({ checkRunReadiness() }, RUN_READINESS_POLL_MS)
        }
    }

    private fun finishReadiness(ready: Boolean) {
        val callbacks = readinessCallbacks.toList()
        readinessCallbacks.clear()
        readinessPollHandler?.removeCallbacksAndMessages(null)
        readinessPollHandler = null
        callbacks.forEach { callback -> runCatching { callback(ready) } }
    }

    fun pauseLoginBalancePolling() = onMainThread {
        requireMainThread()
        webView.evaluateJavascript(
            "if (window.__snowybotLoginBalancePoll) { clearInterval(window.__snowybotLoginBalancePoll); window.__snowybotLoginBalancePoll = null; }",
            null,
        )
    }

    init {
        requireMainThread()
        configureWebView()
    }

    @SuppressLint("SetJavaScriptEnabled", "DeprecatedWebSettings", "Deprecation")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = true
        }
        webView.addJavascriptInterface(jsBridge, JustDiceJsBridge.BRIDGE_NAME)
        webView.webViewClient = object : WebViewClient() {
            @Suppress("DEPRECATION")
            @Deprecated("Use the request-based onReceivedError callback")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (view?.url == failingUrl) {
                    if (loginSubmissionPending) {
                        listener.onLog("[Login] Network/page load failed; login retry is available.")
                        listener.onLoginCompleted(false)
                    }
                    listener.onLog("WebView main-frame load error (code=$errorCode): ${description ?: "Unknown WebView error"}; URL=$failingUrl")
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (loginSubmissionPending) loginSubmissionPending = false
                val watchdogNavigation = watchdogReloadPending
                if (watchdogNavigation) {
                    watchdogReloadPending = false
                } else if (!intentionalWatchdogResumePending) {
                    invalidateReadinessForNavigation()
                }
                pageLoaded = false
                pageLoadFailed = false
                listener.onLog("Page load started: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageLoaded = true
                pageLoadFailed = false
                listener.onLog("Page load finished: $url")
                injectDomMonitor()
                checkRunReadiness()
                dismissModal()
                if (watchdogReloadCallbacks.isNotEmpty()) confirmWatchdogReloadAuthentication()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    pageLoadFailed = true
                    if (loginSubmissionPending) {
                        listener.onLog("[Login] Network/page load failed; login retry is available.")
                        listener.onLoginCompleted(false)
                    }
                    checkRunReadiness()
                    listener.onLog("WebView main-frame load error (code=${error?.errorCode}): ${error?.description ?: "Unknown WebView error"}; URL=${request.url}")
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) {
                    if ((errorResponse?.statusCode ?: 0) >= 400) {
                        pageLoadFailed = true
                        if (loginSubmissionPending) {
                            listener.onLog("[Login] Network HTTP ${errorResponse?.statusCode} error; login retry is available.")
                            listener.onLoginCompleted(false)
                        }
                        checkRunReadiness()
                    }
                    listener.onLog("WebView HTTP error ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase} for ${request.url}")
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                super.onReceivedSslError(view, handler, error)
                listener.onLog("WebView SSL error (request cancelled): $error")
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    val text = "[${it.messageLevel()}] ${it.message()} (${it.sourceId()}:${it.lineNumber()})"
                    listener.logMessage("[DevConsole] $text")
                    devConsoleOutput?.invoke(text)
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }
    }

    fun loadJustDice(targetUrl: String = DEFAULT_TARGET_URL) = onMainThread {
        requireMainThread()
        if (destroyed) return@onMainThread
        val normalizedUrl = targetUrl.trim().ifBlank { DEFAULT_TARGET_URL }
        if (!normalizedUrl.startsWith("https://", ignoreCase = true)) {
            listener.onLog("Refusing to load non-HTTPS Just-Dice URL: $normalizedUrl")
            return@onMainThread
        }
        stopPageTimers()
        loginCallbackGeneration++
        loginAuthPollHandler?.removeCallbacksAndMessages(null)
        loginAuthPollHandler = null
        loginBalancePollStartedCallback = null
        isAuthenticated = false
        confirmedLoginBalance = null
        loginAuthConfirmed = false
        loginAuthPollInProgress = false
        loginTimedOut = false
        listener.onLog("Loading Just-Dice URL: $normalizedUrl")
        webView.loadUrl(normalizedUrl)
    }

    /** Read the current parsed positive #pct_balance in the active WebView session. */
    fun readWalletStash(onBalance: (Double) -> Unit) = onMainThread {
        requireMainThread()
        if (destroyed || !pageLoaded || pageLoadFailed) {
            listener.onLog("[Balance probe] Current WebView is unavailable; walletStash baseline was not accepted.")
            onBalance(Double.NaN)
            return@onMainThread
        }
        webView.evaluateJavascript(
            """(function(){var e=document.getElementById('pct_balance');var raw=e?((('value' in e && e.value)?e.value:(e.innerText||e.textContent||'')).trim()):'';var n=raw.replace(/[^0-9+.-]/g,'');var v=Number(n);return n&&isFinite(v)&&v>0?v:null;})()""",
        ) { result ->
            if (destroyed) return@evaluateJavascript
            val balance = result?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: Double.NaN
            if (!balance.isFinite()) listener.onLog("[Balance probe] Refused invalid/missing/non-positive current #pct_balance; raw WebView result=$result.")
            onBalance(balance)
        }
    }

    /** Reload the currently displayed URL so the WebView retains its cookie/session store. */
    fun reloadExistingPage(onAuthenticated: ((Double?) -> Unit)? = null) = onMainThread {
        requireMainThread()
        if (destroyed) {
            onAuthenticated?.invoke(null)
            return@onMainThread
        }
        if (watchdogReloadRecoveryInProgress) {
            listener.onLog("[Watchdog] Ignored duplicate reload while recovery is already active.")
            return@onMainThread
        }
        if (watchdogRecoveryInProgress) {
            listener.onLog("[Watchdog] Ignored duplicate recovery request while reload/authentication is pending.")
            return@onMainThread
        }
        watchdogRecoveryInProgress = onAuthenticated != null
        watchdogResumeIssuedForRecovery = false
        if (onAuthenticated != null) watchdogReloadCallbacks += onAuthenticated
        watchdogReloadAuthPollCount = 0
        watchdogReloadPending = true
        pageLoaded = false
        pageLoadFailed = false
        stopPageTimers()
        listener.onLog("[Watchdog] Reloading current URL in the existing WebView to preserve the browser session.")
        webView.reload()
    }

    private fun confirmWatchdogReloadAuthentication() {
        requireMainThread()
        isAuthenticated = false
        if (destroyed || pageLoadFailed || !pageLoaded) {
            finishWatchdogReloadAuthentication(null)
            return
        }
        webView.evaluateJavascript(
            """(function(){var e=document.getElementById('pct_balance');var raw=e?((('value' in e && e.value)?e.value:(e.innerText||e.textContent||'')).trim()):'';var n=raw.replace(/[^0-9+.-]/g,'');var v=Number(n);return isFinite(v)&&v>0?v:null;})()""",
        ) { result ->
            if (destroyed) return@evaluateJavascript
            val balance = result?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
            if (balance != null) finishWatchdogReloadAuthentication(balance)
            else scheduleWatchdogReloadAuthenticationPoll()
        }
    }

    private fun scheduleWatchdogReloadAuthenticationPoll() {
        watchdogReloadAuthPollCount++
        if (watchdogReloadAuthPollCount >= WATCHDOG_AUTH_MAX_POLLS) {
            listener.onLog("[Watchdog] Authentication probe failed: no positive wallet balance after page load.")
            finishWatchdogReloadAuthentication(null)
        } else {
            watchdogReloadAuthPoll = Runnable { confirmWatchdogReloadAuthentication() }.also {
                mainHandler.postDelayed(it, WATCHDOG_AUTH_POLL_MS)
            }
        }
    }

    private fun finishWatchdogReloadAuthentication(balance: Double?) {
        isAuthenticated = balance != null
        watchdogReloadAuthPoll?.let(mainHandler::removeCallbacks)
        watchdogReloadAuthPoll = null
        val callbacks = watchdogReloadCallbacks.toList()
        watchdogReloadCallbacks.clear()
        watchdogRecoveryInProgress = false
        callbacks.forEach { callback -> callback(balance) }
    }

    fun stopSnowyBotAfterWatchdog() = onMainThread {
        requireMainThread()
        if (destroyed) return@onMainThread
        intentionalWatchdogResumePending = false
        webView.evaluateJavascript("if (window.__snowybotStopAfterWatchdog) window.__snowybotStopAfterWatchdog();", null)
    }

    /** Stop the bundled JS loop without navigating or clearing authenticated cookies. */
    fun stopSnowyBot() = onMainThread {
        requireMainThread()
        if (destroyed) return@onMainThread
        snowyBotStartupProbe?.let(mainHandler::removeCallbacks)
        snowyBotStartupProbe = null
        snowyBotInjectionInFlight = false
        snowyBotScriptStarted = false
        intentionalWatchdogResumePending = false
        watchdogReloadPending = false
        watchdogRecoveryInProgress = false
        watchdogReloadAuthPoll?.let(mainHandler::removeCallbacks)
        watchdogReloadAuthPoll = null
        watchdogReloadCallbacks.clear()
        watchdogResumeCallbacks.clear()
        webView.evaluateJavascript("window.__snowybotStopRequested = true; window.snowyBotRunning = false; if (window.__snowybotStopAfterWatchdog) window.__snowybotStopAfterWatchdog();", null)
    }

    fun resumeSnowyBotAfterWatchdog(onResult: (Boolean) -> Unit) = onMainThread {
        requireMainThread()
        if (destroyed || !pageLoaded || pageLoadFailed || watchdogResumeCallbacks.isNotEmpty() ||
            watchdogResumeIssuedForRecovery || !isAuthenticated
        ) {
            onResult(false)
            return@onMainThread
        }
        watchdogResumeIssuedForRecovery = true
        intentionalWatchdogResumePending = true
        watchdogResumeCallbacks += onResult
        // Reinject from the bundled asset after page reload: a full navigation discards all
        // closures and runtime state. The script restores its local backup from localStorage.
        injectSnowyBot { result ->
            val resumed = result.startsWith("STARTED:")
            isAuthenticated = resumed
            intentionalWatchdogResumePending = resumed
            val callbacks = watchdogResumeCallbacks.toList()
            watchdogResumeCallbacks.clear()
            callbacks.forEach { it(resumed) }
        }
    }

    fun dismissModal() = onMainThread {
        requireMainThread()
        webView.evaluateJavascript("(function(){ var b=document.querySelector('a.fancybox-item.fancybox-close'); if(b){b.click();return true;} return false; })();", null)
    }

    /** Submit login in-place; auth is solely a valid positive parsed DOM balance. */
    fun performLogin(username: String, password: String, code2FA: String) = onMainThread {
        requireMainThread()
        if (destroyed) return@onMainThread
        loginCallbackGeneration++
        isAuthenticated = false
        confirmedLoginBalance = null
        loginAuthConfirmed = false
        loginAuthPollInProgress = false
        loginTimedOut = false
        loginStabilizationPolicy.reset()
        loginAuthPollHandler?.removeCallbacksAndMessages(null)
        loginAuthPollHandler = null
        loginAuthPollCount = 0
        loginAuthPollInProgress = false
        val loginGeneration = loginCallbackGeneration
        loginBalancePollStartedCallback = {
            if (!destroyed && loginGeneration == loginCallbackGeneration) {
                startLoginBalanceConfirmationPolling(loginGeneration)
            }
        }
        webView.evaluateJavascript(
            """(function() {
              window.__snowybotLoginConfirmed = false;
              if (window.__snowybotLoginWaitForElements) { clearInterval(window.__snowybotLoginWaitForElements); window.__snowybotLoginWaitForElements = null; }
              if (window.__snowybotLoginBalancePoll) { clearInterval(window.__snowybotLoginBalancePoll); window.__snowybotLoginBalancePoll = null; }
              if (window.__snowybotLoginBalanceTimeout) { clearTimeout(window.__snowybotLoginBalanceTimeout); window.__snowybotLoginBalanceTimeout = null; }
            })();""",
            null,
        )
        val escapedUser = escapeJsString(username)
        val escapedPass = escapeJsString(password)
        val escapedCode = escapeJsString(code2FA)
        val has2FA = code2FA.isNotBlank()
        val js = """
            (function() {
              if (window.__snowybotLoginWaitForElements) clearInterval(window.__snowybotLoginWaitForElements);
              if (window.__snowybotLoginBalanceTimeout) clearTimeout(window.__snowybotLoginBalanceTimeout);
              if (window.__snowybotLoginBalancePoll) clearInterval(window.__snowybotLoginBalancePoll);
              var closeBtn = document.querySelector('a.fancybox-item.fancybox-close');
              if (closeBtn) closeBtn.click();
              var account = Array.from(document.querySelectorAll('a')).find(function(e) { return e.textContent.trim() === 'Account'; });
              if (account) account.click();
              var attempts = 0;
              var waitForElements = setInterval(function() {
                attempts++;
                var userEl = document.getElementById('myuser');
                var passEl = document.getElementById('mypass');
                var codeEl = document.getElementById('mycode');
                var okBtn = document.getElementById('myok');
                if ((userEl && passEl && okBtn && (!$has2FA || codeEl)) || attempts >= 50) {
                  clearInterval(waitForElements);
                  window.__snowybotLoginWaitForElements = null;
                  if (!userEl || !passEl || !okBtn || ($has2FA && !codeEl)) {
                    if (window.AndroidBridge) { window.AndroidBridge.onLog('[Login] Login form unavailable; retry is available.'); window.AndroidBridge.onLoginCompleted(false); }
                    return;
                  }
                  if (userEl) { userEl.value = '$escapedUser'; userEl.dispatchEvent(new Event('input')); userEl.dispatchEvent(new Event('change')); }
                  if (passEl) { passEl.value = '$escapedPass'; passEl.dispatchEvent(new Event('input')); passEl.dispatchEvent(new Event('change')); }
                  if ($has2FA && codeEl) { codeEl.value = '$escapedCode'; codeEl.dispatchEvent(new Event('input')); codeEl.dispatchEvent(new Event('change')); }
                  if (!okBtn) {
                    if (window.AndroidBridge) { window.AndroidBridge.onLog('[System] Login failed: #myok button not found.'); window.AndroidBridge.onLoginCompleted(false); }
                    return;
                  }
                  okBtn.click();
                  if (window.AndroidBridge) {
                    window.AndroidBridge.onLog('[System] Login submitted in current WebView; checking current #pct_balance.');
                    window.AndroidBridge.onLoginCompleted(true);
                    window.AndroidBridge.onLoginBalancePollStarted();
                  }
                  function readBalance() {
                    var el = document.getElementById('pct_balance');
                    var raw = el ? ((('value' in el && el.value) ? el.value : (el.innerText || el.textContent || '')).trim()) : '';
                    var normalized = raw.replace(/[^0-9+.-]/g, '');
                    var amount = Number(normalized);
                    if (normalized && isFinite(amount) && amount > 0 && window.AndroidBridge) {
                      window.AndroidBridge.onBalanceUpdated(amount.toString());
                    }
                  }
                  window.__snowybotLoginBalancePoll = setInterval(readBalance, 1000);
                  readBalance();
                }
              }, 200);
              window.__snowybotLoginWaitForElements = waitForElements;
            })();
        """.trimIndent()
        loginSubmissionPending = true
        webView.evaluateJavascript(js) { _ ->
            if (destroyed || loginGeneration != loginCallbackGeneration) return@evaluateJavascript
            if (loginAuthPollHandler == null && !loginAuthConfirmed && !loginTimedOut) {
                startLoginBalanceConfirmationPolling(loginGeneration)
            }
        }
    }

    /** Cancels all login-specific page and native polling callbacks without changing the WebView session. */
    fun cancelLoginPolling() = onMainThread {
        requireMainThread()
        loginCallbackGeneration++
        loginAuthPollHandler?.removeCallbacksAndMessages(null)
        loginAuthPollHandler = null
        loginAuthPollInProgress = false
        loginBalancePollStartedCallback = null
        loginSubmissionPending = false
        loginTimedOut = true
        loginStabilizationPolicy.onTimeout()
        webView.evaluateJavascript(
            """(function() {
              if (window.__snowybotLoginWaitForElements) { clearInterval(window.__snowybotLoginWaitForElements); window.__snowybotLoginWaitForElements = null; }
              if (window.__snowybotLoginBalancePoll) { clearInterval(window.__snowybotLoginBalancePoll); window.__snowybotLoginBalancePoll = null; }
              if (window.__snowybotLoginBalanceTimeout) { clearTimeout(window.__snowybotLoginBalanceTimeout); window.__snowybotLoginBalanceTimeout = null; }
            })();""",
            null,
        )
    }

    fun startLoginBalanceConfirmationPolling(expectedGeneration: Long = loginCallbackGeneration) = onMainThread {
        requireMainThread()
        if (destroyed || expectedGeneration != loginCallbackGeneration || loginAuthConfirmed || loginTimedOut) return@onMainThread
        loginAuthPollInProgress = true
        loginAuthPollHandler?.removeCallbacksAndMessages(null)
        loginAuthPollHandler = mainHandler
        loginAuthPollCount = 0
        loginAuthPollInProgress = true
        loginStabilizationPolicy.reset()
        loginTimedOut = false
        loginSubmissionPending = false
        listener.onLog("[Login auth] Polling current #pct_balance; finite positive values confirm authentication.")
        pollLoginBalanceConfirmation(expectedGeneration)
    }

    private fun pollLoginBalanceConfirmation(expectedGeneration: Long = loginCallbackGeneration) {
        requireMainThread()
        val js = """
            (function() {
              var element = document.getElementById('pct_balance');
              var raw = element ? ((('value' in element && element.value) ? element.value : (element.innerText || element.textContent || '')).trim()) : '';
              var normalized = raw.replace(/[^0-9+.-]/g, '');
              var amount = Number(normalized);
              var balanceElementVisible = !!(element && (!window.getComputedStyle || (window.getComputedStyle(element).display !== 'none' && window.getComputedStyle(element).visibility !== 'hidden' && window.getComputedStyle(element).opacity !== '0')) && (!element.getBoundingClientRect || (element.getBoundingClientRect().width > 0 && element.getBoundingClientRect().height > 0)));
              return JSON.stringify({ balance: balanceElementVisible && normalized && isFinite(amount) && amount > 0 ? amount : null, raw: raw });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            if (destroyed || expectedGeneration != loginCallbackGeneration || !loginAuthPollInProgress || loginAuthConfirmed || loginTimedOut || loginStabilizationPolicy.isCompleted()) return@evaluateJavascript
            val decoded = result?.let(::decodeEvaluationResult) as? String
            val json = runCatching { JSONObject(decoded ?: "") }.getOrNull()
            val balance = json?.optDouble("balance")?.takeIf { it.isFinite() && it > 0.0 }
            val raw = json?.optString("raw", "") ?: ""
            when (loginStabilizationPolicy.onBalance(balance)) {
                LoginStabilizationPolicy.Action.COMPLETE -> {
                    isAuthenticated = true
                    confirmedLoginBalance = balance
                    finishLoginAuthPoll(confirmed = true, balance = balance)
                    listener.onBalanceUpdated(balance!!)
                    listener.onLoginCompleted(true)
                    JustDiceBotService.markLoginReady()
                    listener.onLog("[Login auth] CONFIRMED from current DOM: positive parsed #pct_balance=$balance; raw=\"$raw\".")
                }
                LoginStabilizationPolicy.Action.WAIT -> {
                    listener.onLog("[Login auth] Waiting: #pct_balance is missing, invalid, zero, or negative; raw=\"$raw\".")
                    scheduleLoginAuthPoll(expectedGeneration)
                }
                LoginStabilizationPolicy.Action.IGNORE -> Unit
            }
        }
    }

    private fun scheduleLoginAuthPoll(expectedGeneration: Long = loginCallbackGeneration) {
        if (expectedGeneration != loginCallbackGeneration || loginAuthConfirmed || loginAuthPollHandler == null) return
        if (loginStabilizationPolicy.isCompleted()) return
        loginAuthPollCount++
        if (loginAuthPollCount >= LOGIN_AUTH_MAX_POLLS) {
            listener.onLog("[Login auth] FAILED after $LOGIN_AUTH_MAX_POLLS polls: no finite positive #pct_balance; authentication remains false.")
            finishLoginAuthPoll(confirmed = false, balance = null)
        } else {
            loginAuthPollHandler?.postDelayed({ pollLoginBalanceConfirmation(expectedGeneration) }, LOGIN_AUTH_POLL_MS)
        }
    }

    private fun finishLoginAuthPoll(confirmed: Boolean, balance: Double?) {
        loginAuthPollHandler?.removeCallbacksAndMessages(null)
        loginAuthPollHandler = null
        if ((!confirmed && loginAuthConfirmed) || (confirmed && !loginAuthPollInProgress)) return
        loginAuthPollInProgress = false
        if (!confirmed) {
            loginStabilizationPolicy.onTimeout()
            loginBalancePollStartedCallback = null
            loginTimedOut = true
            webView.evaluateJavascript(
                """(function() {
                  if (window.__snowybotLoginWaitForElements) { clearInterval(window.__snowybotLoginWaitForElements); window.__snowybotLoginWaitForElements = null; }
                  if (window.__snowybotLoginBalancePoll) { clearInterval(window.__snowybotLoginBalancePoll); window.__snowybotLoginBalancePoll = null; }
                  if (window.__snowybotLoginBalanceTimeout) { clearTimeout(window.__snowybotLoginBalanceTimeout); window.__snowybotLoginBalanceTimeout = null; }
                  if (window.AndroidBridge) { window.AndroidBridge.onLog('[Login] Authentication failed or timed out; retry is available.'); window.AndroidBridge.onLoginCompleted(false); }
                })();""",
                null,
            )
        }
        if (confirmed && balance != null) {
            loginAuthConfirmed = true
            loginBalancePollStartedCallback = null
            webView.evaluateJavascript(
                """(function() {
                  if (window.__snowybotLoginWaitForElements) { clearInterval(window.__snowybotLoginWaitForElements); window.__snowybotLoginWaitForElements = null; }
                  if (window.__snowybotLoginBalancePoll) { clearInterval(window.__snowybotLoginBalancePoll); window.__snowybotLoginBalancePoll = null; }
                  if (window.__snowybotLoginBalanceTimeout) { clearTimeout(window.__snowybotLoginBalanceTimeout); window.__snowybotLoginBalanceTimeout = null; }
                  if (window.__snowybotLoginWaitForElements) { clearInterval(window.__snowybotLoginWaitForElements); window.__snowybotLoginWaitForElements = null; }
                  window.__snowybotLoginBalancePoll = null;
                  window.__snowybotLoginBalanceTimeout = null;
                  window.__snowybotLoginWaitForElements = null;
                  window.__snowybotLoginConfirmed = true;
                  if (window.AndroidBridge) {
                    window.AndroidBridge.onLog('[System] login-complete: current wallet balance confirmed; session remains idle.');
                    window.AndroidBridge.onLoginCompleted(true);
                  }
                  return true;
                })();""",
                null,
            )
        }
    }

    /** Executes a single placement in the authenticated existing WebView. */
    fun executeRoll(stakeAmount: Double, winChance: Double = 49.5) = onMainThread {
        requireMainThread()
        if (destroyed) return@onMainThread
        if (!isAuthenticated) {
            listener.onLog("[Wager] Refused: current WebView session is not authenticated.")
            return@onMainThread
        }
        if (!stakeAmount.isFinite() || stakeAmount <= 0.0 || !winChance.isFinite() || winChance <= 0.0 || winChance >= 100.0) {
            listener.onLog("[Wager] Refused: stake/chance is invalid.")
            return@onMainThread
        }
        listener.onLog("[Wager] Executing one placement in current WebView: stake=$stakeAmount chance=$winChance.")
        webView.evaluateJavascript(placementScript(stakeAmount, winChance)) { result ->
            if (result != "true") {
                webView.evaluateJavascript("window.__snowybotPlacementPending = false;", null)
                listener.onLog("[Wager] Placement refused by DOM result=$result.")
            } else {
                listener.onLog("[Wager] One #a_lo click dispatched; awaiting authoritative result.")
            }
        }
    }

    fun markWagerCompleted() = onMainThread {
        requireMainThread()
        if (!destroyed) webView.evaluateJavascript("window.__snowybotPlacementPending = false;", null)
    }

    fun setDevConsoleOutput(output: ((String) -> Unit)?) = onMainThread { devConsoleOutput = output }

    fun executeDevConsoleCommand(command: String, onResult: (String) -> Unit = {}) = onMainThread {
        requireMainThread()
        if (destroyed) { onResult("ERROR: WebView session unavailable"); return@onMainThread }
        if (command.isBlank()) { onResult("ERROR: command is empty"); return@onMainThread }
        webView.evaluateJavascript(devConsoleEvaluationForTest(command)) { result ->
            onResult(result?.let(::decodeEvaluationResult)?.toString() ?: "null")
        }
    }

    fun devConsoleEvaluationForTest(source: String): String {
        val encoded = Base64.encodeToString(source.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return """
            (function() {
              var sourceText = decodeURIComponent(escape(atob('$encoded')));
              var logs = [];
              var original = { log: console.log, warn: console.warn, error: console.error, info: console.info };
              ['log', 'warn', 'error', 'info'].forEach(function(level) {
                console[level] = function() {
                  var text = Array.prototype.slice.call(arguments).map(function(value) {
                    try { return typeof value === 'string' ? value : JSON.stringify(value); }
                    catch (_) { return String(value); }
                  }).join(' ');
                  logs.push({ level: level, text: text });
                  original[level].apply(console, arguments);
                  if (window.AndroidBridge) window.AndroidBridge.onLog('[DevConsole][' + level.toUpperCase() + '] ' + text);
                };
              });
              var errors = [];
              function onError(event) { errors.push(String(event.message || event.reason || event)); }
              window.addEventListener('error', onError);
              window.addEventListener('unhandledrejection', onError);
              try {
                var result = new Function(sourceText).call(window);
                return JSON.stringify({ result: result === undefined ? null : result, logs: logs, errors: errors });
              } catch (error) {
                console.error('[DevConsole][ERROR] ' + (error && error.stack || error));
                return JSON.stringify({ error: String(error), logs: logs, errors: errors });
              } finally {
                console.log = original.log; console.warn = original.warn; console.error = original.error; console.info = original.info;
                window.removeEventListener('error', onError);
                window.removeEventListener('unhandledrejection', onError);
              }
            })();
        """.trimIndent()
    }

    fun snowyBotStartupProbeForTest(): String = """
        (function() {
          var probe = window.__snowybotStartupProbe;
          if (!probe) return 'pending';
          if (probe.failed) return 'failed:' + probe.reason;
          if (probe.ok && window.snowyBotRunning === true && window.__snowybotStopRequested !== true) return 'started';
          if (probe.ok) return 'stopped';
          return 'pending';
        })();
    """.trimIndent()

    fun injectSnowyBot(onResult: (String) -> Unit = {}) = onMainThread {
        requireMainThread()
        if (destroyed) { onResult("ERROR: WebView session unavailable"); return@onMainThread }
        val source = runCatching {
            webView.context.assets.open(SNOWYBOT_SCRIPT_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { error ->
            val message = "FAILURE: bundled snowybot.js unavailable: ${error.message}"
            listener.onLog("[Bot] $message")
            onResult(message)
            return@onMainThread
        }
        executeSnowyBotSource(source, onResult)
    }

    private fun executeSnowyBotSource(source: String, onResult: (String) -> Unit) {
        if (destroyed) { onResult("ERROR: WebView session unavailable"); return }
        if (snowyBotInjectionInFlight) { onResult("PENDING: startup probe already active"); return }
        snowyBotInjectionInFlight = true
        snowyBotScriptStarted = false
        snowyBotStartupProbeCount = 0
        val startedSignal = "window.__snowybotStartupProbe = { ok: true, at: Date.now() };"
        val prefix = "window.__snowybotStopRequested = false; window.__snowybotStartupProbe = { ok: false, failed: false, reason: '' };\n"
        val instrumented = source.replace("window.snowyBotRunning = true;", "$prefix window.snowyBotRunning = true;")
            .replace("void (async function initializeBotEngine() {", "$startedSignal\nvoid (async function initializeBotEngine() {")
        val encoded = Base64.encodeToString(instrumented.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val evaluation = """
            (function() {
              var sourceText = decodeURIComponent(escape(atob('$encoded')));
              try { new Function(sourceText).call(window); return 'evaluated'; }
              catch (error) {
                window.__snowybotStartupProbe = { ok: false, failed: true, reason: String(error && error.message || error) };
                if (window.AndroidBridge) window.AndroidBridge.onLog('[Bot][ERROR] ' + (error && error.stack || error));
                return 'error:' + String(error && error.message || error);
              }
            })();
        """.trimIndent()
        webView.evaluateJavascript(evaluation) { result ->
            if (destroyed) { snowyBotInjectionInFlight = false; return@evaluateJavascript }
            if (result?.contains("error:") == true) {
                val message = "FAILURE: runtime evaluation rejected: ${decodeEvaluationResult(result)}"
                snowyBotInjectionInFlight = false
                listener.onLog("[Bot] $message")
                onResult(message)
            } else {
                listener.onLog("[Bot] snowybot.js evaluated in existing WebView; confirming startup.")
                scheduleSnowyBotStartupProbe(onResult)
            }
        }
    }

    private fun scheduleSnowyBotStartupProbe(onResult: (String) -> Unit) {
        if (destroyed) { snowyBotInjectionInFlight = false; onResult("ERROR: WebView session unavailable"); return }
        if (snowyBotStartupProbeCount >= SNOWYBOT_STARTUP_MAX_POLLS) {
            snowyBotInjectionInFlight = false
            listener.onLog("[Bot] Runtime startup timed out before entrypoint confirmation.")
            onResult("FAILURE: snowybot.js startup probe timed out")
            return
        }
        snowyBotStartupProbeCount++
        webView.evaluateJavascript(snowyBotStartupProbeForTest()) { result ->
            if (destroyed) return@evaluateJavascript
            when {
                result?.contains("started") == true -> {
                    snowyBotScriptStarted = true
                    snowyBotInjectionInFlight = false
                    listener.onLog("[Bot] Runtime startup confirmed by current-page probe.")
                    onResult("STARTED: snowybot.js is running")
                }
                result?.contains("failed:") == true -> {
                    snowyBotInjectionInFlight = false
                    val reason = decodeEvaluationResult(result)?.toString() ?: result
                    listener.onLog("[Bot] Runtime refused startup: $reason")
                    onResult("FAILURE: snowybot.js refused startup: $reason")
                }
                result?.contains("stopped") == true -> {
                    snowyBotScriptStarted = false
                    snowyBotInjectionInFlight = false
                    listener.onSnowyBotStopped("script stopped before or during runtime")
                    onResult("FAILURE: snowybot.js is no longer active")
                }
                else -> {
                    if (snowyBotScriptStarted && result?.contains("running") != true) {
                        snowyBotScriptStarted = false
                        snowyBotInjectionInFlight = false
                        listener.onSnowyBotStopped("script stopped before or during runtime")
                        onResult("FAILURE: snowybot.js is no longer active")
                        return@evaluateJavascript
                    }
                    snowyBotStartupProbe = Runnable { scheduleSnowyBotStartupProbe(onResult) }.also {
                        mainHandler.postDelayed(it, SNOWYBOT_STARTUP_POLL_MS)
                    }
                }
            }
        }
    }

    fun stopDevConsoleExecution() = onMainThread {
        requireMainThread()
        if (!destroyed) {
            snowyBotStartupProbe?.let(mainHandler::removeCallbacks)
            snowyBotStartupProbe = null
            snowyBotInjectionInFlight = false
            snowyBotScriptStarted = false
            webView.evaluateJavascript("window.__snowybotStopRequested = true; window.snowyBotRunning = false;", null)
        }
    }

    fun placementScriptForTest(stakeAmount: Double, winChance: Double): String = placementScript(stakeAmount, winChance)

    private fun placementScript(stakeAmount: Double, winChance: Double): String {
        val stake = String.format(Locale.US, "%.8f", stakeAmount)
        val chance = String.format(Locale.US, "%.4f", winChance)
        return """
            (function() {
              if (window.__snowybotPlacementPending) { console.error('stale/in-flight pending gate is still set'); return false; }
              var balance = document.getElementById('pct_balance');
              var betInput = document.getElementById('pct_bet');
              var chanceInput = document.getElementById('pct_chance');
              var minBtn = document.getElementById('b_min');
              var rollBtn = document.getElementById('a_lo');
              if (!betInput || !chanceInput || !rollBtn || !balance || !minBtn) { console.error('Placement rejected: missing required element(s)'); return false; }
              var amount = '$stake';
              var chance = '$chance';
              var numericAmount = Number(amount);
              if (!isFinite(numericAmount) || numericAmount <= 0) { console.error('Placement rejected: stake is non-finite or rounds to zero'); return false; }
              if (numericAmount.toFixed(8) !== amount) { console.error('Placement rejected: stake is below 8-decimal precision'); return false; }
              minBtn.click();
              betInput.value = amount;
              betInput.dispatchEvent(new Event('input', { bubbles: true }));
              betInput.dispatchEvent(new Event('change', { bubbles: true }));
              var acceptedAmount = Number(betInput.value);
              var currentBalance = Number(String((('value' in balance && balance.value) ? balance.value : (balance.innerText || balance.textContent || ''))).replace(/[^0-9+.-]/g, ''));
              var minAmount = Number(betInput.min || 0);
              var maxAmount = Number(betInput.max || balance.max || currentBalance);
              if (!isFinite(currentBalance) || currentBalance <= 0) { console.error('Placement rejected: #pct_balance is invalid'); return false; }
              if (isFinite(minAmount) && minAmount > 0 && numericAmount < minAmount) { console.error('Placement rejected: stake ' + amount + ' is below site minimum ' + minAmount); return false; }
              if (isFinite(maxAmount) && maxAmount > 0 && numericAmount > maxAmount) { console.error('Placement rejected: stake ' + amount + ' exceeds available/site maximum ' + maxAmount); return false; }
              if (!isFinite(acceptedAmount) || acceptedAmount !== numericAmount) { console.error('Placement rejected: #pct_bet normalized requested ' + amount + ' to ' + betInput.value); return false; }
              chanceInput.value = chance;
              chanceInput.dispatchEvent(new Event('input', { bubbles: true }));
              chanceInput.dispatchEvent(new Event('change', { bubbles: true }));
              if (Number(chanceInput.value) !== Number(chance)) { console.error('Placement rejected: chance value did not persist'); return false; }
              if (betInput.disabled || chanceInput.disabled || rollBtn.disabled) { console.error('disabled (bet=' + betInput.disabled + ', chance=' + chanceInput.disabled + ', roll=' + rollBtn.disabled + ')'); return false; }
              window.__snowybotPlacementPending = true;
              if (window.AndroidBridge) window.AndroidBridge.onLog('[Wager] Controls validated: #pct_bet=' + amount + ' #pct_chance=' + chance);
              if (window.AndroidBridge) window.AndroidBridge.onLog('[Wager] Initial wager click dispatched');
              rollBtn.click();
              return true;
            })();
        """.trimIndent()
    }

    fun stopPageTimers() = onMainThread {
        requireMainThread()
        webView.evaluateJavascript(cancelPageTimersScript(), null)
    }

    fun cancelPendingCallbacks() {
        loginCallbackGeneration++
        loginAuthPollHandler?.removeCallbacksAndMessages(null)
        loginAuthPollHandler = null
        loginAuthPollInProgress = false
        loginBalancePollStartedCallback = null
        readinessPollHandler?.removeCallbacksAndMessages(null)
        readinessPollHandler = null
        finishReadiness(ready = false)
        watchdogReloadCallbacks.clear()
        watchdogRecoveryInProgress = false
        watchdogReloadAuthPoll?.let(mainHandler::removeCallbacks)
        watchdogReloadAuthPoll = null
        watchdogResumeCallbacks.clear()
        watchdogReloadPending = false
        intentionalWatchdogResumePending = false
    }

    fun injectDomMonitor() {
        webView.evaluateJavascript(domMonitorScript(), null)
    }

    fun domMonitorScriptForTest(): String = domMonitorScript()

    private fun domMonitorScript(): String = """
        (function() {
          if (window.__snowybotMonitorTimeout || window.__snowybotMonitorStarting) return;
          window.__snowybotMonitorStopped = false;
          window.__snowybotMonitorStarting = true;
          var attempts = 0;
          function checkDomUpdates() {
            if (window.__snowybotMonitorStopped) { window.__snowybotMonitorStarting = false; return; }
            attempts++;
            var balanceElement = document.getElementById('pct_balance');
            var raw = balanceElement ? (('value' in balanceElement && balanceElement.value) ? balanceElement.value : (balanceElement.innerText || balanceElement.textContent || '')) : '';
            var normalized = String(raw || '').replace(/[^0-9+.-]/g, '');
            var balance = normalized && isFinite(Number(normalized)) ? Number(normalized) : null;
            if (balance !== null && balance > 0) window.AndroidBridge.onBalanceUpdated(balance.toString());
            var wins = document.getElementById('wins');
            var losses = document.getElementById('losses');
            if (wins || losses) window.AndroidBridge.onStatsUpdated(wins ? wins.innerText || '0' : '0', losses ? losses.innerText || '0' : '0');
            if (attempts < 600 && !window.__snowybotMonitorStopped) {
              window.__snowybotMonitorTimeout = setTimeout(checkDomUpdates, 1000);
            }
            else { window.__snowybotMonitorStarting = false; window.__snowybotMonitorTimeout = null; }
          }
          if (document.readyState === 'complete' || document.readyState === 'interactive') checkDomUpdates();
          else document.addEventListener('DOMContentLoaded', checkDomUpdates, { once: true });
        })();
    """.trimIndent()

    private fun decodeEvaluationResult(result: String): Any? {
        if (result == "null") return null
        if (result.startsWith('"') && result.endsWith('"')) return JSONObject("{\"value\":$result}").opt("value")
        return result
    }

    private fun escapeJsString(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")
        .replace("\n", "\\n").replace("\r", "\\r").replace("</script>", "<\\/script>")

    companion object {
        private const val SNOWYBOT_SCRIPT_FILE = "snowybot.js"
        const val DEFAULT_TARGET_URL = "https://just-dice.com"
        const val RUN_READINESS_POLL_MS = 1_000L
        const val RUN_READINESS_MAX_POLLS = 40
        const val DOM_MONITOR_INTERVAL_MS = 1_000
        const val LOGIN_AUTH_POLL_MS = 1_000L
        const val LOGIN_AUTH_MAX_POLLS = 30
        const val SNOWYBOT_STARTUP_POLL_MS = 250L
        const val SNOWYBOT_STARTUP_MAX_POLLS = 24
        const val WATCHDOG_AUTH_POLL_MS = 500L
        const val WATCHDOG_AUTH_MAX_POLLS = 20

        private fun cancelPageTimersScript(): String = """
            (function() {
              window.__snowybotMonitorStopped = true;
              window.__snowybotMonitorStarting = false;
              if (window.__snowybotLoginStabilizationTimer) { clearTimeout(window.__snowybotLoginStabilizationTimer); window.__snowybotLoginStabilizationTimer = null; }
              if (window.__snowybotMonitorTimeout) { clearTimeout(window.__snowybotMonitorTimeout); window.__snowybotMonitorTimeout = null; }
              if (window.__snowybotLoginWaitForElements) { clearInterval(window.__snowybotLoginWaitForElements); window.__snowybotLoginWaitForElements = null; }
              if (window.__snowybotLoginBalancePoll) { clearInterval(window.__snowybotLoginBalancePoll); window.__snowybotLoginBalancePoll = null; }
              if (window.__snowybotLoginBalanceTimeout) { clearTimeout(window.__snowybotLoginBalanceTimeout); window.__snowybotLoginBalanceTimeout = null; }
            })();
        """.trimIndent()
    }
}
