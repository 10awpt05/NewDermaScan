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
    private val messageList = ArrayList<Message>()
    private var receiverId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageMeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .reference.child("messages")

        databaseScan = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("scanResults")

        receiverId = intent.getStringExtra("receiverId")

        receiverId?.let { loadClinicProfile(it) }


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
        database.child(messageId).setValue(message)

        val chatRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .reference.child("userChats")
        chatRef.child(senderId).child(receiverId).setValue(true)
        chatRef.child(receiverId).child(senderId).setValue(true)

        saveNotification(senderId, receiverId)
    }

    private fun saveNotification(fromUserId: String, toUserId: String) {
        val dbRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").reference

        dbRef.child("userInfo").child(fromUserId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(userSnapshot: DataSnapshot) {
                if (userSnapshot.exists()) {
                    val fullName = userSnapshot.child("name").getValue(String::class.java) ?: "Someone"
                    pushNotification(fullName, fromUserId, toUserId)
                } else {
                    dbRef.child("clinicInfo").child(fromUserId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(dermaSnapshot: DataSnapshot) {
                            val fullName = dermaSnapshot.child("name").getValue(String::class.java) ?: "Someone"
                            pushNotification(fullName, fromUserId, toUserId)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(this@MessageMe, "Error fetching derma info", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MessageMe, "Error fetching user info", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun pushNotification(fullName: String, fromUserId: String, toUserId: String) {
        val notificationRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .reference.child("notifications").child(toUserId)
        val notificationId = notificationRef.push().key ?: return
        val timestamp = System.currentTimeMillis()

        val notificationData = mapOf(
            "notificationId" to notificationId,
            "fromUserId" to fromUserId,
            "toUserId" to toUserId,
            "message" to "$fullName has sent you a message.",
            "postId" to "",
            "type" to "message",
            "isRead" to false,
            "timestamp" to timestamp
        )

        notificationRef.child(notificationId).setValue(notificationData)
    }

    private fun loadClinicProfile(receiverId: String) {
        val databaseClinic = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("clinicInfo").child(receiverId)

        databaseClinic.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val clinicProfile = snapshot.getValue(ClinicInfo::class.java)

                if (clinicProfile != null) {
                    // Display the name (you can set this anywhere you want)
                    binding.textView46.text = clinicProfile.name ?: "No name"

                    // Decode Base64 image if available
                    clinicProfile.logoImage?.let {
                        try {
                            val imageBytes = android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            binding.profile.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    Toast.makeText(this@MessageMe, "Clinic profile not found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MessageMe, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

}
