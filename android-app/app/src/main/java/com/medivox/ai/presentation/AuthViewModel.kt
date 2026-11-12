package com.medivox.ai.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medivox.ai.data.AuthRepository
import com.medivox.ai.data.AppUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = true,
    val user: AppUser? = null,
    val errorMessage: String? = null,
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuthRepository(application)
    private val stateFlow = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = stateFlow.asStateFlow()

    init {
        viewModelScope.launch {
            val user = runCatching { repository.currentUser() }.getOrNull()
            if (user == null) repository.logout()
            stateFlow.value = AuthUiState(isLoading = false, user = user)
        }
    }

    fun signIn(email: String, password: String) {
        authenticate { repository.login(email, password) }
    }

    fun register(email: String, password: String, displayName: String) {
        authenticate { repository.register(email, password, displayName) }
    }

    fun signOut() {
        repository.logout()
        stateFlow.value = AuthUiState(isLoading = false)
    }

    private fun authenticate(action: suspend () -> AppUser) {
        viewModelScope.launch {
            stateFlow.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { action() }
                .onSuccess { user ->
                    stateFlow.value = AuthUiState(isLoading = false, user = user)
                }
                .onFailure { error ->
                    stateFlow.value = AuthUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "Authentication failed.",
                    )
                }
        }
    }
}
