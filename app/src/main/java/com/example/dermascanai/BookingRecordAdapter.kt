package com.example.dermascanai

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class BookingApprovalAdapter(
    private val bookingList: List<BookingData>,
    private val onApprove: (BookingData) -> Unit,
    private val onDecline: (BookingData) -> Unit
) : RecyclerView.Adapter<BookingApprovalAdapter.BookingViewHolder>() {

    class BookingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val patientName: TextView = itemView.findViewById(R.id.patientNameTv)
        val appointmentDate: TextView = itemView.findViewById(R.id.appointmentDateTv)
        val appointmentTime: TextView = itemView.findViewById(R.id.appointmentTimeTv)
        val patientMessage: TextView = itemView.findViewById(R.id.patientMessageTv)
        val statusBadge: TextView = itemView.findViewById(R.id.statusBadgeTv)
        val patientImage: ImageView = itemView.findViewById(R.id.patientImageView) // Hidden but we keep the reference
        val approveButton: Button = itemView.findViewById(R.id.approveButton)
        val declineButton: Button = itemView.findViewById(R.id.declineButton)
        val declineReasonLayout: View = itemView.findViewById(R.id.declineReasonLayout)
        val declineReasonText: TextView = itemView.findViewById(R.id.declineReasonTv)
        val actionButtonsLayout: View = itemView.findViewById(R.id.actionButtonsLayout)
        val bookingCard: CardView = itemView.findViewById(R.id.bookingCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_approval, parent, false)
        return BookingViewHolder(view)
    }

    override fun getItemCount(): Int = bookingList.size

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookingList[position]

        // Load patient information
        loadPatientInfo(booking.patientEmail, holder)

        // Set appointment details
        holder.appointmentDate.text = booking.date
        holder.appointmentTime.text = booking.time
        holder.patientMessage.text = "Message: ${booking.message}"

        // Set status and UI based on status
        setStatusUI(holder, booking.status)

        // Show decline reason if available
        if (booking.status == "declined" && booking.declineReason != null && booking.declineReason!!.isNotEmpty()) {
            holder.declineReasonLayout.visibility = View.VISIBLE
            holder.declineReasonText.text = booking.declineReason
        } else {
            holder.declineReasonLayout.visibility = View.GONE
        }

        // Show action buttons only for pending appointments
        if (booking.status == "pending") {
            holder.actionButtonsLayout.visibility = View.VISIBLE

            holder.approveButton.setOnClickListener {
                onApprove(booking)
            }

            holder.declineButton.setOnClickListener {
                onDecline(booking)
            }
        } else {
            holder.actionButtonsLayout.visibility = View.GONE
        }
    }

    private fun loadPatientInfo(patientEmail: String, holder: BookingViewHolder) {
        val database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        val userRef = database.getReference("users").child(patientEmail.replace(".", ","))

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val user = snapshot.getValue(UserData::class.java)
                    user?.let {
                        // Set user name
                        holder.patientName.text = it.name ?: "Unknown Patient"
                    }
                } else {
                    holder.patientName.text = patientEmail
                }
            }

            override fun onCancelled(error: DatabaseError) {
                holder.patientName.text = patientEmail
            }
        })
    }

    private fun setStatusUI(holder: BookingViewHolder, status: String) {
        val context = holder.itemView.context

        holder.statusBadge.text = status.capitalize(Locale.getDefault())

        when (status) {
            "pending" -> {
                holder.statusBadge.setBackgroundResource(R.drawable.status_background)
                holder.statusBadge.setTextColor(ContextCompat.getColor(context, R.color.orange))
            }
            "confirmed" -> {
                holder.statusBadge.setBackgroundResource(R.drawable.status_background)
                holder.statusBadge.setTextColor(ContextCompat.getColor(context, R.color.green))
            }
            "declined" -> {
                holder.statusBadge.setBackgroundResource(R.drawable.status_background)
                holder.statusBadge.setTextColor(ContextCompat.getColor(context, R.color.red))
            }
            "completed" -> {
                holder.statusBadge.setBackgroundResource(R.drawable.status_background)
                holder.statusBadge.setTextColor(ContextCompat.getColor(context, R.color.blue))
            }
        }
    }
}