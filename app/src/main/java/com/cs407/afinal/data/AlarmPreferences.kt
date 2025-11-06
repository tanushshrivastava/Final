package com.cs407.afinal.data

import android.content.Context
import androidx.core.content.edit
import com.cs407.afinal.model.AlarmItem
import com.cs407.afinal.model.SleepHistoryEntry
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AlarmPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadAlarms(): List<AlarmItem> = runCatching {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        json.decodeFromString<List<AlarmItem>>(raw)
    }.getOrElse { emptyList() }

    fun saveAlarms(alarms: List<AlarmItem>) {
        prefs.edit {
            putString(KEY_ALARMS, json.encodeToString(alarms))
        }
    }

    fun nextAlarmId(): Int {
        val nextId = prefs.getInt(KEY_NEXT_ALARM_ID, 1)
        prefs.edit {
            putInt(KEY_NEXT_ALARM_ID, nextId + 1)
        }
        return nextId
    }

    fun loadHistory(): List<SleepHistoryEntry> = runCatching {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        json.decodeFromString<List<SleepHistoryEntry>>(raw)
    }.getOrElse { emptyList() }

    fun saveHistory(entries: List<SleepHistoryEntry>) {
        val limited = entries
            .sortedByDescending { it.actualDismissedMillis }
            .take(MAX_HISTORY_ENTRIES)
        prefs.edit {
            putString(KEY_HISTORY, json.encodeToString(limited))
        }
    }

    fun appendHistory(entry: SleepHistoryEntry) {
        saveHistory(loadHistory() + entry)
    }

    fun markAlarmDismissed(alarmId: Int, dismissedAt: Long): SleepHistoryEntry? {
        val alarms = loadAlarms()
        val target = alarms.firstOrNull { it.id == alarmId }
        if (target == null) return null

        val updated = alarms.map {
            if (it.id == alarmId) it.copy(isEnabled = false) else it
        }
        saveAlarms(updated)
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

    fun deleteAlarm(alarmId: Int) {
        val updated = loadAlarms().filterNot { it.id == alarmId }
        saveAlarms(updated)
    }

    fun upsertAlarm(alarm: AlarmItem) {
        val current = loadAlarms().toMutableList()
        val index = current.indexOfFirst { it.id == alarm.id }
        if (index >= 0) {
            current[index] = alarm
        } else {
            current += alarm
        }
        saveAlarms(current.sortedBy { it.triggerAtMillis })
    }

    private companion object {
        private const val PREFS_NAME = "AlarmApp"
        private const val KEY_ALARMS = "alarms_v2"
        private const val KEY_HISTORY = "sleep_history"
        private const val KEY_NEXT_ALARM_ID = "next_alarm_id"
        private const val MAX_HISTORY_ENTRIES = 20
    }
}
