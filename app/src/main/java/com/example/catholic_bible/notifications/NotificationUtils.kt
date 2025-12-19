package com.example.catholic_bible.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.catholic_bible.R

object NotificationUtils {

    private const val CHANNEL_ID = "daily_verse_channel"
    private const val CHANNEL_NAME = "Daily Verse"
    private const val NOTIF_ID = 1001

    fun showVerseNotification(context: Context, title: String, message: String) {
        ensureChannel(context)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // safe default
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily verse notification"
        }

        nm.createNotificationChannel(channel)
    }
}