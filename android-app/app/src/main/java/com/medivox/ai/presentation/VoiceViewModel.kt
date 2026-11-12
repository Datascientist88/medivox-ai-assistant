package com.medivox.ai.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medivox.ai.data.ConversationRepository
import com.medivox.ai.data.RealtimeTokenProvider
import com.medivox.ai.data.SecureSessionStore
import com.medivox.ai.voice.TranscriptEntry
import com.medivox.ai.voice.VoicePhase
import com.medivox.ai.voice.VoiceUiState
import com.medivox.ai.voice.WebRtcRealtimeClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VoiceViewModel(application: Application) : AndroidViewModel(application),
    WebRtcRealtimeClient.Listener {

    private val conversations = ConversationRepository(application)
    private val tokenStore = SecureSessionStore(application)
    private val client = WebRtcRealtimeClient(
        context = application,
        tokenProvider = RealtimeTokenProvider(application),
        listener = this,
    )

    private val stateFlow = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = stateFlow.asStateFlow()
    private var activeConversationId: String? = null

    init {
        refreshHistory()
    }

    fun connect() {
        if (stateFlow.value.isBusy || stateFlow.value.isSessionActive) return
        viewModelScope.launch {
            stateFlow.update {
                it.copy(
                    phase = VoicePhase.CONNECTING,
                    errorMessage = null,
                    transcript = emptyList(),
                    liveCaption = "",
                )
            }
            runCatching {
                activeConversationId = conversations.create("Voice conversation")
                client.connect()
            }.onFailure { error ->
                stateFlow.update {
                    it.copy(
                        phase = VoicePhase.ERROR,
                        errorMessage = error.message ?: "Could not start the voice conversation.",
                    )
                }
                refreshHistory()
            }
        }
    }

    fun disconnect() {
        if (stateFlow.value.phase == VoicePhase.IDLE ||
            stateFlow.value.phase == VoicePhase.DISCONNECTING
        ) return

        viewModelScope.launch {
            stateFlow.update { it.copy(phase = VoicePhase.DISCONNECTING) }
            client.close()
            activeConversationId = null
            stateFlow.update { it.copy(phase = VoicePhase.IDLE, liveCaption = "") }
            refreshHistory()
        }
    }

    fun signOut() {
        client.close()
        activeConversationId = null
        tokenStore.clear()
        stateFlow.value = VoiceUiState()
    }

    fun reportPermissionDenied() {
        stateFlow.update {
            it.copy(
                phase = VoicePhase.ERROR,
                errorMessage = "Microphone permission is required for a voice conversation.",
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
                liveCaption = "",
            )
        }
        viewModelScope.launch {
            stateFlow.update { it.copy(phase = VoicePhase.THINKING) }
            runCatching {
                val conversationId = activeConversationId ?: conversations.create("Voice conversation")
                activeConversationId = conversationId
                conversations.addMessage(conversationId, "user", trimmed)
                conversations.addMessage(
                    conversationId,
                    "assistant",
                    "Thanks. In the full build, this conversation will flow through OpenAI Realtime over WebRTC."
                )
            }
            stateFlow.update {
                it.copy(
                    phase = VoicePhase.LISTENING,
                    transcript = it.transcript + TranscriptEntry(
                        "MediVox",
                        "Thanks. In the full build, this conversation will flow through OpenAI Realtime over WebRTC."
                    ),
                )
            }
        }
    }

    private fun refreshHistory() {
        viewModelScope.launch {
            runCatching { conversations.recent() }
                .onSuccess { items -> stateFlow.update { current -> current.copy(recentConversations = items) } }
        }
    }

    override fun onPhaseChanged(phase: VoicePhase) {
        stateFlow.update { current ->
            if (current.phase == VoicePhase.DISCONNECTING) current
            else current.copy(phase = phase, errorMessage = null)
        }
    }

    override fun onTranscript(speaker: String, text: String, isFinal: Boolean) {
        if (text.isBlank()) return
        stateFlow.update { current ->
            if (isFinal) {
                current.copy(
                    transcript = current.transcript + TranscriptEntry(speaker, text),
                    liveCaption = "",
                )
            } else {
                current.copy(liveCaption = current.liveCaption + text)
            }
        }
        if (isFinal) {
            val id = activeConversationId ?: return
            viewModelScope.launch {
                runCatching {
                    conversations.addMessage(id, if (speaker == "MediVox") "assistant" else "user", text)
                }
            }
        }
    }

    override fun onError(message: String) {
        stateFlow.update { it.copy(errorMessage = message) }
    }

    override fun onCleared() {
        client.close()
        super.onCleared()
    }
}
