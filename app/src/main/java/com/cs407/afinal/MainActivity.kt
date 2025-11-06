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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs407.afinal.ui.theme.FinalTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        setContent {
            FinalTheme {
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
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedTimeIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val sharedPreferences = context.getSharedPreferences("AlarmApp", Context.MODE_PRIVATE)
    var activeAlarmTime by remember { mutableStateOf(sharedPreferences.getLong("active_alarm_time", -1L)) }
    var showAlarmSet by remember { mutableStateOf(activeAlarmTime != -1L) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    // Update current time every minute and recalculate wake-up times
    LaunchedEffect(currentTime) {
        while (true) {
            currentTime = Calendar.getInstance()

            // Recalculate wake-up times based on current time
            val calendar = currentTime.clone() as Calendar
            val times = mutableListOf<Calendar>()
            for (i in 1..6) {
                calendar.add(Calendar.MINUTE, 90)
                times.add(calendar.clone() as Calendar)
            }
            wakeUpTimes = times

            delay(60000L) // Update every minute
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE8EAF6)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Set Alarm",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (showAlarmSet && activeAlarmTime != -1L) {
                AlarmSetCard(
                    alarmTime = activeAlarmTime,
                    onCancel = {
                        cancelAlarm(context)
                        activeAlarmTime = -1L
                        showAlarmSet = false
                        selectedTimeIndex = null
                        Toast.makeText(context, "Alarm canceled", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                SetAlarmCard(
                    currentTime = currentTime,
                    wakeUpTimes = wakeUpTimes,
                    selectedTimeIndex = selectedTimeIndex,
                    onTimeSelected = { index -> selectedTimeIndex = index },
                    onSetAlarm = {
                        selectedTimeIndex?.let { index ->
                            val selectedTime = wakeUpTimes[index]
                            if (!alarmManager.canScheduleExactAlarms()) {
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also { intent ->
                                    requestPermissionLauncher.launch(intent)
                                }
                            } else {
                                setAlarm(context, selectedTime)
                                with(sharedPreferences.edit()) {
                                    putLong("active_alarm_time", selectedTime.timeInMillis)
                                    apply()
                                }
                                activeAlarmTime = selectedTime.timeInMillis
                                showAlarmSet = true
                                val sdf = SimpleDateFormat("hh:mm a", Locale.US)
                                Toast.makeText(context, "Alarm set for ${sdf.format(selectedTime.time)}", Toast.LENGTH_SHORT).show()
                            }
                        } ?: run {
                            Toast.makeText(context, "Please select a wake-up time", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SetAlarmCard(
    currentTime: Calendar,
    wakeUpTimes: List<Calendar>,
    selectedTimeIndex: Int?,
    onTimeSelected: (Int) -> Unit,
    onSetAlarm: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD6DBFA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "When are you sleeping?",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF3F51B5),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Current Time Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                val sdf = SimpleDateFormat("hh:mm a", Locale.US)
                Text(
                    text = sdf.format(currentTime.time),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Set to system time\nby default",
                fontSize = 11.sp,
                color = Color(0xFF5C6BC0),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Optimal Wake Times (90-min REM Cycles)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF3F51B5),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Scrollable wake-up times
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                wakeUpTimes.forEachIndexed { index, time ->
                    val sdf = SimpleDateFormat("hh:mm a", Locale.US)
                    val cycles = index + 1
                    val hours = (cycles * 90) / 60
                    val minutes = (cycles * 90) % 60
                    val durationText = if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h 0m"

                    WakeTimeOption(
                        time = sdf.format(time.time).uppercase(),
                        cycles = "$cycles cycles, $durationText",
                        isRecommended = cycles == 6,
                        isSelected = selectedTimeIndex == index,
                        onClick = {
                            onTimeSelected(index)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Set Alarm Button
            Button(
                onClick = onSetAlarm,
                modifier = Modifier
                    .fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5C6BC0)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Set Alarm", fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
fun AlarmSetCard(
    alarmTime: Long,
    onCancel: () -> Unit
) {
    val alarmCalendar = Calendar.getInstance().apply { timeInMillis = alarmTime }
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
    val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.US)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD6DBFA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Alarm Set!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3F51B5),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Large alarm time display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = timeFormat.format(alarmCalendar.time),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Alarm set for",
                fontSize = 14.sp,
                color = Color(0xFF5C6BC0),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = dateFormat.format(alarmCalendar.time),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF3F51B5),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Cancel Alarm Button
            Button(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5C6BC0)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Cancel Alarm", fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
fun WakeTimeOption(
    time: String,
    cycles: String,
    isRecommended: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFBBDEFB) else Color.White
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = time,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = cycles,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            if (isRecommended) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF2962FF), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Recommended",
                        fontSize = 10.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
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