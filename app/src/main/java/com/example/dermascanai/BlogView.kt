package com.example.dermascanai

import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.ActivityBlogViewBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class BlogView : AppCompatActivity() {

    private lateinit var binding: ActivityBlogViewBinding
    private lateinit var commentAdapter: CommentAdapter
    private val commentList = mutableListOf<Comment>()

    private val database = FirebaseDatabase.getInstance(
        "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
    ).reference
    private val auth = FirebaseAuth.getInstance()

    private lateinit var postId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlogViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        postId = intent.getStringExtra("id") ?: return

        commentAdapter = CommentAdapter(commentList, object : OnCommentReplyListener {
            override fun onReply(parentCommentId: String) {
                showReplyInputDialog(parentCommentId)
            }
        }, postId)

        binding.recyclerViewResponse.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewResponse.adapter = commentAdapter

        binding.sendBtn.setOnClickListener {
            val commentText = binding.commentEditText.text.toString().trim()
            if (commentText.isNotEmpty()) {
                sendComment(commentText)
            }
        }

        loadComments()
        loadBlogPost()
        binding.backBTN.setOnClickListener { finish() }
    }

    // 🔹 Load blog post
    private fun loadBlogPost() {
        val postRef = database.child("blogPosts").child(postId)
        postRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val blogPost = snapshot.getValue(BlogPost::class.java)
                blogPost?.let {
                    binding.title.text = it.content
                    val postOwnerId = it.userId ?: return
                    fetchOwnerInfo(postOwnerId) { fullName, _ ->
                        binding.textView23.text = fullName
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@BlogView, "Failed to load post", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // 🔹 Load all comments and replies
    private fun loadComments() {
        val commentsRef = database.child("comments").child(postId)
        commentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                commentList.clear()

                for (commentSnap in snapshot.children) {
                    val comment = commentSnap.getValue(Comment::class.java)
                    comment?.let {
                        // Get replies under this comment
                        val repliesSnapshot = commentSnap.child("replies")
                        val replies = mutableListOf<Comment>()
                        for (replySnap in repliesSnapshot.children) {
                            val reply = replySnap.getValue(Comment::class.java)
                            reply?.let { replies.add(it) }
                        }

                        // Sort replies by timestamp descending
                        replies.sortByDescending { it.timestamp }
                        it.repliesList = replies

                        // Add comment to list
                        commentList.add(it)
                    }
                }

                // Sort main comments by timestamp descending
                commentList.sortByDescending { it.timestamp }
                commentAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@BlogView, "Failed to load comments", Toast.LENGTH_SHORT).show()
            }
        })
    }






    // 🔹 Show reply dialog
    private fun showReplyInputDialog(parentCommentId: String) {
        val dialog = AlertDialog.Builder(this)
        dialog.setTitle("Reply to Comment")

        val input = EditText(this)
        input.hint = "Write your reply..."
        dialog.setView(input)

        dialog.setPositiveButton("Send") { _, _ ->
            val replyText = input.text.toString().trim()
            if (replyText.isNotEmpty()) sendReply(parentCommentId, replyText)
        }
        dialog.setNegativeButton("Cancel", null)
        dialog.show()
    }

    // 🔹 Send comment
    private fun sendComment(text: String, parentCommentId: String? = null) {
        val userId = auth.currentUser?.uid ?: return
        val commentId = database.child("comments").child(postId).push().key ?: return
        val timestamp = System.currentTimeMillis()

        fetchOwnerInfo(userId) { fullName, profileImage ->
            val comment = Comment(
                commentId = commentId,
                postId = postId,
                userId = userId,
                userName = fullName,
                userProfileImageBase64 = profileImage,
                comment = text,
                timestamp = timestamp,
                parentCommentId = parentCommentId
            )

            // Save to central comments node
            val centralCommentsRef = database.child("comments").child(postId).child(commentId)
            centralCommentsRef.setValue(comment)

            // Save under blogPosts node
            val blogCommentsRef = database.child("blogPosts").child(postId).child("comments").child(commentId)
            blogCommentsRef.setValue(comment)

            // Save under userInfo or clinicInfo depending on owner
            database.child("blogPosts").child(postId).child("userId").get()
                .addOnSuccessListener { postSnapshot ->
                    val ownerId = postSnapshot.getValue(String::class.java)
                    ownerId?.let {
                        database.child("userInfo").child(ownerId).get().addOnSuccessListener { userSnap ->
                            val ownerRef = if (userSnap.exists()) {
                                database.child("userInfo").child(ownerId)
                            } else {
                                database.child("clinicInfo").child(ownerId)
                            }
                            ownerRef.child("blogPosts").child(postId).child("comments").child(commentId)
                                .setValue(comment)
                        }
                    }
                }

            // Add notification if commenter is not the owner
            database.child("blogPosts").child(postId).child("userId").get()
                .addOnSuccessListener { snapshot ->
                    val postOwnerId = snapshot.getValue(String::class.java)
                    if (postOwnerId != null && postOwnerId != userId) {
                        addNotification(
                            toUserId = postOwnerId,
                            fromUserId = userId,
                            fromUserName = fullName,
                            postId = postId,
                            commentId = commentId,
                            type = "comment"
                        )
                    }
                }

            commentAdapter.addComment(comment)
            binding.commentEditText.text?.clear()
        }
    }

    private fun sendReply(parentCommentId: String, replyText: String) {
        val userId = auth.currentUser?.uid ?: return
        val replyId = database.child("comments").child(postId).child(parentCommentId).child("replies").push().key ?: return
        val timestamp = System.currentTimeMillis()

        fetchOwnerInfo(userId) { fullName, profileImage ->
            val reply = Comment(
                commentId = replyId,
                postId = postId,
                userId = userId,
                userName = fullName,
                userProfileImageBase64 = profileImage,
                comment = replyText,
                timestamp = timestamp,
                parentCommentId = parentCommentId
            )

            // Central comments
            database.child("comments").child(postId).child(parentCommentId)
                .child("replies").child(replyId).setValue(reply)

            // Blog posts
            database.child("blogPosts").child(postId).child("comments")
                .child(parentCommentId).child("replies").child(replyId).setValue(reply)

            // Owner's userInfo / clinicInfo
            database.child("blogPosts").child(postId).child("userId").get()
                .addOnSuccessListener { postSnapshot ->
                    val ownerId = postSnapshot.getValue(String::class.java)
                    ownerId?.let {
                        database.child("userInfo").child(ownerId).get().addOnSuccessListener { userSnap ->
                            val ownerRef = if (userSnap.exists()) {
                                database.child("userInfo").child(ownerId)
                            } else {
                                database.child("clinicInfo").child(ownerId)
                            }
                            ownerRef.child("blogPosts").child(postId).child("comments")
                                .child(parentCommentId).child("replies").child(replyId).setValue(reply)
                        }
                    }
                }

            // Notification to parent comment owner
            database.child("comments").child(postId).child(parentCommentId).child("userId").get()
                .addOnSuccessListener { snapshot ->
                    val parentOwnerId = snapshot.getValue(String::class.java)
                    if (parentOwnerId != null && parentOwnerId != userId) {
                        addNotification(
                            toUserId = parentOwnerId,
                            fromUserId = userId,
                            fromUserName = fullName,
                            postId = postId,
                            commentId = replyId,
                            parentCommentId = parentCommentId,
                            type = "reply"
                        )
                    }
                }
        }
    }


    // 🔹 Fetch owner info (User or Clinic)
    private fun fetchOwnerInfo(ownerId: String, callback: (fullName: String, profileImage: String?) -> Unit) {
        val userRef = database.child("userInfo").child(ownerId)
        val clinicRef = database.child("clinicInfo").child(ownerId)

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val fullName = snapshot.child("name").getValue(String::class.java) ?: "Unknown"
                val profileImage = snapshot.child("profileImage").getValue(String::class.java)
                callback(fullName, profileImage)
            } else {
                clinicRef.get().addOnSuccessListener { clinicSnapshot ->
                    if (clinicSnapshot.exists()) {
                        val fullName = clinicSnapshot.child("clinicName").getValue(String::class.java) ?: "Unknown Clinic"
                        val profileImage = clinicSnapshot.child("logoImage").getValue(String::class.java)
                        callback(fullName, profileImage)
                    } else {
                        callback("Unknown", null)
                    }
                }
            }
        }
    }

    // 🔹 Add notification
    private fun addNotification(
        toUserId: String,
        fromUserId: String,
        fromUserName: String,
        postId: String,
        commentId: String,
        type: String,
        parentCommentId: String? = null
    ) {
        val notificationId = database.child("notifications").child(toUserId).push().key ?: return
        val timestamp = System.currentTimeMillis()

        val message = when (type) {
            "comment" -> "$fromUserName commented on your post"
            "reply" -> "$fromUserName replied to your comment"
            else -> "$fromUserName interacted with your post"
        }

        val notification = Notification(
            notificationId = notificationId,
            postId = postId,
            fromUserId = fromUserId,
            toUserId = toUserId,
            type = type,
            message = message,
            timestamp = timestamp,
            isRead = false
        )

        database.child("notifications").child(toUserId).child(notificationId).setValue(notification)
    }
}
