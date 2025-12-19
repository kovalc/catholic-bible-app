package com.example.catholic_bible.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.catholic_bible.network.VerseService
import com.example.catholic_bible.notifications.NotificationUtils
import com.example.catholic_bible.notifications.VerseNotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VerseNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val isTest = inputData.getBoolean("is_test", false)

        return try {
            val verse = withContext(Dispatchers.IO) {
                VerseService.api.getVerseToday()
            }

            val title = if (isTest) "Test: Today’s Verse" else "Today’s Verse"
            val body = "${verse.book} ${verse.chapter}:${verse.verse}\n\n${verse.text}"

            NotificationUtils.showVerseNotification(applicationContext, title, body)

            // IMPORTANT: schedule the NEXT day's notification after this one runs
            VerseNotificationScheduler.reschedule(applicationContext)

            Result.success()
        } catch (e: Exception) {
            // still schedule next attempt, otherwise it dies forever
            VerseNotificationScheduler.reschedule(applicationContext)
            Result.retry()
        }
    }
}