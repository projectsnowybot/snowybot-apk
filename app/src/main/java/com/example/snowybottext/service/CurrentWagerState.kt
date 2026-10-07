package com.example.snowybottext.service

/**
 * Source-of-truth arbitration for the wager shown by the UI.
 * While snowybot.js is running, its callback is authoritative; native strategy snapshots
 * must not replace it. Outside a bundled-script run, native state remains usable.
 */
class CurrentWagerState {
    var amount: Double = 0.0
        private set
    var isBundledScriptAuthoritative: Boolean = false
        private set

    fun beginBundledScript(initialAmount: Double) {
        isBundledScriptAuthoritative = true
        amount = 0.0
        acceptScriptAmount(initialAmount)
    }

    fun acceptScriptAmount(value: Double): Boolean {
        if (!value.isFinite() || value <= 0.0) return false
        amount = value
        return true
    }

    fun acceptNativeAmount(value: Double): Boolean {
        if (isBundledScriptAuthoritative || !value.isFinite() || value <= 0.0) return false
        amount = value
        return true
    }

    fun endBundledScript() {
        isBundledScriptAuthoritative = false
    }
}
