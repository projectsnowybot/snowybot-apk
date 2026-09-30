package com.example.snowybottext.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snowybottext.data.local.RollEntity
import com.example.snowybottext.service.BotStatus
import com.example.snowybottext.ui.theme.AmberWarning
import com.example.snowybottext.ui.theme.CyanAccent
import com.example.snowybottext.ui.theme.DarkBorder
import com.example.snowybottext.ui.theme.DarkSurface
import com.example.snowybottext.ui.theme.EmeraldGreen
import com.example.snowybottext.ui.theme.LossRed
import com.example.snowybottext.ui.theme.ProfitGreen
import com.example.snowybottext.ui.theme.PurpleAccent
import com.example.snowybottext.ui.theme.SnowybottextTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusHeaderCard(status: BotStatus, modifier: Modifier = Modifier, detail: String? = null) {
    val color = when (status) {
        BotStatus.RUNNING -> EmeraldGreen
        BotStatus.LOGGING_IN -> MaterialTheme.colorScheme.primary
        BotStatus.STALLED -> AmberWarning
        BotStatus.TARGET_REACHED -> CyanAccent
        BotStatus.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("BOT STATUS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (status == BotStatus.RUNNING) "Running · snowybot.js active" else status.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            if (status == BotStatus.LOGGING_IN) {
                Text("Submitting credentials and verifying the wallet…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun HeroWalletCard(walletStash: Double, currentWager: Double, profitLoss: Double, startingBalance: Double, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Current Balance (Wallet Stash)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(String.format(Locale.US, "%.8f", walletStash), style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black, color = CyanAccent, fontFamily = FontFamily.Monospace)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Current Bet Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(String.format(Locale.US, "%.8f", currentWager), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total P / L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(String.format(Locale.US, "%+.8f", profitLoss), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (profitLoss >= 0) ProfitGreen else LossRed)
                }
            }
        }
    }
}

@Composable
fun LoginSectionCard(
    uiState: DashboardUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    on2FACodeChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DarkBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = PurpleAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "JUST-DICE LOGIN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = uiState.username,
                onValueChange = onUsernameChange,
                enabled = uiState.canLogin,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                enabled = uiState.canLogin,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisibility, enabled = uiState.canLogin) {
                        Icon(
                            imageVector = if (uiState.isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle Password Visibility"
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = uiState.code2FA,
                onValueChange = on2FACodeChange,
                enabled = uiState.canLogin,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("2FA Secret Key / Code (Optional)") },
                leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = uiState.canLogin,
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (uiState.isLoggingIn) "Logging in…" else "Login", fontWeight = FontWeight.Bold)
            }
            if (uiState.walletStash.isFinite() && uiState.walletStash > 0.0) {
                Text(
                    text = "Balance detected · %.8f".format(Locale.US, uiState.walletStash),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (uiState.loginError != null) {
                Text(
                    text = uiState.loginError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TargetProgressCard(walletStash: Double, startingBalance: Double, targetLimit: Double, targetProgress: Float, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Target · start %.4f".format(Locale.US, startingBalance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%.4f / %.4f", walletStash, targetLimit), style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(progress = { targetProgress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun MetricStatCard(title: String, value: String, subtitle: String, icon: ImageVector, iconTint: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ServiceControlsBar(status: BotStatus, readyState: ReadyState, runtimeError: String? = null, isSessionAuthenticated: Boolean = false,
                       modifier: Modifier = Modifier,
                       onStart: () -> Unit, onReady: () -> Unit, onReload: () -> Unit, onStop: () -> Unit,
                       onResetState: (() -> Unit)? = null) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp), border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DarkBorder))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onReady,
                enabled = readyState != ReadyState.CHECKING && status != BotStatus.RUNNING && status != BotStatus.LOGGING_IN,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (readyState == ReadyState.READY) EmeraldGreen else MaterialTheme.colorScheme.primary,
                    contentColor = if (readyState == ReadyState.READY) Color.Black else MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    when (readyState) {
                        ReadyState.NOT_READY -> if (status == BotStatus.LOGGING_IN) "Logging in…" else if (isSessionAuthenticated) "Ready · Confirm session" else "Are you ready? · Check session"
                        ReadyState.CHECKING -> "Checking session…"
                        ReadyState.READY -> "Ready ✓ · Check again"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                when (readyState) {
                    ReadyState.NOT_READY -> if (status == BotStatus.LOGGING_IN) "Login is in progress; readiness must be confirmed after authentication." else if (isSessionAuthenticated) "Wallet detected. Confirm Ready to enable Run." else "Login and wait for your wallet balance, then confirm Ready."
                    ReadyState.CHECKING -> "Verifying the current WebView session; no bet will be placed."
                    ReadyState.READY -> if (runtimeError != null) "Last run error: $runtimeError" else "Session confirmed. Run is enabled until the page navigates or reloads."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val running = status == BotStatus.RUNNING
                Button(onClick = onStart, enabled = readyState == ReadyState.READY && status != BotStatus.RUNNING && status != BotStatus.STALLED && status != BotStatus.LOGGING_IN,
                    modifier = Modifier.weight(1f).height(52.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) EmeraldGreen else MaterialTheme.colorScheme.primary,
                        contentColor = if (running) Color.Black else MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ), shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(3.dp))
                    Text(if (running) "RUNNING" else "Run Bot", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = onReload, enabled = status == BotStatus.RUNNING || status == BotStatus.STALLED,
                    modifier = Modifier.weight(1f).height(52.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reload", Modifier.size(18.dp)); Spacer(Modifier.width(3.dp))
                    Text("Reload", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                }
                Button(onClick = onStop, enabled = status != BotStatus.STOPPED && status != BotStatus.TARGET_REACHED,
                    modifier = Modifier.weight(1f).height(52.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed, contentColor = Color.White,
                        disabledContainerColor = LossRed.copy(alpha = 0.3f)), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Stop wagering", Modifier.size(18.dp)); Spacer(Modifier.width(3.dp))
                    Text("Stop", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                }
            }
            if (onResetState != null) OutlinedButton(onClick = onResetState, modifier = Modifier.fillMaxWidth().height(48.dp),
                border = BorderStroke(1.dp, LossRed.copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Reset State", Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Reset State")
            }
        }
    }
}

@Composable
fun ActivityLogSection(messages: List<String>, modifier: Modifier = Modifier) {
    if (messages.isEmpty()) return
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("APP ACTIVITY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            messages.take(8).forEach { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun RecentWagerSection(rolls: List<RollEntity>, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("RECENT ROLLS PREVIEW", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            rolls.take(4).forEach { RecentRollItem(it); Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
fun RecentRollItem(roll: RollEntity) {
    val win = roll.isWin
    val badge = if (win) ProfitGreen else LossRed
    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(roll.timestamp))
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (win) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown, contentDescription = null, tint = badge, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Column {
                    Text(if (win) "WIN · Roll %.4f".format(Locale.US, roll.rollResult) else "LOSS · Roll %.4f".format(Locale.US, roll.rollResult), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(String.format(Locale.US, "%+.8f", roll.profit), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = badge)
                Text(String.format(Locale.US, "Bet: %.8f", roll.betAmount), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
