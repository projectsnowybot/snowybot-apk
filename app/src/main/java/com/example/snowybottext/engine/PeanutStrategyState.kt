package com.example.snowybottext.engine

/**
 * Data class representing the full state of the Peanut Strategy bot.
 */
data class PeanutStrategyState(
    val startingPocketChange: Double = 0.0,
    val tinyPeanutSize: Double = 0.0,
    val backupPeanut: Double = 0.0,
    val tenPeanuts: Double = 0.0,
    val walletStash: Double = 0.0,
    val targetLimit: Double = Double.POSITIVE_INFINITY,
    val areWeRichYet: Boolean = false,
    val oopsieCounter: Int = 0,
    val previousWalletState: Double = 0.0,
    val oldTicketStub: Long = 0,
    val shinyNewTicket: Long = 0,
    val totalSessionWins: Int = 0,
    val totalSessionLosses: Int = 0,
    val baseWinReference: Double = 0.0,
    val baseLossReference: Double = 0.0,
    val currentWagerAmount: Double = 0.0,
    val previousWagerAmount: Double = 0.0,
    val luckyCoinFlip: Int = 0,
    val checkpointJuice: Double = 0.0,
    val wobbleFactor: Double = 1.0,
    val safetyCheckpoint: Double = 0.0,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
