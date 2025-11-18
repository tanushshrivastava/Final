/**
 * This file contains the AlarmScheduler class, a helper responsible for interacting
 * with the Android system's AlarmManager to schedule and cancel alarms.
 */
package com.cs407.afinal.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.cs407.afinal.AlarmReceiver
import com.cs407.afinal.MainActivity
import com.cs407.afinal.model.AlarmItem
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A wrapper class for the Android [AlarmManager] to simplify scheduling and canceling alarms.
 *
 * This class abstracts away the complexities of creating [PendingIntent]s and setting alarms
 * that can wake the device from sleep. It also includes logic to handle recurring alarms
 * by calculating their next valid occurrence.
 *
 * @param context The application context, used to get the AlarmManager system service.
 */
class AlarmScheduler(private val context: Context) {

    // Get a reference to the system's AlarmManager service.
    private val alarmManager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules an alarm to be triggered at a specific time.
     *
     * This method handles both one-time and recurring alarms. It creates the necessary
     * intents and uses [AlarmManager.setAlarmClock] to ensure the alarm is treated as a
     * high-priority event by the system.
     *
     * @param alarm The [AlarmItem] containing all the details needed to schedule the alarm.
     */
    fun schedule(alarm: AlarmItem) {
        // For recurring alarms, calculate the timestamp of the next valid occurrence.
        // For one-time alarms, use the trigger time directly.
        val actualTriggerTime = if (alarm.recurringDays.isNotEmpty()) {
            calculateNextOccurrence(alarm.triggerAtMillis, alarm.recurringDays)
        } else {
            alarm.triggerAtMillis
        }

        // Create an intent that will be broadcast to the AlarmReceiver when the alarm fires.
        val broadcastIntent = Intent(context, AlarmReceiver::class.java).apply {
            // Pack all necessary alarm data into the intent's extras.
            putExtra(AlarmConstants.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmConstants.EXTRA_ALARM_LABEL, alarm.label)
            putExtra(AlarmConstants.EXTRA_ALARM_TIME, actualTriggerTime)
            putExtra(AlarmConstants.EXTRA_GENTLE_WAKE, alarm.gentleWake)
            putExtra(AlarmConstants.EXTRA_PLANNED_BEDTIME, alarm.plannedBedTimeMillis ?: -1L)
            putExtra(AlarmConstants.EXTRA_IS_RECURRING, alarm.recurringDays.isNotEmpty())
            putIntegerArrayListExtra(AlarmConstants.EXTRA_RECURRING_DAYS, ArrayList(alarm.recurringDays))
        }

        // Create the PendingIntent that the system will use to execute the broadcast.
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id, // The alarm's unique ID serves as the request code.
            broadcastIntent,
            // FLAG_UPDATE_CURRENT ensures that if we reschedule, the extras in the intent are updated.
            // FLAG_IMMUTABLE is required for security on modern Android versions.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create a separate intent for the AlarmClockInfo. This is what the system uses to show
        // the user information about the upcoming alarm (e.g., in the status bar).
        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use the setAlarmClock API, which is the standard for user-visible alarms.
        // It is guaranteed to be exact and will wake the device from Doze mode.
        // It also makes the alarm visible to the system, so the user sees an icon in their status bar.
        val alarmClockInfo = AlarmManager.AlarmClockInfo(actualTriggerTime, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }

    /**
     * Calculates the next occurrence for a recurring alarm based on the current time.
     *
     * @param baseTimeMillis The original trigger time, used to determine the hour and minute of the alarm.
     * @param recurringDays A list of integers representing the days of the week (1=Monday, 7=Sunday).
     * @return The timestamp in milliseconds for the next valid and future occurrence of the alarm.
     */
    private fun calculateNextOccurrence(baseTimeMillis: Long, recurringDays: List<Int>): Long {
        if (recurringDays.isEmpty()) return baseTimeMillis

        val now = ZonedDateTime.now()
        val baseTime = Instant.ofEpochMilli(baseTimeMillis).atZone(ZoneId.systemDefault())

        // Extract the target hour and minute from the original alarm time.
        val targetTime = baseTime.toLocalTime()

        // Iterate through the next 7 days (starting from today) to find the next valid day and time.
        for (daysAhead in 0..7) {
            val candidateDateTime = now.plusDays(daysAhead.toLong()).with(targetTime)
            val candidateDayOfWeek = candidateDateTime.dayOfWeek.value // java.time.DayOfWeek: 1=Monday, 7=Sunday.

            // Check two conditions:
            // 1. Is the candidate day one of the selected recurring days?
            // 2. Is the candidate date and time in the future?
            if (recurringDays.contains(candidateDayOfWeek) && candidateDateTime.isAfter(now)) {
                // If both are true, we've found the next occurrence.
                return candidateDateTime.toInstant().toEpochMilli()
            }
        }

        // This fallback should ideally not be reached with the logic above, but serves as a safeguard.
        // It finds the first recurring day and schedules it for the following week.
        val firstRecurringDay = recurringDays.sorted().first()
        val daysUntilFirst = (firstRecurringDay - now.dayOfWeek.value + 7) % 7
        val nextOccurrence = now.plusDays(if (daysUntilFirst == 0) 7 else daysUntilFirst.toLong()).with(targetTime)
        return nextOccurrence.toInstant().toEpochMilli()
    }

    /**
     * Cancels a previously scheduled alarm.
     *
     * @param alarmId The unique ID of the alarm to cancel.
     */
    fun cancel(alarmId: Int) {
        // To cancel an alarm, you must create a PendingIntent that is identical to the one you used to set it.
        // This means the same action, request code, and intent extras (though extras are not always strictly required).
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            Intent(context, AlarmReceiver::class.java),
            // FLAG_NO_CREATE ensures that we don't accidentally create a new PendingIntent.
            // It will return null if one with this signature doesn't already exist.
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )

        // If the PendingIntent exists, cancel it with the AlarmManager.
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel() // Also cancel the pending intent itself.
        }
    }

    /**
     * Checks if the app has the permission to schedule exact alarms.
     * This is a requirement for Android 12 (API 31) and higher.
     * @return `true` if exact alarms can be scheduled, `false` otherwise.
     */
    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // On Android S and above, this permission can be revoked by the user.
            alarmManager.canScheduleExactAlarms()
        } else {
            // On older versions, the permission is granted by default with the app install.
            true
        }
}
