package com.example.catholic_bible

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

// We REUSE the existing VerseResponse from MainActivity.kt.
// Do NOT redefine it here.

/**
 * Separate API interface for the worker (could also reuse VerseApi if you want).
 */
interface VerseApiWorker {
    @GET("verse/today")
    suspend fun getVerseToday(): VerseResponse
}

class DailyWallpaperWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val verseApi: VerseApiWorker by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://s4abq0nc6a.execute-api.us-west-2.amazonaws.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(VerseApiWorker::class.java)
    }

    private val httpClient = OkHttpClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 1) Get verse of the day (includes image_url)
            val verse = verseApi.getVerseToday()
            val imageUrl = verse.image_url

            if (imageUrl.isNullOrBlank()) {
                // No image = nothing to set, but job succeeded
                return@withContext Result.success()
            }

            // 2) Download the image bytes
            val request = Request.Builder().url(imageUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext Result.retry()
            }

            val bytes = response.body?.bytes()
            response.close()

            if (bytes == null) {
                return@withContext Result.retry()
            }

            // 3) Decode to Bitmap
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext Result.retry()

            // 4) Set as wallpaper
            val wm = WallpaperManager.getInstance(applicationContext)
            wm.setBitmap(bitmap)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}