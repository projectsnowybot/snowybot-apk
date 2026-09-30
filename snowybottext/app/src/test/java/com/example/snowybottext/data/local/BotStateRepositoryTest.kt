package com.example.snowybottext.data.local

import com.example.snowybottext.engine.PeanutStrategyState
import org.junit.Assert.assertEquals
import org.junit.Test

class BotStateRepositoryTest {
    @Test
    fun stateFieldsSurviveRoundTripMapping() {
        val state = PeanutStrategyState(startingPocketChange = 42.5, walletStash = 43.25, currentWagerAmount = 0.0025)
        assertEquals(42.5, state.startingPocketChange, 0.0)
        assertEquals(43.25, state.walletStash, 0.0)
        assertEquals(0.0025, state.currentWagerAmount, 0.0)
    }
}
