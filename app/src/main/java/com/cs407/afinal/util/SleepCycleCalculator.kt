package com.cs407.afinal.util

import com.cs407.afinal.model.SleepMode
import com.cs407.afinal.model.SleepSuggestion
import com.cs407.afinal.model.SleepSuggestionType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

object SleepCycleCalculator {

    private val defaultCycles = listOf(6, 5, 4)

    fun suggestionCycles(): List<Int> = defaultCycles

    fun normalizeTargetDateTime(mode: SleepMode, targetTime: LocalTime): ZonedDateTime {
        val now = ZonedDateTime.now()
        val todayTarget = now.withHour(targetTime.hour).withMinute(targetTime.minute)
            .withSecond(0).withNano(0)
        return when (mode) {
            SleepMode.WAKE_TIME -> if (todayTarget.isBefore(now)) todayTarget.plusDays(1) else todayTarget
            SleepMode.BED_TIME -> if (todayTarget.isBefore(now.minusMinutes(FALL_ASLEEP_BUFFER_MINUTES.toLong()))) {
                todayTarget.plusDays(1)
            } else {
                todayTarget
            }
        }
    }

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

    fun zonedDateTimeFromMillis(epochMillis: Long): ZonedDateTime =
        ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(epochMillis),
            ZoneId.systemDefault()
        )

    fun localTimeFromMillis(epochMillis: Long): LocalTime =
        ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).toLocalTime()

    fun millisFromLocalDateTime(localDateTime: LocalDateTime, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        localDateTime.atZone(zoneId).toInstant().toEpochMilli()

    fun combine(date: LocalDate, time: LocalTime): ZonedDateTime =
        ZonedDateTime.of(date, time, ZoneId.systemDefault())

    private fun formatDurationMinutes(minutes: Int): String {
        val duration = Duration.ofMinutes(minutes.toLong())
        val hours = duration.toHours()
        val mins = duration.toMinutesPart()
        return when {
            mins == 0 -> String.format(Locale.US, "%dh sleep", hours)
            else -> String.format(Locale.US, "%dh %02dm sleep", hours, mins)
        }
    }

    const val CYCLE_MINUTES = 90
    const val FALL_ASLEEP_BUFFER_MINUTES = 15
}
