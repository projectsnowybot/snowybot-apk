package com.example.snowybottext.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.snowybottext.ui.theme.BrightGreen
import com.example.snowybottext.ui.theme.DarkBackground
import com.example.snowybottext.ui.theme.BrightYellow
import com.example.snowybottext.ui.theme.DarkGreyButton
import com.example.snowybottext.ui.theme.EmeraldGreen
import com.example.snowybottext.ui.theme.SnowybottextTheme
import com.example.snowybottext.ui.theme.DarkSurface

@Composable
fun DashboardScreen(modifier: Modifier = Modifier, viewModel: DashboardViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveMessage) {
        uiState.saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSaveMessage()
        }
    }

    DashboardContent(
        uiState = uiState,
        modifier = modifier,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        on2FACodeChange = viewModel::on2FACodeChange,
        onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
        onLogin = { viewModel.loginToJustDice(context) },
        onStart = { viewModel.startBot(context) },
        onReady = { viewModel.checkReady(context) },
        onReload = { viewModel.reloadBot(context) },
        onStop = { viewModel.stopBot(context) },
        onShowResetAllConfirmation = viewModel::showResetAllConfirmation,
        onResetAll = { viewModel.resetAll(context) },
        snackbarHostState = snackbarHostState,
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
    onShowResetAllConfirmation: (Boolean) -> Unit = {},
    onResetAll: () -> Unit = {},
    snackbarHostState: SnackbarHostState,
) {
    if (uiState.showResetAllConfirmation) {
        AlertDialog(
            onDismissRequest = { onShowResetAllConfirmation(false) },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = BrightYellow,
            textContentColor = BrightYellow,
            title = { Text("Reset All?", fontWeight = FontWeight.Bold, color = BrightYellow) },
            text = {
                Text(
                    "This stops the bot/service/watchdog, clears auto-resume, and deletes all WebView cookies, site storage, cache, history, form data, bot state DataStore, and saved credentials. You will be logged out and must enter credentials again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrightYellow,
                )
            },
            confirmButton = {
                Button(
                    onClick = onResetAll,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreyButton, contentColor = BrightGreen),
                    border = BorderStroke(1.dp, BrightGreen),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Reset All", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { onShowResetAllConfirmation(false) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrightGreen),
                    border = BorderStroke(1.dp, BrightGreen),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Cancel", fontWeight = FontWeight.Bold) }
            },
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        val tight = maxHeight < 900.dp
        val padding = if (tight) 6.dp else 12.dp
        val gap = if (tight) 6.dp else 10.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = padding, vertical = padding),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Canvas(Modifier.size(56.dp)) {
                    val center = this.center
                    drawCircle(
                        color = BrightYellow,
                        radius = size.minDimension * (32f / 108f),
                        center = center,
                        style = Stroke(width = size.minDimension * (8f / 108f)),
                    )
                    drawCircle(
                        color = BrightYellow,
                        radius = size.minDimension * (16f / 108f),
                        center = center,
                    )
                }
                Text(
                    "snowybot",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrightYellow,
                    maxLines = 1,
                )
            }
            StatusHeaderCard(
                status = uiState.status,
                detail = uiState.runtimeError ?: uiState.loginError,
                pageStatus = uiState.pageStatus,
            )
            HeroWalletCard(uiState.walletStash, uiState.currentWager, uiState.profitLoss)
            LoginSectionCard(
                uiState = uiState,
                onUsernameChange = onUsernameChange,
                onPasswordChange = onPasswordChange,
                on2FACodeChange = on2FACodeChange,
                onTogglePasswordVisibility = onTogglePasswordVisibility,
                onLogin = onLogin,
            )
            ServiceControlsBar(
                status = uiState.status,
                readyState = uiState.readyState,
                runtimeError = uiState.runtimeError,
                isSessionAuthenticated = uiState.isSessionAuthenticated,
                onStart = onStart,
                onReady = onReady,
                onReload = onReload,
                onStop = onStop,
                onResetAll = { onShowResetAllConfirmation(true) },
            )
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, device = "spec:width=393dp,height=852dp,dpi=440")
@Composable
private fun DashboardPreview() {
    SnowybottextTheme {
        DashboardContent(
            uiState = DashboardUiState(pageStatus = "Page loaded"),
            onUsernameChange = {},
            onPasswordChange = {},
            on2FACodeChange = {},
            onTogglePasswordVisibility = {},
            onLogin = {},
            onStart = {},
            onReload = {},
            onReady = {},
            onStop = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
