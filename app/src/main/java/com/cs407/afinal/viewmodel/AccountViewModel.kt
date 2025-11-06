package com.cs407.afinal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
    val currentUserEmail: String? = null
)

class AccountViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(
        AccountUiState(
            currentUserEmail = auth.currentUser?.email
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
    }

    private fun friendlyMessage(throwable: Throwable): String =
        throwable.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
}
