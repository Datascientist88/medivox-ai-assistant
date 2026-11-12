package com.medivox.ai.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medivox.ai.data.AppUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AuthUiState(
    val isLoading: Boolean = false,
    val user: AppUser? = null,
    val errorMessage: String? = null,
)

class AuthViewModel : ViewModel() {
    private val stateFlow = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = stateFlow.asStateFlow()

    fun signIn(email: String, password: String) {
        authenticate(email, password, null)
    }

    fun register(email: String, password: String, displayName: String) {
        authenticate(email, password, displayName)
    }

    fun signOut() {
        stateFlow.value = AuthUiState()
    }

    private fun authenticate(email: String, password: String, displayName: String?) {
        viewModelScope.launch {
            val cleanEmail = email.trim()
            if (cleanEmail.isBlank() || password.isBlank()) {
                stateFlow.update {
                    it.copy(errorMessage = "Email and password are required.")
                }
                return@launch
            }

            stateFlow.update { it.copy(isLoading = true, errorMessage = null) }
            delay(250)
            stateFlow.value = AuthUiState(
                isLoading = false,
                user = AppUser(
                    id = UUID.randomUUID().toString(),
                    email = cleanEmail,
                    displayName = displayName?.trim().takeUnless { it.isNullOrBlank() }
                        ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                )
            )
        }
    }
}
