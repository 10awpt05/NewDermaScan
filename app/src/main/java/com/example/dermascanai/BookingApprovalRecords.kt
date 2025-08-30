package com.example.dermascanai

import android.os.Bundle
import android.view.View
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.ActivityBookingRecordsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

class BookingApprovalRecords : AppCompatActivity() {
    private lateinit var binding: ActivityBookingRecordsBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: BookingApprovalAdapter
    private val appointmentList = mutableListOf<BookingData>()
    private var clinicName: String = ""
    private var currentUserEmail: String = ""
    private var selectedDate: Calendar = Calendar.getInstance()
    private lateinit var clinicId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        auth = FirebaseAuth.getInstance()

        currentUserEmail = auth.currentUser?.email ?: ""
        if (currentUserEmail.isEmpty()) {
            Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        fetchClinicInfo()

        setupRecyclerView()

        binding.backBTN.setOnClickListener {
            finish()
        }
        clinicId = auth.currentUser?.uid ?: ""

        val chipStatusMap = mapOf(
            binding.pendingFilterChip to "pending",
            binding.approvedFilterChip to "confirmed",
            binding.declinedFilterChip to "declined",
            binding.cancelledFilterChip to "cancelled",
            binding.allFilterChip to null
        )

        chipStatusMap.forEach { (chip, status) ->
            chip.setOnClickListener {
                chipStatusMap.keys.forEach { it.isChecked = false }
                chip.isChecked = true
                loadAppointments(status)
            }
        }







//        binding.pendingFilterChip.setOnClickListener {
//            binding.pendingFilterChip.isChecked = true
//            binding.allFilterChip.isChecked = false
//            binding.approvedFilterChip.isChecked = false
//            binding.declinedFilterChip.isChecked = false
//            binding.cancelledFilterChip.isChecked = false
//            loadPendingAppointments()
//        }
//
//        binding.approvedFilterChip.setOnClickListener {
//            binding.approvedFilterChip.isChecked = true
//            binding.allFilterChip.isChecked = false
//            binding.pendingFilterChip.isChecked = false
//            binding.declinedFilterChip.isChecked = false
//            binding.cancelledFilterChip.isChecked = false
//            loadApprovedAppointments()
//        }
//
//        binding.declinedFilterChip.setOnClickListener {
//            binding.declinedFilterChip.isChecked = true
//            binding.allFilterChip.isChecked = false
//            binding.pendingFilterChip.isChecked = false
//            binding.approvedFilterChip.isChecked = false
//            binding.cancelledFilterChip.isChecked = false
//            loadDeclineAppointments()
//        }
//
//        binding.cancelledFilterChip.setOnClickListener {
//            binding.cancelledFilterChip.isChecked = true
//            binding.allFilterChip.isChecked = false
//            binding.pendingFilterChip.isChecked = false
//            binding.approvedFilterChip.isChecked = false
//            binding.declinedFilterChip.isChecked = false
//            loadCancelledAppointments()
//        }
//
//        binding.allFilterChip.setOnClickListener {
//            binding.allFilterChip.isChecked = true
//            binding.pendingFilterChip.isChecked = false
//            binding.approvedFilterChip.isChecked = false
//            binding.declinedFilterChip.isChecked = false
//            binding.cancelledFilterChip.isChecked = false
//            loadAllAppointments()
//        }

        updateDateDisplay()

        binding.prevDateBtn.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_MONTH, -1)
            updateDateDisplay()
            refreshCurrentView()
        }

        binding.nextDateBtn.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_MONTH, 1)
            updateDateDisplay()
            refreshCurrentView()
        }

    }


    private fun updateDateDisplay() {
        val sdf = java.text.SimpleDateFormat("MMM dd - EEE", Locale.getDefault())
        binding.currentDateText.text = sdf.format(selectedDate.time)
    }


    private fun fetchClinicInfo() {
        val clinicInfoRef = database.getReference("clinicInfo")

        clinicInfoRef.orderByChild("email").equalTo(currentUserEmail)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (childSnapshot in snapshot.children) {
                            val clinicInfo = childSnapshot.getValue(ClinicInfo::class.java)
                            val clinicId = childSnapshot.key ?: ""

                            if (clinicInfo != null) {
                                clinicName = clinicInfo.clinicName ?: ""
                                Log.d("BookingApprovalRecords", "Found clinicId: $clinicId, clinicName: $clinicName")

                                val openApproved = intent.getBooleanExtra("openApprovedTab", false)
                                if (openApproved) {
                                    binding.approvedFilterChip.performClick()
                                } else {
                                    binding.pendingFilterChip.performClick()
                                }
                                return
                            }
                        }
                    } else {
                        Log.w("BookingApprovalRecords", "No clinic found for user: $currentUserEmail")
                        Toast.makeText(this@BookingApprovalRecords, "Could not find clinic information", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("BookingApprovalRecords", "Database error: ${error.message}")
                }
            })
    }


    private fun setupRecyclerView() {
        adapter = BookingApprovalAdapter(
            appointmentList,
            onApprove = { booking -> updateBookingStatus(booking, "confirmed") },
            onDecline = { booking -> showDeclineReasonDialog(booking) },
            onCancel = { booking -> showCancellationReasonDialog(booking) }
        )
        binding.bookingRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.bookingRecyclerView.adapter = adapter
    }


    private fun loadAppointments(status: String? = null) {
        if (clinicId.isEmpty()) return

        binding.progressBar.visibility = View.VISIBLE
        appointmentList.clear()

        val bookingsRef = database.getReference("clinicInfo")
            .child(clinicId)
            .child("bookings")

        val query = status?.let { bookingsRef.orderByChild("status").equalTo(it) } ?: bookingsRef

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (bookingSnapshot in snapshot.children) { // bookingId level
                        val booking = bookingSnapshot.getValue(BookingData::class.java)
                        booking?.let { appointmentList.add(it) }
                    }
                    appointmentList.sortByDescending { it.timestampMillis }
                    adapter.notifyDataSetChanged()
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






//    private fun loadPendingAppointments() {
//        if (clinicName.isEmpty()) {
//            Log.w("BookingApprovalRecords", "Clinic name is empty, cannot load appointments")
//            return
//        }
//
//        binding.progressBar.visibility = View.VISIBLE
//        appointmentList.clear()
//
//        val doctorBookingsRef = database.getReference("clinicBookings")
//            .child(clinicName.replace(" ", "_").replace(".", ","))
//
//        Log.d("BookingApprovalRecords", "Loading pending appointments for clinic: $clinicName")
//        Log.d("BookingApprovalRecords", "Firebase path: clinicBookings/${clinicName.replace(" ", "_").replace(".", ",")}")
//
//        doctorBookingsRef.orderByChild("status").equalTo("pending")
//            .addListenerForSingleValueEvent(object : ValueEventListener {
//                override fun onDataChange(snapshot: DataSnapshot) {
//                    Log.d("BookingApprovalRecords", "Pending bookings found: ${snapshot.childrenCount}")
//
//                    if (snapshot.exists()) {
//                        for (bookingSnapshot in snapshot.children) {
//                            Log.d("BookingApprovalRecords", "Booking key: ${bookingSnapshot.key}")
//                            val booking = bookingSnapshot.getValue(BookingData::class.java)
//                            if (booking != null) {
//                                Log.d("BookingApprovalRecords", "Booking status: ${booking.status}, patient: ${booking.patientEmail}")
//                                appointmentList.add(booking)
//                            }
//                        }
//                        appointmentList.sortByDescending { it.timestampMillis }
//                        adapter.notifyDataSetChanged()
//                        updateViewVisibility()
//                    } else {
//                        Log.d("BookingApprovalRecords", "No pending bookings found")
//                        appointmentList.clear()
//                        adapter.notifyDataSetChanged()
//                        updateViewVisibility()
//                    }
//                    binding.progressBar.visibility = View.GONE
//                }
//
//                override fun onCancelled(error: DatabaseError) {
//                    Toast.makeText(this@BookingApprovalRecords, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
//                    Log.e("BookingApprovalRecords", "Error loading pending appointments: ${error.message}")
//                    binding.progressBar.visibility = View.GONE
//                }
//            })
//    }
//
//    private fun loadApprovedAppointments() {
//        if (clinicName.isEmpty()) return
//
//        binding.progressBar.visibility = View.VISIBLE
//        appointmentList.clear()
//
//        val doctorBookingsRef = database.getReference("clinicBookings")
//            .child(clinicName.replace(" ", "_").replace(".", ","))
//
//        doctorBookingsRef.orderByChild("status").equalTo("confirmed")
//            .addListenerForSingleValueEvent(object : ValueEventListener {
//                override fun onDataChange(snapshot: DataSnapshot) {
//                    if (snapshot.exists()) {
//                        for (bookingSnapshot in snapshot.children) {
//                            val booking = bookingSnapshot.getValue(BookingData::class.java)
//                            booking?.let {
//                                appointmentList.add(it)
//                            }
//                        }
//                        appointmentList.sortByDescending { it.timestampMillis }
//                        adapter.notifyDataSetChanged()
//                        updateViewVisibility()
//                    } else {
//                        appointmentList.clear()
//                        adapter.notifyDataSetChanged()
//                        updateViewVisibility()
//                    }
//                    binding.progressBar.visibility = View.GONE
//                }
//
//                override fun onCancelled(error: DatabaseError) {
//                    Toast.makeText(this@BookingApprovalRecords, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
//                    binding.progressBar.visibility = View.GONE
//                }
//            })
//    }
//
//    private fun loadDeclineAppointments() {
//        if (clinicName.isEmpty()) return
//
//        binding.progressBar.visibility = View.VISIBLE
//        appointmentList.clear()
//
//        val doctorBookingsRef = database.getReference("clinicBookings")
//            .child(clinicName.replace(" ", "_").replace(".", ","))
//
//        doctorBookingsRef.orderByChild("status").equalTo("declined")
//            .addListenerForSingleValueEvent(object : ValueEventListener {
//                override fun onDataChange(snapshot: DataSnapshot) {
//                    if (snapshot.exists()) {
//                        for (bookingSnapshot in snapshot.children) {
//                            val booking = bookingSnapshot.getValue(BookingData::class.java)
//                            booking?.let {
//                                appointmentList.add(it)
//                            }
//                        }
//                        appointmentList.sortByDescending { it.timestampMillis }
//                        adapter.notifyDataSetChanged()
//                        updateViewVisibility()
//                    } else {
//                        appointmentList.clear()
//                        adapter.notifyDataSetChanged()
//                        updateViewVisibility()
//                    }
//                    binding.progressBar.visibility = View.GONE
//                }
//
//                override fun onCancelled(error: DatabaseError) {
//                    Toast.makeText(this@BookingApprovalRecords, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
//                    binding.progressBar.visibility = View.GONE
//                }
//            })
//    }
//
//    private fun loadCancelledAppointments() {
//        if (clinicName.isEmpty()) return
//
//        binding.progressBar.visibility = View.VISIBLE
//        appointmentList.clear()
//
//        val doctorBookingsRef = database.getReference("clinicBookings")
//            .child(clinicName.replace(" ", "_").replace(".", ","))
//
//        doctorBookingsRef.orderByChild("status").equalTo("cancelled")
//            .addListenerForSingleValueEvent(object : ValueEventListener {
//                override fun onDataChange(snapshot: DataSnapshot) {
//                    if (snapshot.exists()) {
//                        for (bookingSnapshot in snapshot.children) {
//                            val booking = bookingSnapshot.getValue(BookingData::class.java)
//                            booking?.let {
//                                appointmentList.add(it)
//                            }
//                        }
//                        appointmentList.sortByDescending { it.timestampMillis }
//                        adapter.notifyDataSetChanged()
//                        updateViewVisibility()
//                    } else {
//                        appointmentList.clear()
//                        adapter.notifyDataSetChanged()
//                        updateViewVisibility()
//                    }
//                    binding.progressBar.visibility = View.GONE
//                }
//
//                override fun onCancelled(error: DatabaseError) {
//                    Toast.makeText(this@BookingApprovalRecords, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
//                    binding.progressBar.visibility = View.GONE
//                }
//            })
//    }
//
//    private fun loadAllAppointments() {
//        if (clinicName.isEmpty()) return
//
//        binding.progressBar.visibility = View.VISIBLE
//        appointmentList.clear()
//
//        val doctorBookingsRef = database.getReference("clinicBookings")
//            .child(clinicName.replace(" ", "_").replace(".", ","))
//
//        doctorBookingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
//            override fun onDataChange(snapshot: DataSnapshot) {
//                if (snapshot.exists()) {
//                    for (bookingSnapshot in snapshot.children) {
//                        val booking = bookingSnapshot.getValue(BookingData::class.java)
//                        booking?.let {
//                            appointmentList.add(it)
//                        }
//                    }
//                    appointmentList.sortByDescending { it.timestampMillis }
//                    adapter.notifyDataSetChanged()
//                    updateViewVisibility()
//                } else {
//                    appointmentList.clear()
//                    adapter.notifyDataSetChanged()
//                    updateViewVisibility()
//                }
//                binding.progressBar.visibility = View.GONE
//            }
//
//            override fun onCancelled(error: DatabaseError) {
//                Toast.makeText(this@BookingApprovalRecords, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
//                binding.progressBar.visibility = View.GONE
//            }
//        })
//    }

    private fun updateViewVisibility() {
        if (appointmentList.isEmpty()) {
            when {
                binding.pendingFilterChip.isChecked -> {
                    binding.emptyStateLayout.visibility = View.VISIBLE
                    binding.emptyStateDeclinedLayout.visibility = View.GONE
                    binding.emptyStateApprovedLayout.visibility = View.GONE
                    binding.emptyStateCancelledLayout.visibility = View.GONE
                }
                binding.declinedFilterChip.isChecked -> {
                    binding.emptyStateLayout.visibility = View.GONE
                    binding.emptyStateDeclinedLayout.visibility = View.VISIBLE
                    binding.emptyStateApprovedLayout.visibility = View.GONE
                    binding.emptyStateCancelledLayout.visibility = View.GONE
                }
                binding.approvedFilterChip.isChecked -> {
                    binding.emptyStateLayout.visibility = View.GONE
                    binding.emptyStateDeclinedLayout.visibility = View.GONE
                    binding.emptyStateApprovedLayout.visibility = View.VISIBLE
                    binding.emptyStateCancelledLayout.visibility = View.GONE
                }
                binding.cancelledFilterChip.isChecked -> {
                    binding.emptyStateLayout.visibility = View.GONE
                    binding.emptyStateDeclinedLayout.visibility = View.GONE
                    binding.emptyStateApprovedLayout.visibility = View.GONE
                    binding.emptyStateCancelledLayout.visibility = View.VISIBLE
                }
                else -> {
                    binding.emptyStateLayout.visibility = View.VISIBLE
                    binding.emptyStateDeclinedLayout.visibility = View.GONE
                    binding.emptyStateApprovedLayout.visibility = View.GONE
                    binding.emptyStateCancelledLayout.visibility = View.GONE
                }
            }
            binding.bookingRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.emptyStateDeclinedLayout.visibility = View.GONE
            binding.emptyStateApprovedLayout.visibility = View.GONE
            binding.emptyStateCancelledLayout.visibility = View.GONE
            binding.bookingRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun updateBookingStatus(booking: BookingData, newStatus: String) {
        if (clinicId.isEmpty()) {
            Toast.makeText(this, "Error: Clinic ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = booking.userId ?: return
        val bookingId = booking.bookingId ?: return

        binding.progressBar.visibility = View.VISIBLE

        val updates = HashMap<String, Any?>()

        // ✅ Master bookings
        updates["/bookings/$bookingId/status"] = newStatus

        // ✅ Clinic’s bookings
        updates["/clinicInfo/$clinicId/bookings/$bookingId/status"] = newStatus

        // ✅ User’s bookings
        updates["/userInfo/$userId/bookings/$bookingId/status"] = newStatus

        // Add decline reason if available
        booking.declineReason?.takeIf { it.isNotEmpty() }?.let {
            updates["/bookings/$bookingId/declineReason"] = it
            updates["/clinicInfo/$clinicId/bookings/$bookingId/declineReason"] = it
            updates["/userInfo/$userId/bookings/$bookingId/declineReason"] = it
        }

        // Add cancellation reason if available
        booking.cancellationReason?.takeIf { it.isNotEmpty() }?.let {
            updates["/bookings/$bookingId/cancellationReason"] = it
            updates["/clinicInfo/$clinicId/bookings/$bookingId/cancellationReason"] = it
            updates["/userInfo/$userId/bookings/$bookingId/cancellationReason"] = it
        }

        // Apply all updates
        database.reference.updateChildren(updates)
            .addOnSuccessListener {
                val statusMessage = when (newStatus) {
                    "confirmed" -> "approved"
                    "cancelled" -> "cancelled"
                    else -> "declined"
                }

                Toast.makeText(this, "Appointment $statusMessage", Toast.LENGTH_SHORT).show()

                // ✅ Send notification to patient
                sendBookingNotification(
                    toUserId = userId,
                    fromUserId = clinicId,
                    status = statusMessage,
                    message = when (newStatus) {
                        "confirmed" -> "Your appointment has been approved"
                        "cancelled" -> "Your appointment has been cancelled"
                        else -> "Your appointment has been declined"
                    }
                )

                refreshCurrentView()
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("BookingApprovalRecords", "Error updating booking status", e)
                binding.progressBar.visibility = View.GONE
            }
    }



    private fun refreshCurrentView() {
        when {
            binding.pendingFilterChip.isChecked -> loadAppointments("pending")
            binding.approvedFilterChip.isChecked -> loadAppointments("confirmed")
            binding.declinedFilterChip.isChecked -> loadAppointments("declined")
            binding.cancelledFilterChip.isChecked -> loadAppointments("cancelled")
            else -> loadAppointments()
        }
    }
//    private fun refreshCurrentView() {
//        when {
//            binding.pendingFilterChip.isChecked -> loadPendingAppointments()
//            binding.approvedFilterChip.isChecked -> loadApprovedAppointments()
//            binding.declinedFilterChip.isChecked -> loadDeclineAppointments()
//            binding.cancelledFilterChip.isChecked -> loadCancelledAppointments()
//            else -> loadAllAppointments()
//        }
//    }

    private fun showDeclineReasonDialog(booking: BookingData) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Decline Appointment")
        builder.setMessage("Please provide a reason for declining (optional):")

        val input = androidx.appcompat.widget.AppCompatEditText(this)
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

    private fun showCancellationReasonDialog(booking: BookingData) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Cancel Appointment")
        builder.setMessage("Please provide a reason for cancellation:")

        val input = androidx.appcompat.widget.AppCompatEditText(this)
        builder.setView(input)

        builder.setPositiveButton("Cancel Appointment") { _, _ ->
            val reason = input.text.toString().trim()
            if (reason.isNotEmpty()) {
                booking.cancellationReason = reason
                updateBookingStatus(booking, "cancelled")
            } else {
                Toast.makeText(this, "Please provide a cancellation reason", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Back") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun sendBookingNotification(
        toUserId: String,
        fromUserId: String,
        status: String, // "approved", "declined", or "cancelled"
        message: String
    ) {
        val database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").reference
        val notificationId = database.child("notifications").child(toUserId).push().key ?: return
        val timestamp = System.currentTimeMillis()

        val notification = Notification(
            notificationId = notificationId,
            postId = "",
            fromUserId = fromUserId,
            toUserId = toUserId,
            type = "booking",
            message = message,
            timestamp = timestamp,
            isRead = false,
            status = status
        )

        database.child("notifications").child(toUserId).child(notificationId).setValue(notification)
    }

}