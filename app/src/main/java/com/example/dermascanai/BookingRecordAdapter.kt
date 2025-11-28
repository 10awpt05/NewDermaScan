package com.example.dermascanai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dermascanai.databinding.ItemBookingApprovalBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class BookingApprovalAdapter(
    private val bookings: List<BookingData>,
    private val onApprove: (BookingData) -> Unit,
    private val onDecline: (BookingData) -> Unit,
    private val onCancel: (BookingData) -> Unit,
    private val onDone: (BookingData) -> Unit
) : RecyclerView.Adapter<BookingApprovalAdapter.BookingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val binding = ItemBookingApprovalBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BookingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        holder.bind(bookings[position])
    }

    override fun getItemCount(): Int = bookings.size

    inner class BookingViewHolder(private val binding: ItemBookingApprovalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(booking: BookingData) {
            // Set text fields
            binding.patientNameTv.text = booking.patientName ?: booking.patientEmail
            binding.notesTextView.text = booking.message
            binding.serviceTextView.text = booking.service ?: "General Consultation"
            binding.bookingIdTextView.text = "#${booking.bookingId.take(8).uppercase()}"

            val bookingDateFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
            val timestamp = if (booking.createdAt > 0) booking.createdAt else booking.timestampMillis
            binding.bookingTimestampTv.text = "Booked on ${bookingDateFormat.format(Date(timestamp))}"

            // === DONE BUTTON LOGIC ===
            val currentCal = Calendar.getInstance()
            val bookingCal = Calendar.getInstance()
            val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            booking.date?.let {
                bookingCal.time = sdf.parse(it) ?: Date()
            }

            val isToday = currentCal.get(Calendar.YEAR) == bookingCal.get(Calendar.YEAR) &&
                    currentCal.get(Calendar.DAY_OF_MONTH) == bookingCal.get(Calendar.DAY_OF_MONTH) &&
                    currentCal.get(Calendar.MONTH) == bookingCal.get(Calendar.MONTH)

            // Show Done button only if today
            binding.doneButton.visibility = if (isToday) View.VISIBLE else View.GONE

            // Disable Done and Cancel if already done
            val isDone = booking.status == "done"
            binding.doneButton.isEnabled = !isDone
            binding.doneButton.setBackgroundColor(
                binding.root.context.getColor(android.R.color.darker_gray)
            )
            binding.cancelButton.isEnabled = !isDone

            // Button click listeners
            binding.approveButton.setOnClickListener { onApprove(booking) }
            binding.declineButton.setOnClickListener { onDecline(booking) }
            binding.cancelButton.setOnClickListener { onCancel(booking) }

            binding.doneButton.setOnClickListener {
                val context = binding.root.context
                val input = androidx.appcompat.widget.AppCompatEditText(context)
                input.hint = "Add a note (optional)"

                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Complete Appointment")
                    .setView(input)
                    .setPositiveButton("Save") { dialog, _ ->
                        val additionalNote = input.text.toString().trim()
                        val updatedBooking = booking.copy(
                            message = if (additionalNote.isNotEmpty()) additionalNote else booking.message,
                            status = "done"
                        )
                        onDone(updatedBooking)
                        // Disable buttons
                        binding.doneButton.isEnabled = false
                        binding.cancelButton.isEnabled = false

                        // Update status in Firebase
                        val clinicId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton
                        val bookingId = booking.bookingId ?: return@setPositiveButton
                        val updates = mapOf(
                            "status" to "done",
                            "doneTimestamp" to System.currentTimeMillis()
                        )
                        FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").reference
                            .child("clinicInfo")
                            .child(clinicId)
                            .child("bookings")
                            .child(bookingId)
                            .updateChildren(updates)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    .show()
            }

            // Configure status UI
            configureStatusElements(booking)

            // Show decline reason if applicable
            if (!booking.declineReason.isNullOrEmpty() && booking.status == "declined") {
                binding.declineReasonLayout.visibility = View.VISIBLE
                binding.declineReasonTv.text = booking.declineReason
            } else {
                binding.declineReasonLayout.visibility = View.GONE
            }

            // Show cancellation reason if applicable
            if (!booking.cancellationReason.isNullOrEmpty() && booking.status == "cancelled") {
                binding.cancellationReasonLayout.visibility = View.VISIBLE
                binding.cancellationReasonTv.text = booking.cancellationReason
            } else {
                binding.cancellationReasonLayout.visibility = View.GONE
            }
        }

        private fun configureStatusElements(booking: BookingData) {
            when (booking.status) {
                "pending" -> {
                    binding.approvalButtonsLayout.visibility = View.VISIBLE
                    binding.approveButton.visibility = View.VISIBLE
                    binding.declineButton.visibility = View.VISIBLE
                    binding.cancelButton.visibility = View.GONE
                    binding.statusLayout.visibility = View.GONE
                }
                "confirmed" -> {
                    binding.approvalButtonsLayout.visibility = View.GONE
                    binding.cancelButton.visibility = View.VISIBLE
                    binding.statusLayout.visibility = View.VISIBLE
                    binding.statusTextView.text = "Confirmed"
                    binding.statusLayout.setBackgroundResource(R.drawable.status_confirmed_background)
                    binding.statusIcon.setImageResource(R.drawable.check_circle)
                }
                "cancelled" -> {
                    binding.approvalButtonsLayout.visibility = View.GONE
                    binding.cancelButton.visibility = View.GONE
                    binding.statusLayout.visibility = View.VISIBLE
                    binding.statusTextView.text = "Cancelled"
                    binding.statusLayout.setBackgroundResource(R.drawable.status_cancelled_background)
                    binding.statusIcon.setImageResource(R.drawable.cancelled)
                }
                "declined" -> {
                    binding.approvalButtonsLayout.visibility = View.GONE
                    binding.cancelButton.visibility = View.GONE
                    binding.statusLayout.visibility = View.VISIBLE
                    binding.statusTextView.text = "Declined"
                    binding.statusLayout.setBackgroundResource(R.drawable.status_declined_background)
                    binding.statusIcon.setImageResource(R.drawable.close_circle)
                }
                "done" -> {
                    // Hide all action buttons for done
                    binding.approvalButtonsLayout.visibility = View.GONE
                    binding.cancelButton.visibility = View.GONE
                    binding.statusLayout.visibility = View.VISIBLE
                    binding.statusTextView.text = "Done"
                    binding.statusLayout.setBackgroundResource(R.drawable.status_confirmed_background)
                    binding.statusIcon.setImageResource(R.drawable.check_circle)
                    binding.statusIcon.setBackgroundColor(
                        binding.root.context.getColor(android.R.color.white)
                    )
                }
                else -> {
                    binding.approvalButtonsLayout.visibility = View.GONE
                    binding.cancelButton.visibility = View.GONE
                    binding.statusLayout.visibility = View.VISIBLE
                    binding.statusTextView.text = booking.status.replaceFirstChar { it.uppercase() }
                }
            }
        }
    }
}
