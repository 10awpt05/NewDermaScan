package com.example.dermascanai

data class Appointment(
    val bookingId: String = "",
    val patientEmail: String = "",
    val doctorName: String = "",
    val date: String = "",
    val timeSlot: String = "",
    val service: String = "",
    val message: String = "",
    val status: String = "pending",
    val timestampMillis: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val cancellationReason: String? = ""
)