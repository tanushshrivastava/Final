package com.cs407.afinal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs407.afinal.model.AlarmItem
import com.cs407.afinal.viewmodel.SleepViewModel
import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: SleepViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showWeekView by remember { mutableStateOf(true) }

    // Calculate week days around selected date
    val weekDays = remember(selectedDate) {
        val startOfWeek = selectedDate.with(java.time.DayOfWeek.MONDAY)
        (0..6).map { startOfWeek.plusDays(it.toLong()) }
    }

    // Get alarms grouped by date (expanding recurring alarms)
    val alarmsByDate = remember(uiState.alarms, selectedDate, weekDays) {
        val alarmsMap = mutableMapOf<LocalDate, MutableList<AlarmItem>>()
        
        uiState.alarms.forEach { alarm ->
            if (alarm.recurringDays.isEmpty()) {
                // One-time alarm: show only on its trigger date
                val alarmDate = Instant.ofEpochMilli(alarm.triggerAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                alarmsMap.getOrPut(alarmDate) { mutableListOf() }.add(alarm)
            } else {
                // Recurring alarm: show on all matching days in the visible range
                val startDate = weekDays.first()
                val endDate = weekDays.last()
                var currentDate = startDate
                
                while (!currentDate.isAfter(endDate)) {
                    val dayOfWeek = currentDate.dayOfWeek.value // 1=Monday, 7=Sunday
                    if (alarm.recurringDays.contains(dayOfWeek)) {
                        alarmsMap.getOrPut(currentDate) { mutableListOf() }.add(alarm)
                    }
                    currentDate = currentDate.plusDays(1)
                }
            }
        }
        
        alarmsMap.mapValues { it.value.sortedBy { alarm -> 
            // Sort by time of day for display purposes
            val time = Instant.ofEpochMilli(alarm.triggerAtMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
            time.toSecondOfDay()
        } }
    }

    // Get alarms for selected date
    val selectedDateAlarms = remember(alarmsByDate, selectedDate) {
        alarmsByDate[selectedDate] ?: emptyList()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Sleep Schedule",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF3F51B5),
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { 
                        showWeekView = !showWeekView
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }) {
                        Icon(
                            imageVector = if (showWeekView) Icons.Default.CalendarViewMonth else Icons.Default.CalendarMonth,
                            contentDescription = "Toggle View",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE8EAF6))
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Statistics Card
            ScheduleStatsCard(
                totalAlarms = uiState.alarms.size,
                activeAlarms = uiState.alarms.count { it.isEnabled },
                upcomingAlarm = uiState.alarms
                    .filter { it.isEnabled && it.triggerAtMillis > System.currentTimeMillis() }
                    .minByOrNull { it.triggerAtMillis }
            )

            if (showWeekView) {
                // Week View
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Month/Year header with navigation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                selectedDate = selectedDate.minusWeeks(1)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Week")
                            }
                            
                            Text(
                                text = selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3F51B5)
                            )
                            
                            IconButton(onClick = {
                                selectedDate = selectedDate.plusWeeks(1)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Week")
                            }
                        }

                        // Week days
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            weekDays.forEach { date ->
                                WeekDayItem(
                                    date = date,
                                    isSelected = date == selectedDate,
                                    isToday = date == LocalDate.now(),
                                    hasAlarms = alarmsByDate.containsKey(date),
                                    alarmsCount = alarmsByDate[date]?.size ?: 0,
                                    onClick = {
                                        selectedDate = date
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                )
                            }
                        }
                    }
                }

                // Selected Date Alarms
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDateHeader(selectedDate),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3F51B5)
                            )
                            Text(
                                text = "${selectedDateAlarms.size} alarm${if (selectedDateAlarms.size != 1) "s" else ""}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }

                        if (selectedDateAlarms.isEmpty()) {
                            EmptyAlarmsPlaceholder()
                        } else {
                            selectedDateAlarms.forEach { alarm ->
                                ScheduleAlarmItem(
                                    alarm = alarm,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            } else {
                // List View - Show unique alarms (no date grouping for recurring)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "All Alarms",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3F51B5)
                        )

                        if (uiState.alarms.isEmpty()) {
                            EmptyAlarmsPlaceholder()
                        } else {
                            // Group by recurring vs one-time
                            val recurringAlarms = uiState.alarms.filter { it.recurringDays.isNotEmpty() }
                            val oneTimeAlarms = uiState.alarms.filter { it.recurringDays.isEmpty() }
                            
                            // Show recurring alarms first
                            if (recurringAlarms.isNotEmpty()) {
                                Text(
                                    text = "Recurring Alarms",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF5C6BC0),
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                recurringAlarms.sortedBy { 
                                    Instant.ofEpochMilli(it.triggerAtMillis)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalTime()
                                        .toSecondOfDay()
                                }.forEach { alarm ->
                                    ScheduleAlarmItem(
                                        alarm = alarm,
                                        viewModel = viewModel
                                    )
                                }
                            }
                            
                            // Show one-time alarms grouped by date
                            if (oneTimeAlarms.isNotEmpty()) {
                                val oneTimeByDate = oneTimeAlarms.groupBy { alarm ->
                                    Instant.ofEpochMilli(alarm.triggerAtMillis)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                }
                                
                                if (recurringAlarms.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                
                                Text(
                                    text = "One-time Alarms",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF5C6BC0),
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                
                                oneTimeByDate.entries
                                    .sortedBy { it.key }
                                    .forEach { (date, alarms) ->
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = formatDateHeader(date),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.Gray,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                            alarms.sortedBy { it.triggerAtMillis }.forEach { alarm ->
                                                ScheduleAlarmItem(
                                                    alarm = alarm,
                                                    viewModel = viewModel
                                                )
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
            }

            // Sleep History Section
            if (uiState.history.isNotEmpty()) {
                SleepHistorySection(history = uiState.history.take(5))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ScheduleStatsCard(
    totalAlarms: Int,
    activeAlarms: Int,
    upcomingAlarm: AlarmItem?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF5C6BC0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Default.Alarm,
                value = totalAlarms.toString(),
                label = "Total",
                color = Color.White
            )
            HorizontalDivider(
                modifier = Modifier
                    .height(50.dp)
                    .width(1.dp),
                color = Color.White.copy(alpha = 0.3f)
            )
            StatItem(
                icon = Icons.Default.CheckCircle,
                value = activeAlarms.toString(),
                label = "Active",
                color = Color.White
            )
            HorizontalDivider(
                modifier = Modifier
                    .height(50.dp)
                    .width(1.dp),
                color = Color.White.copy(alpha = 0.3f)
            )
            StatItem(
                icon = Icons.Default.Schedule,
                value = upcomingAlarm?.let { 
                    val hours = ChronoUnit.HOURS.between(Instant.now(), Instant.ofEpochMilli(it.triggerAtMillis))
                    "${hours}h"
                } ?: "—",
                label = "Next",
                color = Color.White
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = color.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun WeekDayItem(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasAlarms: Boolean,
    alarmsCount: Int,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> Color(0xFF5C6BC0)
        isToday -> Color(0xFFE8EAF6)
        else -> Color.Transparent
    }
    
    val textColor = when {
        isSelected -> Color.White
        isToday -> Color(0xFF3F51B5)
        else -> Color.Black
    }

    Column(
        modifier = Modifier
            .width(45.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1),
            fontSize = 12.sp,
            color = textColor.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = date.dayOfMonth.toString(),
            fontSize = 18.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
        if (hasAlarms) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color(0xFF5C6BC0))
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ScheduleAlarmItem(
    alarm: AlarmItem,
    viewModel: SleepViewModel
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val isPast = alarm.triggerAtMillis < System.currentTimeMillis()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !alarm.isEnabled -> Color(0xFFEEEEEE)
                isPast -> Color(0xFFFFCDD2)
                else -> Color(0xFFC5E1A5)
            }
        ),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            !alarm.isEnabled -> Icons.Default.AlarmOff
                            isPast -> Icons.Default.History
                            else -> Icons.Default.Alarm
                        },
                        contentDescription = null,
                        tint = when {
                            !alarm.isEnabled -> Color.Gray
                            isPast -> Color(0xFFC62828)
                            else -> Color(0xFF33691E)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Column {
                        Text(
                            text = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                .format(Date(alarm.triggerAtMillis)),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            alarm.targetCycles?.let {
                                Text(
                                    text = "$it cycles • ${alarm.label}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            } ?: Text(
                                text = alarm.label,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            if (alarm.recurringDays.isNotEmpty()) {
                                Text(
                                    text = " • ",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = formatRecurringDays(alarm.recurringDays),
                                    fontSize = 11.sp,
                                    color = Color(0xFF5C6BC0),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = { enabled ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleAlarm(alarm, enabled)
                    },
                    enabled = !isPast
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                    
                    AlarmDetailRow(
                        label = "Label",
                        value = alarm.label
                    )
                    
                    alarm.plannedBedTimeMillis?.let { bedTime ->
                        AlarmDetailRow(
                            label = "Bedtime",
                            value = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(bedTime))
                        )
                    }
                    
                    AlarmDetailRow(
                        label = "Gentle Wake",
                        value = if (alarm.gentleWake) "Yes" else "No"
                    )
                    
                    AlarmDetailRow(
                        label = "Created",
                        value = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                            .format(Date(alarm.createdAtMillis))
                    )
                    
                    if (alarm.recurringDays.isNotEmpty()) {
                        AlarmDetailRow(
                            label = "Repeats",
                            value = formatRecurringDaysFull(alarm.recurringDays)
                        )
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.deleteAlarm(alarm.id)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Alarm", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = Color.Black,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyAlarmsPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.AlarmOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color.Gray.copy(alpha = 0.5f)
        )
        Text(
            text = "No alarms scheduled",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Go to the Alarm tab to set one",
            fontSize = 12.sp,
            color = Color.Gray.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SleepHistorySection(history: List<com.cs407.afinal.model.SleepHistoryEntry>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent History",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3F51B5)
                )
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = Color(0xFF5C6BC0),
                    modifier = Modifier.size(20.dp)
                )
            }

            history.forEach { entry ->
                HistoryItem(entry)
            }
        }
    }
}

@Composable
private fun HistoryItem(entry: com.cs407.afinal.model.SleepHistoryEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = entry.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            Text(
                text = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                    .format(Date(entry.actualDismissedMillis)),
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
        
        val delay = (entry.actualDismissedMillis - entry.plannedWakeMillis) / (1000 * 60)
        Text(
            text = when {
                delay > 0 -> "+${delay}m late"
                delay < 0 -> "${-delay}m early"
                else -> "On time"
            },
            fontSize = 11.sp,
            color = when {
                delay > 5 -> Color(0xFFD32F2F)
                delay < -5 -> Color(0xFF1976D2)
                else -> Color(0xFF388E3C)
            },
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDateHeader(date: LocalDate): String {
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        date == today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM dd"))
    }
}

private fun formatRecurringDays(days: List<Int>): String {
    val dayAbbreviations = mapOf(
        1 to "Mon",
        2 to "Tue",
        3 to "Wed",
        4 to "Thu",
        5 to "Fri",
        6 to "Sat",
        7 to "Sun"
    )
    
    if (days.isEmpty()) return ""
    if (days.size == 7) return "Every day"
    if (days.containsAll(listOf(1, 2, 3, 4, 5)) && days.size == 5) return "Weekdays"
    if (days.containsAll(listOf(6, 7)) && days.size == 2) return "Weekends"
    
    return days.sorted().joinToString(", ") { dayAbbreviations[it] ?: "" }
}

private fun formatRecurringDaysFull(days: List<Int>): String {
    val dayNames = mapOf(
        1 to "Monday",
        2 to "Tuesday",
        3 to "Wednesday",
        4 to "Thursday",
        5 to "Friday",
        6 to "Saturday",
        7 to "Sunday"
    )
    
    if (days.isEmpty()) return "One-time"
    if (days.size == 7) return "Every day"
    if (days.containsAll(listOf(1, 2, 3, 4, 5)) && days.size == 5) return "Every weekday"
    if (days.containsAll(listOf(6, 7)) && days.size == 2) return "Every weekend"
    
    return days.sorted().joinToString(", ") { dayNames[it] ?: "" }
}

