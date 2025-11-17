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
import androidx.core.app.NotificationCompat
import com.cs407.afinal.alarm.AlarmScheduler
import com.cs407.afinal.data.AlarmPreferences
import com.cs407.afinal.model.AlarmItem
import java.util.Calendar
import kotlin.math.sqrt

class InactivityMonitorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var alarmPreferences: AlarmPreferences
    private lateinit var alarmScheduler: AlarmScheduler
    
    private var lastMovementTime = System.currentTimeMillis()
    private var isMonitoring = false

    override fun onCreate() {
        super.onCreate()
        alarmPreferences = AlarmPreferences(this)
        alarmScheduler = AlarmScheduler(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!alarmPreferences.isAutoAlarmEnabled()) {
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
            updateNotification()
            
            // Show movement detection for testing
            if (alarmPreferences.getAutoAlarmInactivityMinutes() <= 5) {
                android.widget.Toast.makeText(this, "Movement detected - timer reset", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        checkInactivity()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun checkInactivity() {
        val now = System.currentTimeMillis()
        val inactiveDuration = now - lastMovementTime
        val thresholdMs = alarmPreferences.getAutoAlarmInactivityMinutes() * 60 * 1000L
        
        updateNotification()
        
        if (inactiveDuration >= thresholdMs && shouldMonitorNow()) {
            val existingAutoAlarm = alarmPreferences.loadAlarms().firstOrNull { it.isAutoSet && it.isEnabled }
            if (existingAutoAlarm == null) {
                setAutoAlarm()
            }
        }
    }
    
    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun shouldMonitorNow(): Boolean {
        val (triggerHour, triggerMinute) = alarmPreferences.getAutoAlarmTriggerTime()
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val triggerTimeInMinutes = triggerHour * 60 + triggerMinute
        
        return currentTimeInMinutes >= triggerTimeInMinutes || currentTimeInMinutes < 6 * 60
    }

    private fun setAutoAlarm() {
        val sleepCycleDurationMs = 90 * 60 * 1000L // 90 minutes
        val wakeUpTime = System.currentTimeMillis() + (6 * sleepCycleDurationMs)
        
        val alarmId = alarmPreferences.nextAlarmId()
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
        
        alarmPreferences.upsertAlarm(alarm)
        alarmScheduler.schedule(alarm)
        
        // Send broadcast to refresh UI
        val intent = android.content.Intent("com.cs407.afinal.ALARM_CREATED")
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Alarm Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors phone inactivity for auto alarm"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val inactivityMinutes = alarmPreferences.getAutoAlarmInactivityMinutes()
        val (triggerHour, triggerMinute) = alarmPreferences.getAutoAlarmTriggerTime()
        val timeUntilInactive = (inactivityThresholdMs - (System.currentTimeMillis() - lastMovementTime)) / 1000
        
        val contentText = if (shouldMonitorNow()) {
            if (timeUntilInactive > 0) {
                "Still for ${(inactivityThresholdMs - (System.currentTimeMillis() - lastMovementTime)) / 1000}s/${inactivityMinutes * 60}s"
            } else {
                "Monitoring active - ${inactivityMinutes}m threshold"
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

    override fun onDestroy() {
        super.onDestroy()
        if (isMonitoring) {
            sensorManager.unregisterListener(this)
            isMonitoring = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "inactivity_monitor_channel"
        private const val NOTIFICATION_ID = 2001
        private const val MOVEMENT_THRESHOLD = 10.5f
    }
    
    private val inactivityThresholdMs: Long
        get() = alarmPreferences.getAutoAlarmInactivityMinutes() * 60 * 1000L
}
