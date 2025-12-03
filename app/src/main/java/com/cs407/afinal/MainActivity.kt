/**
 * This file contains the main entry point of the Android application, the MainActivity.
 * It is responsible for setting up the main UI, creating notification channels,
 * and starting the background service for inactivity monitoring if the user has enabled it.
 */
package com.cs407.afinal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.cs407.afinal.alarm.AlarmManager
import com.cs407.afinal.ui.SmartSleepApp
import com.cs407.afinal.ui.theme.FinalTheme

/**
 * MainActivity is the primary activity for the application.
 *
 * It serves as the main entry point for the user interface. Its key responsibilities include:
 * - Setting the content view to the main Jetpack Compose UI entry point ([SmartSleepApp]).
 * - Creating the necessary notification channel for alarms to function correctly on modern Android versions.
 * - Checking user preferences and starting the [InactivityMonitorService] if the auto-alarm feature is enabled.
 */
class MainActivity : ComponentActivity() {

    /**
     * Called when the activity is first created. This is where you should do all of your normal static set up:
     * create views, bind data to lists, etc.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then
     * this Bundle contains the data it most recently supplied in [onSaveInstanceState]. Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create the notification channel required for posting alarm notifications on Android Oreo and above.
        createNotificationChannel()

        // Check user preferences and start the background service if needed.
        startInactivityMonitorIfEnabled()

        // Start the voice recognition service
        val voiceServiceIntent = Intent(this, VoiceRecognitionService::class.java)
        startService(voiceServiceIntent)

        // Set the main content of the activity to be our Jetpack Compose application UI.
        setContent {
            FinalTheme {
                // A Surface container using the 'background' color from the theme.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // SmartSleepApp is the root composable of the application's UI.
                    SmartSleepApp()
                }
            }
        }
    }

    /**
     * Called when the activity will start interacting with the user.
     * At this point your activity is at the top of the activity stack, with user input going to it.
     *
     * We re-check and start the inactivity monitor here to ensure it's running if the user
     * enabled it from the settings screen and then returned to this activity.
     */
    override fun onResume() {
        super.onResume()
        startInactivityMonitorIfEnabled()
    }

    /**
     * Checks if the auto-alarm feature is enabled in user preferences and starts or stops the
     * [InactivityMonitorService] accordingly.
     */
    private fun startInactivityMonitorIfEnabled() {
        // Access user preferences to see if the auto-alarm feature is turned on.
        val alarmManager = AlarmManager(this)
        if (alarmManager.isAutoAlarmEnabled()) {
            // If enabled, create an intent for the service.
            val intent = Intent(this, InactivityMonitorService::class.java)
            // Start the service as a foreground service to ensure it continues running even if the app is in the background.
            ContextCompat.startForegroundService(this, intent)
        } else {
            // If disabled, explicitly stop the service to save system resources.
            stopService(Intent(this, InactivityMonitorService::class.java))
        }
    }

    /**
     * Creates a NotificationChannel, which is required for showing notifications on Android 8.0 (API level 26) and higher.
     * This channel is used for displaying the persistent alarm notification.
     */
    private fun createNotificationChannel() {
        // Notification channels are only available on Android O and above.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Define the properties of the notification channel.
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID, // A unique ID for the channel.
                "Alarm Channel", // The user-visible name of the channel.
                NotificationManager.IMPORTANCE_HIGH // High importance for time-sensitive alarms.
            ).apply {
                description = "Channel for alarm notifications"
            }

            // Get the NotificationManager system service.
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Register the channel with the system. You can't change the importance or other notification
            // behaviors after this. Existing channels are not updated, so this is safe to call every time.
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * A companion object to hold constants for this activity.
     */
    companion object {
        // A unique identifier for the alarm notification channel.
        const val ALARM_CHANNEL_ID = "alarm_channel"
    }
}