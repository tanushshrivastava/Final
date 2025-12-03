/**
 * Central alarm management system - handles scheduling, storage, and sync.
 *
 * CONSOLIDATES:
 * - AlarmScheduler.kt (system scheduling)
 * - AlarmPreferences.kt (local storage)
 * - FirestoreMappers.kt (cloud sync)
 * - AlarmConstants.kt (now in AlarmModels.kt)
 *
 * Location: com.cs407.afinal.alarm/AlarmManager.kt
 */
package com.cs407.afinal.alarm

import android.app.AlarmManager as AndroidAlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import com.cs407.afinal.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Comprehensive alarm management system.
 * This class is a one-stop-shop for all alarm operations.
 */
class AlarmManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AndroidAlarmManager
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // ==================== SYSTEM SCHEDULING ====================
    // (Replaces AlarmScheduler.kt)

    /**
     * Schedule an alarm with the Android AlarmManager system.
     * Handles both one-time and recurring alarms.
     */
    fun scheduleAlarm(alarm: AlarmItem) {
        // For recurring alarms, calculate next occurrence
        val actualTriggerTime = if (alarm.recurringDays.isNotEmpty()) {
            calculateNextOccurrence(alarm.triggerAtMillis, alarm.recurringDays)
        } else {
            alarm.triggerAtMillis
        }

        // Create broadcast intent for AlarmReceiver
        val broadcastIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmConstants.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmConstants.EXTRA_ALARM_LABEL, alarm.label)
            putExtra(AlarmConstants.EXTRA_ALARM_TIME, actualTriggerTime)
            putExtra(AlarmConstants.EXTRA_GENTLE_WAKE, alarm.gentleWake)
            putExtra(AlarmConstants.EXTRA_PLANNED_BEDTIME, alarm.plannedBedTimeMillis ?: -1L)
            putExtra(AlarmConstants.EXTRA_IS_RECURRING, alarm.recurringDays.isNotEmpty())
            putIntegerArrayListExtra(AlarmConstants.EXTRA_RECURRING_DAYS, ArrayList(alarm.recurringDays))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show intent for notification click
        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use AlarmClockInfo for high-priority, exact alarms
        val alarmClockInfo = AndroidAlarmManager.AlarmClockInfo(actualTriggerTime, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }

    /**
     * Cancel a scheduled alarm from the system.
     */
    fun cancelAlarm(alarmId: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    /**
     * Check if we have permission to schedule exact alarms.
     * Required on Android 12+.
     */
    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    /**
     * Calculate the next valid occurrence for a recurring alarm.
     * Takes current time into account and finds next matching day.
     */
    private fun calculateNextOccurrence(baseTimeMillis: Long, recurringDays: List<Int>): Long {
        if (recurringDays.isEmpty()) return baseTimeMillis

        val now = ZonedDateTime.now()
        val baseTime = Instant.ofEpochMilli(baseTimeMillis).atZone(ZoneId.systemDefault())
        val targetTime = baseTime.toLocalTime()

        // Check next 7 days for valid occurrence
        for (daysAhead in 0..7) {
            val candidateDateTime = now.plusDays(daysAhead.toLong()).with(targetTime)
            val candidateDayOfWeek = candidateDateTime.dayOfWeek.value // 1=Monday, 7=Sunday

            if (recurringDays.contains(candidateDayOfWeek) && candidateDateTime.isAfter(now)) {
                return candidateDateTime.toInstant().toEpochMilli()
            }
        }

        // Fallback: schedule for next week
        val firstRecurringDay = recurringDays.sorted().first()
        val daysUntilFirst = (firstRecurringDay - now.dayOfWeek.value + 7) % 7
        val nextOccurrence = now.plusDays(if (daysUntilFirst == 0) 7 else daysUntilFirst.toLong()).with(targetTime)
        return nextOccurrence.toInstant().toEpochMilli()
    }

    // ==================== LOCAL STORAGE ====================
    // (Replaces AlarmPreferences.kt)

    /**
     * Load all alarms from SharedPreferences.
     */
    fun loadAlarms(): List<AlarmItem> = runCatching {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        json.decodeFromString<List<AlarmItem>>(raw)
    }.getOrElse { emptyList() }

    /**
     * Save all alarms to SharedPreferences.
     */
    fun saveAlarms(alarms: List<AlarmItem>) {
        prefs.edit {
            putString(KEY_ALARMS, json.encodeToString(alarms))
        }
    }

    /**
     * Generate next unique alarm ID.
     */
    fun nextAlarmId(): Int {
        val nextId = prefs.getInt(KEY_NEXT_ALARM_ID, 1)
        prefs.edit {
            putInt(KEY_NEXT_ALARM_ID, nextId + 1)
        }
        return nextId
    }

    /**
     * Load sleep history from SharedPreferences.
     */
    fun loadHistory(): List<SleepHistoryEntry> = runCatching {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        json.decodeFromString<List<SleepHistoryEntry>>(raw)
    }.getOrElse { emptyList() }

    /**
     * Save sleep history, limited to max entries.
     */
    fun saveHistory(entries: List<SleepHistoryEntry>) {
        val limited = entries
            .sortedByDescending { it.actualDismissedMillis }
            .take(MAX_HISTORY_ENTRIES)
        prefs.edit {
            putString(KEY_HISTORY, json.encodeToString(limited))
        }
    }

    /**
     * Add new history entry.
     */
    fun appendHistory(entry: SleepHistoryEntry) {
        saveHistory(loadHistory() + entry)
    }

    /**
     * Delete an alarm and all its follow-up alarms (if any).
     * This implements cascading delete for parent-child relationships.
     */
    fun deleteAlarm(alarmId: Int) {
        val alarms = loadAlarms()
        val alarm = alarms.firstOrNull { it.id == alarmId } ?: return

        // Cancel from Android system
        cancelAlarm(alarmId)

        // If this is a parent alarm, delete all follow-ups too
        if (!alarm.isFollowUp) {
            val followUpAlarms = alarms.filter { it.parentAlarmId == alarmId }
            followUpAlarms.forEach { cancelAlarm(it.id) }

            // Remove parent and all follow-ups from storage
            val updated = alarms.filterNot { it.id == alarmId || it.parentAlarmId == alarmId }
            saveAlarms(updated)
        } else {
            // Just remove this follow-up
            val updated = alarms.filterNot { it.id == alarmId }
            saveAlarms(updated)
        }
    }

    /**
     * Insert or update an alarm.
     */
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

    /**
     * Mark alarm as dismissed and create history entry.
     */
    fun markAlarmDismissed(alarmId: Int, dismissedAt: Long): SleepHistoryEntry? {
        val alarms = loadAlarms()
        val target = alarms.firstOrNull { it.id == alarmId } ?: return null

        // Disable non-recurring alarms after dismissal
        if (target.recurringDays.isEmpty()) {
            val updated = alarms.map {
                if (it.id == alarmId) it.copy(isEnabled = false) else it
            }
            saveAlarms(updated)
        }

        // Create history entry
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

    // ==================== FIREBASE SYNC ====================
    // (Replaces FirestoreMappers.kt functionality)

    /**
     * Sync alarm to Firebase Firestore.
     */
    suspend fun syncAlarmToFirebase(alarm: AlarmItem) {
        val user = auth.currentUser ?: return
        firestore.collection("users")
            .document(user.uid)
            .collection("alarms")
            .document(alarm.id.toString())
            .set(alarm.toFirestoreMap(), SetOptions.merge())
            .await()
    }

    /**
     * Delete alarm from Firebase Firestore.
     */
    suspend fun deleteAlarmFromFirebase(alarmId: Int) {
        val user = auth.currentUser ?: return
        firestore.collection("users")
            .document(user.uid)
            .collection("alarms")
            .document(alarmId.toString())
            .delete()
            .await()
    }

    /**
     * Sync history entry to Firebase Firestore.
     */
    suspend fun syncHistoryToFirebase(entry: SleepHistoryEntry) {
        val user = auth.currentUser ?: return
        firestore.collection("users")
            .document(user.uid)
            .collection("history")
            .document(entry.id.toString())
            .set(entry.toFirestoreMap(), SetOptions.merge())
            .await()
    }

    // ==================== FIRESTORE MAPPERS ====================
    // (Replaces FirestoreMappers.kt)

    /**
     * Convert AlarmItem to Firestore map.
     */
    private fun AlarmItem.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "triggerAtMillis" to triggerAtMillis,
        "label" to label,
        "isEnabled" to isEnabled,
        "gentleWake" to gentleWake,
        "createdAtMillis" to createdAtMillis,
        "plannedBedTimeMillis" to plannedBedTimeMillis,
        "targetCycles" to targetCycles,
        "recurringDays" to recurringDays,
        "isAutoSet" to isAutoSet,
        "parentAlarmId" to parentAlarmId,  // NEW
        "isFollowUp" to isFollowUp          // NEW
    )

    /**
     * Convert SleepHistoryEntry to Firestore map.
     */
    private fun SleepHistoryEntry.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "alarmId" to alarmId,
        "label" to label,
        "plannedWakeMillis" to plannedWakeMillis,
        "actualDismissedMillis" to actualDismissedMillis,
        "plannedBedTimeMillis" to plannedBedTimeMillis
    )

    /**
     * Convert Firestore DocumentSnapshot to AlarmItem.
     */
    fun DocumentSnapshot.toAlarmItem(): AlarmItem? {
        val triggerAtMillis = getLong("triggerAtMillis") ?: return null
        val label = getString("label") ?: "Alarm"
        val isEnabled = getBoolean("isEnabled") ?: true
        val gentleWake = getBoolean("gentleWake") ?: true
        val createdAtMillis = getLong("createdAtMillis") ?: System.currentTimeMillis()
        val plannedBedTimeMillis = getLong("plannedBedTimeMillis")
        val targetCycles = getLong("targetCycles")?.toInt()
        val id = getLong("id")?.toInt() ?: this.id.toIntOrNull() ?: return null
        val isAutoSet = getBoolean("isAutoSet") ?: false
        val parentAlarmId = getLong("parentAlarmId")?.toInt()  // NEW
        val isFollowUp = getBoolean("isFollowUp") ?: false      // NEW

        @Suppress("UNCHECKED_CAST")
        val recurringDays = (get("recurringDays") as? List<Long>)?.map { it.toInt() } ?: emptyList()

        return AlarmItem(
            id = id,
            triggerAtMillis = triggerAtMillis,
            label = label,
            isEnabled = isEnabled,
            gentleWake = gentleWake,
            createdAtMillis = createdAtMillis,
            plannedBedTimeMillis = plannedBedTimeMillis,
            targetCycles = targetCycles,
            recurringDays = recurringDays,
            isAutoSet = isAutoSet,
            parentAlarmId = parentAlarmId,  // NEW
            isFollowUp = isFollowUp          // NEW
        )
    }

    /**
     * Convert Firestore DocumentSnapshot to SleepHistoryEntry.
     */
    fun DocumentSnapshot.toHistoryEntry(): SleepHistoryEntry? {
        val alarmId = getLong("alarmId")?.toInt() ?: return null
        val label = getString("label") ?: "Alarm"
        val plannedWakeMillis = getLong("plannedWakeMillis") ?: return null
        val actualDismissedMillis = getLong("actualDismissedMillis") ?: return null
        val plannedBedTimeMillis = getLong("plannedBedTimeMillis")
        val id = getLong("id") ?: this.id.toLongOrNull() ?: actualDismissedMillis

        return SleepHistoryEntry(
            id = id,
            alarmId = alarmId,
            label = label,
            plannedWakeMillis = plannedWakeMillis,
            actualDismissedMillis = actualDismissedMillis,
            plannedBedTimeMillis = plannedBedTimeMillis
        )
    }

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

    fun getAutoAlarmInactivityMinutes(): Int = prefs.getInt(KEY_AUTO_ALARM_INACTIVITY_MINUTES, 15)

    fun setAutoAlarmInactivityMinutes(minutes: Int) {
        prefs.edit { putInt(KEY_AUTO_ALARM_INACTIVITY_MINUTES, minutes) }
    }
    
    fun getLastInactivityResetTime(): Long = prefs.getLong(KEY_LAST_INACTIVITY_RESET, 0L)

    fun setLastInactivityResetTime(time: Long) {
        prefs.edit { putLong(KEY_LAST_INACTIVITY_RESET, time) }
    }

    companion object {
        private const val PREFS_NAME = "AlarmApp"
        private const val KEY_ALARMS = "alarms_v4"  // v4 includes follow-up support
        private const val KEY_HISTORY = "sleep_history_v2"
        private const val KEY_NEXT_ALARM_ID = "next_alarm_id"
        private const val MAX_HISTORY_ENTRIES = 50
        private const val KEY_AUTO_ALARM_ENABLED = "auto_alarm_enabled"
        private const val KEY_AUTO_ALARM_HOUR = "auto_alarm_hour"
        private const val KEY_AUTO_ALARM_MINUTE = "auto_alarm_minute"
        private const val KEY_AUTO_ALARM_INACTIVITY_MINUTES = "auto_alarm_inactivity_minutes"
        private const val KEY_LAST_INACTIVITY_RESET = "last_inactivity_reset_time"
    }
}
