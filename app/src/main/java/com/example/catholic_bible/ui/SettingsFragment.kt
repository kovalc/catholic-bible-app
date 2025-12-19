package com.example.catholic_bible.ui

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.example.catholic_bible.R
import com.example.catholic_bible.notifications.VerseNotificationScheduler

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Show selected values under the preference titles
        updateSummaries()

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (
                key == "pref_daily_notification_enabled" ||
                key == "pref_notification_hour" ||
                key == "pref_notification_minute"
            ) {
                updateSummaries()
                VerseNotificationScheduler.reschedule(requireContext())
            }
        }
    }

    private fun updateSummaries() {
        val hourPref = findPreference<ListPreference>("pref_notification_hour")
        val minPref = findPreference<ListPreference>("pref_notification_minute")

        hourPref?.summary = hourPref?.entry ?: ""
        minPref?.summary = minPref?.entry ?: ""
    }
}