package com.example.dermascanai

import java.io.Serializable

data class BookingData(
    val bookingId: String = "",
    val userId: String? = null,
    val clinicName: String? = "",
    val patientEmail: String = "",
    val doctorEmail: String = "",
    val message: String = "",
    val timestampMillis: Long = 0,
    val createdAt: Long = 0,
    val service: String = "",
    val status: String = "pending",
    var declineReason: String? = null,
    var cancellationReason: String? = null
) : Serializable