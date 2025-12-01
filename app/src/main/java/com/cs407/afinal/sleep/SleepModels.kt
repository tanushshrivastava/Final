/**
 * Data models for sleep tracking and calculations.
 * Location: com.cs407.afinal.sleep/SleepModels.kt
 */
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
 * UI state for the sleep screen
 */
data class SleepUiState(
    val mode: SleepMode = SleepMode.WAKE_TIME,
    val targetTime: LocalTime = LocalTime.now().plusMinutes(30).withSecond(0).withNano(0),
    val suggestions: List<SleepSuggestion> = emptyList(),
    val alarms: List<AlarmItem> = emptyList(),
    val history: List<SleepHistoryEntry> = emptyList(),
    val message: String? = null,
    val currentUserEmail: String? = null
)