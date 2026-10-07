package com.example.snowybottext.service

import com.example.snowybottext.ui.dashboard.DashboardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentWagerStateTest {
    @Test
    fun bundledScriptInitialWalletWagerFlowsThroughDashboard() {
        val source = CurrentWagerState()
        source.beginBundledScript(1_440_000.0)

        assertEquals(1_440_000.0, DashboardUiState(currentWager = source.amount).currentWager, 0.0)
    }

    @Test
    fun bundledScriptProgressionIncreaseUpdatesDisplayedWager() {
        val source = CurrentWagerState().apply { beginBundledScript(1.0) }

        assertTrue(source.acceptScriptAmount(2.0))
        assertEquals(2.0, DashboardUiState(currentWager = source.amount).currentWager, 0.0)
    }

    @Test
    fun scriptResetToBackupUpdatesDisplayedWager() {
        val source = CurrentWagerState().apply { beginBundledScript(8.0) }

        assertTrue(source.acceptScriptAmount(1.0))
        assertEquals(1.0, DashboardUiState(currentWager = source.amount).currentWager, 0.0)
    }

    @Test
    fun nativePeanutEngineSnapshotCannotOverwriteBundledScriptWager() {
        val source = CurrentWagerState().apply { beginBundledScript(4.0) }
        assertTrue(source.acceptScriptAmount(8.0))
        val nativeSnapshotAccepted = source.acceptNativeAmount(2.0)

        val dashboard = DashboardUiState(
            currentWager = source.amount,
            currentWagerFromScript = source.isBundledScriptAuthoritative,
        )
        assertFalse(nativeSnapshotAccepted)
        assertEquals(8.0, dashboard.currentWager, 0.0)
        assertTrue(dashboard.currentWagerFromScript)
    }

    @Test
    fun nativeWagerIsAcceptedWhenBundledScriptIsNotAuthoritative() {
        val source = CurrentWagerState()

        assertTrue(source.acceptNativeAmount(3.5))
        assertEquals(3.5, source.amount, 0.0)
    }
}
