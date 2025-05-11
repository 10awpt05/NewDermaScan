package com.example.dermascanai

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dermascanai.databinding.ActivityConfirmBookingBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfirmBooking : AppCompatActivity() {
    private lateinit var binding: ActivityConfirmBookingBinding
    private lateinit var database: DatabaseReference
    private lateinit var firebase: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private val emojiList = listOf(
        "😊", "😂", "😍", "😢", "👍", "👋", "🙏", "🤝", "✌️", "👎",
        "❤️", "🤔", "🙄", "😎", "😡", "🤗", "👏", "🖐️", "✋", "🫶"
    )

    private var bookingId: String = ""
    private var selectedDate: String = ""
    private var selectedTime: String = ""
    private var doctorEmail: String = ""
    private var patientEmail: String = ""
    private var timestampMillis: Long = 0L
    private var doctorName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebase = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        auth = FirebaseAuth.getInstance()

        val emojiIcon = findViewById<ImageView>(R.id.emojiIcon)

        // Get values from intent
        selectedDate = intent.getStringExtra("selectedDate") ?: ""
        selectedTime = intent.getStringExtra("selectedTime") ?: ""
        doctorEmail = intent.getStringExtra("doctorEmail") ?: ""
        timestampMillis = intent.getLongExtra("timestampMillis", System.currentTimeMillis())
        bookingId = intent.getStringExtra("bookingId") ?: System.currentTimeMillis().toString()

        // Get current user email and set patient email
        val currentUserEmail = auth.currentUser?.email
        // First check if patientEmail is passed from intent, otherwise use current user's email
        patientEmail = intent.getStringExtra("patientEmail") ?: ""
        if (patientEmail.isEmpty()) {
            patientEmail = currentUserEmail ?: ""
            Log.d("ConfirmBooking", "Using current user email: $patientEmail")
        }

        // Display patient email in UI if you have a field for it
        binding.messageEditText.hint = "Message from $patientEmail"

        if (doctorEmail.isEmpty()) {
            Toast.makeText(this, "Doctor email not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        emojiIcon.setOnClickListener {
            showEmojiPopup(it)
        }

        fetchUserData(doctorEmail)

        binding.backBTN.setOnClickListener {
            finish()
        }

        binding.date.text = selectedDate
        binding.time.text = selectedTime

        binding.confirm.setOnClickListener {
            if (patientEmail.isEmpty()) {
                Toast.makeText(this, "You must be logged in to book an appointment", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveBookingToFirebase()
        }
    }

    private fun fetchUserData(docEmail: String) {
        database = firebase.getReference("dermaInfo")
        val query = database.orderByChild("email").equalTo(docEmail)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (childSnapshot in snapshot.children) {
                        val dermaInfo = childSnapshot.getValue(DermaInfo::class.java)
                        if (dermaInfo != null) {
                            binding.docName.text = dermaInfo.name
                            doctorName = dermaInfo.name ?: ""

                            dermaInfo.profileImage?.let {
                                if (it.isNotEmpty()) {
                                    try {
                                        val decodedBytes = Base64.decode(it, Base64.DEFAULT)
                                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                        binding.profPic.setImageBitmap(bitmap)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Toast.makeText(this@ConfirmBooking, "No matching derma found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ConfirmBooking, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveBookingToFirebase() {
        val messageText = binding.messageEditText.text.toString()

        // Get reference to bookings
        val bookingsRef = firebase.getReference("bookings")

        // Create a booking object
        val booking = HashMap<String, Any>()
        booking["bookingId"] = bookingId
        booking["patientEmail"] = patientEmail
        booking["doctorEmail"] = doctorEmail
        booking["doctorName"] = doctorName
        booking["date"] = selectedDate
        booking["time"] = selectedTime
        booking["message"] = messageText
        booking["status"] = "pending" // pending, confirmed, cancelled, completed
        booking["timestampMillis"] = timestampMillis
        booking["createdAt"] = System.currentTimeMillis()

        // Save in Firebase
        bookingsRef.child(bookingId).setValue(booking)
            .addOnSuccessListener {
                Toast.makeText(this, "Booking confirmed successfully!", Toast.LENGTH_SHORT).show()


                val userBookingsRef = firebase.getReference("userBookings").child(patientEmail.replace(".", ","))
                userBookingsRef.child(bookingId).setValue(booking)

                val doctorBookingsRef = firebase.getReference("doctorBookings").child(doctorEmail.replace(".", ","))
                doctorBookingsRef.child(bookingId).setValue(booking)

                val intent = Intent(this, BookingHistory::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving booking: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEmojiPopup(anchor: View) {
        val popupView = layoutInflater.inflate(R.layout.emoji_popup, null)
        val gridView = popupView.findViewById<GridView>(R.id.emojiGrid)
        val popupWindow = PopupWindow(popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, emojiList)
        gridView.adapter = adapter

        val messageEditText = findViewById<EditText>(R.id.messageEditText)

        gridView.setOnItemClickListener { _, _, position, _ ->
            val emoji = emojiList[position]
            messageEditText.append(emoji)
            popupWindow.dismiss()
        }

        popupWindow.elevation = 10f
        popupWindow.setBackgroundDrawable(getDrawable(android.R.color.transparent))
        popupWindow.showAsDropDown(anchor, 0, -anchor.height * 4)
    }
}