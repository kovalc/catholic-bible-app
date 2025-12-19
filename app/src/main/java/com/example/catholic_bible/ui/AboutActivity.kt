package com.example.catholic_bible.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.catholic_bible.R

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // Set title
        supportActionBar?.apply {
            title = getString(R.string.title_about)
            setDisplayHomeAsUpEnabled(true) // back arrow
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}