/**
 * Sleep data export utilities.
 */
package com.cs407.afinal.sleep

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.cs407.afinal.alarm.SleepHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class SleepDataExporter(private val context: Context) {

    suspend fun exportToCSV(
        history: List<SleepHistoryEntry>,
        includeHeaders: Boolean = true
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            if (history.isEmpty()) {
                return@withContext ExportResult.Error("No sleep data to export")
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val filename = "sleep_data_$timestamp.csv"
            val file = File(context.cacheDir, filename)
            
            FileWriter(file).use { writer ->
                if (includeHeaders) {
                    writer.append(buildCSVHeader())
                    writer.append("\n")
                }

                history.forEach { entry ->
                    writer.append(buildCSVRow(entry))
                    writer.append("\n")
                }
            }

            ExportResult.Success(file, history.size)
        } catch (e: Exception) {
            ExportResult.Error("Export failed: ${e.localizedMessage}")
        }
    }

    suspend fun exportWithAnalytics(
        history: List<SleepHistoryEntry>,
        includeAnalytics: Boolean = true
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            if (history.isEmpty()) {
                return@withContext ExportResult.Error("No sleep data to export")
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                .format(Date())
            val filename = "sleep_data_detailed_$timestamp.csv"
            val file = File(context.cacheDir, filename)

            FileWriter(file).use { writer ->
                writer.append("Sleep History Data\n")
                writer.append(buildCSVHeader())
                writer.append("\n")

                history.forEach { entry ->
                    writer.append(buildCSVRow(entry))
                    writer.append("\n")
                }

                if (includeAnalytics) {
                    writer.append("\n\nSleep Analytics Summary\n")
                    writer.append(buildAnalyticsSummary(history))
                }
            }

            ExportResult.Success(file, history.size)
        } catch (e: Exception) {
            ExportResult.Error("Export failed: ${e.localizedMessage}")
        }
    }

    fun shareCSVFile(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sleep Data Export")
            putExtra(
                Intent.EXTRA_TEXT,
                "Here is my sleep data exported from Smart Sleep App"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildCSVHeader(): String {
        return listOf(
            "Entry ID",
            "Alarm ID",
            "Label",
            "Planned Wake Time",
            "Actual Wake Time",
            "Planned Bed Time",
            "Sleep Duration (Hours)",
            "Delay (Minutes)",
            "Date",
            "Day of Week",
            "Quality Score"
        ).joinToString(",") { escapeCSVField(it) }
    }

    private fun buildCSVRow(entry: SleepHistoryEntry): String {
        val plannedWakeZoned = Instant.ofEpochMilli(entry.plannedWakeMillis)
            .atZone(ZoneId.systemDefault())
        val actualWakeZoned = Instant.ofEpochMilli(entry.actualDismissedMillis)
            .atZone(ZoneId.systemDefault())
        
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val dayFormatter = DateTimeFormatter.ofPattern("EEEE")

        val sleepDuration = entry.plannedBedTimeMillis?.let { bedTime ->
            val durationMs = entry.actualDismissedMillis - bedTime
            String.format("%.2f", durationMs / (1000.0 * 60.0 * 60.0))
        } ?: "N/A"

        val delayMinutes = (entry.actualDismissedMillis - entry.plannedWakeMillis) / (1000 * 60)
        
        val qualityScore = calculateQualityScore(entry)

        return listOf(
            entry.id.toString(),
            entry.alarmId.toString(),
            entry.label,
            plannedWakeZoned.format(timeFormatter),
            actualWakeZoned.format(timeFormatter),
            entry.plannedBedTimeMillis?.let { 
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(timeFormatter) 
            } ?: "N/A",
            sleepDuration,
            delayMinutes.toString(),
            actualWakeZoned.format(dateFormatter),
            actualWakeZoned.format(dayFormatter),
            qualityScore.toString()
        ).joinToString(",") { escapeCSVField(it) }
    }

    private fun buildAnalyticsSummary(history: List<SleepHistoryEntry>): String {
        val sb = StringBuilder()

        val totalEntries = history.size
        val avgSleepDuration = calculateAverageSleepDuration(history)
        val totalSleepHours = calculateTotalSleepHours(history)
        val avgDelay = calculateAverageDelay(history)
        val consistencyScore = calculateConsistencyScore(history)

        sb.append("Metric,Value\n")
        sb.append("Total Sleep Sessions,$totalEntries\n")
        sb.append("Average Sleep Duration (hours),${String.format("%.2f", avgSleepDuration)}\n")
        sb.append("Total Sleep Hours,${String.format("%.1f", totalSleepHours)}\n")
        sb.append("Average Wake Delay (minutes),${String.format("%.1f", avgDelay)}\n")
        sb.append("Consistency Score (0-100),$consistencyScore\n")
        sb.append("Export Date,${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")

        return sb.toString()
    }

    private fun calculateAverageSleepDuration(history: List<SleepHistoryEntry>): Double {
        val durations = history.mapNotNull { entry ->
            entry.plannedBedTimeMillis?.let { bedTime ->
                (entry.actualDismissedMillis - bedTime) / (1000.0 * 60.0 * 60.0)
            }
        }
        return if (durations.isNotEmpty()) durations.average() else 0.0
    }

    private fun calculateTotalSleepHours(history: List<SleepHistoryEntry>): Double {
        return history.sumOf { entry ->
            entry.plannedBedTimeMillis?.let { bedTime ->
                (entry.actualDismissedMillis - bedTime) / (1000.0 * 60.0 * 60.0)
            } ?: 0.0
        }
    }

    private fun calculateAverageDelay(history: List<SleepHistoryEntry>): Double {
        val delays = history.map { entry ->
            (entry.actualDismissedMillis - entry.plannedWakeMillis) / (1000.0 * 60.0)
        }
        return if (delays.isNotEmpty()) delays.average() else 0.0
    }

    private fun calculateConsistencyScore(history: List<SleepHistoryEntry>): Int {
        if (history.size < 2) return 100

        val wakeTimes = history.map { 
            Instant.ofEpochMilli(it.actualDismissedMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
        }

        val minutesOfDay = wakeTimes.map { it.hour * 60 + it.minute }
        val avgMinutes = minutesOfDay.average()
        val variance = minutesOfDay.map { (it - avgMinutes) * (it - avgMinutes) }.average()
        val stdDev = kotlin.math.sqrt(variance)

        return (100 - stdDev.coerceIn(0.0, 100.0)).toInt()
    }

    private fun calculateQualityScore(entry: SleepHistoryEntry): Int {
        var score = 50

        entry.plannedBedTimeMillis?.let { bedTime ->
            val durationHours = (entry.actualDismissedMillis - bedTime) / (1000.0 * 60.0 * 60.0)
            score += when {
                durationHours in 7.0..9.0 -> 30
                durationHours in 6.0..10.0 -> 20
                durationHours in 5.0..11.0 -> 10
                else -> 0
            }
        }

        val delayMinutes = (entry.actualDismissedMillis - entry.plannedWakeMillis) / (1000 * 60)
        score += when {
            delayMinutes in -5..5 -> 20
            delayMinutes in -15..15 -> 10
            else -> 0
        }

        return score.coerceIn(0, 100)
    }

    private fun escapeCSVField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}

sealed class ExportResult {
    data class Success(val file: File, val entriesExported: Int) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

