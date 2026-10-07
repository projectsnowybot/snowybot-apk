package com.example.snowybottext.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snowybottext.service.BotStatus
import com.example.snowybottext.ui.theme.BrightGreen
import com.example.snowybottext.ui.theme.BrightYellow
import com.example.snowybottext.ui.theme.DarkGreyButton
import com.example.snowybottext.ui.theme.EmeraldGreen
import com.example.snowybottext.ui.theme.LossRed
import com.example.snowybottext.ui.theme.SnowybottextTheme
import java.util.Locale

@Composable
fun StatusHeaderCard(
    status: BotStatus,
    modifier: Modifier = Modifier,
    detail: String? = null,
    pageStatus: String? = null,
) {
    val statusColor = when (status) {
        BotStatus.RUNNING -> BrightGreen
        BotStatus.LOGGING_IN -> BrightYellow
        BotStatus.STALLED -> BrightYellow
        BotStatus.TARGET_REACHED, BotStatus.STOPPED -> BrightYellow
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BrightGreen),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SESSION STATUS", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BrightYellow)
            Text(
                text = if (status == BotStatus.RUNNING) "Running · snowybot.js active" else status.displayName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                maxLines = 1,
            )
            detail?.let { Text(it, fontSize = 15.sp, color = BrightYellow, maxLines = 1) }
            pageStatus?.let { Text(it, fontSize = 15.sp, color = BrightYellow, maxLines = 1) }
        }
    }
}

@Composable
fun HeroWalletCard(walletStash: Double, currentWager: Double, profitLoss: Double, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BrightGreen),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Balance", fontSize = 15.sp, color = BrightYellow)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                AdaptiveMetricValue(
                    text = String.format(Locale.US, "%.8f", walletStash),
                    availableWidth = maxWidth,
                    baseFontSize = 56.sp,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Current Bet Amount", fontSize = 15.sp, color = BrightYellow, maxLines = 1)
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        AdaptiveMetricValue(
                            text = String.format(Locale.US, "%.8f", currentWager),
                            availableWidth = maxWidth,
                            baseFontSize = 28.sp,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Profit:", fontSize = 15.sp, color = BrightYellow, maxLines = 1)
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        AdaptiveMetricValue(
                            text = String.format(Locale.US, "%+.8f", profitLoss),
                            availableWidth = maxWidth,
                            baseFontSize = 28.sp,
                        )
                    }
                }
            }
        }
    }
}

/** Shrinks monospace metrics only when their glyphs would exceed the available width. */
internal fun metricFontSize(text: String, availableWidth: Dp, baseFontSize: TextUnit): TextUnit {
    val minimumFontSize = 12.sp
    // Monospace glyphs are approximately 0.6em wide. Do not keep a fixed-size floor for
    // short values: a 10–11 character value can still overflow a narrow bet/profit column.
    val estimatedFitSize = availableWidth.value /
        (text.length.coerceAtLeast(1) * 0.6f)
    return minOf(baseFontSize.value, maxOf(minimumFontSize.value, estimatedFitSize)).sp
}

/** Preserves the metric's intended type size until width requires shrinking it. */
@Composable
private fun AdaptiveMetricValue(text: String, availableWidth: Dp, baseFontSize: TextUnit) {
    Text(
        text = text,
        fontSize = metricFontSize(text, availableWidth, baseFontSize),
        fontWeight = FontWeight.Bold,
        color = BrightYellow,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

internal fun balanceFontSize(text: String, availableWidth: Dp): TextUnit =
    metricFontSize(text, availableWidth, 56.sp)

@Composable
fun LoginSectionCard(
    uiState: DashboardUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    on2FACodeChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BrightGreen),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.username,
                onValueChange = onUsernameChange,
                enabled = uiState.canLogin,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 18.sp, color = BrightYellow),
                label = { Text("Username", fontSize = 16.sp, color = BrightYellow) },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = BrightGreen, modifier = Modifier.size(22.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                enabled = uiState.canLogin,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 18.sp, color = BrightYellow),
                label = { Text("Password", fontSize = 16.sp, color = BrightYellow) },
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = BrightGreen, modifier = Modifier.size(22.dp)) },
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisibility, enabled = uiState.canLogin) {
                        Icon(
                            imageVector = if (uiState.isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle password visibility",
                            tint = BrightGreen,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = uiState.code2FA,
                onValueChange = on2FACodeChange,
                enabled = uiState.canLogin,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 18.sp, color = BrightYellow),
                label = { Text("2FA Secret Key / Code (Optional)", fontSize = 16.sp, color = BrightYellow) },
                leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null, tint = BrightGreen, modifier = Modifier.size(22.dp)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                shape = RoundedCornerShape(12.dp),
            )
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.canLogin,
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreyButton, contentColor = BrightGreen),
                border = BorderStroke(1.dp, BrightGreen),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, tint = BrightGreen, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.isLoggingIn) "Logging in…" else "Login", color = BrightGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            uiState.loginError?.let { Text(it, fontSize = 14.sp, color = BrightYellow, maxLines = 2) }
        }
    }
}

@Composable
fun ServiceControlsBar(
    status: BotStatus,
    readyState: ReadyState,
    modifier: Modifier = Modifier,
    runtimeError: String? = null,
    isSessionAuthenticated: Boolean = false,
    onStart: () -> Unit,
    onReady: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onResetAll: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BrightGreen),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onReady,
                enabled = readyState != ReadyState.CHECKING && status != BotStatus.RUNNING && status != BotStatus.LOGGING_IN,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreyButton, contentColor = BrightGreen),
                border = BorderStroke(1.dp, BrightGreen),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
            ) {
                Text(
                    when (readyState) {
                        ReadyState.NOT_READY -> if (status == BotStatus.LOGGING_IN) "Logging in…" else if (isSessionAuthenticated) "Ready · Confirm session" else "Are you ready? · Check session"
                        ReadyState.CHECKING -> "Checking session…"
                        ReadyState.READY -> "Ready ✓ · Check again"
                    },
                    color = BrightGreen,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            val detail = when (readyState) {
                ReadyState.NOT_READY -> if (status == BotStatus.LOGGING_IN) "Login in progress." else if (isSessionAuthenticated) "Wallet detected; confirm Ready." else "Login and wait for wallet balance, then confirm Ready."
                ReadyState.CHECKING -> "Verifying session; no bet will be placed."
                ReadyState.READY -> runtimeError?.let { "Last run error: $it" } ?: "Session confirmed. Run is enabled."
            }
            Text(detail, fontSize = 14.sp, color = BrightYellow, maxLines = 1)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onStart,
                    enabled = readyState == ReadyState.READY && status != BotStatus.RUNNING && status != BotStatus.STALLED && status != BotStatus.LOGGING_IN,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreyButton, contentColor = BrightGreen),
                    border = BorderStroke(1.dp, BrightGreen),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = BrightGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(if (status == BotStatus.RUNNING) "RUNNING" else "Run", color = BrightGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                }
                Button(
                    onClick = onReload,
                    enabled = status == BotStatus.RUNNING || status == BotStatus.STALLED,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreyButton, contentColor = BrightGreen),
                    border = BorderStroke(1.dp, BrightGreen),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reload", tint = BrightGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Reload", color = BrightGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                }
                Button(
                    onClick = onStop,
                    enabled = status != BotStatus.STOPPED && status != BotStatus.TARGET_REACHED,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreyButton, contentColor = BrightGreen),
                    border = BorderStroke(1.dp, BrightGreen),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Stop wagering", tint = BrightGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Stop", color = BrightGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                }
            }
            if (onResetAll != null) Button(
                onClick = onResetAll,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreyButton, contentColor = BrightGreen),
                border = BorderStroke(1.dp, BrightGreen),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Reset all site and bot data", tint = BrightGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset All", color = BrightGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, device = "spec:width=393dp,height=852dp,dpi=440")
@Composable
private fun DashboardComponentsPreview() {
    SnowybottextTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusHeaderCard(status = BotStatus.STOPPED, pageStatus = "Page loaded")
            HeroWalletCard(walletStash = 0.0142, currentWager = 0.00001, profitLoss = 0.0002)
        }
    }
}
