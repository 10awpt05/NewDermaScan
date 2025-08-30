package com.example.dermascanai

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.dermascanai.databinding.ItemNotificationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class NotificationAdapter(
    private val context: Context,
    private var notifications: List<Notification> // ✅ use Notification instead of String
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    inner class NotificationViewHolder(val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val notif = notifications[adapterPosition]
                handleNotificationClick(notif)
            }
        }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.binding.notificationMessage.text = notification.message
    }

    override fun getItemCount(): Int = notifications.size

    fun updateNotifications(newNotifications: List<Notification>) {
        notifications = newNotifications
        notifyDataSetChanged()  // Notify the adapter that the data has changed
    }

    private fun handleNotificationClick(notification: Notification) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        when (notification.type) {
            "booking" -> {
                checkAccountType(currentUserId) { accountType ->
                    when (accountType) {
                        "derma" -> {
                            val intent = Intent(context, BookingApprovalRecords::class.java)
                            intent.putExtra("bookingId", notification.notificationId)
                            context.startActivity(intent)
                        }
                        "user" -> {
                            val intent = Intent(context, BookingHistory::class.java)
                            intent.putExtra("bookingId", notification.notificationId)
                            context.startActivity(intent)
                        }
                        else -> {
                            Toast.makeText(context, "Unknown account type", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            "message" -> {
                checkAccountType(currentUserId) { accountType ->
                    when (accountType) {
                        "derma" -> {
                            val intent = Intent(context, MessageMe::class.java)
                            intent.putExtra("senderId", notification.toUserId)
                            context.startActivity(intent)
                        }
                        "user" -> {
                            val intent = Intent(context, MessageMe::class.java)
                            intent.putExtra("receiverId", notification.fromUserId)
                            context.startActivity(intent)
                        }
                        else -> {
                            Toast.makeText(context, "Unknown account type", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            "comment" -> {
                val intent = Intent(context, BlogView::class.java)
                intent.putExtra("postId", notification.postId)
                context.startActivity(intent)
            }
            "reply" -> {
                val intent = Intent(context, BlogView::class.java)
                intent.putExtra("postId", notification.postId)
                context.startActivity(intent)
            }

            else -> {
                Toast.makeText(context, "Unknown notification type", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun checkAccountType(userId: String, callback: (String) -> Unit) {
        val db = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").reference

        // Check if user is a clinic
        db.child("clinicInfo").child(userId).get().addOnSuccessListener {
            if (it.exists()) {
                callback("derma")
            } else {
                // Otherwise check if user is a regular user
                db.child("userInfo").child(userId).get().addOnSuccessListener { userSnap ->
                    if (userSnap.exists()) {
                        callback("user")
                    } else {
                        callback("unknown")
                    }
                }
            }
        }
    }

}
