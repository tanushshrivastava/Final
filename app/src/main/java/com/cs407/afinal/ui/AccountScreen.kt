/**
 * This file contains the composables for the user account screen.
 * It includes UI for both authentication (sign-in/sign-up) and for viewing/managing
 * account settings when the user is signed in.
 * The screen's state is managed by [com.cs407.afinal.viewmodel.AccountViewModel].
 */
package com.cs407.afinal.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/**
 * The main composable for the account screen.
 *
 * This screen serves two primary purposes:
 * 1.  **Authentication:** Allows users to sign in or create a new account.
 * 2.  **Account Management:** Once signed in, it displays user information and provides
 *     options to manage settings, such as the "Auto Sleep Alarm" feature.
 *
 * It observes the [AccountUiState] from the [AccountViewModel] to determine which UI to display
 * and to show feedback messages (errors or successes) in a Snackbar.
 *
 * @param modifier The modifier to be applied to the layout.
 * @param viewModel The [AccountViewModel] instance that holds the business logic and state for this screen.
 */
@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = viewModel()
) {
    // Collect the UI state from the ViewModel in a lifecycle-aware manner.
    // This ensures that the UI always reflects the latest state.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Extract error and success messages for easier access.
    val errorMessage = uiState.errorMessage
    val successMessage = uiState.successMessage

    // Remember a SnackbarHostState to control the display of Snackbars.
    val snackbarHostState = remember { SnackbarHostState() }

    // A LaunchedEffect is used to show a Snackbar when an error or success message is available.
    // It runs a side-effect (showing a snackbar) in a coroutine scope tied to the composable's lifecycle.
    // The key parameters `errorMessage` and `successMessage` ensure this effect reruns if they change.
    LaunchedEffect(errorMessage, successMessage) {
        when {
            // If there's an error message, show it in the snackbar.
            errorMessage != null -> {
                snackbarHostState.showSnackbar(errorMessage)
                // Consume the message in the ViewModel to prevent it from being shown again on recomposition.
                viewModel.consumeMessages()
            }

            // If there's a success message, show it in the snackbar.
            successMessage != null -> {
                snackbarHostState.showSnackbar(successMessage)
                // Consume the message.
                viewModel.consumeMessages()
            }
        }
    }

    // Scaffold provides a standard layout structure (like app bars, floating action buttons, etc.).
    // Here, we use it to host the Snackbar.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        // Check if a user is currently signed in by looking at their email in the UI state.
        val currentUserEmail = uiState.currentUserEmail
        if (!currentUserEmail.isNullOrBlank()) {
            // If the user is signed in, display the content for authenticated users.
            SignedInContent(
                email = currentUserEmail,
                onSignOut = viewModel::signOut, // Pass the signOut function from the ViewModel.
                viewModel = viewModel,
                modifier = Modifier
                    .padding(padding) // Apply padding from the Scaffold.
                    .fillMaxSize()
            )
        } else {
            // If no user is signed in, display the authentication form (sign-in or sign-up).
            AuthForm(
                uiState = uiState,
                onEmailChange = viewModel::onEmailChanged,
                onPasswordChange = viewModel::onPasswordChanged,
                onConfirmPasswordChange = viewModel::onConfirmPasswordChanged,
                onSubmit = viewModel::submit,
                onToggleMode = viewModel::toggleMode, // Function to switch between sign-in/sign-up.
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        }
    }
}

/**
 * A composable that displays the authentication form for signing in or signing up.
 *
 * This form is "stateless" because it doesn't manage its own state. Instead, it receives the
 * current state ([AccountUiState]) and callbacks (e.g., `onEmailChange`) from its parent.
 * This makes the component highly reusable and easy to test.
 *
 * @param uiState The current state of the authentication form (e.g., email, password, loading status).
 * @param onEmailChange Callback invoked when the user types in the email field.
 * @param onPasswordChange Callback invoked when the user types in the password field.
 * @param onConfirmPasswordChange Callback invoked when the user types in the confirm password field (for sign-up).
 * @param onSubmit Callback invoked when the user clicks the primary action button (Sign In or Create Account).
 * @param onToggleMode Callback invoked when the user clicks the button to switch between sign-in and sign-up modes.
 * @param modifier The modifier to be applied to the layout.
 */
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
    // A Column to arrange the UI elements vertically.
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween // Pushes content to the top and bottom.
    ) {
        // Top section: Title and introductory text.
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

        // Middle section: Input fields and action buttons.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Email input field.
            OutlinedTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // Password input field.
            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(), // Hides the password text.
                modifier = Modifier.fillMaxWidth()
            )
            // "Confirm Password" field, shown only in SIGN_UP mode.
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
            // Submit button (Sign In or Create Account).
            Button(
                onClick = onSubmit,
                enabled = !uiState.loading, // Disable button while a network request is in progress.
                modifier = Modifier.fillMaxWidth()
            ) {
                // Show a loading indicator inside the button if `uiState.loading` is true.
                if (uiState.loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .size(18.dp)
                    )
                } else {
                    // Otherwise, show the appropriate text.
                    Text(if (uiState.mode == AuthMode.SIGN_IN) "Sign in" else "Create account")
                }
            }
            // Text button to toggle between Sign In and Sign Up modes.
            TextButton(onClick = onToggleMode) {
                Text(
                    if (uiState.mode == AuthMode.SIGN_IN) "Need an account? Sign up"
                    else "Already have an account? Sign in"
                )
            }
        }
    }
}

/**
 * A composable that displays the content for a signed-in user.
 *
 * This includes the user's email, a sign-out button, and settings for the
 * "Auto Sleep Alarm" feature.
 *
 * @param email The email of the currently signed-in user.
 * @param onSignOut Callback invoked when the user clicks the sign-out button.
 * @param modifier The modifier to be applied to the layout.
 * @param viewModel The [AccountViewModel] instance, used here to manage the state of the settings.
 */
@Composable
private fun SignedInContent(
    email: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel
) {
    // Collect the UI state to get the latest settings values.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Get the current context, which is needed for showing dialogs.
    val context = LocalContext.current

    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Top section: Welcome message and user email.
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

        // Settings Card for "Appearance".
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                
                // Row for the "Dark Mode" switch.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dark Mode", fontWeight = FontWeight.Medium)
                        Text(
                            text = "Use dark theme throughout the app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.isDarkMode,
                        onCheckedChange = { viewModel.setDarkMode(it) }
                    )
                }
            }
        }

        // Settings Card for "Auto Sleep Alarm".
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
                HorizontalDivider() // A visual separator.

                // Row for the "Enable Auto Alarm" switch.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Auto Alarm")
                    Switch(
                        checked = uiState.autoAlarmEnabled,
                        onCheckedChange = { viewModel.setAutoAlarmEnabled(it) } // Update ViewModel on change.
                    )
                }

                // Row for setting the "Trigger Time".
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { // The whole row is clickable to open the time picker.
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    // When a time is selected, update the ViewModel.
                                    viewModel.setAutoAlarmTime(hour, minute)
                                },
                                uiState.autoAlarmHour,
                                uiState.autoAlarmMinute,
                                false // Use 24-hour format internally, but display in 12-hour.
                            ).show()
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trigger Time")
                    // Display the formatted time.
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

                // Row for setting the "Inactivity Duration".
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { // The whole row is clickable to open the selection dialog.
                            android.app.AlertDialog
                                .Builder(context)
                                .setTitle("Inactivity Duration")
                                .setItems(
                                    arrayOf(
                                        "1 minute (test)",
                                        "5 minutes",
                                        "15 minutes",
                                        "30 minutes",
                                        "1 hour"
                                    )
                                ) { _, which ->
                                    // Map the selected index to the corresponding minute value.
                                    val minutes = when (which) {
                                        0 -> 1
                                        1 -> 5
                                        2 -> 15
                                        3 -> 30
                                        4 -> 60
                                        else -> 15 // Default value.
                                    }
                                    // Update the ViewModel with the selected duration.
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
                        text = "${uiState.autoAlarmInactivityMinutes} minutes",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        // Sign-out button at the bottom.
        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Sign Out")
        }
    }
}
