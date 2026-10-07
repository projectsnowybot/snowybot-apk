package com.example.snowybottext.web

/** Deterministic load/session decisions independent of Android WebView and live network. */
internal class WebSessionLifecyclePolicy {
    private var initialLoadStarted = false
    private var pageLoaded = false
    private var sessionGeneration = 0

    fun onSessionAttached(): Boolean {
        if (initialLoadStarted) return false
        initialLoadStarted = true
        return true
    }

    fun onBackgroundOrForeground(): Boolean = false

    fun onPageLoaded() {
        pageLoaded = true
    }

    fun onRunRequested(): RunSessionAction =
        if (initialLoadStarted) RunSessionAction.REUSE_EXISTING_SESSION
        else {
            initialLoadStarted = true
            RunSessionAction.CREATE_AND_LOAD_SESSION
        }

    fun onResetAll() {
        initialLoadStarted = false
        pageLoaded = false
        sessionGeneration++
    }

    fun isPageLoaded(): Boolean = pageLoaded
    fun sessionGeneration(): Int = sessionGeneration
}

internal enum class RunSessionAction { CREATE_AND_LOAD_SESSION, REUSE_EXISTING_SESSION }

/** Bounds DOM readiness stabilization after the main page load; authentication has its own gate. */
internal class PageDomReadinessPolicy(private val timeoutMs: Long = TIMEOUT_MS) {
    private var startedAtMs: Long? = null
    private var resolved = false

    fun begin(nowMs: Long) {
        startedAtMs = nowMs
        resolved = false
    }

    fun onDomAvailable(): Boolean {
        if (resolved) return false
        resolved = true
        return true
    }

    fun isTimedOut(nowMs: Long): Boolean {
        val start = startedAtMs ?: return false
        if (resolved || nowMs - start < timeoutMs) return false
        resolved = true
        return true
    }

    companion object {
        const val TIMEOUT_MS = 35_000L
    }
}
