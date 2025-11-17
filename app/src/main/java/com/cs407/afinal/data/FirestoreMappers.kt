package com.cs407.afinal.data

import com.cs407.afinal.model.AlarmItem
import com.cs407.afinal.model.SleepHistoryEntry
import com.google.firebase.firestore.DocumentSnapshot

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
        "recurringDays" to recurringDays
    )

fun SleepHistoryEntry.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "alarmId" to alarmId,
        "label" to label,
        "plannedWakeMillis" to plannedWakeMillis,
        "actualDismissedMillis" to actualDismissedMillis,
        "plannedBedTimeMillis" to plannedBedTimeMillis
    )

fun DocumentSnapshot.toAlarmItem(): AlarmItem? {
    val triggerAtMillis = getLong("triggerAtMillis") ?: return null
    val label = getString("label") ?: "Alarm"
    val isEnabled = getBoolean("isEnabled") ?: true
    val gentleWake = getBoolean("gentleWake") ?: true
    val createdAtMillis = getLong("createdAtMillis") ?: System.currentTimeMillis()
    val plannedBedTimeMillis = getLong("plannedBedTimeMillis")
    val targetCycles = getLong("targetCycles")?.toInt()
    val id = getLong("id")?.toInt() ?: id.toIntOrNull() ?: return null
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
        recurringDays = recurringDays
    )
}

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
