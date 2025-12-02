/**
 * This file contains the AlarmReceiver, a BroadcastReceiver responsible for handling
 * alarm intents sent by the Android system's AlarmManager.
 */
package com.cs407.afinal.alarm

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cs407.afinal.MainActivity
import com.cs407.afinal.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A BroadcastReceiver that listens for alarm clock broadcasts.
 *
 * When the [android.app.AlarmManager] triggers an alarm, the Android system sends a broadcast
 * that this receiver is registered to intercept. Its primary job is to initiate the
 * user-facing part of the alarm by showing a high-priority notification and launching
 * the [AlarmActivity]. It also handles the logic for re-scheduling recurring alarms.
 */
class AlarmReceiver : BroadcastReceiver() {

    /**
     * This method is called when the BroadcastReceiver is receiving an Intent broadcast.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received, which contains all the alarm details.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS) // Lint check to ensure the app has permission to post notifications.
    override fun onReceive(context: Context, intent: Intent) {
        // --- Extract all alarm details from the incoming intent ---
        val alarmId = intent.getIntExtra(AlarmConstants.EXTRA_ALARM_ID, -1)
        if (alarmId == -1) return // Do nothing if the alarm ID is invalid.

        val label = intent.getStringExtra(AlarmConstants.EXTRA_ALARM_LABEL) ?: "Alarm"
        val triggerAtMillis = intent.getLongExtra(AlarmConstants.EXTRA_ALARM_TIME, System.currentTimeMillis())
        val gentleWake = intent.getBooleanExtra(AlarmConstants.EXTRA_GENTLE_WAKE, true)
        val plannedBedTime = intent.getLongExtra(AlarmConstants.EXTRA_PLANNED_BEDTIME, -1)
        val isRecurring = intent.getBooleanExtra(AlarmConstants.EXTRA_IS_RECURRING, false)

        val alarmManager = AlarmManager(context)
        val currentAlarms = alarmManager.loadAlarms()

        // Find the specific alarm that is ringing from the stored list.
        currentAlarms.firstOrNull { it.id == alarmId }?.let { alarm ->
            if (isRecurring) {
                // For recurring alarms, we need to calculate and schedule the next occurrence.
                alarmManager.scheduleAlarm(alarm)
            } else {
                // For one-time alarms, we simply mark them as disabled so they don't ring again.
                alarmManager.upsertAlarm(alarm.copy(isEnabled = false))
            }
        }

        // --- Prepare to show the user the alarm screen ---

        // Create an intent to launch the AlarmActivity, which is the full-screen UI for the ringing alarm.
        val alarmActivityIntent = Intent(context, AlarmActivity::class.java).apply {
            // Use NEW_TASK because we are starting an activity from a broadcast receiver context.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // Pass all the alarm details to the activity so it can display them.
            putExtra(AlarmConstants.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmConstants.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmConstants.EXTRA_ALARM_TIME, triggerAtMillis)
            putExtra(AlarmConstants.EXTRA_GENTLE_WAKE, gentleWake)
            putExtra(AlarmConstants.EXTRA_PLANNED_BEDTIME, plannedBedTime)
        }

        // A PendingIntent is a token that gives another application (in this case, the system NotificationManager)
        // the right to perform an action on your behalf. Here, it will open our AlarmActivity.
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarmId, // Use the alarmId as the request code to ensure uniqueness.
            alarmActivityIntent,
            // UPDATE_CURRENT will update the intent's extras if it already exists.
            // IMMUTABLE is required for security on modern Android versions.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // --- Build and display a high-priority notification ---
        // This is crucial for alarms, as it's the primary way to get the user's attention.
        val notification = NotificationCompat.Builder(context, MainActivity.Companion.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(label)
            .setContentText("Alarm for ${formatTime(triggerAtMillis)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Ensures the notification gets top visibility.
            .setCategory(NotificationCompat.CATEGORY_ALARM) // Lets the system know this is an alarm.
            .setAutoCancel(true) // The notification will be dismissed when the user clicks it.
            .setFullScreenIntent(fullScreenPendingIntent, true) // This is what makes the AlarmActivity pop up immediately.
            .build()

        // Use the NotificationManagerCompat to display the notification.
        NotificationManagerCompat.from(context).notify(alarmId, notification)

        // Some devices might not automatically launch the full-screen intent, so we manually start the activity
        // as a fallback to ensure the alarm screen always appears.
        context.startActivity(alarmActivityIntent)
    }

    /**
     * A helper function to format a timestamp in milliseconds into a human-readable time string (e.g., "07:30 AM").
     *
     * @param millis The time in milliseconds since the epoch.
     * @return A formatted time string.
     */
    private fun formatTime(millis: Long): String {
        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return formatter.format(Date(millis))
    }
}