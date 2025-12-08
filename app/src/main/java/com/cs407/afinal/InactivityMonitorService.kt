package com.cs407.afinal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.cs407.afinal.alarm.AlarmItem
import com.cs407.afinal.alarm.AlarmManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.sqrt

class InactivityMonitorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var alarmManager: AlarmManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastMovementTime = System.currentTimeMillis()
    private var isMonitoring = false

    override fun onCreate() {
        super.onCreate()
        alarmManager = AlarmManager(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESET_INACTIVITY_TIMER) {
            lastMovementTime = System.currentTimeMillis()
            broadcastStatus(isReset = true)
            updateNotification()
            return START_STICKY
        }

        if (!alarmManager.isAutoAlarmEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isMonitoring) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                isMonitoring = true
            }
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val acceleration = sqrt(x * x + y * y + z * z)

        if (acceleration > MOVEMENT_THRESHOLD) {
            lastMovementTime = System.currentTimeMillis()
            broadcastStatus(isReset = true)
            updateNotification()
        }

        checkInactivity()
    }

    private fun checkInactivity() {
        val now = System.currentTimeMillis()
        val inactiveDurationMs = now - lastMovementTime
        val thresholdMs = alarmManager.getAutoAlarmInactivityMillis()

        updateNotification()
        broadcastStatus()

        if (inactiveDurationMs >= thresholdMs && shouldMonitorNow()) {
            val existingAutoAlarm = alarmManager.loadAlarms().firstOrNull { it.isAutoSet && it.isEnabled }
            if (existingAutoAlarm == null) {
                setAutoAlarm()
            }
        }
    }

    private fun setAutoAlarm() {
        val sleepCycleDurationMs = 90 * 60 * 1000L
        val wakeUpTime = System.currentTimeMillis() + (6 * sleepCycleDurationMs)

        val alarmId = alarmManager.nextAlarmId()
        val alarm = AlarmItem(
            id = alarmId,
            triggerAtMillis = wakeUpTime,
            label = "Auto Sleep Alarm",
            isEnabled = true,
            gentleWake = true,
            createdAtMillis = System.currentTimeMillis(),
            plannedBedTimeMillis = System.currentTimeMillis(),
            targetCycles = 6,
            isAutoSet = true
        )

        alarmManager.upsertAlarm(alarm)
        alarmManager.scheduleAlarm(alarm)

        serviceScope.launch {
            alarmManager.syncAlarmToFirebase(alarm)
        }

        broadcastAlarmCreated(alarmId, wakeUpTime)
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun shouldMonitorNow(): Boolean {
        val (triggerHour, triggerMinute) = alarmManager.getAutoAlarmTriggerTime()
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val triggerTimeInMinutes = triggerHour * 60 + triggerMinute
        return currentTimeInMinutes >= triggerTimeInMinutes || currentTimeInMinutes < 6 * 60
    }

    private fun createNotification(): Notification {
        val inactivityMinutes = alarmManager.getAutoAlarmInactivityMinutes()
        val inactivityMillis = alarmManager.getAutoAlarmInactivityMillis()
        val (triggerHour, triggerMinute) = alarmManager.getAutoAlarmTriggerTime()
        val timeUntilInactiveSeconds = (inactivityMillis - (System.currentTimeMillis() - lastMovementTime)) / 1000

        val contentText = if (shouldMonitorNow()) {
            val thresholdLabel = if (inactivityMinutes < 1) "15s" else "${inactivityMinutes}m"
            if (timeUntilInactiveSeconds > 0) {
                "Still for ${timeUntilInactiveSeconds}s / $thresholdLabel"
            } else {
                "Monitoring active - $thresholdLabel threshold"
            }
        } else {
            "Waiting until ${String.format("%02d:%02d", triggerHour, triggerMinute)}"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Alarm Monitor")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Auto Alarm Monitor", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Monitors phone inactivity for auto alarm"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun broadcastStatus(isReset: Boolean = false) {
        val intent = Intent(ACTION_STATUS_UPDATE)
        val timeUntilTrigger = alarmManager.getAutoAlarmInactivityMillis() - (System.currentTimeMillis() - lastMovementTime)
        intent.putExtra(EXTRA_TIME_UNTIL_TRIGGER, timeUntilTrigger)
        intent.putExtra(EXTRA_IS_RESET, isReset)
        intent.putExtra(EXTRA_IS_MONITORING, shouldMonitorNow())
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastAlarmCreated(alarmId: Int, triggerAtMillis: Long) {
        val intent = Intent(ACTION_AUTO_ALARM_CREATED).apply {
            putExtra(EXTRA_NEW_ALARM_ID, alarmId)
            putExtra(EXTRA_NEW_ALARM_TRIGGER, triggerAtMillis)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isMonitoring) {
            sensorManager.unregisterListener(this)
            isMonitoring = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_RESET_INACTIVITY_TIMER = "com.cs407.afinal.ACTION_RESET_INACTIVITY_TIMER"
        const val ACTION_STATUS_UPDATE = "com.cs407.afinal.ACTION_STATUS_UPDATE"
        const val ACTION_AUTO_ALARM_CREATED = "com.cs407.afinal.ACTION_AUTO_ALARM_CREATED"
        const val EXTRA_TIME_UNTIL_TRIGGER = "time_until_trigger"
        const val EXTRA_IS_RESET = "is_reset"
        const val EXTRA_IS_MONITORING = "is_monitoring"
        const val EXTRA_NEW_ALARM_ID = "new_alarm_id"
        const val EXTRA_NEW_ALARM_TRIGGER = "new_alarm_trigger"
        private const val CHANNEL_ID = "inactivity_monitor_channel"
        private const val NOTIFICATION_ID = 2001
        private const val MOVEMENT_THRESHOLD = 10.5f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
