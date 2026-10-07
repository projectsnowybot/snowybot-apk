package com.example.snowybottext.service

/** Pure login-state decisions shared by the service UI contract and unit tests. */
object LoginFlowPolicy {
    fun isAuthenticating(status: BotStatus, loginProgress: Boolean): Boolean =
        loginProgress || status == BotStatus.LOGGING_IN

    fun shouldComplete(balance: Double): Boolean = balance.isFinite() && balance > 0.0

    fun retryableError(status: BotStatus, loginProgress: Boolean): Boolean =
        !isAuthenticating(status, loginProgress)
}
