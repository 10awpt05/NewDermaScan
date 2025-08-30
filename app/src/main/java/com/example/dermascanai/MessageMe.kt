package com.example.dermascanai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.ActivityMessageMeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MessageMe : AppCompatActivity() {

    private lateinit var binding: ActivityMessageMeBinding
    private lateinit var database: DatabaseReference
    private lateinit var databaseScan: DatabaseReference
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var auth: FirebaseAuth
    private val messageList = ArrayList<Message>()
    private var receiverId: String? = null
    private var senderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageMeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .reference.child("messages")

        databaseScan = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("scanResults")

        receiverId = intent.getStringExtra("receiverId")
        senderId = intent.getStringExtra("senderId")

        receiverId?.let { loadProfile(it) }


        binding.backBTN.setOnClickListener {
            finish()
        }

        binding.attachFile.setOnClickListener {
            showScanPopup()
        }

        setupRecyclerView()
        loadMessages()

        binding.send.setOnClickListener {
            val messageText = binding.messageText.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                binding.messageText.setText("")

            }
        }

    }

    private fun showScanPopup() {
        val popupView = layoutInflater.inflate(R.layout.popup_scan_list, null)
        val listView = popupView.findViewById<ListView>(R.id.scanListView)
        val popupWindow = PopupWindow(popupView, 600, 800, true)
        popupWindow.elevation = 10f
        popupWindow.showAsDropDown(binding.attachFile)

        val scanList = mutableListOf<String>()
        val scanMap = mutableMapOf<String, String>()

//        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
//        databaseScan.child(userId).addListenerForSingleValueEvent


        //--------------------TRIAL--------------------


        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val databaseScan = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("scanResults/$currentUserId")

        databaseScan.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scanList.clear()
                scanMap.clear()

                for (scanSnapshot in snapshot.children) {
                    val timestamp = scanSnapshot.key ?: continue
                    val fullPath = "$currentUserId/$timestamp"
                    scanList.add(timestamp)
                    scanMap[timestamp] = timestamp
                }

                val adapter = ArrayAdapter(this@MessageMe, android.R.layout.simple_list_item_1, scanList)
                listView.adapter = adapter

                listView.setOnItemClickListener { _, _, position, _ ->
                    val selectedTimestamp = scanList[position]
                    val selectedPath = scanMap[selectedTimestamp]
                    popupWindow.dismiss()

                    AlertDialog.Builder(this@MessageMe)
                        .setTitle("Send Scan Result")
                        .setMessage("Are you sure you want to send the File?\n\nSelected: $selectedTimestamp")
                        .setPositiveButton("Yes") { dialog, _ ->
                            sendMessage("Scan result sent", selectedPath)
                            Toast.makeText(this@MessageMe, "Scan file sent!", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MessageMe, "Failed to load scans", Toast.LENGTH_SHORT).show()
            }
        })


//        databaseScan.addListenerForSingleValueEvent(object : ValueEventListener {
//            override fun onDataChange(snapshot: DataSnapshot) {
//                for (userSnapshot in snapshot.children) {
//                    val userId = userSnapshot.key ?: continue
//                    for (scanSnapshot in userSnapshot.children) {
//                        val timestamp = scanSnapshot.key ?: continue
//                        val fullPath = "$userId/$timestamp"
//                        scanList.add(timestamp)
//                        scanMap[timestamp] = fullPath
//                    }
//                }
//
//                val adapter = ArrayAdapter(this@MessageMe, android.R.layout.simple_list_item_1, scanList)
//                listView.adapter = adapter
//
//                listView.setOnItemClickListener { _, _, position, _ ->
//                    val selectedTimestamp = scanList[position]
//                    val selectedPath = scanMap[selectedTimestamp]
//                    popupWindow.dismiss()
//
//                    AlertDialog.Builder(this@MessageMe)
//                        .setTitle("Send Scan Result")
//                        .setMessage("Are you sure you want to send the File?\n\nSelected: $selectedTimestamp")
//                        .setPositiveButton("Yes") { dialog, _ ->
//                            sendMessage("Scan result sent", selectedPath)
//                            Toast.makeText(this@MessageMe, "Scan file sent!", Toast.LENGTH_SHORT).show()
//                            dialog.dismiss()
//                        }
//                        .setNegativeButton("Cancel") { dialog, _ ->
//                            dialog.dismiss()
//                        }
//                        .show()
//                }
//            }
//
//            override fun onCancelled(error: DatabaseError) {
//                Toast.makeText(this@MessageMe, "Failed to load scans", Toast.LENGTH_SHORT).show()
//            }
//        })
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(this, messageList)
        binding.recycleView.layoutManager = LinearLayoutManager(this)
        binding.recycleView.adapter = messageAdapter
    }

    private fun loadMessages() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messageList.clear()
                for (messageSnap in snapshot.children) {
                    val message = messageSnap.getValue(Message::class.java)
                    if (message != null) {
                        if ((message.senderId == FirebaseAuth.getInstance().currentUser?.uid && message.receiverId == receiverId) ||
                            (message.receiverId == FirebaseAuth.getInstance().currentUser?.uid && message.senderId == receiverId)) {
                            messageList.add(message)
                        }
                    }
                }
                messageAdapter.notifyDataSetChanged()
                binding.recycleView.scrollToPosition(messageList.size - 1)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MessageMe, "Failed to load messages", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun sendMessage(text: String, filePath: String? = null) {
        val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val receiverId = this.receiverId ?: return
        val messageId = database.push().key ?: return
        val timestamp = System.currentTimeMillis()

        val message = Message(messageId, senderId, receiverId, text, timestamp, filePath)

        // 1️⃣ Save to main messages node
        database.child(messageId).setValue(message)

        val currentUser = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val dbRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").reference

// First check if current user is a regular user
        dbRef.child("userInfo").child(currentUser)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Current user is a regular user
                        val userMessageRef = dbRef.child("userInfo").child(senderId!!).child("messages").child(messageId)
                        userMessageRef.setValue(message)

                        val clinicMessageRef = dbRef.child("clinicInfo").child(receiverId!!).child("messages").child(messageId)
                        clinicMessageRef.setValue(message)

                    } else {
                        // Current user is a clinic
                        dbRef.child("clinicInfo").child(currentUser)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (snapshot.exists()) {
                                        val clinicMessageRef = dbRef.child("clinicInfo").child(senderId!!).child("messages").child(messageId)
                                        clinicMessageRef.setValue(message)

                                        val userMessageRef = dbRef.child("userInfo").child(receiverId!!).child("messages").child(messageId)
                                        userMessageRef.setValue(message)
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {}
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })


        // 2️⃣ Save to userInfo


        // Optional: keep a "chat connection" for easy retrieval
        val chatRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .reference.child("userChats")
        chatRef.child(senderId).child(receiverId).setValue(true)
        chatRef.child(receiverId).child(senderId).setValue(true)

        // Save notification
        saveNotification(senderId, receiverId)
    }

    private fun getCurrentUserType(onResult: (userType: String) -> Unit) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val database = FirebaseDatabase.getInstance(
            "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )

        // Check in userInfo first
        database.getReference("userInfo").child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        onResult("user")
                    } else {
                        // If not found in userInfo, check clinicInfo
                        database.getReference("clinicInfo").child(currentUserId)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (snapshot.exists()) {
                                        onResult("clinic")
                                    } else {
                                        onResult("unknown") // not found
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    onResult("unknown")
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    onResult("unknown")
                }
            })
    }


    private fun saveNotification(fromUserId: String, toUserId: String) {
        val dbRef = FirebaseDatabase.getInstance(
            "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).reference

        // First, get the sender's name (user or clinic)
        dbRef.child("userInfo").child(fromUserId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(senderSnapshot: DataSnapshot) {
                val senderName = if (senderSnapshot.exists()) {
                    senderSnapshot.child("name").getValue(String::class.java) ?: "Someone"
                } else {
                    // Fallback to clinic
                    dbRef.child("clinicInfo").child(fromUserId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(clinicSnapshot: DataSnapshot) {
                            val clinicName = clinicSnapshot.child("clinicName").getValue(String::class.java) ?: "Someone"
                            pushNotification(clinicName, fromUserId, toUserId)
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                    return
                }
                pushNotification(senderName, fromUserId, toUserId)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun pushNotification(senderName: String, fromUserId: String, toUserId: String) {
        val dbRef = FirebaseDatabase.getInstance(
            "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).reference

        val notificationId = dbRef.child("notifications").child(toUserId).push().key ?: return
        val timestamp = System.currentTimeMillis()

        val notificationData = mapOf(
            "notificationId" to notificationId,
            "fromUserId" to fromUserId,
            "toUserId" to toUserId,
            "message" to "$senderName has sent you a message.",
            "postId" to "",
            "type" to "message",
            "isRead" to false,
            "timestamp" to timestamp
        )

        // Only save to the correct receiver node
        dbRef.child("userInfo").child(toUserId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(userSnapshot: DataSnapshot) {
                if (userSnapshot.exists()) {
                    // Receiver is a user
                    dbRef.child("userInfo").child(toUserId).child("notifications").child(notificationId)
                        .setValue(notificationData)
                } else {
                    // Receiver is a clinic
                    dbRef.child("clinicInfo").child(toUserId).child("notifications").child(notificationId)
                        .setValue(notificationData)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }



    private fun loadProfile(userId: String) {
        val database = FirebaseDatabase.getInstance(
            "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )

        // Try userInfo first
        database.getReference("userInfo").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val userProfile = snapshot.getValue(UserInfo::class.java)
                        userProfile?.let { showProfile(it.name ?: "No name", it.profileImage) }
                    } else {
                        // fallback to clinicInfo
                        database.getReference("clinicInfo").child(userId)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    val clinicProfile = snapshot.getValue(ClinicInfo::class.java)
                                    clinicProfile?.let { showProfile(it.clinicName ?: "No name", it.logoImage) }
                                }

                                override fun onCancelled(error: DatabaseError) {}
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }
    private fun showProfile(name: String, base64Image: String?) {
        binding.textView46.text = name
        base64Image?.let {
            try {
                val imageBytes = android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                binding.profile.setImageBitmap(bitmap)
            } catch (_: Exception) {
                binding.profile.setImageResource(R.drawable.default_profile)
            }
        } ?: run {
            binding.profile.setImageResource(R.drawable.default_profile)
        }
    }


}
