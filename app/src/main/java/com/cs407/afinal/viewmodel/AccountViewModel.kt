package com.cs407.afinal.viewmodel

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.afinal.InactivityMonitorService
import com.cs407.afinal.alarm.AlarmManager  // CHANGED: from alarm.AlarmPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class AuthMode {
    SIGN_IN,
    SIGN_UP
}

data class AccountUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val currentUserEmail: String? = null,
    val autoAlarmEnabled: Boolean = false,
    val autoAlarmHour: Int = 22,
    val autoAlarmMinute: Int = 30,
    val autoAlarmInactivityMinutes: Int = 15
)

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val alarmManager = AlarmManager(application)  // CHANGED: from AlarmPreferences

    private val _uiState = MutableStateFlow(
        AccountUiState(
            currentUserEmail = auth.currentUser?.email,
            autoAlarmEnabled = alarmManager.isAutoAlarmEnabled(),  // CHANGED
            autoAlarmHour = alarmManager.getAutoAlarmTriggerTime().first,  // CHANGED
            autoAlarmMinute = alarmManager.getAutoAlarmTriggerTime().second,  // CHANGED
            autoAlarmInactivityMinutes = alarmManager.getAutoAlarmInactivityMinutes()  // CHANGED
        )
    )
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        handleAuthChanged(firebaseAuth.currentUser)
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
    }

    fun onEmailChanged(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null, successMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null, successMessage = null) }
    }

    fun onConfirmPasswordChanged(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null, successMessage = null) }
    }

    fun toggleMode() {
        _uiState.update {
            it.copy(
                mode = if (it.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun submit() {
        val state = _uiState.value
        val email = state.email.trim()
        val password = state.password
        val confirm = state.confirmPassword

        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your email address.") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
            return
        }
        if (state.mode == AuthMode.SIGN_UP && password != confirm) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(loading = true, errorMessage = null, successMessage = null) }
            runCatching {
                if (state.mode == AuthMode.SIGN_IN) {
                    auth.signInWithEmailAndPassword(email, password).await()
                } else {
                    auth.createUserWithEmailAndPassword(email, password).await()
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        loading = false,
                        password = "",
                        confirmPassword = "",
                        successMessage = if (state.mode == AuthMode.SIGN_IN) "Welcome back!" else "Account created. You're signed in.",
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = friendlyMessage(throwable),
                        successMessage = null
                    )
                }
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _uiState.update {
            it.copy(
                successMessage = "Signed out",
                mode = AuthMode.SIGN_IN,
                currentUserEmail = null
            )
        }
    }

    fun consumeMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun setAutoAlarmEnabled(enabled: Boolean) {
        alarmManager.setAutoAlarmEnabled(enabled)  // CHANGED
        _uiState.update { it.copy(autoAlarmEnabled = enabled) }

        val intent = Intent(getApplication(), InactivityMonitorService::class.java)
        if (enabled) {
            ContextCompat.startForegroundService(getApplication(), intent)
        } else {
            getApplication<Application>().stopService(intent)
        }

        viewModelScope.launch(Dispatchers.IO) {
            syncAutoAlarmSettingsToFirebase()
        }
    }

    fun setAutoAlarmTime(hour: Int, minute: Int) {
        alarmManager.setAutoAlarmTriggerTime(hour, minute)  // CHANGED
        _uiState.update { it.copy(autoAlarmHour = hour, autoAlarmMinute = minute) }
        viewModelScope.launch(Dispatchers.IO) {
            syncAutoAlarmSettingsToFirebase()
        }
    }

    fun setAutoAlarmInactivityMinutes(minutes: Int) {
        alarmManager.setAutoAlarmInactivityMinutes(minutes)  // CHANGED
        _uiState.update { it.copy(autoAlarmInactivityMinutes = minutes) }
        viewModelScope.launch(Dispatchers.IO) {
            syncAutoAlarmSettingsToFirebase()
        }
    }

    private suspend fun syncAutoAlarmSettingsToFirebase() {
        val user = auth.currentUser ?: return
        val settings = mapOf(
            "autoAlarmEnabled" to alarmManager.isAutoAlarmEnabled(),  // CHANGED
            "autoAlarmHour" to alarmManager.getAutoAlarmTriggerTime().first,  // CHANGED
            "autoAlarmMinute" to alarmManager.getAutoAlarmTriggerTime().second,  // CHANGED
            "autoAlarmInactivityMinutes" to alarmManager.getAutoAlarmInactivityMinutes()  // CHANGED
        )
        firestore.collection("users")
            .document(user.uid)
            .set(mapOf("settings" to settings), SetOptions.merge())
            .await()
    }

    private fun handleAuthChanged(user: FirebaseUser?) {
        _uiState.update {
            it.copy(
                currentUserEmail = user?.email,
                loading = false,
                email = user?.email ?: it.email,
                password = "",
                confirmPassword = ""
            )
        }
        if (user != null) {
            viewModelScope.launch(Dispatchers.IO) {
                loadAutoAlarmSettingsFromFirebase(user)
            }
        }
    }

    private suspend fun loadAutoAlarmSettingsFromFirebase(user: FirebaseUser) {
        runCatching {
            val doc = firestore.collection("users").document(user.uid).get().await()
            @Suppress("UNCHECKED_CAST")
            val settings = doc.get("settings") as? Map<String, Any>
            if (settings != null) {
                val enabled = settings["autoAlarmEnabled"] as? Boolean ?: false
                val hour = (settings["autoAlarmHour"] as? Long)?.toInt() ?: 22
                val minute = (settings["autoAlarmMinute"] as? Long)?.toInt() ?: 30
                val inactivityMinutes = (settings["autoAlarmInactivityMinutes"] as? Long)?.toInt() ?: 15
                alarmManager.setAutoAlarmEnabled(enabled)  // CHANGED
                alarmManager.setAutoAlarmTriggerTime(hour, minute)  // CHANGED
                alarmManager.setAutoAlarmInactivityMinutes(inactivityMinutes)  // CHANGED
                _uiState.update {
                    it.copy(
                        autoAlarmEnabled = enabled,
                        autoAlarmHour = hour,
                        autoAlarmMinute = minute,
                        autoAlarmInactivityMinutes = inactivityMinutes
                    )
                }
            }
        }
    }

    private fun friendlyMessage(throwable: Throwable): String =
        throwable.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
}