package com.medivox.ai.voice

import com.medivox.ai.data.ConversationSummary

enum class VoicePhase(val label: String) {
    IDLE("Ready when you are"),
    CONNECTING("Connecting securely"),
    LISTENING("Listening"),
    USER_SPEAKING("Listening to you"),
    THINKING("Thinking"),
    ASSISTANT_SPEAKING("MediVox is speaking"),
    DISCONNECTING("Ending conversation"),
    ERROR("Connection issue")
}

data class TranscriptEntry(
    val speaker: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val transcript: List<TranscriptEntry> = emptyList(),
    val liveCaption: String = "",
    val recentConversations: List<ConversationSummary> = emptyList(),
    val errorMessage: String? = null,
) {
    val isSessionActive: Boolean
        get() = phase == VoicePhase.LISTENING ||
            phase == VoicePhase.USER_SPEAKING ||
            phase == VoicePhase.THINKING ||
            phase == VoicePhase.ASSISTANT_SPEAKING

    val isBusy: Boolean
        get() = phase == VoicePhase.CONNECTING || phase == VoicePhase.DISCONNECTING
}
