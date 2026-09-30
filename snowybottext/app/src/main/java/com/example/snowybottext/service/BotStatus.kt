package com.example.snowybottext.service

/**
 * Enum representing the current operational status of the JustDice Foreground Service.
 */
enum class BotStatus(val displayName: String) {
    STOPPED("Stopped"),
    LOGGING_IN("Logging in…"),
    RUNNING("Running"),
    STALLED("Stalled (Auto-reconnecting)"),
    TARGET_REACHED("Target Reached")
}
