package com.example.snowybottext.web

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.lang.ref.WeakReference

/** Activity-hosted owner for the single authenticated WebView session. */
class JustDiceWebSession private constructor(listener: JustDiceBridgeListener) {
    @Volatile private var listener: JustDiceBridgeListener = listener

    private fun updateListener(listener: JustDiceBridgeListener) {
        this.listener = listener
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var host = WeakReference<Activity>(null)
    private var webView: WebView? = null
    private var bridge: JustDiceWebBridge? = null
    @Volatile private var destroyed = false

    fun attach(activity: Activity) {
        checkMainThread()
        // Configuration recreation must preserve the single authenticated WebView instance.
        destroyed = false
        host = WeakReference(activity)
        if (webView == null) {
            val created = WebView(activity).apply {
                setBackgroundColor(Color.TRANSPARENT)
            }
            webView = created
            bridge = JustDiceWebBridge(created, listener)
        }
    }

    fun detach(activity: Activity) {
        checkMainThread()
        // Keep WebView attached to its original context/session; do not destroy or re-parent it.
        if (host.get() === activity) host.clear()
    }

    fun withBridge(action: (JustDiceWebBridge) -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!destroyed) bridge?.let(action)
                ?: listener.onLog("WebView is not attached. Open the app before Login or Run.")
        } else {
            mainHandler.post {
                if (!destroyed) bridge?.let(action)
                    ?: listener.onLog("WebView is not attached. Open the app before Login or Run.")
            }
        }
    }

    fun destroy() {
        if (Looper.myLooper() == Looper.getMainLooper()) destroyOnMainThread()
        else mainHandler.post { destroyOnMainThread() }
    }

    private fun destroyOnMainThread() {
        checkMainThread()
        destroyed = true
        bridge?.destroy()
        webView?.let { view ->
            view.stopLoading()
            view.removeJavascriptInterface(JustDiceJsBridge.BRIDGE_NAME)
            view.webChromeClient = null
            view.destroy()
        }
        webView = null
        bridge = null
        host.clear()
        synchronized(this) {
            if (instance?.get() === this) instance = null
        }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "WebView session must be managed on the main thread" }
    }

    companion object {
        @Volatile private var instance: WeakReference<JustDiceWebSession>? = null

        fun get(listener: JustDiceBridgeListener): JustDiceWebSession = synchronized(this) {
            instance?.get()?.also { it.updateListener(listener) }
                ?: JustDiceWebSession(listener).also { instance = WeakReference(it) }
        }
    }
}
