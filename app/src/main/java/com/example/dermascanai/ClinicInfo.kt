package com.example.dermascanai

import android.os.Parcelable
import com.google.firebase.database.Exclude
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
//    var phone: String? = null,
    val profileImage: String? = null,
//    var stableLevel: Int? = null,

    var quote: String? = null,
    var bio: String? = null,
    var verificationImg: String? = null,
    var feedback: String? = null,
    var street: String? = null,
    var postalCode: String? = null,
    val tagline: String? = null,
    val acceptingPatients: Boolean? = null,
    val address: String? = null,
    val operatingDays: String? = null,
    val openingTime: String? = null,
    val closingTime: String? = null,
    val about: String? = null,
    val logoImage: String? = null,
//    @get:Exclude
//    val clinicStable: Boolean = false,
//    val birDocument: String? = null,
//    val permitDocument: String? = null,
    val services: List<String>? = null,
    val dermatologists: List<Dermatologist>? = null,

    val specialization: String = "",
    val description: String = "",
    var rating: Float? = 0.0f,
    val availability: String = "",

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
    var validIdImage: String? = null
) : Parcelable