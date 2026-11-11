package com.foundit.app

data class MatchConversation(
    val id: String,
    val reportAId: String,
    val reportBId: String,
    val userAId: String,
    val userBId: String,
    val createdAt: String? = null
)

data class MatchMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val createdAt: String? = null,
    val deliveredAt: String? = null,
    val readAt: String? = null
)