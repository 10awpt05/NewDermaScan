package com.example.dermascanai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dermascanai.databinding.ItemBookingApprovalBinding
import java.text.SimpleDateFormat
import java.util.*

class BookingApprovalAdapter(
    private val bookings: List<BookingData>,
    private val onApprove: (BookingData) -> Unit,
    private val onDecline: (BookingData) -> Unit,
    private val onCancel: (BookingData) -> Unit
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
        val booking = bookings[position]
        holder.bind(booking)
    }

    override fun getItemCount(): Int = bookings.size

    inner class BookingViewHolder(private val binding: ItemBookingApprovalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(booking: BookingData) {
            // Format the date
            // Format both date and time

            // Combine date with the booked time range
            val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
            val appointmentDate = Date(booking.timestampMillis)
            val formattedDate = dateFormat.format(appointmentDate)
            val timeRange = if (!booking.time.isNullOrEmpty()) booking.time else "Time not set"

            binding.appointmentDateTv.text = "$formattedDate at $timeRange"

            binding.patientNameTv.text = booking.patientEmail
            binding.notesTextView.text = booking.message

            if (booking.bookingId.isNotEmpty()) {
                binding.bookingIdTextView.text = "#${booking.bookingId.take(8).uppercase()}"
            }

            // Display the service instead of clinic name
            val serviceDisplayName = if (!booking.service.isNullOrEmpty()) {
                booking.service
            } else {
                "General Consultation"
            }
            binding.serviceTextView.text = serviceDisplayName

            val bookingDateFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
            val bookingTimestamp = if (booking.createdAt > 0) booking.createdAt else booking.timestampMillis
            binding.bookingTimestampTv.text = "Booked on ${bookingDateFormat.format(Date(bookingTimestamp))}"

            configureStatusElements(booking)

            if (!booking.declineReason.isNullOrEmpty() && booking.status == "declined") {
                binding.declineReasonLayout.visibility = View.VISIBLE
                binding.declineReasonTv.text = booking.declineReason
            } else {
                binding.declineReasonLayout.visibility = View.GONE
            }

            if (!booking.cancellationReason.isNullOrEmpty() && booking.status == "cancelled") {
                binding.cancellationReasonLayout.visibility = View.VISIBLE
                binding.cancellationReasonTv.text = booking.cancellationReason
            } else {
                binding.cancellationReasonLayout.visibility = View.GONE
            }

            binding.approveButton.setOnClickListener {
                onApprove(booking)
            }

            binding.declineButton.setOnClickListener {
                onDecline(booking)
            }

            binding.cancelButton.setOnClickListener {
                onCancel(booking)
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

                    // Configure status bar with confirmed styling
                    binding.statusTextView.text = "Confirmed"
                    binding.statusLayout.setBackgroundResource(R.drawable.status_confirmed_background)
                    binding.statusIcon.setImageResource(R.drawable.check_circle)
                }
                "cancelled" -> {
                    // Hide all action buttons for cancelled appointments
                    binding.approvalButtonsLayout.visibility = View.GONE
                    binding.cancelButton.visibility = View.GONE
                    binding.statusLayout.visibility = View.VISIBLE

                    // Configure status bar with cancelled styling
                    binding.statusTextView.text = "Cancelled"
                    binding.statusLayout.setBackgroundResource(R.drawable.status_cancelled_background)
                    binding.statusIcon.setImageResource(R.drawable.cancelled)
                }
                "declined" -> {
                    // Hide all action buttons for declined appointments
                    binding.approvalButtonsLayout.visibility = View.GONE
                    binding.cancelButton.visibility = View.GONE
                    binding.statusLayout.visibility = View.VISIBLE

                    // Configure status bar with declined styling
                    binding.statusTextView.text = "Declined"
                    binding.statusLayout.setBackgroundResource(R.drawable.status_declined_background)
                    binding.statusIcon.setImageResource(R.drawable.close_circle)
                }
                else -> {
                    // Default case for unknown status
                    binding.approvalButtonsLayout.visibility = View.GONE
                    binding.cancelButton.visibility = View.GONE
                    binding.statusLayout.visibility = View.VISIBLE
                    binding.statusTextView.text = booking.status.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                    }
                }
            }
        }
    }
}