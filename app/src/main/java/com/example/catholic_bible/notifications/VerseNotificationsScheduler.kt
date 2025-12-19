package com.example.catholic_bible.notifications

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.*
import com.example.catholic_bible.workers.VerseNotificationWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

object VerseNotificationScheduler {

    private const val UNIQUE_WORK_NAME = "daily_verse_notification_work"

    fun reschedule(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val enabled = prefs.getBoolean("pref_daily_notification_enabled", true)

        if (!enabled) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val hour = prefs.getString("pref_notification_hour", "7")?.toIntOrNull() ?: 7
        val minute = prefs.getString("pref_notification_minute", "0")?.toIntOrNull() ?: 0

        val initialDelayMs = computeInitialDelayMs(hour, minute)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<VerseNotificationWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun computeInitialDelayMs(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return next.timeInMillis - now.timeInMillis
    }
}