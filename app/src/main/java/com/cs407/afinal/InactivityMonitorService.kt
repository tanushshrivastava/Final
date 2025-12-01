/**
 * This file contains the InactivityMonitorService, a background service responsible for
 * implementing the "Auto Sleep Alarm" feature. It uses the accelerometer to detect periods
 * of phone inactivity and automatically schedules an alarm if certain conditions are met.
 */
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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.cs407.afinal.alarm.AlarmManager  // CHANGED: from AlarmScheduler
import com.cs407.afinal.alarm.AlarmItem     // CHANGED: now in alarm package
import java.util.Calendar
import kotlin.math.sqrt

/**
 * A background Service that monitors the device's accelerometer to detect periods of inactivity.
 *
 * This service is the core of the "Auto Sleep Alarm" feature. Its responsibilities are:
 * - Running as a foreground service to ensure it is not killed by the system.
 * - Listening to the accelerometer sensor to detect device movement.
 * - Tracking the last time significant movement was detected.
 * - Checking if the device has been inactive (still) for a user-defined duration.
 * - This check only happens after a user-defined trigger time (e.g., after 10 PM).
 * - If the device is detected as inactive, it automatically schedules a new alarm for a full night's sleep (e.g., 9 hours).
 * - It manages its own lifecycle, stopping itself if the auto-alarm feature is disabled by the user.
 */
class InactivityMonitorService : Service(), SensorEventListener {

    // System services and managers for sensors and scheduling.
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var alarmManager: AlarmManager  // CHANGED: renamed from alarmPreferences/alarmScheduler

    // Tracks the timestamp of the last detected movement.
    private var lastMovementTime = System.currentTimeMillis()
    // Flag to ensure the sensor listener is only registered once.
    private var isMonitoring = false

    /**
     * Called by the system when the service is first created.
     * Used for one-time setup.
     */
    override fun onCreate() {
        super.onCreate()
        // Initialize alarm manager and sensor manager.
        alarmManager = AlarmManager(this)  // CHANGED: single manager instance
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Setup for the foreground service.
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification()) // Promote the service to foreground.
    }

    /**
     * Called by the system every time a client starts the service using startService().
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If the user has disabled the auto-alarm feature, stop the service immediately.
        if (!alarmManager.isAutoAlarmEnabled()) {  // CHANGED: use alarmManager
            stopSelf()
            return START_NOT_STICKY // Do not restart the service automatically.
        }

        // Register the sensor listener if it's not already running.
        if (!isMonitoring) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                isMonitoring = true
            }
        }

        // If the service is killed, the system should restart it.
        return START_STICKY
    }

    /**
     * Called when there is a new sensor event. This is where movement is detected.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        // Calculate the magnitude of the acceleration vector to detect movement.
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val acceleration = sqrt(x * x + y * y + z * z)

        // If acceleration exceeds a certain threshold, consider it as movement.
        if (acceleration > MOVEMENT_THRESHOLD) {
            // Reset the inactivity timer by updating the last movement time.
            lastMovementTime = System.currentTimeMillis()
            updateNotification() // Update notification to show reset timer.

            // For testing purposes, show a Toast when movement is detected on short inactivity settings.
            if (alarmManager.getAutoAlarmInactivityMinutes() <= 5) {  // CHANGED
                Toast.makeText(this, "Movement detected - timer reset", Toast.LENGTH_SHORT).show()
            }
        }

        // After each sensor change, check if the inactivity threshold has been met.
        checkInactivity()
    }

    /**
     * Called when the accuracy of the registered sensor has changed. Not used in this implementation.
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Checks if the device has been inactive long enough to trigger the auto-alarm.
     */
    private fun checkInactivity() {
        val now = System.currentTimeMillis()
        val inactiveDurationMs = now - lastMovementTime
        val thresholdMs = alarmManager.getAutoAlarmInactivityMinutes() * 60 * 1000L  // CHANGED

        // Update the foreground notification with the current status.
        updateNotification()

        // Trigger conditions: 1) Inactivity duration has passed, 2) It's within the monitoring time window.
        if (inactiveDurationMs >= thresholdMs && shouldMonitorNow()) {
            // Check if an auto-set alarm is already active to avoid setting duplicates.
            val existingAutoAlarm = alarmManager.loadAlarms().firstOrNull { it.isAutoSet && it.isEnabled }  // CHANGED
            if (existingAutoAlarm == null) {
                // If no auto-alarm exists, create and schedule a new one.
                setAutoAlarm()
            }
        }
    }

    /**
     * Updates the persistent foreground notification with the latest monitoring status.
     */
    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Determines if the service should be actively monitoring for inactivity based on the current time.
     * Monitoring is typically active late at night and in the early morning.
     * @return `true` if the current time is within the monitoring window, `false` otherwise.
     */
    private fun shouldMonitorNow(): Boolean {
        val (triggerHour, triggerMinute) = alarmManager.getAutoAlarmTriggerTime()  // CHANGED
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val triggerTimeInMinutes = triggerHour * 60 + triggerMinute

        // Monitor if the current time is after the trigger time (e.g., 10 PM) or before a certain morning hour (e.g., 6 AM).
        return currentTimeInMinutes >= triggerTimeInMinutes || currentTimeInMinutes < 6 * 60
    }

    /**
     * Creates and schedules a new alarm for a standard sleep duration (6 sleep cycles).
     */
    private fun setAutoAlarm() {
        val sleepCycleDurationMs = 90 * 60 * 1000L // 90 minutes per cycle.
        val wakeUpTime = System.currentTimeMillis() + (6 * sleepCycleDurationMs) // Set alarm for 9 hours from now.

        val alarmId = alarmManager.nextAlarmId() // Get a new, unique ID for the alarm.  // CHANGED
        val alarm = AlarmItem(
            id = alarmId,
            triggerAtMillis = wakeUpTime,
            label = "Auto Sleep Alarm",
            isEnabled = true,
            gentleWake = true,
            createdAtMillis = System.currentTimeMillis(),
            plannedBedTimeMillis = System.currentTimeMillis(), // The "bedtime" is when the alarm was set.
            targetCycles = 6,
            isAutoSet = true // Flag to identify this as an automatically set alarm.
        )

        // Save the new alarm to preferences and schedule it with the AlarmManager.
        alarmManager.upsertAlarm(alarm)      // CHANGED
        alarmManager.scheduleAlarm(alarm)    // CHANGED

        // Send a broadcast to notify the UI (e.g., the alarm list screen) that a new alarm has been created.
        val intent = Intent("com.cs407.afinal.ALARM_CREATED")
        sendBroadcast(intent)
    }

    /**
     * Creates the notification channel required for the foreground service notification on Android O+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Alarm Monitor",
                NotificationManager.IMPORTANCE_LOW // Low importance for a non-intrusive notification.
            ).apply {
                description = "Monitors phone inactivity for auto alarm"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds the persistent notification for the foreground service.
     * The content text is dynamic and shows the current status of the monitor.
     * @return The configured [Notification] object.
     */
    private fun createNotification(): Notification {
        val inactivityMinutes = alarmManager.getAutoAlarmInactivityMinutes()  // CHANGED
        val (triggerHour, triggerMinute) = alarmManager.getAutoAlarmTriggerTime()  // CHANGED
        val timeUntilInactiveSeconds = (inactivityThresholdMs - (System.currentTimeMillis() - lastMovementTime)) / 1000

        // Determine the text to display based on the current state.
        val contentText = if (shouldMonitorNow()) {
            if (timeUntilInactiveSeconds > 0) {
                // If monitoring, show countdown to being considered inactive.
                "Still for ${timeUntilInactiveSeconds}s / ${inactivityMinutes * 60}s"
            } else {
                // If threshold is passed, show the threshold.
                "Monitoring active - ${inactivityMinutes}m threshold"
            }
        } else {
            // If outside the monitoring window, show when it will start.
            "Waiting until ${String.format("%02d:%02d", triggerHour, triggerMinute)}"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Alarm Monitor")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Makes the notification non-dismissible.
            .build()
    }

    /**
     * Called by the system to notify a Service that it is no longer used and is being removed.
     */
    override fun onDestroy() {
        super.onDestroy()
        // Unregister the sensor listener to save battery when the service is stopped.
        if (isMonitoring) {
            sensorManager.unregisterListener(this)
            isMonitoring = false
        }
    }

    /**
     * Return the communication channel to the service. This service does not allow binding, so return null.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Companion object to hold constants for the service.
     */
    companion object {
        private const val CHANNEL_ID = "inactivity_monitor_channel"
        private const val NOTIFICATION_ID = 2001 // A unique ID for the foreground notification.
        private const val MOVEMENT_THRESHOLD = 10.5f // Empirically determined value for significant movement.
    }

    // A helper property to get the inactivity threshold in milliseconds from preferences.
    private val inactivityThresholdMs: Long
        get() = alarmManager.getAutoAlarmInactivityMinutes() * 60 * 1000L  // CHANGED
}