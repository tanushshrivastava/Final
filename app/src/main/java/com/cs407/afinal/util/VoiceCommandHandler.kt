package com.cs407.afinal.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Handles voice command processing for setting alarms
 */
class VoiceCommandHandler(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Start listening for voice commands
     * Returns a Flow that emits recognition results
     */
    fun startListening(): Flow<VoiceResult> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(VoiceResult.Error("Speech recognition not available"))
            close()
            return@callbackFlow
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        
        val recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(VoiceResult.Listening)
            }

            override fun onBeginningOfSpeech() {
                trySend(VoiceResult.Speaking)
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changed
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Buffer received
            }

            override fun onEndOfSpeech() {
                trySend(VoiceResult.Processing)
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }
                trySend(VoiceResult.Error(errorMessage))
                close()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    val command = matches[0]
                    val parsedCommand = parseVoiceCommand(command)
                    trySend(VoiceResult.Success(command, parsedCommand))
                } else {
                    trySend(VoiceResult.Error("No speech recognized"))
                }
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Partial results
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Event occurred
            }
        }

        speechRecognizer?.setRecognitionListener(recognitionListener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your alarm time...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
        }

        speechRecognizer?.startListening(intent)

        awaitClose {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    /**
     * Parse voice command to extract alarm parameters
     */
    private fun parseVoiceCommand(command: String): AlarmCommand? {
        val lowerCommand = command.lowercase()
        
        // Try to extract time from various formats
        val timePatterns = listOf(
            // "set alarm for 7:30 am"
            Regex("""(\d{1,2}):(\d{2})\s*(am|pm)"""),
            // "wake me at 7 am"
            Regex("""(\d{1,2})\s*(am|pm)"""),
            // "alarm in 30 minutes"
            Regex("""in\s+(\d+)\s+minute[s]?"""),
            // "alarm in 2 hours"
            Regex("""in\s+(\d+)\s+hour[s]?"""),
            // "set alarm for 730"
            Regex("""(\d{3,4})""")
        )

        for (pattern in timePatterns) {
            val match = pattern.find(lowerCommand)
            if (match != null) {
                return when {
                    // "in X minutes"
                    lowerCommand.contains("in") && lowerCommand.contains("minute") -> {
                        val minutes = match.groupValues[1].toIntOrNull() ?: return null
                        val targetTime = ZonedDateTime.now().plusMinutes(minutes.toLong())
                        AlarmCommand(
                            triggerAtMillis = targetTime.toInstant().toEpochMilli(),
                            label = "Voice alarm",
                            cycles = null
                        )
                    }
                    // "in X hours"
                    lowerCommand.contains("in") && lowerCommand.contains("hour") -> {
                        val hours = match.groupValues[1].toIntOrNull() ?: return null
                        val targetTime = ZonedDateTime.now().plusHours(hours.toLong())
                        AlarmCommand(
                            triggerAtMillis = targetTime.toInstant().toEpochMilli(),
                            label = "Voice alarm",
                            cycles = null
                        )
                    }
                    // Time with minutes "7:30 am"
                    match.groupValues.size >= 4 && match.groupValues[2].isNotEmpty() -> {
                        val hour = match.groupValues[1].toIntOrNull() ?: return null
                        val minute = match.groupValues[2].toIntOrNull() ?: return null
                        val isPM = match.groupValues[3].lowercase() == "pm"
                        
                        var adjustedHour = hour
                        if (isPM && hour != 12) adjustedHour += 12
                        if (!isPM && hour == 12) adjustedHour = 0
                        
                        val targetTime = LocalTime.of(adjustedHour, minute)
                        val zonedTime = SleepCycleCalculator.normalizeTargetDateTime(
                            com.cs407.afinal.model.SleepMode.WAKE_TIME,
                            targetTime
                        )
                        
                        AlarmCommand(
                            triggerAtMillis = zonedTime.toInstant().toEpochMilli(),
                            label = "Voice alarm",
                            cycles = null
                        )
                    }
                    // Time without minutes "7 am"
                    match.groupValues.size >= 3 -> {
                        val hour = match.groupValues[1].toIntOrNull() ?: return null
                        val isPM = match.groupValues[2].lowercase() == "pm"
                        
                        var adjustedHour = hour
                        if (isPM && hour != 12) adjustedHour += 12
                        if (!isPM && hour == 12) adjustedHour = 0
                        
                        val targetTime = LocalTime.of(adjustedHour, 0)
                        val zonedTime = SleepCycleCalculator.normalizeTargetDateTime(
                            com.cs407.afinal.model.SleepMode.WAKE_TIME,
                            targetTime
                        )
                        
                        AlarmCommand(
                            triggerAtMillis = zonedTime.toInstant().toEpochMilli(),
                            label = "Voice alarm",
                            cycles = null
                        )
                    }
                    // Military time "730" or "1930"
                    else -> {
                        val timeStr = match.groupValues[1]
                        val hour: Int
                        val minute: Int
                        
                        when (timeStr.length) {
                            3 -> {
                                hour = timeStr.substring(0, 1).toIntOrNull() ?: return null
                                minute = timeStr.substring(1).toIntOrNull() ?: return null
                            }
                            4 -> {
                                hour = timeStr.substring(0, 2).toIntOrNull() ?: return null
                                minute = timeStr.substring(2).toIntOrNull() ?: return null
                            }
                            else -> return null
                        }
                        
                        if (hour !in 0..23 || minute !in 0..59) return null
                        
                        val targetTime = LocalTime.of(hour, minute)
                        val zonedTime = SleepCycleCalculator.normalizeTargetDateTime(
                            com.cs407.afinal.model.SleepMode.WAKE_TIME,
                            targetTime
                        )
                        
                        AlarmCommand(
                            triggerAtMillis = zonedTime.toInstant().toEpochMilli(),
                            label = "Voice alarm",
                            cycles = null
                        )
                    }
                }
            }
        }

        return null
    }
}

/**
 * Result of voice recognition
 */
sealed class VoiceResult {
    object Listening : VoiceResult()
    object Speaking : VoiceResult()
    object Processing : VoiceResult()
    data class Success(val recognizedText: String, val command: AlarmCommand?) : VoiceResult()
    data class Error(val message: String) : VoiceResult()
}

/**
 * Parsed alarm command from voice input
 */
data class AlarmCommand(
    val triggerAtMillis: Long,
    val label: String,
    val cycles: Int?
)


