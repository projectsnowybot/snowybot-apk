package com.example.snowybottext.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for recorded roll history.
 */
@Dao
interface RollDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoll(roll: RollEntity)

    @Query("SELECT * FROM roll_history ORDER BY timestamp DESC")
    fun getAllRolls(): Flow<List<RollEntity>>

    @Query("SELECT * FROM roll_history ORDER BY timestamp DESC LIMIT :limit")
    fun getLatestRolls(limit: Int): Flow<List<RollEntity>>

    @Query("SELECT COUNT(*) FROM roll_history")
    suspend fun getRollCount(): Int

    @Query("DELETE FROM roll_history")
    suspend fun clearAll()

    suspend fun clearAllRolls() = clearAll()
}
