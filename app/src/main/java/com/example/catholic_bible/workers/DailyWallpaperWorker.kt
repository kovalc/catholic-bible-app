package com.example.catholic_bible.workers

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.catholic_bible.network.VerseService
import okhttp3.OkHttpClient
import okhttp3.Request

class DailyWallpaperWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        return try {
            val verse = VerseService.api.getVerseToday()
            val imageUrl = verse.imageUrl

            if (imageUrl.isNullOrBlank()) {
                return Result.success() // nothing to set
            }

            val request = Request.Builder()
                .url(imageUrl)
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return Result.retry()

                val bytes = resp.body?.bytes() ?: return Result.retry()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return Result.retry()

                val wm = WallpaperManager.getInstance(applicationContext)
                wm.setBitmap(bitmap)
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}