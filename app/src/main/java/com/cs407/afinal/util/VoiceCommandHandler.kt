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
import android.util.Log

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
        var retryCount = 0
        lateinit var listenIntent: Intent
        var lastPartial: String? = null
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
                Log.d("VoiceDebug", "RMS = $rmsdB")
                // Audio level changed - this confirms microphone is working
                // rmsdB ranges from 0 to 10+ (louder = higher value)
                if (rmsdB > 0) {
                    // Microphone is picking up sound
                    trySend(VoiceResult.AudioDetected(rmsdB))
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Buffer received
            }

            override fun onEndOfSpeech() {
                trySend(VoiceResult.Processing)
            }

            override fun onError(error: Int) {
                Log.w("VoiceDebug", "Recognizer error code = $error")
                if (error == SpeechRecognizer.ERROR_NO_MATCH && retryCount < 2) {
                    retryCount++
                    speechRecognizer?.cancel()
                    speechRecognizer?.startListening(listenIntent)
                    return
                }
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "❌ Audio recording error\n\nCheck if another app is using the microphone"
                    SpeechRecognizer.ERROR_CLIENT -> "❌ Client error\n\nTry again"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "❌ Microphone permission denied\n\nPlease grant permission in Settings"
                    SpeechRecognizer.ERROR_NETWORK -> "❌ Network error\n\nSpeech recognition needs internet connection"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "❌ Network timeout\n\nCheck your internet connection"
                    SpeechRecognizer.ERROR_NO_MATCH -> "❌ Couldn't understand speech\n\nTry speaking more clearly"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "❌ Recognition service busy\n\nWait a moment and try again"
                    SpeechRecognizer.ERROR_SERVER -> "❌ Server error\n\nGoogle's servers may be down. Try again later"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "❌ No speech detected\n\nMake sure you speak right after tapping the mic"
                    else -> "❌ Unknown error ($error)\n\nTry again"
                }
                trySend(VoiceResult.Error(errorMessage))
                close()
            }

            override fun onResults(results: Bundle?) {
                var matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if ((matches == null || matches.isEmpty()) && lastPartial != null) {
                    matches = arrayListOf(lastPartial!!)
                }
                Log.d("VoiceDebug", "Final results = ${matches?.joinToString()}")
                if (!matches.isNullOrEmpty()) {
                    val command = matches[0]
                    val parsedCommand = parseVoiceCommand(command)
                    trySend(VoiceResult.Success(command, parsedCommand))
                } else {
                    trySend(VoiceResult.Error("No speech recognized"))
                }
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d("VoiceDebug", "Partial = ${matches?.joinToString()}")
                if (!matches.isNullOrEmpty()) {
                    lastPartial = matches[0]
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Event occurred
            }
        }

        speechRecognizer?.setRecognitionListener(recognitionListener)

        listenIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say alarm time (e.g., '7 AM' or '30 minutes')")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Relaxed settings for better microphone pickup
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }

        speechRecognizer?.startListening(listenIntent)

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
        val lowerCommand = command.lowercase().trim()
        
        // Convert words to numbers first
        val numberWords = mapOf(
            "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
            "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10",
            "eleven" to "11", "twelve" to "12", "fifteen" to "15", "twenty" to "20",
            "thirty" to "30", "forty" to "40", "fifty" to "50", "sixty" to "60"
        )
        
        var processedCommand = lowerCommand
        for ((word, num) in numberWords) {
            processedCommand = processedCommand.replace(word, num)
        }
        
        // Handle relative time first (higher priority)
        // "30 minutes", "in 30 minutes", "thirty minutes"
        Regex("""(\d+)\s*minute[s]?""").find(processedCommand)?.let { match ->
            val minutes = match.groupValues[1].toIntOrNull() ?: return null
            val targetTime = ZonedDateTime.now().plusMinutes(minutes.toLong())
            return AlarmCommand(
                triggerAtMillis = targetTime.toInstant().toEpochMilli(),
                label = "Voice alarm",
                cycles = null
            )
        }
        
        // "2 hours", "in 2 hours", "two hours"
        Regex("""(\d+)\s*hour[s]?""").find(processedCommand)?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: return null
            val targetTime = ZonedDateTime.now().plusHours(hours.toLong())
            return AlarmCommand(
                triggerAtMillis = targetTime.toInstant().toEpochMilli(),
                label = "Voice alarm",
                cycles = null
            )
        }
        
        // Try to extract time from various formats
        val timePatterns = listOf(
            // "7:30 am" or "7:30 pm"
            Regex("""(\d{1,2}):(\d{2})\s*(a\.?m\.?|p\.?m\.?)"""),
            // "7 am" or "7 pm"
            Regex("""(\d{1,2})\s*(a\.?m\.?|p\.?m\.?)"""),
            // "730" or "1930" (military time)
            Regex("""^(\d{3,4})$""")
        )

        for (pattern in timePatterns) {
            val match = pattern.find(processedCommand)
            if (match != null) {
                return try {
                    when {
                        // Time with minutes "7:30 am"
                        match.groupValues.size >= 4 && match.groupValues[2].isNotEmpty() -> {
                            val hour = match.groupValues[1].toIntOrNull() ?: continue
                            val minute = match.groupValues[2].toIntOrNull() ?: continue
                            val amPm = match.groupValues[3].replace(".", "").lowercase()
                            val isPM = amPm.startsWith("p")
                            
                            var adjustedHour = hour
                            if (isPM && hour != 12) adjustedHour += 12
                            if (!isPM && hour == 12) adjustedHour = 0
                            
                            if (adjustedHour !in 0..23 || minute !in 0..59) continue
                            
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
                            val hour = match.groupValues[1].toIntOrNull() ?: continue
                            val amPm = match.groupValues[2].replace(".", "").lowercase()
                            val isPM = amPm.startsWith("p")
                            
                            var adjustedHour = hour
                            if (isPM && hour != 12) adjustedHour += 12
                            if (!isPM && hour == 12) adjustedHour = 0
                            
                            if (adjustedHour !in 0..23) continue
                            
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
                                    hour = timeStr.substring(0, 1).toIntOrNull() ?: continue
                                    minute = timeStr.substring(1).toIntOrNull() ?: continue
                                }
                                4 -> {
                                    hour = timeStr.substring(0, 2).toIntOrNull() ?: continue
                                    minute = timeStr.substring(2).toIntOrNull() ?: continue
                                }
                                else -> continue
                            }
                            
                            if (hour !in 0..23 || minute !in 0..59) continue
                            
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
                } catch (e: Exception) {
                    continue
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
    data class AudioDetected(val rmsdB: Float) : VoiceResult()
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


