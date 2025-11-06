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

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: AlarmItem) {
        val broadcastIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmConstants.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmConstants.EXTRA_ALARM_LABEL, alarm.label)
            putExtra(AlarmConstants.EXTRA_ALARM_TIME, alarm.triggerAtMillis)
            putExtra(AlarmConstants.EXTRA_GENTLE_WAKE, alarm.gentleWake)
            putExtra(AlarmConstants.EXTRA_PLANNED_BEDTIME, alarm.plannedBedTimeMillis ?: -1L)
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
            val alarmClockInfo = AlarmManager.AlarmClockInfo(alarm.triggerAtMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } else {
            AlarmManagerCompat.setExactAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, pendingIntent)
        }
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
