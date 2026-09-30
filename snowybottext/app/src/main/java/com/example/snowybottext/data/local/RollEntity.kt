package com.example.snowybottext.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a recorded dice roll wager result in history.
 */
@Entity(tableName = "roll_history")
data class RollEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val wagerId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val betAmount: Double,
    val rollResult: Double,
    val isWin: Boolean,
    val profit: Double,
    val balanceAfter: Double
)
