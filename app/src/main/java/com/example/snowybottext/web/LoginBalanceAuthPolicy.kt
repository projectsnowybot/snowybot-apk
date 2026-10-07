package com.example.snowybottext.web

/** Authentication is determined solely by a valid positive balance from the current DOM. */
internal object LoginBalanceAuthPolicy {
    enum class Decision { CONFIRMED, WAIT }

    fun evaluate(postLoginBalance: Double?): Decision =
        if (postLoginBalance != null && postLoginBalance.isFinite() && postLoginBalance > 0.0) {
            Decision.CONFIRMED
        } else {
            Decision.WAIT
        }
}
