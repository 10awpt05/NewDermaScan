package com.example.dermascanai

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.ActivityBookingRecordsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class BookingApprovalRecords : AppCompatActivity() {
    private lateinit var binding: ActivityBookingRecordsBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: BookingApprovalAdapter
    private val appointmentList = mutableListOf<BookingData>()
    private lateinit var doctorEmail: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        auth = FirebaseAuth.getInstance()

        // Get current doctor's email
        doctorEmail = auth.currentUser?.email ?: ""
        if (doctorEmail.isEmpty()) {
            Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        loadPendingAppointments()

        binding.backBTN.setOnClickListener {
            finish()
        }

        // Toggle between pending and all bookings
        binding.pendingFilterChip.setOnClickListener {
            binding.pendingFilterChip.isChecked = true
            binding.allFilterChip.isChecked = false
            loadPendingAppointments()
        }

        // Toggle between pending and all bookings
        binding.approvedFilterChip.setOnClickListener {
            binding.approvedFilterChip.isChecked = true
            binding.allFilterChip.isChecked = false
            loadApprovedAppointments()
        }

        // Toggle between pending and all bookings
        binding.declinedFilterChip.setOnClickListener {
            binding.declinedFilterChip.isChecked = true
            binding.allFilterChip.isChecked = false
            loadDeclineAppointments()
        }

        binding.allFilterChip.setOnClickListener {
            binding.allFilterChip.isChecked = true
            binding.pendingFilterChip.isChecked = false
            loadAllAppointments()
        }
    }

    private fun setupRecyclerView() {
        adapter = BookingApprovalAdapter(
            appointmentList,
            onApprove = { booking -> updateBookingStatus(booking, "confirmed") },
            onDecline = { booking -> showDeclineReasonDialog(booking) }
        )
        binding.bookingRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.bookingRecyclerView.adapter = adapter
    }

    private fun loadPendingAppointments() {
        binding.progressBar.visibility = View.VISIBLE
        appointmentList.clear()

        val doctorBookingsRef = database.getReference("doctorBookings")
            .child(doctorEmail.replace(".", ","))

        doctorBookingsRef.orderByChild("status").equalTo("pending")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (bookingSnapshot in snapshot.children) {
                            val booking = bookingSnapshot.getValue(BookingData::class.java)
                            booking?.let {
                                appointmentList.add(it)
                            }
                        }
                        // Sort by appointment date/time
                        appointmentList.sortBy { it.timestampMillis }
                        adapter.notifyDataSetChanged()

                        // Show empty state or content
                        updateViewVisibility()
                    } else {
                        appointmentList.clear()
                        adapter.notifyDataSetChanged()
                        updateViewVisibility()
                    }
                    binding.progressBar.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@BookingApprovalRecords, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                }
            })
    }

    private fun loadApprovedAppointments() {
        binding.progressBar.visibility = View.VISIBLE
        appointmentList.clear()

        val doctorBookingsRef = database.getReference("doctorBookings")
            .child(doctorEmail.replace(".", ","))

        doctorBookingsRef.orderByChild("status").equalTo("confirmed")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (bookingSnapshot in snapshot.children) {
                            val booking = bookingSnapshot.getValue(BookingData::class.java)
                            booking?.let {
                                appointmentList.add(it)
                            }
                        }
                        // Sort by appointment date/time
                        appointmentList.sortBy { it.timestampMillis }
                        adapter.notifyDataSetChanged()

                        // Show empty state or content
                        updateViewVisibility()
                    } else {
                        appointmentList.clear()
                        adapter.notifyDataSetChanged()
                        updateViewVisibility()
                    }
                    binding.progressBar.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@BookingApprovalRecords, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                }
            })
    }

    private fun loadDeclineAppointments() {
        binding.progressBar.visibility = View.VISIBLE
        appointmentList.clear()

        val doctorBookingsRef = database.getReference("doctorBookings")
            .child(doctorEmail.replace(".", ","))

        doctorBookingsRef.orderByChild("status").equalTo("declined")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (bookingSnapshot in snapshot.children) {
                            val booking = bookingSnapshot.getValue(BookingData::class.java)
                            booking?.let {
                                appointmentList.add(it)
                            }
                        }
                        // Sort by appointment date/time
                        appointmentList.sortBy { it.timestampMillis }
                        adapter.notifyDataSetChanged()

                        // Show empty state or content
                        updateViewVisibility()
                    } else {
                        appointmentList.clear()
                        adapter.notifyDataSetChanged()
                        updateViewVisibility()
                    }
                    binding.progressBar.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@BookingApprovalRecords, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                }
            })
    }

    private fun loadAllAppointments() {
        binding.progressBar.visibility = View.VISIBLE
        appointmentList.clear()

        val doctorBookingsRef = database.getReference("doctorBookings")
            .child(doctorEmail.replace(".", ","))

        doctorBookingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (bookingSnapshot in snapshot.children) {
                        val booking = bookingSnapshot.getValue(BookingData::class.java)
                        booking?.let {
                            appointmentList.add(it)
                        }
                    }
                    // Sort by appointment date/time
                    appointmentList.sortBy { it.timestampMillis }
                    adapter.notifyDataSetChanged()

                    // Show empty state or content
                    updateViewVisibility()
                } else {
                    appointmentList.clear()
                    adapter.notifyDataSetChanged()
                    updateViewVisibility()
                }
                binding.progressBar.visibility = View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@BookingApprovalRecords, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
            }
        })
    }

    private fun updateViewVisibility() {
        if (appointmentList.isEmpty()) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.bookingRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.bookingRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun updateBookingStatus(booking: BookingData, newStatus: String) {
        binding.progressBar.visibility = View.VISIBLE

        // Update status in all three locations
        val updates = HashMap<String, Any>()

        // Main bookings reference
        updates["/bookings/${booking.bookingId}/status"] = newStatus

        // Doctor bookings reference
        updates["/doctorBookings/${booking.doctorEmail.replace(".", ",")}" +
                "/${booking.bookingId}/status"] = newStatus

        // User bookings reference
        updates["/userBookings/${booking.patientEmail.replace(".", ",")}" +
                "/${booking.bookingId}/status"] = newStatus

        // Add note if it's a decline
        if (booking.declineReason != null && booking.declineReason!!.isNotEmpty()) {
            updates["/bookings/${booking.bookingId}/declineReason"] = booking.declineReason!!
            updates["/doctorBookings/${booking.doctorEmail.replace(".", ",")}" +
                    "/${booking.bookingId}/declineReason"] = booking.declineReason!!
            updates["/userBookings/${booking.patientEmail.replace(".", ",")}" +
                    "/${booking.bookingId}/declineReason"] = booking.declineReason!!
        }

        // Apply all updates atomically
        database.reference.updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Appointment ${if (newStatus == "confirmed") "approved" else "declined"}",
                    Toast.LENGTH_SHORT
                ).show()

                // Refresh the list
                if (binding.pendingFilterChip.isChecked) {
                    loadPendingAppointments()
                } else {
                    loadAllAppointments()
                }
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
            }
    }

    private fun showDeclineReasonDialog(booking: BookingData) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Decline Appointment")
        builder.setMessage("Please provide a reason for declining (optional):")

        val input = AppCompatEditText(this)
        builder.setView(input)

        builder.setPositiveButton("Decline") { _, _ ->
            val reason = input.text.toString().trim()
            if (reason.isNotEmpty()) {
                booking.declineReason = reason
            }
            updateBookingStatus(booking, "declined")
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }
}