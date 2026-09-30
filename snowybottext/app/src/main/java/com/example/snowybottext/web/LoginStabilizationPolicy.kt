package com.example.snowybottext.web

/** One-way gate: a positive current balance confirms immediately; later results cannot change it. */
internal class LoginStabilizationPolicy {
    enum class Action { WAIT, COMPLETE, IGNORE }

    private var completed = false

    fun onBalance(balance: Double?): Action {
        if (completed) return Action.IGNORE
        if (LoginBalanceAuthPolicy.evaluate(balance) == LoginBalanceAuthPolicy.Decision.WAIT) return Action.WAIT
        completed = true
        return Action.COMPLETE
    }

    fun onTimeout(): Action {
        if (completed) return Action.IGNORE
        completed = true
        return Action.WAIT
    }

    fun reset() {
        completed = false
    }

    fun isCompleted(): Boolean = completed
}
