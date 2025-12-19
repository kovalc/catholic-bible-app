package com.example.catholic_bible.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.load
import com.example.catholic_bible.R
import com.example.catholic_bible.notifications.VerseNotificationScheduler
import com.example.catholic_bible.workers.DailyWallpaperWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var verseTextView: TextView
    private lateinit var verseImageView: ImageView

    private var latestVerseForSpeech: String = ""

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val verseApi: VerseApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://pvb7zkkj57.execute-api.us-west-2.amazonaws.com/")
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

        requestNotificationPermissionIfNeeded()
        VerseNotificationScheduler.reschedule(this)

        tts = TextToSpeech(this, this)

        scheduleDailyWallpaperWork()
        loadVerse()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US) ?: TextToSpeech.LANG_NOT_SUPPORTED
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED

            if (ttsReady) {
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
            } else {
                Toast.makeText(this, "Text-to-speech language not supported.", Toast.LENGTH_SHORT).show()
            }
        } else {
            ttsReady = false
            Toast.makeText(this, "Text-to-speech unavailable.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        activityScope.cancel()

        tts?.stop()
        tts?.shutdown()
        tts = null

        super.onDestroy()
    }

    private fun loadVerse() {
        verseTextView.text = "Preparing today's verse…"
        latestVerseForSpeech = ""

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
                latestVerseForSpeech = display

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
                latestVerseForSpeech = ""
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                5001
            )
        }
    }

    private fun scheduleDailyWallpaperWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<DailyWallpaperWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "daily_wallpaper_work",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

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
            R.id.action_tts -> {
                toggleSpeakVerse()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleSpeakVerse() {
        if (!ttsReady) {
            Toast.makeText(this, "Text-to-speech not ready yet.", Toast.LENGTH_SHORT).show()
            return
        }

        if (tts?.isSpeaking == true) {
            tts?.stop()
            return
        }

        val textToRead = latestVerseForSpeech.trim()
        if (textToRead.isBlank()) {
            Toast.makeText(this, "No verse to read yet.", Toast.LENGTH_SHORT).show()
            return
        }

        tts?.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null, "VERSE_TTS")
    }
}

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