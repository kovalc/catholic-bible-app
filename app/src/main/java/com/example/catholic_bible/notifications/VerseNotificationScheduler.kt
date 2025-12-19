package com.example.catholic_bible.notifications

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.*
import com.example.catholic_bible.workers.VerseNotificationWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

object VerseNotificationScheduler {

    private const val UNIQUE_WORK_NAME = "daily_verse_notification_work"

    /**
     * Call this on app start and whenever user changes settings.
     * It schedules ONE OneTimeWorkRequest for the next notify time,
     * and the Worker schedules the next one after it runs.
     */
    fun reschedule(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val enabled = prefs.getBoolean("pref_notifications_enabled", true)
        val hour = prefs.getInt("pref_notify_hour", 7)      // default 7 AM
        val minute = prefs.getInt("pref_notify_minute", 0)  // default :00

        val wm = WorkManager.getInstance(context)

        if (!enabled) {
            wm.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val delayMs = computeDelayUntilNext(hour, minute)

        val request = OneTimeWorkRequestBuilder<VerseNotificationWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(UNIQUE_WORK_NAME)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        wm.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Sends a notification immediately (useful for Settings "Test notification").
     */
    fun sendTestNow(context: Context) {
        val wm = WorkManager.getInstance(context)

        val request = OneTimeWorkRequestBuilder<VerseNotificationWorker>()
            .setInputData(workDataOf("is_test" to true))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        wm.enqueue(request)
    }

    private fun computeDelayUntilNext(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()

        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If target time already passed today, schedule for tomorrow
        if (next.timeInMillis <= now.timeInMillis) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }

        return next.timeInMillis - now.timeInMillis
    }
}