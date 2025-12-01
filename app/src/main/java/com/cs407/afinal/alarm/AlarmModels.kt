/**
 * Core data models for the alarm system.
 * Location: com.cs407.afinal.alarm/AlarmModels.kt
 */
package com.cs407.afinal.alarm

import kotlinx.serialization.Serializable

/**
 * Represents a single alarm.
 *
 * NEW FIELDS:
 * @property parentAlarmId If this is a follow-up alarm, the ID of the parent alarm. Null for primary alarms.
 * @property isFollowUp True if this alarm is a follow-up reminder for another alarm.
 */
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
    val recurringDays: List<Int> = emptyList(),
    val isAutoSet: Boolean = false,
    val parentAlarmId: Int? = null,  // NEW: For follow-up alarms
    val isFollowUp: Boolean = false   // NEW: Flag for follow-up alarms
)

/**
 * Represents a single entry in the user's sleep history.
 * Created when an alarm is dismissed.
 */
@Serializable
data class SleepHistoryEntry(
    val id: Long,
    val alarmId: Int,
    val label: String,
    val plannedWakeMillis: Long,
    val actualDismissedMillis: Long,
    val plannedBedTimeMillis: Long? = null
)

/**
 * Constants for alarm intents and operations.
 * Replaces the old AlarmConstants.kt file.
 */
object AlarmConstants {
    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_ALARM_LABEL = "extra_alarm_label"
    const val EXTRA_ALARM_TIME = "extra_alarm_time"
    const val EXTRA_GENTLE_WAKE = "extra_gentle_wake"
    const val EXTRA_PLANNED_BEDTIME = "extra_planned_bedtime"
    const val EXTRA_IS_RECURRING = "extra_is_recurring"
    const val EXTRA_RECURRING_DAYS = "extra_recurring_days"
}

/**
 * Outcome of alarm scheduling operations.
 * Replaces the old sealed class in SleepViewModel.
 */
sealed class AlarmScheduleOutcome {
    object Success : AlarmScheduleOutcome()
    object MissingExactAlarmPermission : AlarmScheduleOutcome()
    data class Error(val reason: String) : AlarmScheduleOutcome()
}