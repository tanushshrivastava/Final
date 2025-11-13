package com.cs407.afinal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs407.afinal.model.AlarmItem
import com.cs407.afinal.model.SleepMode
import com.cs407.afinal.model.SleepSuggestion
import com.cs407.afinal.util.SleepCycleCalculator
import com.cs407.afinal.viewmodel.AlarmScheduleOutcome
import com.cs407.afinal.viewmodel.SleepViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    // Track primary alarm and follow-up alarms
    val primaryAlarm = uiState.alarms.firstOrNull()
    val followUpAlarms = remember { mutableStateListOf<FollowUpAlarm>() }
    var showAddFollowUpDialog by remember { mutableStateOf(false) }

    // Update current time every minute
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            delay(60000L)
        }
    }

    // Calculate suggestions based on selected option (1-6 cycles, no buffer)
    LaunchedEffect(selectedBedTimeOption, customBedTime, currentTime) {
        val bedTime = when (selectedBedTimeOption) {
            BedTimeOption.SleepNow -> ZonedDateTime.now()
            BedTimeOption.Custom -> customBedTime?.let {
                SleepCycleCalculator.normalizeTargetDateTime(SleepMode.BED_TIME, it)
            }
        }
        bedTime?.let { bed ->
            suggestions = (1..6).map { cycles ->
                val totalMinutes = cycles * 90 // Removed buffer for now
                val suggestedTime = bed.plusMinutes(totalMinutes.toLong())
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60
                val durationText = if (mins == 0) "${hours}h sleep" else "${hours}h ${mins}m sleep"

                SleepSuggestion(
                    id = "wake-$cycles",
                    displayMillis = suggestedTime.toInstant().toEpochMilli(),
                    cycles = cycles,
                    type = com.cs407.afinal.model.SleepSuggestionType.WAKE_UP,
                    note = durationText,
                    referenceMillis = bed.toInstant().toEpochMilli()
                )
            }
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE8EAF6))
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Set Alarm",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Permission Warning at top
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                    PermissionWarningCard(
                        onGrant = { notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                }

                // Sync Status - Compact
                if (uiState.currentUserEmail != null) {
                    Text(
                        text = "☁️ Synced to ${uiState.currentUserEmail}",
                        fontSize = 12.sp,
                        color = Color(0xFF5C6BC0)
                    )
                }

                // Main Card - Either set alarm or show active alarm
                if (primaryAlarm == null) {
                    SetAlarmCard(
                        currentTime = currentTime,
                        selectedOption = selectedBedTimeOption,
                        customBedTime = customBedTime,
                        suggestions = suggestions,
                        onOptionSelected = { selectedBedTimeOption = it },
                        onSetCustomTime = { showTimePicker = true },
                        onScheduleAlarm = { suggestion ->
                            val bedTimeMillis = when (selectedBedTimeOption) {
                                BedTimeOption.SleepNow -> System.currentTimeMillis()
                                BedTimeOption.Custom -> customBedTime?.let {
                                    SleepCycleCalculator.normalizeTargetDateTime(SleepMode.BED_TIME, it)
                                        .toInstant().toEpochMilli()
                                }
                            }

                            when (val result = viewModel.tryScheduleAlarm(
                                triggerAtMillis = suggestion.displayMillis,
                                label = "Wake up",
                                gentleWake = true,
                                cycles = suggestion.cycles,
                                plannedBedTimeMillis = bedTimeMillis
                            )) {
                                AlarmScheduleOutcome.MissingExactAlarmPermission -> promptExactAlarmPermission(context)
                                is AlarmScheduleOutcome.Error -> {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(result.reason)
                                    }
                                }
                                else -> {
                                    selectedBedTimeOption = BedTimeOption.SleepNow
                                    customBedTime = null
                                }
                            }
                        }
                    )
                } else {
                    ActiveAlarmCard(
                        alarm = primaryAlarm,
                        followUpAlarms = followUpAlarms,
                        onToggle = { enabled ->
                            when (val result = viewModel.toggleAlarm(primaryAlarm, enabled)) {
                                AlarmScheduleOutcome.MissingExactAlarmPermission -> promptExactAlarmPermission(context)
                                is AlarmScheduleOutcome.Error -> {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(result.reason)
                                    }
                                }
                                else -> Unit
                            }
                        },
                        onDelete = {
                            viewModel.deleteAlarm(primaryAlarm.id)
                            followUpAlarms.clear()
                        },
                        onAddFollowUp = { showAddFollowUpDialog = true },
                        onDeleteFollowUp = { followUp ->
                            followUpAlarms.remove(followUp)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
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

    if (showAddFollowUpDialog) {
        AddFollowUpDialog(
            onDismiss = { showAddFollowUpDialog = false },
            onConfirm = { minutes ->
                primaryAlarm?.let { alarm ->
                    followUpAlarms.add(
                        FollowUpAlarm(
                            id = followUpAlarms.size,
                            minutesAfter = minutes,
                            triggerAtMillis = alarm.triggerAtMillis + (minutes * 60 * 1000)
                        )
                    )
                }
                showAddFollowUpDialog = false
            }
        )
    }
}

@Composable
private fun SetAlarmCard(
    currentTime: Calendar,
    selectedOption: BedTimeOption,
    customBedTime: LocalTime?,
    suggestions: List<SleepSuggestion>,
    onOptionSelected: (BedTimeOption) -> Unit,
    onSetCustomTime: () -> Unit,
    onScheduleAlarm: (SleepSuggestion) -> Unit
) {
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

            // Time Display
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

            // Bed Time Options
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

            Text(
                text = "Optimal Wake Times (90-min REM Cycles)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF3F51B5),
                modifier = Modifier.padding(top = 8.dp)
            )

            // Scrollable Suggestions
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
    followUpAlarms: List<FollowUpAlarm>,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onAddFollowUp: () -> Unit,
    onDeleteFollowUp: (FollowUpAlarm) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFC5E1A5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "✓ Alarm Set",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF33691E)
            )

            // Large alarm time display
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

            // Follow-up alarms section
            if (followUpAlarms.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    followUpAlarms.forEach { followUp ->
                        FollowUpAlarmItem(
                            followUp = followUp,
                            onDelete = { onDeleteFollowUp(followUp) }
                        )
                    }
                }
            }

            // Add follow-up button
            OutlinedButton(
                onClick = onAddFollowUp,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF33691E)
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("Add Reminder", fontSize = 13.sp)
            }

            // Delete button
            Button(
                onClick = onDelete,
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
    followUp: FollowUpAlarm,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Column {
                Text(
                    text = formatEpochMillisTime(followUp.triggerAtMillis),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "+${followUp.minutesAfter} min reminder",
                    fontSize = 11.sp,
                    color = Color(0xFFE65100)
                )
            }
            IconButton(onClick = onDelete) {
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                    text = formatEpochMillisTime(suggestion.displayMillis),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "${suggestion.cycles} cycles • ${suggestion.note}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            if (suggestion.cycles == 6) {
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
            Text(
                text = "⚠️ Enable Notifications",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Grant permission so alarms ring reliably",
                fontSize = 12.sp
            )
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
    var minutesState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue("5"))
    }

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
            Button(onClick = {
                val minutes = minutesState.text.toIntOrNull() ?: 5
                onConfirm(minutes)
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
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
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time") },
        text = { TimePicker(state = timePickerState) },
        confirmButton = {
            Button(onClick = {
                onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
            }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun promptExactAlarmPermission(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}

private fun isNotificationPermissionGranted(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun formatLocalTime(localTime: LocalTime): String {
    val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val date = Date.from(
        localTime.atDate(LocalDate.now())
            .atZone(ZoneId.systemDefault())
            .toInstant()
    )
    return formatter.format(date)
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

private fun formatDurationMinutes(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (mins == 0) "${hours}h sleep" else "${hours}h ${mins}m sleep"
}

private enum class BedTimeOption {
    SleepNow,
    Custom
}

private data class FollowUpAlarm(
    val id: Int,
    val minutesAfter: Int,
    val triggerAtMillis: Long
)

private data class CreateAlarmDialogState(
    val triggerAtMillis: Long,
    val cycles: Int?,
    val plannedBedTimeMillis: Long?
)