package com.example.snowybottext.service

import android.R
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.snowybottext.MainActivity
import java.util.Locale

/**
 * Manager class responsible for creating notification channels and building/updating
 * persistent foreground service notifications for the JustDice Bot engine.
 */
class JustDiceNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Live status notification for JustDice Bot engine"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        status: BotStatus,
        balance: Double,
        totalWins: Int,
        totalLosses: Int,
        initialBalance: Double,
        targetLimit: Double,
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(context, JustDiceBotService::class.java).apply {
            action = JustDiceBotService.ACTION_STOP_BOT
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val reloadIntent = Intent(context, JustDiceBotService::class.java).apply {
            action = JustDiceBotService.ACTION_RELOAD_PAGE
        }
        val reloadPendingIntent = PendingIntent.getService(
            context,
            2,
            reloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val profitLoss = balance - initialBalance
        val profitText = String.format(Locale.US, "%+.8f", profitLoss)
        val balanceText = String.format(Locale.US, "%.8f", balance)

        val contentTitle = "SnowyBot - ${status.displayName}"
        val contentText = when (status) {
            BotStatus.LOGGING_IN -> "Logging in to the existing authenticated browser session."
            BotStatus.TARGET_REACHED -> "Target Reached! Final Balance: $balanceText | P/L: $profitText"
            BotStatus.STALLED -> "Stalled for 30s! Auto-reconnecting... Balance: $balanceText"
            BotStatus.RUNNING -> "Bal: $balanceText | P/L: $profitText | W: $totalWins L: $totalLosses"
            BotStatus.STOPPED -> "Bot is stopped. Final Balance: $balanceText"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.stat_notify_sync)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$contentText\nTarget Limit: ${String.format(Locale.US, "%.2f", targetLimit)}"
                )
            )
            .setOngoing((status == BotStatus.RUNNING) || (status == BotStatus.STALLED))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if ((status == BotStatus.RUNNING) || (status == BotStatus.STALLED)) {
            builder.addAction(
                R.drawable.ic_menu_rotate,
                "Reload",
                reloadPendingIntent,
            )
            builder.addAction(
                R.drawable.ic_delete,
                "Stop",
                stopPendingIntent,
            )
        }

        return builder.build()
    }

    @SuppressLint("MissingPermission")
    fun updateNotification(
        status: BotStatus,
        balance: Double,
        totalWins: Int,
        totalLosses: Int,
        initialBalance: Double,
        targetLimit: Double,
    ) {
        val notification = buildNotification(
            status,
            balance,
            totalWins,
            totalLosses,
            initialBalance,
            targetLimit,
        )
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Handle missing POST_NOTIFICATIONS permission gracefully
        }
    }

    companion object {
        const val CHANNEL_ID = "just_dice_bot_channel"
        const val CHANNEL_NAME = "JustDice Bot Service"
        const val NOTIFICATION_ID = 1001
    }
}
