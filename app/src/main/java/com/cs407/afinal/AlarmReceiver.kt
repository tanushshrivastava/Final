package com.cs407.afinal

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cs407.afinal.alarm.AlarmConstants
import com.cs407.afinal.alarm.AlarmScheduler
import com.cs407.afinal.data.AlarmPreferences

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(AlarmConstants.EXTRA_ALARM_ID, -1)
        val label = intent.getStringExtra(AlarmConstants.EXTRA_ALARM_LABEL) ?: "Alarm"
        val triggerAtMillis = intent.getLongExtra(AlarmConstants.EXTRA_ALARM_TIME, System.currentTimeMillis())
        val gentleWake = intent.getBooleanExtra(AlarmConstants.EXTRA_GENTLE_WAKE, true)
        val plannedBedTime = intent.getLongExtra(AlarmConstants.EXTRA_PLANNED_BEDTIME, -1)
        val isRecurring = intent.getBooleanExtra(AlarmConstants.EXTRA_IS_RECURRING, false)
        val recurringDays = intent.getIntegerArrayListExtra(AlarmConstants.EXTRA_RECURRING_DAYS) ?: arrayListOf()

        val alarmPreferences = AlarmPreferences(context)
        val currentAlarms = alarmPreferences.loadAlarms()
        currentAlarms.firstOrNull { it.id == alarmId }?.let { alarm ->
            if (isRecurring && recurringDays.isNotEmpty()) {
                // For recurring alarms, reschedule the next occurrence
                val alarmScheduler = AlarmScheduler(context)
                alarmScheduler.schedule(alarm)
            } else {
                // For one-time alarms, disable after firing
                alarmPreferences.upsertAlarm(alarm.copy(isEnabled = false))
            }
        }

        val alarmActivityIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmConstants.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmConstants.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmConstants.EXTRA_ALARM_TIME, triggerAtMillis)
            putExtra(AlarmConstants.EXTRA_GENTLE_WAKE, gentleWake)
            putExtra(AlarmConstants.EXTRA_PLANNED_BEDTIME, plannedBedTime)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarmId,
            alarmActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MainActivity.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(label)
            .setContentText("Alarm for ${formatTime(triggerAtMillis)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        NotificationManagerCompat.from(context).notify(alarmId, notification)
        context.startActivity(alarmActivityIntent)
    }

    private fun formatTime(millis: Long): String {
        val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return formatter.format(java.util.Date(millis))
    }
}
