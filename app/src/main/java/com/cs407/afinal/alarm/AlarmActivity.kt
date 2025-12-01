/**
 * This file defines the AlarmActivity, which is displayed when an alarm is triggered.
 * It is responsible for playing the alarm sound, showing a full-screen UI to the user,
 * and handling the dismissal of the alarm.
 */
package com.cs407.afinal.alarm

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.cs407.afinal.sleep.SleepViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AlarmActivity is a full-screen activity that appears when an alarm goes off.
 *
 * Its main responsibilities are:
 * - Waking up the device and displaying itself over the lock screen.
 * - Playing the selected alarm tone, with support for a "gentle wake" feature that gradually increases the volume.
 * - Displaying information about the alarm, such as its label and time.
 * - Providing a button for the user to dismiss the alarm.
 * - On dismissal, it stops the sound, updates the alarm's state in preferences, notifies the [SleepViewModel],
 *   and closes itself.
 */
class AlarmActivity : ComponentActivity() {

    // MediaPlayer instance to handle playing the alarm sound.
    private var mediaPlayer: MediaPlayer? = null
    // A coroutine Job to manage the gradual volume increase for the "gentle wake" feature.
    private var volumeJob: Job? = null

    // Properties to store the details of the ringing alarm, extracted from the intent.
    private var alarmId: Int = -1
    private var gentleWake: Boolean = true
    private var triggerAtMillis: Long = System.currentTimeMillis()
    private var plannedBedTimeMillis: Long? = null

    // ViewModel for handling sleep-related data logic.
    private val sleepViewModel: SleepViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure the screen wakes up and stays on.
        keepScreenAwake()

        // --- Extract alarm details from the intent that started this activity ---
        alarmId = intent.getIntExtra(AlarmConstants.EXTRA_ALARM_ID, -1)
        val label = intent.getStringExtra(AlarmConstants.EXTRA_ALARM_LABEL) ?: "Alarm"
        triggerAtMillis = intent.getLongExtra(AlarmConstants.EXTRA_ALARM_TIME, System.currentTimeMillis())
        gentleWake = intent.getBooleanExtra(AlarmConstants.EXTRA_GENTLE_WAKE, true)
        plannedBedTimeMillis = intent.getLongExtra(AlarmConstants.EXTRA_PLANNED_BEDTIME, -1L).takeIf { it > 0 }

        // The alarm is now visible to the user, so cancel the persistent notification.
        NotificationManagerCompat.from(this).cancel(alarmId)

        // Set the UI content using Jetpack Compose.
        setContent {
            AlarmRingingScreen(
                label = label,
                triggerAtMillis = triggerAtMillis,
                quote = "Wake up!", // Placeholder for a dynamic quote.
                plannedBedTimeMillis = plannedBedTimeMillis,
                onDismiss = { dismissAlarm() } // Pass the dismiss function as a callback.
            )
        }

        // Start playing the alarm sound.
        startAlarmSound()
    }

    /**
     * Called when the activity is being destroyed. This is the final call that the activity will receive.
     * It's crucial to release resources here.
     */
    override fun onDestroy() {
        super.onDestroy()
        // Ensure the alarm sound is stopped and the MediaPlayer is released.
        stopAlarmSound()
    }

    /**
     * Handles the logic for dismissing the alarm.
     */
    private fun dismissAlarm() {
        // Stop the alarm sound immediately.
        stopAlarmSound()

        // Ensure the notification is removed if it wasn't already.
        NotificationManagerCompat.from(this).cancel(alarmId)

        // Record the time the alarm was dismissed and update the sleep data.
        // CHANGED: Use AlarmManager instead of AlarmPreferences
        AlarmManager(applicationContext).markAlarmDismissed(alarmId, System.currentTimeMillis())
        sleepViewModel.onAlarmDismissed(alarmId)  // CHANGED: Simplified signature

        // Finish the activity to return to the previous screen or the home screen.
        finish()
    }

    /**
     * Initializes and starts the MediaPlayer to play the alarm sound.
     */
    private fun startAlarmSound() {
        // Get the default URI for the alarm sound. Fall back to the ringtone if no alarm sound is set.
        val toneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@AlarmActivity, toneUri) // Set the sound source.
            isLooping = true // Ensure the sound repeats until dismissed.
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM) // Important for the system to handle this as an alarm.
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            prepare() // Prepare the player synchronously.

            // Set initial volume based on the "gentle wake" preference.
            if (gentleWake) {
                setVolume(0f, 0f) // Start at zero volume.
            } else {
                setVolume(1f, 1f) // Start at full volume.
            }
            start() // Start playback.
        }

        // If gentle wake is enabled, start a coroutine to gradually increase the volume.
        if (gentleWake) {
            volumeJob = lifecycleScope.launch {
                val steps = 10 // The number of steps to reach full volume.
                repeat(steps) { step ->
                    val volumeLevel = (step + 1) / steps.toFloat() // Calculate volume for the current step.
                    mediaPlayer?.setVolume(volumeLevel, volumeLevel) // Set the new volume.
                    delay(3000L) // Wait before increasing the volume again.
                }
            }
        }
    }

    /**
     * Stops the alarm sound and releases the MediaPlayer resources.
     */
    private fun stopAlarmSound() {
        // Cancel the volume-increasing coroutine if it's running.
        volumeJob?.cancel()
        volumeJob = null

        // Stop and release the MediaPlayer instance.
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release() // Release system resources.
        }
        mediaPlayer = null
    }

    /**
     * Configures the window to show over the lock screen and turn the screen on.
     * This is crucial for an alarm clock app.
     */
    private fun keepScreenAwake() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Modern APIs for showing activity over lock screen.
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            // Deprecated flags for older Android versions.
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }
}

/**
 * A stateless composable function that defines the UI for the alarm ringing screen.
 *
 * @param label The label of the alarm to display.
 * @param triggerAtMillis The exact time the alarm was triggered, used for display.
 * @param quote An inspirational or simple quote to show the user.
 * @param plannedBedTimeMillis The bedtime the user had originally planned, if available.
 * @param onDismiss A callback function to be invoked when the user presses the dismiss button.
 */
@Composable
private fun AlarmRingingScreen(
    label: String,
    triggerAtMillis: Long,
    quote: String,
    plannedBedTimeMillis: Long?,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center, // Center content vertically.
            horizontalAlignment = Alignment.CenterHorizontally // Center content horizontally.
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "It's ${formatTime(triggerAtMillis)}",
                style = MaterialTheme.typography.displaySmall
            )
            // Conditionally display the planned bedtime if it exists.
            plannedBedTimeMillis?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You planned for ${formatTime(it)} last night",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = quote,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(40.dp))
            Button(onClick = onDismiss) {
                Text("I'm awake")
            }
        }
    }
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