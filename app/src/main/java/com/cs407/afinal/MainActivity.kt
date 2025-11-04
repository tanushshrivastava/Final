package com.cs407.afinal

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cs407.afinal.ui.theme.FinalTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        setContent {
            FinalTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alarm Channel"
            val descriptionText = "Channel for alarm notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("alarm_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

@Composable
fun SleepCalculatorScreen(modifier: Modifier = Modifier) {
    var wakeUpTimes by remember { mutableStateOf<List<Calendar>>(emptyList()) }
    val context = LocalContext.current
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val sharedPreferences = context.getSharedPreferences("AlarmApp", Context.MODE_PRIVATE)
    var activeAlarmTime by remember { mutableStateOf(sharedPreferences.getLong("active_alarm_time", -1L)) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (activeAlarmTime != -1L) {
            val calendar = Calendar.getInstance().apply { timeInMillis = activeAlarmTime }
            val sdf = SimpleDateFormat("hh:mm a", Locale.US)
            Text("Active alarm set for: ${sdf.format(calendar.time)}")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                cancelAlarm(context)
                wakeUpTimes = emptyList()
                activeAlarmTime = -1L
                Toast.makeText(context, "Alarm canceled", Toast.LENGTH_SHORT).show()
            }) {
                Text("Cancel Alarm")
            }
        } else {
            Button(onClick = {
                val calendar = Calendar.getInstance()
                val times = mutableListOf<Calendar>()
                for (i in 1..6) {
                    calendar.add(Calendar.MINUTE, 90)
                    times.add(calendar.clone() as Calendar)
                }
                wakeUpTimes = times
            }) {
                Text("Calculate Wake-up Times")
            }
            Spacer(modifier = Modifier.height(16.dp))
            wakeUpTimes.forEach { time ->
                val sdf = SimpleDateFormat("hh:mm a", Locale.US)
                Button(onClick = {
                    if (!alarmManager.canScheduleExactAlarms()) {
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also { intent ->
                            requestPermissionLauncher.launch(intent)
                        }
                    } else {
                        setAlarm(context, time)
                        with(sharedPreferences.edit()) {
                            putLong("active_alarm_time", time.timeInMillis)
                            apply()
                        }
                        activeAlarmTime = time.timeInMillis
                        Toast.makeText(context, "Alarm set for ${sdf.format(time.time)}", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(sdf.format(time.time))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@SuppressLint("ScheduleExactAlarm")
fun setAlarm(context: Context, calendar: Calendar) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
}

fun cancelAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    alarmManager.cancel(pendingIntent)

    val sharedPreferences = context.getSharedPreferences("AlarmApp", Context.MODE_PRIVATE)
    with(sharedPreferences.edit()) {
        remove("active_alarm_time")
        apply()
    }
}

@Preview(showBackground = true)
@Composable
fun SleepCalculatorScreenPreview() {
    FinalTheme {
        SleepCalculatorScreen()
    }
}