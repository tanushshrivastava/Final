package com.cs407.afinal.model

import kotlinx.serialization.Serializable

@Serializable
data class AlarmItem(
    val id: Int,
    val triggerAtMillis: Long,
    val label: String,
    val isEnabled: Boolean,
    val gentleWake: Boolean,
    val createdAtMillis: Long,
    val plannedBedTimeMillis: Long? = null,
    val targetCycles: Int? = null,
    val recurringDays: List<Int> = emptyList() // 1=Monday, 2=Tuesday, ..., 7=Sunday; empty = one-time alarm
)

@Serializable
data class SleepHistoryEntry(
    val id: Long,
    val alarmId: Int,
    val label: String,
    val plannedWakeMillis: Long,
    val actualDismissedMillis: Long,
    val plannedBedTimeMillis: Long? = null
)

enum class SleepMode {
    WAKE_TIME,
    BED_TIME
}

enum class SleepSuggestionType {
    BEDTIME,
    WAKE_UP
}

data class SleepSuggestion(
    val id: String,
    val displayMillis: Long,
    val cycles: Int,
    val type: SleepSuggestionType,
    val note: String,
    val referenceMillis: Long? = null
)
