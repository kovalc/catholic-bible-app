package com.example.catholic_bible

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.work.*
import coil.load
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var verseTextView: TextView
    private lateinit var verseImageView: ImageView

    private val verseApi: VerseApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://s4abq0nc6a.execute-api.us-west-2.amazonaws.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(VerseApi::class.java)
    }

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        verseTextView = findViewById(R.id.verseText)
        verseImageView = findViewById(R.id.verseImage)

        // Schedule the daily wallpaper update (once per day; uses existing AI image)
        scheduleDailyWallpaperWork()

        // Load today's verse + image for the UI
        loadVerse()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    private fun loadVerse() {
        verseTextView.text = "Preparing today's verse…"

        activityScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    verseApi.getVerseToday()
                }

                val display = buildString {
                    append("${response.book} ${response.chapter}:${response.verse}")
                    append("\n\n")
                    append(response.text)
                }

                verseTextView.text = display

                val url = response.image_url
                if (!url.isNullOrBlank()) {
                    verseImageView.load(url) {
                        crossfade(true)
                        crossfade(500)
                    }
                } else {
                    verseImageView.setImageDrawable(null)
                }
            } catch (e: Exception) {
                verseTextView.text = "Error loading verse: ${e.message}"
            }
        }
    }

    private fun scheduleDailyWallpaperWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<DailyWallpaperWorker>(
            1, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "daily_wallpaper_work",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    // ----- Menu: Settings & About -----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

// Shared with worker
data class VerseResponse(
    val date: String,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val image_url: String? = null
)

interface VerseApi {
    @GET("verse/today")
    suspend fun getVerseToday(): VerseResponse
}