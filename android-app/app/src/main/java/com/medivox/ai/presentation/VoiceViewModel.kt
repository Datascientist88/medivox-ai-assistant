package com.medivox.ai.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medivox.ai.data.ConversationSummary
import com.medivox.ai.voice.TranscriptEntry
import com.medivox.ai.voice.VoicePhase
import com.medivox.ai.voice.VoiceUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class VoiceViewModel : ViewModel() {
    private val stateFlow = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = stateFlow.asStateFlow()

    fun connect() {
        if (stateFlow.value.isBusy || stateFlow.value.isSessionActive) return
        viewModelScope.launch {
            stateFlow.update {
                it.copy(
                    phase = VoicePhase.CONNECTING,
                    errorMessage = null,
                    transcript = emptyList(),
                    liveCaption = ""
                )
            }
            delay(500)
            stateFlow.update {
                it.copy(
                    phase = VoicePhase.LISTENING,
                    transcript = listOf(
                        TranscriptEntry("MediVox", "Demo mode is ready. Replace this with WebRTC voice transport next."),
                    ),
                    recentConversations = listOf(
                        ConversationSummary(UUID.randomUUID().toString(), "Voice session demo", "just now")
                    )
                )
            }
        }
    }

    fun disconnect() {
        if (stateFlow.value.phase == VoicePhase.IDLE || stateFlow.value.phase == VoicePhase.DISCONNECTING) return
        viewModelScope.launch {
            stateFlow.update { it.copy(phase = VoicePhase.DISCONNECTING) }
            delay(250)
            stateFlow.update { it.copy(phase = VoicePhase.IDLE, liveCaption = "") }
        }
    }

    fun reportPermissionDenied() {
        stateFlow.update {
            it.copy(
                phase = VoicePhase.ERROR,
                errorMessage = "Microphone permission is required for voice sessions."
            )
        }
    }

    fun clearError() {
        stateFlow.update { it.copy(errorMessage = null, phase = VoicePhase.IDLE) }
    }

    fun addDemoUserUtterance(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        stateFlow.update {
            it.copy(
                transcript = it.transcript + TranscriptEntry("You", trimmed),
                liveCaption = ""
            )
        }
        viewModelScope.launch {
            stateFlow.update { it.copy(phase = VoicePhase.THINKING) }
            delay(450)
            stateFlow.update {
                it.copy(
                    phase = VoicePhase.LISTENING,
                    transcript = it.transcript + TranscriptEntry(
                        "MediVox",
                        "Thanks. In the full build, this conversation will flow through OpenAI Realtime over WebRTC."
                    )
                )
            }
        }
    }
}
