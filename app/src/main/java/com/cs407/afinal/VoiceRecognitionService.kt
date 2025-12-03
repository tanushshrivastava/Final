package com.cs407.afinal

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.cs407.afinal.alarm.AlarmItem
import com.cs407.afinal.alarm.AlarmManager
import com.cs407.afinal.util.AlarmCommand
import com.cs407.afinal.util.VoiceCommandHandler
import com.cs407.afinal.util.VoiceResult
import kotlinx.coroutines.*

class VoiceRecognitionService : Service() {

    private lateinit var voiceCommandHandler: VoiceCommandHandler
    private lateinit var alarmManager: AlarmManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var listeningJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        voiceCommandHandler = VoiceCommandHandler(this)
        alarmManager = AlarmManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startContinuousListening()
        return START_STICKY
    }

    private fun startContinuousListening() {
        if (listeningJob?.isActive == true) return

        listeningJob = serviceScope.launch {
            while (isActive) {
                Log.d("VoiceRecognitionService", "Starting new listening session.")
                voiceCommandHandler.startListening().collect { result ->
                    when (result) {
                        is VoiceResult.Success -> {
                            Log.i("VoiceRecognitionService", "Voice recognized: '${result.recognizedText}'")
                            resetInactivityMonitor()

                            if (result.command != null) {
                                handleAlarmCommand(result.command)
                            } else {
                                Toast.makeText(applicationContext, "Heard: \"${result.recognizedText}\"", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is VoiceResult.Error -> {
                            Log.e("VoiceRecognitionService", "Recognition error: ${result.message}")
                        }
                        else -> {}
                    }
                }
                delay(500) // Brief pause before restarting
            }
        }
    }

    private fun handleAlarmCommand(command: AlarmCommand) {
        if (!alarmManager.canScheduleExactAlarms()) {
            Log.w("VoiceRecognitionService", "Missing exact alarm permission.")
            Toast.makeText(this, "Exact alarm permission needed.", Toast.LENGTH_LONG).show()
            return
        }

        val alarmId = alarmManager.nextAlarmId()
        val newAlarm = AlarmItem(
            id = alarmId,
            triggerAtMillis = command.triggerAtMillis,
            label = command.label.ifBlank { "Voice Alarm" },
            isEnabled = true,
            gentleWake = true,
            createdAtMillis = System.currentTimeMillis(),
            isAutoSet = false
        )
        
        alarmManager.upsertAlarm(newAlarm)
        alarmManager.scheduleAlarm(newAlarm)
        
        Log.i("VoiceRecognitionService", "Scheduled alarm from voice command for ${newAlarm.triggerAtMillis}.")
        Toast.makeText(this, "Alarm set: ${newAlarm.label}", Toast.LENGTH_SHORT).show()
    }

    private fun resetInactivityMonitor() {
        Log.d("VoiceRecognitionService", "Resetting inactivity monitor due to voice activity.")
        val intent = Intent(this, InactivityMonitorService::class.java).apply {
            action = InactivityMonitorService.ACTION_RESET_INACTIVITY_TIMER
        }
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        listeningJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
