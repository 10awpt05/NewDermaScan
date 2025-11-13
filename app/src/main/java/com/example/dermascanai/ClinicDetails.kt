package com.example.dermascanai

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.ActivityDermaDetailsBinding
import com.example.dermascanai.databinding.DialogRateBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

class ClinicDetails : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityDermaDetailsBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var clinicRef: DatabaseReference
    private lateinit var dermatologistsAdapter: DermatologistsViewAdapter
    private val dermatologistsList = mutableListOf<Dermatologist>()
    private lateinit var servicesAdapter: ServicesViewAdapter
    private val servicesList = mutableListOf<String>()
    private var currentClinicId: String? = null
    private var googleMap: GoogleMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDermaDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")

        dermatologistsAdapter = DermatologistsViewAdapter(dermatologistsList)
        binding.dermatologistsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ClinicDetails)
            adapter = dermatologistsAdapter
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.popupMapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        val clinicEmail = intent.getStringExtra("email")
        if (clinicEmail == null) {
            Toast.makeText(this, "Clinic email not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        fetchClinicData(clinicEmail)

        binding.backBtn.setOnClickListener { finish() }

        binding.appointmentBtn.setOnClickListener {
            val intent = Intent(this, Booking::class.java)
            intent.putExtra("clinicEmail", clinicEmail)
            startActivity(intent)
        }

        binding.rateMe.setOnClickListener {
            currentClinicId?.let { id -> rateMe(id) }
        }

        binding.messageMe.setOnClickListener {
            currentClinicId?.let { clinicId ->
                val intent = Intent(this, MessageMe::class.java)
                intent.putExtra("receiverId", clinicId)
                startActivity(intent)
            } ?: run {
                Toast.makeText(this, "Clinic ID not found", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun setupRecyclerView() {
        servicesAdapter = ServicesViewAdapter(servicesList)
        binding.servicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ClinicDetails)
            adapter = servicesAdapter
        }
    }

    private fun fetchClinicData(clinicEmail: String) {
        clinicRef = database.getReference("clinicInfo")
        val query = clinicRef.orderByChild("email").equalTo(clinicEmail)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (childSnapshot in snapshot.children) {
                        currentClinicId = childSnapshot.key

                        val map = childSnapshot.value as? Map<String, Any> ?: continue

                        val clinicPhone = when (val valPhone = map["clinicPhone"]) {
                            is String -> valPhone
                            is Long, is Double -> valPhone.toString()
                            else -> null
                        }

                        val rating = when (val valRating = map["rating"]) {
                            is Number -> valRating.toFloat()
                            else -> 0.0f
                        }

                        val clinicInfo = ClinicInfo(
                            name = map["name"] as? String,
                            email = map["email"] as? String,
                            password = map["password"] as? String ?: "",
                            role = map["role"] as? String ?: "",
                            status = map["status"] as? String ?: "not verified",
                            contact = map["contact"] as? String,
                            birthday = map["birthday"] as? String,
                            gender = map["gender"] as? String,
                            province = map["province"] as? String,
                            city = map["city"] as? String,
                            barangay = map["barangay"] as? String,
                            profileImage = map["profileImage"] as? String,
//                            stableLevel = (map["stableLevel"] as? Long)?.toInt(),

                            quote = map["quote"] as? String,
                            bio = map["bio"] as? String,
                            verificationImg = map["verificationImg"] as? String,
                            feedback = map["feedback"] as? String,
                            street = map["street"] as? String,
                            postalCode = map["postalCode"] as? String,
                            tagline = map["tagline"] as? String,
                            acceptingPatients = map["acceptingPatients"] as? Boolean,
                            address = map["address"] as? String,
                            operatingDays = map["operatingDays"] as? String,
                            openingTime = map["openingTime"] as? String,
                            closingTime = map["closingTime"] as? String,
                            about = map["about"] as? String,
                            logoImage = map["logoImage"] as? String,
//                            clinicStable = map["clinicStable"] as? Boolean ?: false,
//                            birDocument = map["birDocument"] as? String,
//                            permitDocument = map["permitDocument"] as? String,
                            services = (map["services"] as? List<String>),
                            dermatologists = null,
                            specialization = map["specialization"] as? String ?: "",
                            description = map["description"] as? String ?: "",
                            rating = rating,
                            availability = map["availability"] as? String ?: "",
                            clinicName = map["clinicName"] as? String,
                            clinicAddress = map["clinicAddress"] as? String,
                            clinicPhone = clinicPhone,
                            clinicOpenDay = map["clinicOpenDay"] as? String,
                            clinicOpenTime = map["clinicOpenTime"] as? String,
                            clinicCloseDay = map["clinicCloseDay"] as? String,
                            clinicCloseTime = map["clinicCloseTime"] as? String,
                            birImage = map["birImage"] as? String,
                            businessPermitImage = map["businessPermitImage"] as? String,
                            validIdImage = map["validIdImage"] as? String
                        )

                        populateViews(clinicInfo)
                        currentClinicId?.let { calculateAndDisplayAverageRating(it) }
                    }
                } else {
                    Toast.makeText(this@ClinicDetails, "No matching clinic found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ClinicDetails, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun populateViews(clinicInfo: ClinicInfo) {
        binding.displayName.text = clinicInfo.name ?: "Clinic Name"
        binding.ratingText.text = clinicInfo.rating?.toString() ?: "0.0"
        binding.clinicDaysText.text = clinicInfo.operatingDays ?: "Not specified"
        binding.location.text = clinicInfo.clinicAddress ?: "Address"

        val openingTime = clinicInfo.openingTime
        val closingTime = clinicInfo.closingTime
        binding.clinicTimeText.text = if (openingTime != null && closingTime != null) "$openingTime to $closingTime" else "Hours not specified"

        binding.phone.text = clinicInfo.clinicPhone ?: "Contact not specified"
        binding.clinicEmail.text = clinicInfo.email ?: "Email not specified"
        binding.displayAddress.text = clinicInfo.address ?: "Address not specified"
        binding.bio.text = clinicInfo.about ?: "Clinic bio and description will appear here..."

        clinicInfo.logoImage?.let { logoBase64 ->
            try {
                val decodedBytes = Base64.decode(logoBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                binding.profile.setImageBitmap(bitmap)
            } catch (_: Exception) {}
        }

        clinicInfo.services?.let {
            binding.servicesCard.visibility = if (it.isNotEmpty()) View.VISIBLE else View.GONE
            servicesList.clear()
            servicesList.addAll(it)
            servicesAdapter.notifyDataSetChanged()
        }

        clinicInfo.dermatologists?.let {
            binding.dermatologistsCard.visibility = if (it.isNotEmpty()) View.VISIBLE else View.GONE
            dermatologistsList.clear()
            dermatologistsList.addAll(it)
            dermatologistsAdapter.notifyDataSetChanged()
        }

        binding.appointmentBtn.apply {
            visibility = View.VISIBLE
            isEnabled = clinicInfo.status == "verified"
            text = if (isEnabled) "Book Appointment" else "Clinic Not Verified"
        }

        // 🗺️ Geocode address and add marker
        val clinicAddress = clinicInfo.clinicAddress ?: return
        val context = this
        val addressText = clinicInfo.clinicAddress ?: return

        Thread {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocationName(addressText, 1)
                if (!addresses.isNullOrEmpty()) {
                    val location = addresses[0]
                    val clinicLatLng = LatLng(location.latitude, location.longitude)

                    runOnUiThread {
                        googleMap?.apply {
                            clear()
                            addMarker(MarkerOptions().position(clinicLatLng).title(clinicInfo.name ?: "Clinic Location"))
                            moveCamera(CameraUpdateFactory.newLatLngZoom(clinicLatLng, 15f))
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(context, "Could not find location: $addressText", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(context, "Geocoding failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()

    }

    private fun rateMe(clinicId: String) {
        val dialogBinding = DialogRateBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.submitRatingBtn.setOnClickListener {
            val rating = dialogBinding.ratingBar.rating
            val message = dialogBinding.messageEditText.text.toString().trim()
            val userId = FirebaseAuth.getInstance().currentUser?.uid

            if (userId == null) {
                Toast.makeText(this, "You must be logged in to rate.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return@setOnClickListener
            }

            val ratingData = mapOf(
                "rating" to rating,
                "message" to message,
                "timestamp" to System.currentTimeMillis()
            )

            val ratingRef = database.reference.child("ratings").child(clinicId).child(userId)

            ratingRef.setValue(ratingData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    calculateAndDisplayAverageRating(clinicId)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to submit rating: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }

    private fun calculateAndDisplayAverageRating(clinicId: String) {
        val ratingsRef = database.reference.child("ratings").child(clinicId)
        val clinicRef = database.reference.child("clinicInfo").child(clinicId)

        ratingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var totalRating = 0f
                var count = 0

                for (child in snapshot.children) {
                    val ratingValue = child.child("rating").getValue(Float::class.java)
                    if (ratingValue != null) {
                        totalRating += ratingValue
                        count++
                    }
                }

                val averageRating = if (count > 0) totalRating / count else 0f

                // Update UI
                binding.ratingText.text = String.format("%.1f", averageRating)

                // Save the average rating to clinicInfo
                clinicRef.child("rating").setValue(averageRating)
                    .addOnSuccessListener {
                        // Optional: log or toast if needed
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this@ClinicDetails, "Failed to save average rating: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ClinicDetails, "Failed to load ratings: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
    }
}
