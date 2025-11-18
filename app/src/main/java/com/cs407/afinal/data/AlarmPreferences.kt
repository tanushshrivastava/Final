/**
 * This file contains the AlarmPreferences class, a data management utility that uses
 * SharedPreferences to persist alarm settings, sleep history, and user preferences locally
 * on the device.
 */
package com.cs407.afinal.data

import android.content.Context
import androidx.core.content.edit
import com.cs407.afinal.model.AlarmItem
import com.cs407.afinal.model.SleepHistoryEntry
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages the persistence of all application data using [android.content.SharedPreferences].
 *
 * This class abstracts the details of reading from and writing to SharedPreferences.
 * It serializes lists of complex objects ([AlarmItem], [SleepHistoryEntry]) into JSON strings
 * for storage and deserializes them back into objects upon retrieval.
 * It also provides convenient methods for managing individual settings for the auto-alarm feature.
 *
 * @param context The application context, needed to access SharedPreferences.
 */
class AlarmPreferences(context: Context) {

    // Get an instance of SharedPreferences for the app. Data is stored privately.
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // Configure a JSON serializer to handle converting objects to and from strings.
    // `ignoreUnknownKeys = true` makes it more robust to future data model changes.
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Loads the list of all saved alarms from SharedPreferences.
     * @return A list of [AlarmItem] objects. Returns an empty list if no alarms are saved or if an error occurs.
     */
    fun loadAlarms(): List<AlarmItem> = runCatching {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        json.decodeFromString<List<AlarmItem>>(raw)
    }.getOrElse { emptyList() } // Return an empty list if JSON decoding fails.

    /**
     * Saves a list of alarms to SharedPreferences, overwriting any existing list.
     * @param alarms The list of [AlarmItem] objects to save.
     */
    fun saveAlarms(alarms: List<AlarmItem>) {
        prefs.edit {
            putString(KEY_ALARMS, json.encodeToString(alarms))
        }
    }

    /**
     * Generates and returns a new unique ID for an alarm.
     * This is a simple auto-incrementing integer.
     * @return A unique integer ID.
     */
    fun nextAlarmId(): Int {
        val nextId = prefs.getInt(KEY_NEXT_ALARM_ID, 1)
        prefs.edit {
            putInt(KEY_NEXT_ALARM_ID, nextId + 1)
        }
        return nextId
    }

    /**
     * Loads the user's sleep history from SharedPreferences.
     * @return A list of [SleepHistoryEntry] objects. Returns an empty list if history is empty or an error occurs.
     */
    fun loadHistory(): List<SleepHistoryEntry> = runCatching {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        json.decodeFromString<List<SleepHistoryEntry>>(raw)
    }.getOrElse { emptyList() }

    /**
     * Saves a list of sleep history entries, ensuring the list does not exceed a maximum size.
     * @param entries The list of [SleepHistoryEntry] to save.
     */
    fun saveHistory(entries: List<SleepHistoryEntry>) {
        // Sort by most recent and limit the number of entries to save storage space.
        val limited = entries
            .sortedByDescending { it.actualDismissedMillis }
            .take(MAX_HISTORY_ENTRIES)
        prefs.edit {
            putString(KEY_HISTORY, json.encodeToString(limited))
        }
    }

    /**
     * Adds a new entry to the sleep history.
     * @param entry The [SleepHistoryEntry] to add.
     */
    fun appendHistory(entry: SleepHistoryEntry) {
        saveHistory(loadHistory() + entry)
    }

    /**
     * Marks a given alarm as dismissed, updates its state, and creates a new sleep history entry.
     * @param alarmId The ID of the alarm that was dismissed.
     * @param dismissedAt The timestamp (in milliseconds) when the alarm was dismissed.
     * @return The newly created [SleepHistoryEntry], or null if the alarm was not found.
     */
    fun markAlarmDismissed(alarmId: Int, dismissedAt: Long): SleepHistoryEntry? {
        val alarms = loadAlarms()
        val target = alarms.firstOrNull { it.id == alarmId }
        if (target == null) return null // Alarm not found.

        // For non-recurring alarms, disable them after they are dismissed.
        if (target.recurringDays.isEmpty()) {
            val updated = alarms.map {
                if (it.id == alarmId) it.copy(isEnabled = false) else it
            }
            saveAlarms(updated)
        }

        // Create a new history entry from the alarm details.
        val entry = SleepHistoryEntry(
            id = dismissedAt,
            alarmId = alarmId,
            label = target.label,
            plannedWakeMillis = target.triggerAtMillis,
            actualDismissedMillis = dismissedAt,
            plannedBedTimeMillis = target.plannedBedTimeMillis
        )
        appendHistory(entry)
        return entry
    }

    /**
     * Deletes an alarm from the saved list.
     * @param alarmId The ID of the alarm to delete.
     */
    fun deleteAlarm(alarmId: Int) {
        val updated = loadAlarms().filterNot { it.id == alarmId }
        saveAlarms(updated)
    }

    /**
     * Inserts a new alarm or updates an existing one.
     * If an alarm with the same ID already exists, it is replaced. Otherwise, the new alarm is added.
     * @param alarm The [AlarmItem] to add or update.
     */
    fun upsertAlarm(alarm: AlarmItem) {
        val current = loadAlarms().toMutableList()
        val index = current.indexOfFirst { it.id == alarm.id }
        if (index >= 0) {
            // Update existing alarm.
            current[index] = alarm
        } else {
            // Add new alarm.
            current += alarm
        }
        // Save the list, sorted by trigger time for chronological display.
        saveAlarms(current.sortedBy { it.triggerAtMillis })
    }

    // --- Getters and Setters for Auto-Alarm Feature Preferences ---

    fun isAutoAlarmEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_ALARM_ENABLED, false)

    fun setAutoAlarmEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_ALARM_ENABLED, enabled) }
    }

    fun getAutoAlarmTriggerTime(): Pair<Int, Int> {
        val hour = prefs.getInt(KEY_AUTO_ALARM_HOUR, 22) // Default to 10 PM
        val minute = prefs.getInt(KEY_AUTO_ALARM_MINUTE, 30) // Default to 30
        return hour to minute
    }

    fun setAutoAlarmTriggerTime(hour: Int, minute: Int) {
        prefs.edit {
            putInt(KEY_AUTO_ALARM_HOUR, hour)
            putInt(KEY_AUTO_ALARM_MINUTE, minute)
        }
    }

    fun getAutoAlarmInactivityMinutes(): Int = prefs.getInt(KEY_AUTO_ALARM_INACTIVITY_MINUTES, 15) // Default to 15 mins

    fun setAutoAlarmInactivityMinutes(minutes: Int) {
        prefs.edit { putInt(KEY_AUTO_ALARM_INACTIVITY_MINUTES, minutes) }
    }

    /**
     * A companion object to hold the constant keys for SharedPreferences.
     */
    private companion object {
        private const val PREFS_NAME = "AlarmApp"
        private const val KEY_ALARMS = "alarms_v3" // Changed to v3 to include recurring days
        private const val KEY_HISTORY = "sleep_history_v2"
        private const val KEY_NEXT_ALARM_ID = "next_alarm_id"
        private const val KEY_AUTO_ALARM_ENABLED = "auto_alarm_enabled"
        private const val KEY_AUTO_ALARM_HOUR = "auto_alarm_hour"
        private const val KEY_AUTO_ALARM_MINUTE = "auto_alarm_minute"
        private const val KEY_AUTO_ALARM_INACTIVITY_MINUTES = "auto_alarm_inactivity_minutes"
        private const val MAX_HISTORY_ENTRIES = 50 // Increased history size
    }
}
