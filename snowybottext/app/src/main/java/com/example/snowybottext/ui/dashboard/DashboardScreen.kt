package com.example.snowybottext.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.snowybottext.ui.theme.CyanAccent
import com.example.snowybottext.ui.theme.DarkSurface
import com.example.snowybottext.ui.theme.EmeraldGreen
import com.example.snowybottext.ui.theme.LossRed
import com.example.snowybottext.ui.theme.PurpleAccent
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveMessage) {
        uiState.saveMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissSaveMessage()
        }
    }

    DashboardContent(
        uiState = uiState,
        modifier = modifier,
        onUsernameChange = { viewModel.onUsernameChange(it) },
        onPasswordChange = { viewModel.onPasswordChange(it) },
        on2FACodeChange = { viewModel.on2FACodeChange(it) },
        onTogglePasswordVisibility = { viewModel.togglePasswordVisibility() },
        onLogin = { viewModel.loginToJustDice(context) },
        onStart = { viewModel.startBot(context) },
        onReady = { viewModel.checkReady(context) },
        onReload = { viewModel.reloadBot(context) },
        onStop = { viewModel.stopBot(context) },
        onShowResetConfirmation = { viewModel.showResetConfirmation(it) },
        onResetBotState = { viewModel.resetBotState(context) },
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    modifier: Modifier = Modifier,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    on2FACodeChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onLogin: () -> Unit,
    onStart: () -> Unit,
    onReload: () -> Unit,
    onReady: () -> Unit = {},
    onStop: () -> Unit,
    onShowResetConfirmation: (Boolean) -> Unit = {},
    onResetBotState: () -> Unit = {},
    snackbarHostState: SnackbarHostState
) {
    val scrollState = rememberScrollState()

    if (uiState.showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { onShowResetConfirmation(false) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = LossRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Bot State File?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            text = {
                Text(
                    "Are you sure you want to reset app-private DataStore bot state, credentials, and PeanutEngine progression? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = onResetBotState,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LossRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Reset State File", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { onShowResetConfirmation(false) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusHeaderCard(status = uiState.status, detail = uiState.runtimeError ?: uiState.loginError)

        // Hero Wallet card containing Current Balance, Current Bet Amount, and Total P/L
        HeroWalletCard(
            walletStash = uiState.walletStash,
            currentWager = uiState.currentWager,
            profitLoss = uiState.profitLoss,
            startingBalance = uiState.startingPocketChange
        )

        // Login inputs and Login button
        LoginSectionCard(
            uiState = uiState,
            onUsernameChange = onUsernameChange,
            onPasswordChange = onPasswordChange,
            on2FACodeChange = on2FACodeChange,
            onTogglePasswordVisibility = onTogglePasswordVisibility,
            onLogin = onLogin
        )

        // Service Controls (Run Bot / Stop / Ready / Reload / Reset)
        ServiceControlsBar(
            status = uiState.status,
            readyState = uiState.readyState,
            runtimeError = uiState.runtimeError,
            onStart = onStart,
            onReady = onReady,
            onReload = onReload,
            onStop = onStop,
            onResetState = { onShowResetConfirmation(true) }
        )

        TargetProgressCard(
            walletStash = uiState.walletStash,
            startingBalance = uiState.startingPocketChange,
            targetLimit = uiState.targetLimit,
            targetProgress = uiState.targetProgress
        )

        // Financial & Strategy Metrics Grid (2x2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricStatCard(
                title = "Current Wager",
                value = String.format(Locale.US, "%.8f", uiState.currentWager),
                subtitle = "Wobble Factor: %.1f".format(Locale.US, uiState.wobbleFactor),
                icon = Icons.Rounded.Casino,
                iconTint = CyanAccent,
                modifier = Modifier.weight(1f)
            )

            MetricStatCard(
                title = "Safety Checkpoint",
                value = String.format(Locale.US, "%.8f", uiState.safetyCheckpoint),
                subtitle = "Oopsie Counter: %d".format(Locale.US, uiState.oopsieCounter),
                icon = Icons.Rounded.Shield,
                iconTint = EmeraldGreen,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricStatCard(
                title = "Checkpoint Juice",
                value = String.format(Locale.US, "%.8f", uiState.checkpointJuice),
                subtitle = "Base Floor Protection",
                icon = Icons.Rounded.WaterDrop,
                iconTint = PurpleAccent,
                modifier = Modifier.weight(1f)
            )

            MetricStatCard(
                title = "Wins / Losses",
                value = "${uiState.totalWins}W / ${uiState.totalLosses}L",
                subtitle = String.format(Locale.US, "Win Rate: %.1f%%", uiState.winRate),
                icon = Icons.Rounded.EmojiEvents,
                iconTint = EmeraldGreen,
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedVisibility(visible = uiState.recentRolls.isNotEmpty()) {
            RecentWagerSection(rolls = uiState.recentRolls)
        }

        ActivityLogSection(messages = uiState.activityLogs)

        SnackbarHost(hostState = snackbarHostState)
    }
}
