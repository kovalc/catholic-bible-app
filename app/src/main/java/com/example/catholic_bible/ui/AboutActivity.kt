package com.example.catholic_bible.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.catholic_bible.BuildConfig
import com.example.catholic_bible.R

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        supportActionBar?.title = "About"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val versionTv = findViewById<TextView>(R.id.aboutVersion)
        versionTv.text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        val privacy = findViewById<TextView>(R.id.privacyPolicy)
        privacy.setOnClickListener {
            val url = getString(R.string.privacy_policy_url)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}