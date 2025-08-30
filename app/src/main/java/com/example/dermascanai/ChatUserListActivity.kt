package com.example.dermascanai

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.ActivityChatUserListBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ChatUserListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatUserListBinding
    private lateinit var chatItems: ArrayList<ChatItem>
    private lateinit var adapter: UserListAdapter
    private val dermaId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatUserListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatItems = ArrayList()
        adapter = UserListAdapter(chatItems) { item ->
            val intent = Intent(this, MessageMe::class.java)
            intent.putExtra("receiverId", item.uid)
            intent.putExtra("name", item.displayName)
            intent.putExtra("senderId", dermaId)
            startActivity(intent)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.backButton.setOnClickListener { finish() }

        loadChatUsers()
        loadDermasWhoMessagedUser()
    }

    private fun loadChatUsers() {
        val database = FirebaseDatabase.getInstance(
            "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )
        val userChatsRef = database.getReference("userChats")

        userChatsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chatItems.clear()
                val uniqueUserIds = mutableSetOf<String>()

                for (chatSnapshot in snapshot.children) {
                    val senderId = chatSnapshot.key ?: continue
                    for (receiverSnapshot in chatSnapshot.children) {
                        val receiverId = receiverSnapshot.key ?: continue

                        if (senderId == dermaId && receiverId != dermaId) uniqueUserIds.add(receiverId)
                        if (receiverId == dermaId && senderId != dermaId) uniqueUserIds.add(senderId)
                    }
                }

                // Load each user or clinic
                for (id in uniqueUserIds) {
                    loadUserOrClinic(id)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadUserOrClinic(userId: String) {
        val database = FirebaseDatabase.getInstance(
            "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )

        // Try userInfo first
        database.getReference("userInfo").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val name = snapshot.child("name").getValue(String::class.java) ?: "Unknown"
                        val image = snapshot.child("profileImage").getValue(String::class.java)
                        if (chatItems.none { it.uid == userId }) {
                            chatItems.add(ChatItem(userId, name, image))
                            adapter.notifyDataSetChanged()
                        }
                    } else {
                        // fallback to clinicInfo
                        database.getReference("clinicInfo").child(userId)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    val name = snapshot.child("clinicName").getValue(String::class.java) ?: "Unknown"
                                    val image = snapshot.child("logoImage").getValue(String::class.java)
                                    if (chatItems.none { it.uid == userId }) {
                                        chatItems.add(ChatItem(userId, name, image))
                                        adapter.notifyDataSetChanged()
                                    }
                                }
                                override fun onCancelled(error: DatabaseError) {}
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadDermasWhoMessagedUser() {
        val currentUserId = dermaId
        val messagesRef = FirebaseDatabase.getInstance(
            "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).getReference("userChats").child(currentUserId)

        messagesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val senderIds = HashSet<String>()

                for (chatSnapshot in snapshot.children) {
                    for (messageSnapshot in chatSnapshot.children) {
                        val senderId = messageSnapshot.child("senderId").getValue(String::class.java)
                        val receiverId = messageSnapshot.child("receiverId").getValue(String::class.java)
                        if (receiverId == currentUserId && senderId != null) {
                            senderIds.add(senderId)
                        }
                    }
                }

                for (senderId in senderIds) {
                    loadUserOrClinic(senderId)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
