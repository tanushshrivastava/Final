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
import com.cs407.afinal.data.AlarmPreferences
import com.cs407.afinal.ui.SmartSleepApp
import com.cs407.afinal.ui.theme.FinalTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        startInactivityMonitorIfEnabled()
        setContent {
            FinalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartSleepApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startInactivityMonitorIfEnabled()
    }

    private fun startInactivityMonitorIfEnabled() {
        val prefs = AlarmPreferences(this)
        if (prefs.isAutoAlarmEnabled()) {
            val intent = Intent(this, InactivityMonitorService::class.java)
            ContextCompat.startForegroundService(this, intent)
        } else {
            stopService(Intent(this, InactivityMonitorService::class.java))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Alarm Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for alarm notifications"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ALARM_CHANNEL_ID = "alarm_channel"
    }
}
