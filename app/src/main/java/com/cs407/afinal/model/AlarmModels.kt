/**
 * This file contains the core data models for the application, such as AlarmItem and SleepHistoryEntry.
 * These data classes represent the fundamental objects the app works with.
 * They are marked as `@Serializable` to be easily converted to/from JSON by the kotlinx.serialization library.
 */
package com.cs407.afinal.model

import kotlinx.serialization.Serializable

/**
 * Represents a single alarm created by the user or the system.
 * This is a central data class used for scheduling, storing, and displaying alarms.
 *
 * @property id A unique integer identifier for the alarm.
 * @property triggerAtMillis The exact time the alarm is set to go off, in milliseconds since the epoch.
 * @property label A user-defined name for the alarm (e.g., "Work").
 * @property isEnabled A boolean flag indicating whether the alarm is active or has been switched off.
 * @property gentleWake A boolean flag for the gradual volume increase feature.
 * @property createdAtMillis The timestamp when the alarm was first created.
 * @property plannedBedTimeMillis An optional timestamp for when the user planned to go to sleep.
 * @property targetCycles An optional integer for how many sleep cycles the user was aiming for.
 * @property recurringDays A list of integers (1=Mon, 7=Sun) for recurring alarms. An empty list means it's a one-time alarm.
 * @property isAutoSet A boolean flag that is true if the alarm was set automatically by the inactivity monitor.
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
    val isAutoSet: Boolean = false
)

/**
 * Represents a single entry in the user's sleep history.
 * Each entry is created when an alarm is dismissed, logging the event.
 *
 * @property id A unique identifier for the history entry, typically the dismissal timestamp.
 * @property alarmId The ID of the alarm that triggered this history event.
 * @property label The label of the original alarm.
 * @property plannedWakeMillis The time the alarm was originally scheduled to wake the user.
 * @property actualDismissedMillis The actual time the user dismissed the alarm.
 * @property plannedBedTimeMillis The bedtime the user had planned for, if available.
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
 * An enum representing the two primary modes of the sleep calculator UI:
 * either the user is calculating when to WAKE UP, or when to go to BED.
 */
enum class SleepMode {
    WAKE_TIME,
    BED_TIME
}

/**
 * An enum to differentiate between sleep suggestions for bedtime and for wake-up time.
 */
enum class SleepSuggestionType {
    BEDTIME,
    WAKE_UP
}

/**
 * Represents a single suggested time in the sleep calculator UI.
 * For example, "Go to bed at 10:30 PM to wake up at 7:30 AM (6 cycles)".
 *
 * @property id A unique string identifier for the suggestion.
 * @property displayMillis The suggested time to be displayed to the user (e.g., 10:30 PM).
 * @property cycles The number of 90-minute sleep cycles this suggestion corresponds to.
 * @property type Whether this is a [BEDTIME] or [WAKE_UP] suggestion.
 * @property note A descriptive string (e.g., "9h of sleep").
 * @property referenceMillis The original time the user picked, from which this suggestion was calculated.
 */
data class SleepSuggestion(
    val id: String,
    val displayMillis: Long,
    val cycles: Int,
    val type: SleepSuggestionType,
    val note: String,
    val referenceMillis: Long? = null
)
