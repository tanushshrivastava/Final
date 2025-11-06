package com.cs407.afinal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.cs407.afinal.model.AlarmItem
import com.cs407.afinal.model.SleepMode
import com.cs407.afinal.model.SleepSuggestion
import com.cs407.afinal.model.SleepSuggestionType
import com.cs407.afinal.util.SleepCycleCalculator
import com.cs407.afinal.viewmodel.AlarmScheduleOutcome
import com.cs407.afinal.viewmodel.SleepViewModel
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    ) { granted ->
        hasNotificationPermission = granted
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var createDialogState by remember { mutableStateOf<CreateAlarmDialogState?>(null) }
    var editDialogAlarm by remember { mutableStateOf<AlarmItem?>(null) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Smart Sleep Cycle Alarm") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SyncStatusCard(email = uiState.currentUserEmail)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                item {
                    NotificationPermissionCard(
                        onRequestPermission = {
                            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                }
            }

            item {
                SleepModeSection(
                    mode = uiState.mode,
                    onModeSelected = { viewModel.onModeChanged(it) }
                )
            }
            item {
                val normalizedDateTime = SleepCycleCalculator
                    .normalizeTargetDateTime(uiState.mode, uiState.targetTime)
                TargetTimeCard(
                    targetTime = uiState.targetTime,
                    mode = uiState.mode,
                    relativeLabel = formatRelativeDayLabel(normalizedDateTime.toInstant().toEpochMilli()),
                    onClick = { showTimePicker = true }
                )
            }
            if (uiState.mode == SleepMode.WAKE_TIME) {
                item {
                    val normalizedWakeMillis = SleepCycleCalculator
                        .normalizeTargetDateTime(SleepMode.WAKE_TIME, uiState.targetTime)
                        .toInstant().toEpochMilli()
                    Button(
                        onClick = {
                            val suggestedBedtime = uiState.suggestions.firstOrNull()?.displayMillis
                            createDialogState = CreateAlarmDialogState(
                                triggerAtMillis = normalizedWakeMillis,
                                cycles = uiState.suggestions.firstOrNull()?.cycles,
                                plannedBedTimeMillis = suggestedBedtime
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set alarm for ${formatEpochMillisWithDay(normalizedWakeMillis)}")
                    }
                }
            }
            item {
                FilledTonalButton(
                    onClick = { viewModel.onSleepNowSelected() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sleep Now — suggest wake-up times")
                }
            }
            if (uiState.suggestions.isNotEmpty()) {
                item {
                    SuggestionsSection(
                        mode = uiState.mode,
                        suggestions = uiState.suggestions,
                        onScheduleRequested = { suggestion ->
                            createDialogState = CreateAlarmDialogState(
                                triggerAtMillis = suggestion.displayMillis,
                                cycles = suggestion.cycles,
                                plannedBedTimeMillis = suggestion.referenceMillis
                                    ?.takeIf { suggestion.type == SleepSuggestionType.WAKE_UP }
                            )
                        }
                    )
                }
            }
            item {
                AlarmListSection(
                    alarms = uiState.alarms,
                    onToggle = { alarm, enabled ->
                        when (val result = viewModel.toggleAlarm(alarm, enabled)) {
                            AlarmScheduleOutcome.MissingExactAlarmPermission -> promptExactAlarmPermission(context)
                            is AlarmScheduleOutcome.Error -> {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(result.reason)
                                }
                            }
                            else -> Unit
                        }
                    },
                    onEdit = { editDialogAlarm = it },
                    onDelete = { viewModel.deleteAlarm(it.id) }
                )
            }
            if (uiState.history.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent sleep history",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                items(uiState.history) { entry ->
                    HistoryCard(
                        label = entry.label,
                        wakeMillis = entry.plannedWakeMillis,
                        dismissedMillis = entry.actualDismissedMillis,
                        bedMillis = entry.plannedBedTimeMillis
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        TimeSelectionDialog(
            initialTime = uiState.targetTime,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                viewModel.onTargetTimeChanged(it)
                showTimePicker = false
            }
        )
    }

    createDialogState?.let { state ->
        AlarmConfirmationDialog(
            triggerAtMillis = state.triggerAtMillis,
            cycles = state.cycles,
            plannedBedTimeMillis = state.plannedBedTimeMillis,
            onDismiss = { createDialogState = null },
            onConfirm = { label, gentle ->
                when (val result = viewModel.tryScheduleAlarm(
                    triggerAtMillis = state.triggerAtMillis,
                    label = label,
                    gentleWake = gentle,
                    cycles = state.cycles,
                    plannedBedTimeMillis = state.plannedBedTimeMillis
                )) {
                    AlarmScheduleOutcome.MissingExactAlarmPermission -> {
                        promptExactAlarmPermission(context)
                    }
                    is AlarmScheduleOutcome.Error -> {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(result.reason)
                        }
                    }
                    else -> Unit
                }
                createDialogState = null
            }
        )
    }

    editDialogAlarm?.let { alarm ->
        EditAlarmDialog(
            alarm = alarm,
            onDismiss = { editDialogAlarm = null },
            onConfirm = { newMillis, label, gentle ->
                when (val result = viewModel.updateAlarm(
                    alarmId = alarm.id,
                    triggerAtMillis = newMillis,
                    label = label,
                    gentleWake = gentle,
                    plannedBedTimeMillis = alarm.plannedBedTimeMillis
                )) {
                    AlarmScheduleOutcome.MissingExactAlarmPermission -> {
                        promptExactAlarmPermission(context)
                    }
                    is AlarmScheduleOutcome.Error -> {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(result.reason)
                        }
                    }
                    else -> Unit
                }
                editDialogAlarm = null
            }
        )
    }
}

@Composable
private fun SleepModeSection(
    mode: SleepMode,
    onModeSelected: (SleepMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Plan your sleep",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = mode == SleepMode.WAKE_TIME,
                    onClick = { onModeSelected(SleepMode.WAKE_TIME) },
                    label = { Text("Wake-up time") },
                    leadingIcon = {
                        Icon(Icons.Outlined.LightMode, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
                FilterChip(
                    selected = mode == SleepMode.BED_TIME,
                    onClick = { onModeSelected(SleepMode.BED_TIME) },
                    label = { Text("Bedtime") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Hotel, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }
            Text(
                text = when (mode) {
                    SleepMode.WAKE_TIME -> "Choose when you need to wake up. We’ll suggest ideal bedtimes so you complete full sleep cycles."
                    SleepMode.BED_TIME -> "Pick your bedtime (or tap “Sleep Now”). We’ll suggest 2–3 gentle wake times aligned with full cycles."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun TargetTimeCard(
    targetTime: LocalTime,
    mode: SleepMode,
    relativeLabel: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Surface(onClick = onClick) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (mode == SleepMode.WAKE_TIME) "Desired wake time" else "Bedtime target",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = formatLocalTime(targetTime),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = relativeLabel,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "Tap to adjust",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SuggestionsSection(
    mode: SleepMode,
    suggestions: List<SleepSuggestion>,
    onScheduleRequested: (SleepSuggestion) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (mode == SleepMode.WAKE_TIME) "Recommended bedtimes" else "Optimal wake-up alarms",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        suggestions.forEach { suggestion ->
            SuggestionCard(
                suggestion = suggestion,
                mode = mode,
                onSchedule = onScheduleRequested
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: SleepSuggestion,
    mode: SleepMode,
    onSchedule: (SleepSuggestion) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = formatEpochMillisWithDay(suggestion.displayMillis),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = when (suggestion.type) {
                    SleepSuggestionType.BEDTIME -> "Head to bed around this time to complete ${suggestion.cycles} sleep cycles (${suggestion.note})."
                    SleepSuggestionType.WAKE_UP -> "Wake up gently after ${suggestion.cycles} full cycles (${suggestion.note})."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (mode == SleepMode.BED_TIME && suggestion.type == SleepSuggestionType.WAKE_UP) {
                Button(onClick = { onSchedule(suggestion) }) {
                    Text("Set alarm for ${formatEpochMillisWithDay(suggestion.displayMillis)}")
                }
            }
            if (mode == SleepMode.WAKE_TIME && suggestion.type == SleepSuggestionType.BEDTIME) {
                Text(
                    text = "Around ${suggestion.note} of sleep.",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun AlarmListSection(
    alarms: List<AlarmItem>,
    onToggle: (AlarmItem, Boolean) -> Unit,
    onEdit: (AlarmItem) -> Unit,
    onDelete: (AlarmItem) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Your alarms",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        if (alarms.isEmpty()) {
            Text(
                text = "No alarms yet. Set one from the suggestions above.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            alarms.forEach { alarm ->
                AlarmCard(
                    alarm = alarm,
                    onToggle = onToggle,
                    onEdit = onEdit,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: AlarmItem,
    onToggle: (AlarmItem, Boolean) -> Unit,
    onEdit: (AlarmItem) -> Unit,
    onDelete: (AlarmItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = formatEpochMillisWithDay(alarm.triggerAtMillis),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                androidx.compose.material3.Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = { onToggle(alarm, it) }
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = { onEdit(alarm) }) {
                    Text("Edit")
                }
                TextButton(onClick = { onDelete(alarm) }) {
                    Text("Delete")
                }
            }
            alarm.targetCycles?.let {
                Text(
                    text = "$it cycles planned • ${alarmNote(alarm)}",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(
    label: String,
    wakeMillis: Long,
    dismissedMillis: Long,
    bedMillis: Long?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                text = "Planned wake: ${formatEpochMillisWithDay(wakeMillis)}",
                style = MaterialTheme.typography.bodyMedium
            )
            bedMillis?.let {
                Text(
                    text = "Planned bed: ${formatEpochMillisWithDay(it)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = "Dismissed at: ${formatEpochMillisWithDay(dismissedMillis)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AlarmConfirmationDialog(
    triggerAtMillis: Long,
    cycles: Int?,
    plannedBedTimeMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit
) {
    var labelState by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var gentleWake by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set alarm for ${formatEpochMillisWithDay(triggerAtMillis)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Label your alarm and choose gentle wake to ramp up the volume softly.")
                OutlinedTextField(
                    value = labelState,
                    onValueChange = { labelState = it },
                    label = { Text("Alarm label") },
                    placeholder = { Text("Gym Morning, Exam Day…") }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Gentle wake")
                    Checkbox(
                        checked = gentleWake,
                        onCheckedChange = { gentleWake = it }
                    )
                }
                cycles?.let {
                    Text(
                        text = "$it sleep cycles • ${SleepCycleCalculatorHelper.durationForCycles(it)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                plannedBedTimeMillis?.let {
                    Text(
                        text = "Suggested bedtime: ${formatEpochMillisWithDay(it)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(labelState.text, gentleWake) }) {
                Text("Save alarm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NotificationPermissionCard(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Enable gentle alarm notifications",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Allow notifications so alarms can ring reliably even if the app is closed.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onRequestPermission) {
                Text("Grant notification permission")
            }
        }
    }
}

@Composable
private fun SyncStatusCard(email: String?) {
    val signedIn = !email.isNullOrBlank()
    val containerColor = if (signedIn) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (signedIn) "Cloud sync enabled" else "Local-only mode",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = if (signedIn) {
                    "Alarms and history sync to $email. Dismiss alarms to save them to your cloud history."
                } else {
                    "Not signed in. Alarms stay on this device. Visit the Account tab to sign in and back up your sleep data."
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun EditAlarmDialog(
    alarm: AlarmItem,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, Boolean) -> Unit
) {
    var labelState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(alarm.label))
    }
    var gentleWake by rememberSaveable { mutableStateOf(alarm.gentleWake) }
    var timeMillis by rememberSaveable { mutableStateOf(alarm.triggerAtMillis) }
    var showTimePicker by remember { mutableStateOf(false) }

    if (showTimePicker) {
        TimeSelectionDialog(
            initialTime = Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()).toLocalTime(),
            onDismiss = { showTimePicker = false },
            onConfirm = {
                timeMillis = LocalTimeToMillis(it, timeMillis)
                showTimePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit alarm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Adjust the time or label, then choose whether gentle wake should stay on.")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Alarm time", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = formatEpochMillisWithDay(timeMillis),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    TextButton(onClick = { showTimePicker = true }) {
                        Text("Change")
                    }
                }
                OutlinedTextField(
                    value = labelState,
                    onValueChange = { labelState = it },
                    label = { Text("Alarm label") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gentle wake")
                    Checkbox(
                        checked = gentleWake,
                        onCheckedChange = { gentleWake = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(timeMillis, labelState.text, gentleWake) }) {
                Text("Save changes")
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
        title = { Text("Select time") },
        text = {
            TimePicker(
                state = timePickerState
            )
        },
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

private fun formatEpochMillisWithDay(epochMillis: Long): String {
    val timePart = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(epochMillis))
    val dayLabel = dayDescriptor(epochMillis)
    return "$timePart • $dayLabel"
}

private fun formatRelativeDayLabel(epochMillis: Long): String {
    val datePart = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(epochMillis))
    val dayLabel = dayDescriptor(epochMillis)
    return "$dayLabel • $datePart"
}

private fun dayDescriptor(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}

private fun alarmNote(alarm: AlarmItem): String {
    val created = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(alarm.createdAtMillis))
    return "set $created"
}

private object SleepCycleCalculatorHelper {
    fun durationForCycles(cycles: Int): String {
        val totalMinutes = cycles * com.cs407.afinal.util.SleepCycleCalculator.CYCLE_MINUTES
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (minutes == 0) "${hours}h" else "${hours}h ${minutes}m"
    }
}

private fun LocalTimeToMillis(localTime: LocalTime, referenceMillis: Long): Long {
    val zone = ZoneId.systemDefault()
    val referenceDateTime = Instant.ofEpochMilli(referenceMillis).atZone(zone)
    var candidate = localTime.atDate(referenceDateTime.toLocalDate()).atZone(zone)
    if (candidate.toInstant().toEpochMilli() <= System.currentTimeMillis()) {
        candidate = candidate.plusDays(1)
    }
    return candidate.toInstant().toEpochMilli()
}

private data class CreateAlarmDialogState(
    val triggerAtMillis: Long,
    val cycles: Int?,
    val plannedBedTimeMillis: Long?
)
