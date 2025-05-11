package com.example.dermascanai

data class Appointment(
    val bookingId: String = "",
    val createdAt: Long = 0,
    val date: String = "",
    val doctorEmail: String = "",
    val doctorName: String = "",
    val message: String = "",
    val patientEmail: String = "",
    var status: String = "",
    val time: String = "",
    val id: String = "",
    val name: String = "",
    val timestampMillis: Long = 0
)
