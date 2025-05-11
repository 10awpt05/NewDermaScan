package com.example.dermascanai

data class UserData(
    val email: String = "",
    val name: String? = null,
    val phone: String? = null,
    val profileImage: String? = null,
    val userType: String = "patient"
)