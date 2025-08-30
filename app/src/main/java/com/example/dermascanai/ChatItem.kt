package com.example.dermascanai

data class ChatItem(
    val uid: String,
    val displayName: String,
    val profileBase64: String? = null, // User profile or clinic logo
    val isClinic: Boolean = false,
    var isRead: Boolean = false
)
