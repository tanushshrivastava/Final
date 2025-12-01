package com.cs407.afinal.sleep

import com.cs407.afinal.alarm.AlarmItem
import com.cs407.afinal.alarm.SleepHistoryEntry
import java.time.LocalTime

/**
 * Mode for sleep calculator UI
 */
enum class SleepMode {
    WAKE_TIME,  // User picks wake time, we suggest bed times
    BED_TIME    // User picks bed time, we suggest wake times
}

/**
 * Type of sleep suggestion
 */
enum class SleepSuggestionType {
    BEDTIME,
    WAKE_UP
}

/**
 * A suggested sleep time with cycle information
 */
data class SleepSuggestion(
    val id: String,
    val displayMillis: Long,
    val cycles: Int,
    val type: SleepSuggestionType,
    val note: String,
    val referenceMillis: Long? = null
)

/**
 * Holds the current status of the auto-alarm feature.
 */
data class AutoAlarmStatus(
    val isEnabled: Boolean = false,
    val isMonitoring: Boolean = false,
    val timeUntilTrigger: Long = 0L,
    val wasJustReset: Boolean = false
)

/**
 * UI state for the sleep screen
 */
data class SleepUiState(
    val mode: SleepMode = SleepMode.WAKE_TIME,
    val targetTime: LocalTime = LocalTime.now().plusMinutes(30).withSecond(0).withNano(0),
    val suggestions: List<SleepSuggestion> = emptyList(),
    val alarms: List<AlarmItem> = emptyList(),
    val history: List<SleepHistoryEntry> = emptyList(),
    val message: String? = null,
    val currentUserEmail: String? = null,
    val autoAlarmStatus: AutoAlarmStatus = AutoAlarmStatus()
)