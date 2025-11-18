/**
 * This file contains extension functions that map (convert) the app's local data models
 * ([AlarmItem], [SleepHistoryEntry]) to and from formats compatible with Google Firestore.
 * This is a crucial separation of concerns, keeping Firestore-specific logic out of the core data models.
 */
package com.cs407.afinal.data

import com.cs407.afinal.model.AlarmItem
import com.cs407.afinal.model.SleepHistoryEntry
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Converts an [AlarmItem] object into a [Map] that can be saved to Firestore.
 * Firestore stores data in a key-value format, similar to a map.
 * @return A [Map] where keys are field names and values are the corresponding data.
 */
fun AlarmItem.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "triggerAtMillis" to triggerAtMillis,
        "label" to label,
        "isEnabled" to isEnabled,
        "gentleWake" to gentleWake,
        "createdAtMillis" to createdAtMillis,
        "plannedBedTimeMillis" to plannedBedTimeMillis,
        "targetCycles" to targetCycles,
        "recurringDays" to recurringDays,
        "isAutoSet" to isAutoSet
    )

/**
 * Converts a [SleepHistoryEntry] object into a [Map] for Firestore storage.
 * @return A [Map] representing the sleep history entry.
 */
fun SleepHistoryEntry.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "alarmId" to alarmId,
        "label" to label,
        "plannedWakeMillis" to plannedWakeMillis,
        "actualDismissedMillis" to actualDismissedMillis,
        "plannedBedTimeMillis" to plannedBedTimeMillis
    )

/**
 * Converts a Firestore [DocumentSnapshot] into a nullable [AlarmItem].
 *
 * This function safely extracts data from the document, providing default values for missing fields
 * and handling potential type mismatches (e.g., numbers stored as Longs).
 * @return An [AlarmItem] object if the snapshot contains the minimum required data, otherwise `null`.
 */
fun DocumentSnapshot.toAlarmItem(): AlarmItem? {
    // Safely get data from the snapshot, returning null if essential fields are missing.
    val triggerAtMillis = getLong("triggerAtMillis") ?: return null
    val label = getString("label") ?: "Alarm" // Default value if label is missing.
    val isEnabled = getBoolean("isEnabled") ?: true
    val gentleWake = getBoolean("gentleWake") ?: true
    val createdAtMillis = getLong("createdAtMillis") ?: System.currentTimeMillis()
    val plannedBedTimeMillis = getLong("plannedBedTimeMillis")
    val targetCycles = getLong("targetCycles")?.toInt()
    val id = getLong("id")?.toInt() ?: id.toIntOrNull() ?: return null
    val isAutoSet = getBoolean("isAutoSet") ?: false
    // Firestore stores arrays of numbers as List<Long>, so we cast and convert to List<Int>.
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
        isAutoSet = isAutoSet
    )
}

/**
 * Converts a Firestore [DocumentSnapshot] into a nullable [SleepHistoryEntry].
 *
 * This function safely extracts data from the document and reconstructs the local data model.
 * @return A [SleepHistoryEntry] object, or `null` if essential data is missing.
 */
fun DocumentSnapshot.toHistoryEntry(): SleepHistoryEntry? {
    val alarmId = getLong("alarmId")?.toInt() ?: return null
    val label = getString("label") ?: "Alarm"
    val plannedWakeMillis = getLong("plannedWakeMillis") ?: return null
    val actualDismissedMillis = getLong("actualDismissedMillis") ?: return null
    val plannedBedTimeMillis = getLong("plannedBedTimeMillis")
    // Use the 'id' field if it exists, otherwise fall back to the document's ID or the dismissal time.
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
