package com.example.dermascanai


data class UserInfo(

    var name: String? = null,
    var email: String? = null,
    var password: String = "",
    var role: String = "",
    var profileImage: String? = null,
    var birthday: String? = null,
    var gender: String? = null,
    var contact: String? = null,
    var province: String? = null,
    var city: String? = null,
    var barangay: String? = null,
    var quote: String? = null,
    var bio: String? = null,
    var uid: String? = null,
)

data class DermaInfo(
    var uid: String? = null,
    val name: String? = null,
    val email: String? = null,
    val password: String = "",
    val role: String = "",
    val profileImage: String? = null,
    val status: String = "not verified",
    val birthday: String? = null,
    val gender: String? = null,
    val contact: String? = null,
    val province: String? = null,
    val city: String? = null,
    val barangay: String? = null,
    val quote: String? = null,
    val bio: String? = null,
    val verificationImg: String? = null,
    val rating: String? = null,
    val feedback: String? = null,
    var specialization: String? = null

)

//data class User(
//    val userId: String = "",
//    val name: String = "",
//    val profileImage: String = ""
//)