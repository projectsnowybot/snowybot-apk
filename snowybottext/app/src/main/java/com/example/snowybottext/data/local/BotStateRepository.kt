package com.example.snowybottext.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.snowybottext.engine.PeanutStrategyState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.botStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "bot_state_prefs")

/** Persists Peanut Strategy state in app-private DataStore storage. */
class BotStateRepository(private val context: Context) {
    val botStateFlow: Flow<PeanutStrategyState> = context.botStateDataStore.data.map { prefs ->
        PeanutStrategyState(
            startingPocketChange = prefs[KEY_STARTING_POCKET_CHANGE] ?: 0.0,
            tinyPeanutSize = prefs[KEY_TINY_PEANUT_SIZE] ?: 0.0,
            backupPeanut = prefs[KEY_BACKUP_PEANUT] ?: 0.0,
            tenPeanuts = prefs[KEY_TEN_PEANUTS] ?: 0.0,
            walletStash = prefs[KEY_WALLET_STASH] ?: 0.0,
            targetLimit = prefs[KEY_TARGET_LIMIT] ?: 144000.0,
            areWeRichYet = prefs[KEY_ARE_WE_RICH_YET] ?: false,
            oopsieCounter = prefs[KEY_OOPSIE_COUNTER] ?: 0,
            previousWalletState = prefs[KEY_PREVIOUS_WALLET_STATE] ?: 0.0,
            oldTicketStub = prefs[KEY_OLD_TICKET_STUB] ?: 0L,
            shinyNewTicket = prefs[KEY_SHINY_NEW_TICKET] ?: 0L,
            totalSessionWins = prefs[KEY_TOTAL_SESSION_WINS] ?: 0,
            totalSessionLosses = prefs[KEY_TOTAL_SESSION_LOSSES] ?: 0,
            baseWinReference = prefs[KEY_BASE_WIN_REFERENCE] ?: 0.0,
            baseLossReference = prefs[KEY_BASE_LOSS_REFERENCE] ?: 0.0,
            currentWagerAmount = prefs[KEY_CURRENT_WAGER_AMOUNT] ?: 0.0,
            previousWagerAmount = prefs[KEY_PREVIOUS_WAGER_AMOUNT] ?: 0.0,
            luckyCoinFlip = prefs[KEY_LUCKY_COIN_FLIP] ?: 0,
            checkpointJuice = prefs[KEY_CHECKPOINT_JUICE] ?: 0.0,
            wobbleFactor = prefs[KEY_WOBBLE_FACTOR] ?: 1.0,
            safetyCheckpoint = prefs[KEY_SAFETY_CHECKPOINT] ?: 0.0,
            lastSeenTimestamp = prefs[KEY_LAST_SEEN_TIMESTAMP] ?: 0L
        )
    }

    suspend fun saveBotState(state: PeanutStrategyState) {
        context.botStateDataStore.edit { prefs ->
            prefs[KEY_STARTING_POCKET_CHANGE] = state.startingPocketChange
            prefs[KEY_TINY_PEANUT_SIZE] = state.tinyPeanutSize
            prefs[KEY_BACKUP_PEANUT] = state.backupPeanut
            prefs[KEY_TEN_PEANUTS] = state.tenPeanuts
            prefs[KEY_WALLET_STASH] = state.walletStash
            prefs[KEY_TARGET_LIMIT] = state.targetLimit
            prefs[KEY_ARE_WE_RICH_YET] = state.areWeRichYet
            prefs[KEY_OOPSIE_COUNTER] = state.oopsieCounter
            prefs[KEY_PREVIOUS_WALLET_STATE] = state.previousWalletState
            prefs[KEY_OLD_TICKET_STUB] = state.oldTicketStub
            prefs[KEY_SHINY_NEW_TICKET] = state.shinyNewTicket
            prefs[KEY_TOTAL_SESSION_WINS] = state.totalSessionWins
            prefs[KEY_TOTAL_SESSION_LOSSES] = state.totalSessionLosses
            prefs[KEY_BASE_WIN_REFERENCE] = state.baseWinReference
            prefs[KEY_BASE_LOSS_REFERENCE] = state.baseLossReference
            prefs[KEY_CURRENT_WAGER_AMOUNT] = state.currentWagerAmount
            prefs[KEY_PREVIOUS_WAGER_AMOUNT] = state.previousWagerAmount
            prefs[KEY_LUCKY_COIN_FLIP] = state.luckyCoinFlip
            prefs[KEY_CHECKPOINT_JUICE] = state.checkpointJuice
            prefs[KEY_WOBBLE_FACTOR] = state.wobbleFactor
            prefs[KEY_SAFETY_CHECKPOINT] = state.safetyCheckpoint
            prefs[KEY_LAST_SEEN_TIMESTAMP] = state.lastSeenTimestamp
        }
    }

    suspend fun clearBotState() {
        context.botStateDataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_STARTING_POCKET_CHANGE = doublePreferencesKey("starting_pocket_change")
        private val KEY_TINY_PEANUT_SIZE = doublePreferencesKey("tiny_peanut_size")
        private val KEY_BACKUP_PEANUT = doublePreferencesKey("backup_peanut")
        private val KEY_TEN_PEANUTS = doublePreferencesKey("ten_peanuts")
        private val KEY_WALLET_STASH = doublePreferencesKey("wallet_stash")
        private val KEY_TARGET_LIMIT = doublePreferencesKey("target_limit")
        private val KEY_ARE_WE_RICH_YET = booleanPreferencesKey("are_we_rich_yet")
        private val KEY_OOPSIE_COUNTER = intPreferencesKey("oopsie_counter")
        private val KEY_PREVIOUS_WALLET_STATE = doublePreferencesKey("previous_wallet_state")
        private val KEY_OLD_TICKET_STUB = longPreferencesKey("old_ticket_stub")
        private val KEY_SHINY_NEW_TICKET = longPreferencesKey("shiny_new_ticket")
        private val KEY_TOTAL_SESSION_WINS = intPreferencesKey("total_session_wins")
        private val KEY_TOTAL_SESSION_LOSSES = intPreferencesKey("total_session_losses")
        private val KEY_BASE_WIN_REFERENCE = doublePreferencesKey("base_win_reference")
        private val KEY_BASE_LOSS_REFERENCE = doublePreferencesKey("base_loss_reference")
        private val KEY_CURRENT_WAGER_AMOUNT = doublePreferencesKey("current_wager_amount")
        private val KEY_PREVIOUS_WAGER_AMOUNT = doublePreferencesKey("previous_wager_amount")
        private val KEY_LUCKY_COIN_FLIP = intPreferencesKey("lucky_coin_flip")
        private val KEY_CHECKPOINT_JUICE = doublePreferencesKey("checkpoint_juice")
        private val KEY_WOBBLE_FACTOR = doublePreferencesKey("wobble_factor")
        private val KEY_SAFETY_CHECKPOINT = doublePreferencesKey("safety_checkpoint")
        private val KEY_LAST_SEEN_TIMESTAMP = longPreferencesKey("last_seen_timestamp")
    }
}
