package com.cs407.afinal.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs407.afinal.alarm.AlarmItem
import com.cs407.afinal.alarm.AlarmScheduleOutcome
import com.cs407.afinal.sleep.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepCalculatorScreen(
    modifier: Modifier = Modifier,
    viewModel: SleepViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var hasNotificationPermission by remember {
        mutableStateOf(isNotificationPermissionGranted(context))
    }
    val notificationsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotificationPermission = granted }

    var showTimePicker by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedBedTimeOption by remember { mutableStateOf<BedTimeOption>(BedTimeOption.SleepNow) }
    var customBedTime by remember { mutableStateOf<LocalTime?>(null) }
    var suggestions by remember { mutableStateOf<List<SleepSuggestion>>(emptyList()) }
    val selectedRecurringDays = remember { mutableStateListOf<Int>() }

    val primaryAlarm = uiState.alarms.firstOrNull { !it.isFollowUp }
    val followUpAlarms = uiState.alarms.filter { it.parentAlarmId == primaryAlarm?.id }
    var showAddFollowUpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            delay(60000L)
        }
    }

    LaunchedEffect(selectedBedTimeOption, customBedTime, currentTime) {
        val bedTime = when (selectedBedTimeOption) {
            BedTimeOption.SleepNow -> ZonedDateTime.now()
            BedTimeOption.Custom -> customBedTime?.let { SleepCalculator.normalizeTargetDateTime(SleepMode.BED_TIME, it) }
        }
        bedTime?.let {
            suggestions = SleepCalculator.wakeTimeSuggestions(it)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFFE8EAF6)).padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Set Alarm", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                    PermissionWarningCard { notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }

                if (uiState.currentUserEmail != null) {
                    Text("☁️ Synced to ${uiState.currentUserEmail}", fontSize = 12.sp, color = Color(0xFF5C6BC0))
                }

                if (primaryAlarm == null) {
                    AutoAlarmStatusCard(status = uiState.autoAlarmStatus)
                    SetAlarmCard(
                        currentTime = currentTime,
                        selectedOption = selectedBedTimeOption,
                        customBedTime = customBedTime,
                        suggestions = suggestions,
                        selectedRecurringDays = selectedRecurringDays,
                        onOptionSelected = { selectedBedTimeOption = it },
                        onSetCustomTime = { showTimePicker = true },
                        onQuickTimeSelected = { minutes ->
                            customBedTime = LocalTime.now().plusMinutes(minutes.toLong())
                            selectedBedTimeOption = BedTimeOption.Custom
                        },
                        onRecurringDayToggled = { day ->
                            if (selectedRecurringDays.contains(day)) selectedRecurringDays.remove(day)
                            else selectedRecurringDays.add(day)
                        },
                        onScheduleAlarm = { suggestion ->
                            val bedTimeMillis = when (selectedBedTimeOption) {
                                BedTimeOption.SleepNow -> System.currentTimeMillis()
                                BedTimeOption.Custom -> customBedTime?.let {
                                    SleepCalculator.normalizeTargetDateTime(SleepMode.BED_TIME, it).toInstant().toEpochMilli()
                                }
                            }
                            when (val result = viewModel.tryScheduleAlarm(
                                triggerAtMillis = suggestion.displayMillis,
                                label = "Wake up",
                                gentleWake = true,
                                cycles = suggestion.cycles,
                                plannedBedTimeMillis = bedTimeMillis,
                                recurringDays = selectedRecurringDays.toList()
                            )) {
                                AlarmScheduleOutcome.MissingExactAlarmPermission -> promptExactAlarmPermission(context)
                                is AlarmScheduleOutcome.Error -> coroutineScope.launch { snackbarHostState.showSnackbar(result.reason) }
                                else -> {
                                    selectedBedTimeOption = BedTimeOption.SleepNow
                                    customBedTime = null
                                    selectedRecurringDays.clear()
                                }
                            }
                        }
                    )
                } else {
                    TimeUntilAlarmCard(triggerAtMillis = primaryAlarm.triggerAtMillis)
                    ActiveAlarmCard(
                        alarm = primaryAlarm,
                        followUpAlarms = followUpAlarms,
                        onToggle = { enabled ->
                            when (val result = viewModel.toggleAlarm(primaryAlarm, enabled)) {
                                AlarmScheduleOutcome.MissingExactAlarmPermission -> promptExactAlarmPermission(context)
                                is AlarmScheduleOutcome.Error -> coroutineScope.launch { snackbarHostState.showSnackbar(result.reason) }
                                else -> Unit
                            }
                        },
                        onDelete = { viewModel.deleteAlarm(primaryAlarm.id) },
                        onAddFollowUp = { showAddFollowUpDialog = true },
                        onDeleteFollowUp = { followUpId -> viewModel.deleteAlarm(followUpId) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showTimePicker) {
        TimeSelectionDialog(
            initialTime = customBedTime ?: LocalTime.now(),
            onDismiss = { showTimePicker = false },
            onConfirm = {
                customBedTime = it
                showTimePicker = false
            }
        )
    }

    if (showAddFollowUpDialog && primaryAlarm != null) {
        AddFollowUpDialog(
            onDismiss = { showAddFollowUpDialog = false },
            onConfirm = { minutes ->
                viewModel.tryScheduleAlarm(
                    triggerAtMillis = primaryAlarm.triggerAtMillis + (minutes * 60 * 1000),
                    label = "Reminder",
                    gentleWake = false,
                    cycles = null,
                    plannedBedTimeMillis = null,
                    parentAlarmId = primaryAlarm.id
                )
                showAddFollowUpDialog = false
            }
        )
    }
}

@Composable
private fun AutoAlarmStatusCard(status: AutoAlarmStatus) {
    AnimatedVisibility(visible = status.isEnabled, enter = fadeIn(), exit = fadeOut()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF1565C0))
                    Text(text = "Auto Alarm", fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                }
                AnimatedContent(targetState = status.wasJustReset, transitionSpec = { fadeIn() togetherWith fadeOut() }) {
                    if (it) {
                        Text("Timer Reset!", color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                    } else {
                        if (status.isMonitoring && status.timeUntilTrigger > 0) {
                            val minutes = status.timeUntilTrigger / 1000 / 60
                            val seconds = (status.timeUntilTrigger / 1000) % 60
                            Text(String.format("in %02d:%02d", minutes, seconds), color = Color.Gray)
                        } else {
                            Text("Monitoring...", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// Other composables remain the same, so they are omitted for brevity.

private enum class BedTimeOption { SleepNow, Custom }

@Composable
private fun SetAlarmCard(
    currentTime: Calendar,
    selectedOption: BedTimeOption,
    customBedTime: LocalTime?,
    suggestions: List<SleepSuggestion>,
    selectedRecurringDays: List<Int>,
    onOptionSelected: (BedTimeOption) -> Unit,
    onSetCustomTime: () -> Unit,
    onQuickTimeSelected: (Int) -> Unit,
    onRecurringDayToggled: (Int) -> Unit,
    onScheduleAlarm: (SleepSuggestion) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD6DBFA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "When are you sleeping?",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF3F51B5)
            )
            
            QuickActionChips(
                onQuickSelect = { minutes ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onQuickTimeSelected(minutes)
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                val sdf = SimpleDateFormat("hh:mm a", Locale.US)
                val displayTime = when (selectedOption) {
                    BedTimeOption.SleepNow -> sdf.format(currentTime.time)
                    BedTimeOption.Custom -> customBedTime?.let { formatLocalTime(it) } ?: "Select time"
                }
                Text(
                    text = displayTime,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = selectedOption == BedTimeOption.SleepNow,
                    onClick = { onOptionSelected(BedTimeOption.SleepNow) },
                    label = { Text("Sleep Now", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF5C6BC0),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = selectedOption == BedTimeOption.Custom,
                    onClick = {
                        onOptionSelected(BedTimeOption.Custom)
                        if (customBedTime == null) onSetCustomTime()
                    },
                    label = { Text("Custom Time", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF5C6BC0),
                        selectedLabelColor = Color.White
                    )
                )
            }

            if (selectedOption == BedTimeOption.Custom) {
                TextButton(onClick = onSetCustomTime) {
                    Text("Change Time", fontSize = 12.sp, color = Color(0xFF5C6BC0))
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Repeat On (optional)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF3F51B5)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val daysOfWeek = listOf(
                        1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S"
                    )
                    daysOfWeek.forEach { (dayNum, dayLabel) ->
                        DayChip(
                            label = dayLabel,
                            isSelected = selectedRecurringDays.contains(dayNum),
                            onClick = { onRecurringDayToggled(dayNum) }
                        )
                    }
                }
                if (selectedRecurringDays.isNotEmpty()) {
                    Text(
                        text = "✓ Alarm will repeat weekly",
                        fontSize = 11.sp,
                        color = Color(0xFF388E3C),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Text(
                text = "Optimal Wake Times (90-min REM Cycles)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF3F51B5),
                modifier = Modifier.padding(top = 8.dp)
            )
            
            AnimatedVisibility(
                visible = suggestions.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                SleepTipCard(cycles = 6)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    SuggestionOption(
                        suggestion = suggestion,
                        onClick = { onScheduleAlarm(suggestion) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveAlarmCard(
    alarm: AlarmItem,
    followUpAlarms: List<AlarmItem>,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onAddFollowUp: () -> Unit,
    onDeleteFollowUp: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFC5E1A5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF33691E),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Alarm Set",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF33691E)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formatEpochMillisTime(alarm.triggerAtMillis),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = formatDayLabel(alarm.triggerAtMillis),
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            alarm.targetCycles?.let {
                Text(
                    text = "$it sleep cycles",
                    fontSize = 12.sp,
                    color = Color(0xFF558B2F)
                )
            }

            if (followUpAlarms.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    followUpAlarms.forEach { followUp ->
                        FollowUpAlarmItem(
                            followUp = followUp,
                            onDelete = { onDeleteFollowUp(followUp.id) }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAddFollowUp()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF33691E)
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("Add Reminder", fontSize = 13.sp)
            }

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF558B2F)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Delete Alarm", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun FollowUpAlarmItem(
    followUp: AlarmItem,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = formatEpochMillisTime(followUp.triggerAtMillis),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    followUp.label?.let{
                        Text(
                            text = it,
                            fontSize = 11.sp,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            }) {
                Text("✕", fontSize = 20.sp, color = Color(0xFFE65100))
            }
        }
    }
}

@Composable
private fun SuggestionOption(
    suggestion: SleepSuggestion,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val sleepQuality = getSleepQuality(suggestion.cycles)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = sleepQuality.icon,
                    contentDescription = null,
                    tint = sleepQuality.color,
                    modifier = Modifier.size(28.dp)
                )
                
                Column {
                    Text(
                        text = formatEpochMillisTime(suggestion.displayMillis),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${suggestion.cycles} cycles • ${suggestion.note}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .background(sleepQuality.color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = sleepQuality.label,
                    fontSize = 10.sp,
                    color = sleepQuality.color,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PermissionWarningCard(onGrant: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("⚠️ Enable Notifications", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Grant permission so alarms ring reliably", fontSize = 12.sp)
            Button(
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) {
                Text("Grant Permission", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AddFollowUpDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var minutesState by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("5")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Follow-up Alarm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Set a reminder alarm after your main alarm")
                OutlinedTextField(
                    value = minutesState,
                    onValueChange = { minutesState = it },
                    label = { Text("Minutes after") },
                    placeholder = { Text("5") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(minutesState.text.toIntOrNull() ?: 5) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSelectionDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val timePickerState = rememberTimePickerState(initialTime.hour, initialTime.minute, false)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time") },
        text = { TimePicker(state = timePickerState) },
        confirmButton = {
            Button(onClick = { onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute)) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun promptExactAlarmPermission(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}

private fun isNotificationPermissionGranted(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun formatLocalTime(localTime: LocalTime): String {
    val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return formatter.format(Date.from(localTime.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant()))
}

private fun formatEpochMillisTime(epochMillis: Long): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(epochMillis))

private fun formatDayLabel(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}

@Composable
private fun TimeUntilAlarmCard(triggerAtMillis: Long) {
    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }
    
    val timeUntil = triggerAtMillis - currentTimeMillis
    val hours = (timeUntil / (1000 * 60 * 60)).toInt()
    val minutes = ((timeUntil / (1000 * 60)) % 60).toInt()
    
    AnimatedVisibility(
        visible = timeUntil > 0,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.WatchLater, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
                Text("Alarm in", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
                Text("${hours}h ${minutes}m", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
                
                val progress = 1f - (timeUntil.toFloat() / (8 * 60 * 60 * 1000))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Color(0xFF4CAF50),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
private fun QuickActionChips(onQuickSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("⚡ Quick Sleep In:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3F51B5))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("15 min" to 15, "30 min" to 30, "1 hour" to 60).forEach { (label, minutes) ->
                AssistChip(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onQuickSelect(minutes) },
                    label = { Text(label, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f),
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.White, labelColor = Color(0xFF5C6BC0), leadingIconContentColor = Color(0xFF5C6BC0))
                )
            }
        }
    }
}

@Composable
private fun SleepTipCard(cycles: Int) {
    val tips = mapOf(
        1 to "💡 1.5 hours gives you one complete sleep cycle",
        2 to "💡 3 hours covers light sleep phases",
        3 to "💡 4.5 hours - minimum for feeling somewhat rested",
        4 to "💡 6 hours - good for a quick recovery",
        5 to "💡 7.5 hours - great sleep duration!",
        6 to "⭐ 9 hours - optimal sleep for full recovery!"
    )
    
    AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.TipsAndUpdates, contentDescription = null, tint = Color(0xFFF57F17), modifier = Modifier.size(20.dp))
                Text(tips[cycles] ?: "💡 Sleep in 90-minute cycles for optimal rest", fontSize = 12.sp, color = Color(0xFF827717), lineHeight = 16.sp)
            }
        }
    }
}

private data class SleepQuality(val label: String, val color: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private fun getSleepQuality(cycles: Int): SleepQuality {
    return when {
        cycles >= 6 -> SleepQuality("Optimal", Color(0xFF2E7D32), Icons.Default.Star)
        cycles >= 5 -> SleepQuality("Great", Color(0xFF388E3C), Icons.Default.CheckCircle)
        cycles >= 4 -> SleepQuality("Good", Color(0xFF689F38), Icons.Default.CheckCircle)
        cycles >= 3 -> SleepQuality("Fair", Color(0xFFF57C00), Icons.Default.Bedtime)
        else -> SleepQuality("Short", Color(0xFFD32F2F), Icons.Default.Schedule)
    }
}

@Composable
private fun DayChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isSelected) Color(0xFF5C6BC0) else Color.White).border(1.dp, if (isSelected) Color(0xFF5C6BC0) else Color.Gray.copy(alpha = 0.3f), CircleShape).clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color.White else Color.Gray)
    }
}