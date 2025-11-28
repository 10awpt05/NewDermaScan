package com.example.dermascanai

data class ReportsData(
    val bookingId: String = "",
    val createdAt: Long = 0L,
    val date: String = "",
    val message: String = "",
    val patientEmail: String = "",
    val patientName: String = "",
    val service: String = "",
    val status: String = "",
    val time: String = ""
)
