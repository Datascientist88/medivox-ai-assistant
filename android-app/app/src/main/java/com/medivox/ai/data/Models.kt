package com.medivox.ai.data

data class AppUser(
    val id: String,
    val email: String,
    val displayName: String
)

data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: String
)
