/**
 * Sleep cycle calculations and time utilities.
 * Location: com.cs407.afinal.sleep.SleepCalculator.kt
 */
package com.cs407.afinal.sleep

import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

object SleepCalculator {

    private const val CYCLE_MINUTES = 90
    private const val FALL_ASLEEP_BUFFER_MINUTES = 15
    private val defaultCycles = listOf(6, 5, 4, 3, 2, 1)

    fun suggestionCycles(): List<Int> = defaultCycles

    /**
     * Normalize a target time to a future ZonedDateTime
     */
    fun normalizeTargetDateTime(mode: SleepMode, targetTime: LocalTime): ZonedDateTime {
        val now = ZonedDateTime.now()
        val todayTarget = now.withHour(targetTime.hour).withMinute(targetTime.minute)
            .withSecond(0).withNano(0)

        return when (mode) {
            SleepMode.WAKE_TIME -> {
                if (todayTarget.isBefore(now)) todayTarget.plusDays(1) else todayTarget
            }
            SleepMode.BED_TIME -> {
                if (todayTarget.isBefore(now.minusMinutes(FALL_ASLEEP_BUFFER_MINUTES.toLong()))) {
                    todayTarget.plusDays(1)
                } else {
                    todayTarget
                }
            }
        }
    }

    /**
     * Calculate suggested bed times for a target wake time
     */
    fun bedTimeSuggestions(targetWake: ZonedDateTime): List<SleepSuggestion> =
        defaultCycles.map { cycles ->
            val sleepDurationMinutes = cycles * CYCLE_MINUTES + FALL_ASLEEP_BUFFER_MINUTES
            val suggestedTime = targetWake.minusMinutes(sleepDurationMinutes.toLong())
            SleepSuggestion(
                id = "bed-$cycles",
                displayMillis = suggestedTime.toInstant().toEpochMilli(),
                cycles = cycles,
                type = SleepSuggestionType.BEDTIME,
                note = formatDurationMinutes(cycles * CYCLE_MINUTES),
                referenceMillis = targetWake.toInstant().toEpochMilli()
            )
        }.sortedByDescending { it.cycles }

    /**
     * Calculate suggested wake times for a bed time
     */
    fun wakeTimeSuggestions(bedTime: ZonedDateTime): List<SleepSuggestion> =
        defaultCycles.map { cycles ->
            val totalMinutes = FALL_ASLEEP_BUFFER_MINUTES + cycles * CYCLE_MINUTES
            val suggestedTime = bedTime.plusMinutes(totalMinutes.toLong())
            SleepSuggestion(
                id = "wake-$cycles",
                displayMillis = suggestedTime.toInstant().toEpochMilli(),
                cycles = cycles,
                type = SleepSuggestionType.WAKE_UP,
                note = formatDurationMinutes(cycles * CYCLE_MINUTES),
                referenceMillis = bedTime.toInstant().toEpochMilli()
            )
        }.sortedBy { it.displayMillis }

    /**
     * Format time from epoch millis
     */
    fun formatTime(epochMillis: Long): String {
        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return formatter.format(Date(epochMillis))
    }

    /**
     * Format day label (Today, Tomorrow, or date)
     */
    fun formatDayLabel(epochMillis: Long): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        }
    }

    /**
     * Format LocalTime to readable string
     */
    fun formatLocalTime(localTime: LocalTime): String {
        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val date = Date.from(
            localTime.atDate(LocalDate.now())
                .atZone(ZoneId.systemDefault())
                .toInstant()
        )
        return formatter.format(date)
    }

    private fun formatDurationMinutes(minutes: Int): String {
        val duration = Duration.ofMinutes(minutes.toLong())
        val hours = duration.toHours()
        val mins = duration.toMinutesPart()
        return when {
            mins == 0 -> String.format(Locale.US, "%dh sleep", hours)
            else -> String.format(Locale.US, "%dh %02dm sleep", hours, mins)
        }
    }

    fun zonedDateTimeFromMillis(epochMillis: Long): ZonedDateTime =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())

    fun localTimeFromMillis(epochMillis: Long): LocalTime =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).toLocalTime()

    fun millisFromLocalDateTime(localDateTime: LocalDateTime, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        localDateTime.atZone(zoneId).toInstant().toEpochMilli()

    fun combine(date: LocalDate, time: LocalTime): ZonedDateTime =
        ZonedDateTime.of(date, time, ZoneId.systemDefault())
}