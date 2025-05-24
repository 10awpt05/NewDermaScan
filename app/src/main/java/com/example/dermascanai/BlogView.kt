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
import java.util.*

class BlogView : AppCompatActivity() {

    private lateinit var binding: ActivityBlogViewBinding
    private lateinit var commentAdapter: CommentAdapter
    private val commentList = mutableListOf<Comment>()

    private val database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").reference
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

    private fun loadBlogPost() {
        val postRef = database.child("blogPosts").child(postId)
        postRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val blogPost = snapshot.getValue(BlogPost::class.java)
                blogPost?.let {
                    binding.textView23.text = it.fullName
                    binding.title.text = it.content
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@BlogView, "Failed to load post", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadComments() {
        val commentsRef = database.child("comments").child(postId)

        commentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                commentList.clear()

                for (commentSnap in snapshot.children) {
                    if (commentSnap.key == "replies") continue

                    val comment = commentSnap.getValue(Comment::class.java)
                    comment?.let {
                        val repliesSnapshot = snapshot.child("replies").child(it.commentId ?: "")
                        val replies = mutableListOf<Comment>()
                        for (replySnap in repliesSnapshot.children) {
                            val reply = replySnap.getValue(Comment::class.java)
                            reply?.let { replies.add(it) }
                        }
                        replies.sortByDescending { it.timestamp }
                        it.replies = replies
                        commentList.add(it)
                    }
                }

                commentList.sortByDescending { it.timestamp }
                commentAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@BlogView, "Failed to load comments", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showReplyInputDialog(parentCommentId: String) {
        val dialog = AlertDialog.Builder(this)
        dialog.setTitle("Reply to Comment")

        val input = EditText(this)
        input.hint = "Write your reply..."
        dialog.setView(input)

        dialog.setPositiveButton("Send") { _, _ ->
            val replyText = input.text.toString().trim()
            if (replyText.isNotEmpty()) {
                sendReply(parentCommentId, replyText)
            }
        }
        dialog.setNegativeButton("Cancel", null)
        dialog.show()
    }

    private fun sendComment(text: String, parentCommentId: String? = null) {
        val userId = auth.currentUser?.uid ?: return
        val commentId = database.child("comments").child(postId).push().key ?: return
        val timestamp = System.currentTimeMillis()

        val userRef = database.child("userInfo").child(userId)

        userRef.get().addOnSuccessListener { snapshot ->
            val fullName = snapshot.child("name").getValue(String::class.java) ?: "Unknown"
            val profileImage = snapshot.child("profileImage").getValue(String::class.java)

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

            database.child("comments").child(postId).child(commentId).setValue(comment)
                .addOnSuccessListener {
                    commentAdapter.addComment(comment)
                    binding.commentEditText.text?.clear()

                    // Notify post owner
                    database.child("blogPosts").child(postId).child("userId").get()
                        .addOnSuccessListener { postSnapshot ->
                            val postOwnerId = postSnapshot.getValue(String::class.java)
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
                }
        }
    }

    private fun sendReply(parentCommentId: String, replyText: String) {
        val userId = auth.currentUser?.uid ?: return
        val replyId = database.child("comments").child(postId)
            .child("replies").child(parentCommentId).push().key ?: return
        val timestamp = System.currentTimeMillis()

        database.child("userInfo").child(userId).get()
            .addOnSuccessListener { userSnapshot ->
                val fullName = userSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                val profileImage = userSnapshot.child("profileImage").getValue(String::class.java)

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

                database.child("comments").child(postId)
                    .child("replies").child(parentCommentId)
                    .child(replyId)
                    .setValue(reply)
                    .addOnSuccessListener {
                        // Notify parent comment owner
                        database.child("comments").child(postId).child(parentCommentId).child("userId").get()
                            .addOnSuccessListener { commentSnapshot ->
                                val parentCommentOwnerId = commentSnapshot.getValue(String::class.java)
                                if (parentCommentOwnerId != null && parentCommentOwnerId != userId) {
                                    addNotification(
                                        toUserId = parentCommentOwnerId,
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
    }

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
