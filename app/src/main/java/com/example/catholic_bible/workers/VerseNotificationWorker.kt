package com.example.catholic_bible.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.catholic_bible.notifications.VerseNotificationChannel
import com.example.catholic_bible.notifications.VerseNotifier
import com.example.catholic_bible.ui.VerseApi
import com.example.catholic_bible.ui.VerseResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class VerseNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val verseApi: VerseApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://s4abq0nc6a.execute-api.us-west-2.amazonaws.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VerseApi::class.java)
    }

    override suspend fun doWork(): Result {
        return try {
            VerseNotificationChannel.ensure(applicationContext)

            val verse: VerseResponse = withContext(Dispatchers.IO) {
                verseApi.getVerseToday()
            }

            VerseNotifier.show(applicationContext, verse)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}