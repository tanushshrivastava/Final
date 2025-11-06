package com.cs407.afinal

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.cs407.afinal.alarm.AlarmConstants
import com.cs407.afinal.data.AlarmPreferences
import com.cs407.afinal.viewmodel.SleepViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var volumeJob: Job? = null
    private var alarmId: Int = -1
    private var gentleWake: Boolean = true
    private var triggerAtMillis: Long = System.currentTimeMillis()
    private var plannedBedTimeMillis: Long? = null
    private val sleepViewModel: SleepViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepScreenAwake()

        alarmId = intent.getIntExtra(AlarmConstants.EXTRA_ALARM_ID, -1)
        val label = intent.getStringExtra(AlarmConstants.EXTRA_ALARM_LABEL) ?: "Alarm"
        triggerAtMillis = intent.getLongExtra(AlarmConstants.EXTRA_ALARM_TIME, System.currentTimeMillis())
        gentleWake = intent.getBooleanExtra(AlarmConstants.EXTRA_GENTLE_WAKE, true)
        plannedBedTimeMillis = intent.getLongExtra(AlarmConstants.EXTRA_PLANNED_BEDTIME, -1L).takeIf { it > 0 }

        NotificationManagerCompat.from(this).cancel(alarmId)

        setContent {
            AlarmRingingScreen(
                label = label,
                triggerAtMillis = triggerAtMillis,
                quote = "Wake up!",
                plannedBedTimeMillis = plannedBedTimeMillis,
                onDismiss = { dismissAlarm() }
            )
        }

        startAlarmSound()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
    }

    private fun dismissAlarm() {
        stopAlarmSound()
        NotificationManagerCompat.from(this).cancel(alarmId)
        val entry = AlarmPreferences(applicationContext).markAlarmDismissed(alarmId, System.currentTimeMillis())
        sleepViewModel.onAlarmDismissed(alarmId, entry)
        finish()
    }

    private fun startAlarmSound() {
        val toneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@AlarmActivity, toneUri)
            isLooping = true
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            prepare()
            if (gentleWake) {
                setVolume(0f, 0f)
            } else {
                setVolume(1f, 1f)
            }
            start()
        }

        if (gentleWake) {
            volumeJob = lifecycleScope.launch {
                val steps = 10
                repeat(steps) { step ->
                    val volumeLevel = (step + 1) / steps.toFloat()
                    mediaPlayer?.setVolume(volumeLevel, volumeLevel)
                    delay(3000L)
                }
            }
        }
    }

    private fun stopAlarmSound() {
        volumeJob?.cancel()
        volumeJob = null
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }

    private fun keepScreenAwake() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
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
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
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

private fun formatTime(millis: Long): String {
    val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return formatter.format(Date(millis))
}
