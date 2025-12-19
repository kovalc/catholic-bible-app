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
import com.example.catholic_bible.network.VerseService
import com.example.catholic_bible.notifications.VerseNotificationScheduler
import com.example.catholic_bible.workers.DailyWallpaperWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var verseTextView: TextView
    private lateinit var verseImageView: ImageView

    private var latestVerseForSpeech: String = ""

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If layout inflate fails, you'll crash before anything else.
        setContentView(R.layout.activity_main)

        verseTextView = findViewById(R.id.verseText)
        verseImageView = findViewById(R.id.verseImage)

        // Start with a friendly message so the UI is visible even if something fails.
        verseTextView.text = "Preparing today's verse…"

        // Permission request should never crash, but keep it early.
        requestNotificationPermissionIfNeeded()

        // These can throw on some devices/configs → wrap so app still opens.
        runCatching {
            VerseNotificationScheduler.reschedule(this)
        }.onFailure {
            // Don’t crash the app for notifications
            Toast.makeText(this, "Notifications setup issue: ${it.message}", Toast.LENGTH_LONG).show()
        }

        // Init TTS (should be safe)
        runCatching {
            tts = TextToSpeech(this, this)
        }.onFailure {
            Toast.makeText(this, "TTS init failed: ${it.message}", Toast.LENGTH_LONG).show()
        }

        // WorkManager schedule (wrap)
        runCatching {
            scheduleDailyWallpaperWork()
        }.onFailure {
            Toast.makeText(this, "Wallpaper worker issue: ${it.message}", Toast.LENGTH_LONG).show()
        }

        // Load verse (wrap inside function too)
        loadVerse()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US) ?: TextToSpeech.LANG_NOT_SUPPORTED
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsReady) {
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
        latestVerseForSpeech = ""

        activityScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    VerseService.api.getVerseToday()
                }

                val display = buildString {
                    append("${response.book} ${response.chapter}:${response.verse}")
                    append("\n\n")
                    append(response.text)
                }

                verseTextView.text = display
                latestVerseForSpeech = display

                val url = response.imageUrl
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

            R.id.action_refresh -> {
                loadVerse()
                true
            }

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