package com.example.dermascanai

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class ClinicInfo(
    var name: String? = null,
    var email: String? = null,
    var password: String = "",
    var role: String = "",
    var status: String = "not verified",
    var contact: String? = null,
    var birthday: String? = null,
    var gender: String? = null,
    var province: String? = null,
    var city: String? = null,
    var barangay: String? = null,
    var profileImage: String? = null,

    var quote: String? = null,
    var bio: String? = null,
    var verificationImg: String? = null,
    var feedback: String? = null,
    var street: String? = null,
    var postalCode: String? = null,
    var tagline: String? = null,
    var acceptingPatients: Boolean? = null,
    var address: String? = null,
    var operatingDays: String? = null,
    var openingTime: String? = null,
    var closingTime: String? = null,
    var about: String? = null,
    var logoImage: String? = null,
    var services: List<String>? = null,
    var dermatologists: List<Dermatologist>? = null,

    var specialization: String = "",
    var description: String = "",
    var rating: Float? = 0.0f,
    var availability: String = "",

    // Clinic Information combined here
    var clinicName: String? = null,
    var clinicAddress: String? = null,
    var clinicPhone: String? = null,

    // New fields for clinic opening schedule
    var clinicOpenDay: String? = null,
    var clinicOpenTime: String? = null,
    var clinicCloseDay: String? = null,
    var clinicCloseTime: String? = null,


    // Additional uploaded documents
    var birImage: String? = null,
    var businessPermitImage: String? = null,
    var validIdImage: String? = null,



) : Parcelable