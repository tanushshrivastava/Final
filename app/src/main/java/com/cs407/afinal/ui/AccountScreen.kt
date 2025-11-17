package com.cs407.afinal.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs407.afinal.viewmodel.AccountUiState
import com.cs407.afinal.viewmodel.AccountViewModel
import com.cs407.afinal.viewmodel.AuthMode
import java.util.Locale

@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage = uiState.errorMessage
    val successMessage = uiState.successMessage
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage, successMessage) {
        when {
            errorMessage != null -> {
                snackbarHostState.showSnackbar(errorMessage)
                viewModel.consumeMessages()
            }

            successMessage != null -> {
                snackbarHostState.showSnackbar(successMessage)
                viewModel.consumeMessages()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        val currentUserEmail = uiState.currentUserEmail
        if (!currentUserEmail.isNullOrBlank()) {
            SignedInContent(
                email = currentUserEmail,
                onSignOut = viewModel::signOut,
                viewModel = viewModel,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        } else {
            AuthForm(
                uiState = uiState,
                onEmailChange = viewModel::onEmailChanged,
                onPasswordChange = viewModel::onPasswordChanged,
                onConfirmPasswordChange = viewModel::onConfirmPasswordChanged,
                onSubmit = viewModel::submit,
                onToggleMode = viewModel::toggleMode,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        }
    }
}

@Composable
private fun AuthForm(
    uiState: AccountUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (uiState.mode == AuthMode.SIGN_IN) "Sign in to sync your sleep data" else "Create your account",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Use email + password to back up alarms and history across devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            if (uiState.mode == AuthMode.SIGN_UP) {
                OutlinedTextField(
                    value = uiState.confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Button(
                onClick = onSubmit,
                enabled = !uiState.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .size(18.dp)
                    )
                } else {
                    Text(if (uiState.mode == AuthMode.SIGN_IN) "Sign in" else "Create account")
                }
            }
            TextButton(onClick = onToggleMode) {
                Text(
                    if (uiState.mode == AuthMode.SIGN_IN) "Need an account? Sign up"
                    else "Already have an account? Sign in"
                )
            }
        }
    }
}

@Composable
private fun SignedInContent(
    email: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "You're signed in",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = email,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Future alarms and your sleep history will sync to this account automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Auto Sleep Alarm",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Automatically set an alarm for 6 sleep cycles (9 hours) if your phone hasn't moved after the trigger time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Auto Alarm")
                    Switch(
                        checked = uiState.autoAlarmEnabled,
                        onCheckedChange = { viewModel.setAutoAlarmEnabled(it) }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    viewModel.setAutoAlarmTime(hour, minute)
                                },
                                uiState.autoAlarmHour,
                                uiState.autoAlarmMinute,
                                false
                            ).show()
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trigger Time")
                    Text(
                        text = String.format(
                            Locale.getDefault(),
                            "%02d:%02d %s",
                            if (uiState.autoAlarmHour % 12 == 0) 12 else uiState.autoAlarmHour % 12,
                            uiState.autoAlarmMinute,
                            if (uiState.autoAlarmHour < 12) "AM" else "PM"
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            android.app.AlertDialog.Builder(context)
                                .setTitle("Inactivity Duration")
                                .setItems(arrayOf("1 minute (test)", "5 minutes", "15 minutes", "30 minutes", "1 hour")) { _, which ->
                                    val minutes = when (which) {
                                        0 -> 1
                                        1 -> 5
                                        2 -> 15
                                        3 -> 30
                                        4 -> 60
                                        else -> 15
                                    }
                                    viewModel.setAutoAlarmInactivityMinutes(minutes)
                                }
                                .show()
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Inactivity Duration")
                    Text(
                        text = if (uiState.autoAlarmInactivityMinutes >= 60) 
                            "${uiState.autoAlarmInactivityMinutes / 60}h" 
                        else 
                            "${uiState.autoAlarmInactivityMinutes}m",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (uiState.autoAlarmEnabled) {
                    HorizontalDivider()
                    
                    // Status indicator
                    var statusText by remember { mutableStateOf("Checking status...") }
                    var statusColor by remember { mutableStateOf(Color(0xFF757575)) }
                    
                    LaunchedEffect(Unit) {
                        while (true) {
                            val isServiceRunning = isServiceRunning(context, "com.cs407.afinal.InactivityMonitorService")
                            val (triggerHour, triggerMinute) = viewModel.uiState.value.let { it.autoAlarmHour to it.autoAlarmMinute }
                            val now = java.util.Calendar.getInstance()
                            val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
                            val currentMinute = now.get(java.util.Calendar.MINUTE)
                            val currentTimeInMinutes = currentHour * 60 + currentMinute
                            val triggerTimeInMinutes = triggerHour * 60 + triggerMinute
                            val shouldMonitor = currentTimeInMinutes >= triggerTimeInMinutes || currentTimeInMinutes < 6 * 60
                            
                            statusText = when {
                                !isServiceRunning -> "Service not running"
                                !shouldMonitor -> "Waiting until ${String.format("%02d:%02d", triggerHour, triggerMinute)}"
                                else -> "Monitoring active (${uiState.autoAlarmInactivityMinutes}m threshold)"
                            }
                            
                            statusColor = when {
                                !isServiceRunning -> Color(0xFFD32F2F)
                                !shouldMonitor -> Color(0xFF757575)
                                else -> Color(0xFF4CAF50)
                            }
                            
                            kotlinx.coroutines.delay(2000)
                        }
                    }
                    
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        color = statusColor,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    Button(
                        onClick = {
                            val sleepCycleDurationMs = 90 * 60 * 1000L
                            val wakeUpTime = System.currentTimeMillis() + (6 * sleepCycleDurationMs)
                            android.widget.Toast.makeText(
                                context,
                                "Test: Auto-alarm would set for ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(wakeUpTime)}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Test (Show What Time Would Set)", fontSize = 12.sp)
                    }
                }
            }
        }

        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign out")
        }
    }
}

private fun isServiceRunning(context: android.content.Context, serviceClassName: String): Boolean {
    val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    @Suppress("DEPRECATION")
    for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
        if (serviceClassName == service.service.className) {
            return true
        }
    }
    return false
}
