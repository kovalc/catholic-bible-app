package com.example.catholic_bible.ui

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.example.catholic_bible.R
import com.example.catholic_bible.notifications.VerseNotificationScheduler

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Any setting change should reschedule notifications
        val listener = Preference.OnPreferenceChangeListener { _, _ ->
            VerseNotificationScheduler.reschedule(requireContext())
            true
        }

        findPreference<Preference>("pref_notifications_enabled")?.onPreferenceChangeListener = listener
        findPreference<Preference>("pref_notify_hour")?.onPreferenceChangeListener = listener
        findPreference<Preference>("pref_notify_minute")?.onPreferenceChangeListener = listener

        // Test notification now
        findPreference<Preference>("pref_test_notification")?.setOnPreferenceClickListener {
            VerseNotificationScheduler.sendTestNow(requireContext())
            Toast.makeText(requireContext(), "Sending test notification…", Toast.LENGTH_SHORT).show()
            true
        }
    }
}