package com.example.dermascanai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // Called when a new token is generated (e.g. reinstall, update, etc.)
    override fun onNewToken(token: String) {
        super.onNewToken(token)

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val db = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            val userRef = db.getReference("userInfo").child(userId)

            userRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists() && snapshot.child("role").value != null) {
                    val role = snapshot.child("role").value.toString()
                    val node = if (role == "derma") "clinicInfo" else "userInfo"

                    db.getReference(node)
                        .child(userId)
                        .child("fcmToken")
                        .setValue(token)
                }
            }
        }
    }



    // Called when a push notification is received
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM", "Message received: ${remoteMessage.notification?.title} | ${remoteMessage.notification?.body}")

        val title = remoteMessage.notification?.title ?: "DermaScanAI"
        val body = remoteMessage.notification?.body ?: "You have a new notification"
        val type = remoteMessage.data["type"] ?: ""

        showNotification(title, body, type)
    }

    private fun showNotification(title: String, body: String, type: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        var accountType = "user" // default

        if (currentUser != null) {
            val dbRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("userInfo")
                .child(currentUser.uid)

            dbRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists() && snapshot.child("role").value != null) {
                    accountType = snapshot.child("role").value.toString()
                }

                val intent = when (type) {
                    "booking" -> if (accountType == "user")
                        Intent(this, BookingHistory::class.java)
                    else
                        Intent(this, BookingApprovalRecords::class.java)

                    "message" -> Intent(this, ChatUserListActivity::class.java)

                    "blog" -> Intent(this, BlogActivity::class.java)

                    "registration" -> Intent(this, DermaPage::class.java)

                    else -> if (accountType == "user")
                        Intent(this, UserPage::class.java)
                    else
                        Intent(this, DermaPage::class.java)
                }

                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )

                val channelId = "dermascan_notifications"
                val builder = NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        channelId,
                        "App Notifications",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                    manager.createNotificationChannel(channel)
                }

                manager.notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } else {
            // fallback if no user logged in
            val loginIntent = Intent(this, Login::class.java)
            loginIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, loginIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(this, "dermascan_notifications")
                .setSmallIcon(R.drawable.notification_white)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }


}
