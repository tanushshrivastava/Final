package com.cs407.afinal.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import com.cs407.afinal.MainActivity
import com.cs407.afinal.AlarmReceiver
import com.cs407.afinal.model.AlarmItem
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: AlarmItem) {
        // For recurring alarms, calculate the next occurrence
        val actualTriggerTime = if (alarm.recurringDays.isNotEmpty()) {
            calculateNextOccurrence(alarm.triggerAtMillis, alarm.recurringDays)
        } else {
            alarm.triggerAtMillis
        }

        val broadcastIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmConstants.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmConstants.EXTRA_ALARM_LABEL, alarm.label)
            putExtra(AlarmConstants.EXTRA_ALARM_TIME, actualTriggerTime)
            putExtra(AlarmConstants.EXTRA_GENTLE_WAKE, alarm.gentleWake)
            putExtra(AlarmConstants.EXTRA_PLANNED_BEDTIME, alarm.plannedBedTimeMillis ?: -1L)
            putExtra(AlarmConstants.EXTRA_IS_RECURRING, alarm.recurringDays.isNotEmpty())
            putIntegerArrayListExtra(AlarmConstants.EXTRA_RECURRING_DAYS, ArrayList(alarm.recurringDays))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(actualTriggerTime, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } else {
            AlarmManagerCompat.setExactAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, actualTriggerTime, pendingIntent)
        }
    }

    /**
     * Calculate the next occurrence of a recurring alarm
     * @param baseTimeMillis The original alarm time
     * @param recurringDays List of days (1=Mon, 2=Tue, ..., 7=Sun)
     * @return The next occurrence in milliseconds
     */
    fun calculateNextOccurrence(baseTimeMillis: Long, recurringDays: List<Int>): Long {
        if (recurringDays.isEmpty()) return baseTimeMillis

        val now = ZonedDateTime.now()
        val baseTime = Instant.ofEpochMilli(baseTimeMillis).atZone(ZoneId.systemDefault())
        
        // Get the time of day from the base alarm
        val targetTime = baseTime.toLocalTime()
        
        // Find the next occurrence
        for (daysAhead in 0..7) {
            val candidateDateTime = now.plusDays(daysAhead.toLong()).with(targetTime)
            val candidateDayOfWeek = candidateDateTime.dayOfWeek.value // 1=Monday, 7=Sunday
            
            // Check if this day is in the recurring days and the time hasn't passed yet today
            if (recurringDays.contains(candidateDayOfWeek)) {
                if (candidateDateTime.isAfter(now)) {
                    return candidateDateTime.toInstant().toEpochMilli()
                }
            }
        }
        
        // Fallback: schedule for next week on the first recurring day
        val firstRecurringDay = recurringDays.sorted().first()
        val daysUntilFirst = (firstRecurringDay - now.dayOfWeek.value + 7) % 7
        val nextOccurrence = now.plusDays(if (daysUntilFirst == 0) 7 else daysUntilFirst.toLong()).with(targetTime)
        return nextOccurrence.toInstant().toEpochMilli()
    }

    fun cancel(alarmId: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
}
