package com.example.dermascanai

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dermascanai.databinding.ItemResponseViewBinding
import com.google.firebase.database.*

class CommentAdapter(
    private val commentList: MutableList<Comment>,
    private val replyListener: OnCommentReplyListener,
    private val postId: String
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    inner class CommentViewHolder(val binding: ItemResponseViewBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemResponseViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = commentList[position]
        val context = holder.itemView.context
        holder.binding.linearLayout2.visibility = View.GONE

        // Set comment text
        holder.binding.textView33.text = comment.comment

        // Load profile image
//        comment.userProfileImageBase64?.let {
//            try {
//                val decodedBytes = Base64.decode(it, Base64.DEFAULT)
//                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
//                holder.binding.profile.setImageBitmap(bitmap)
//            } catch (_: Exception) {}
//        }

        // Fetch and display user's name
        comment.userId?.let { uid ->
            FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("userInfo")
                .child(uid)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        holder.binding.name.text = snapshot.child("name").getValue(String::class.java) ?: "Unknown"

                        val base64Image = snapshot.child("profileImage").getValue(String::class.java)
                        if (!base64Image.isNullOrEmpty()) {
                            try {
                                val decodedBytes = Base64.decode(base64Image, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                holder.binding.profile.setImageBitmap(bitmap)
                            } catch (_: Exception) {
                                // Optional: Set a fallback image
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        holder.binding.name.text = "Unknown"
                    }
                })
        }

        // Set reply click listener
        holder.binding.linearLayout.setOnClickListener {
            replyListener.onReply(comment.commentId)
        }

        // Set up nested RecyclerView for replies
        val replyList = mutableListOf<Comment>()
        val replyAdapter = ReplyAdapter(replyList)
        holder.binding.recyclerViewComment.layoutManager = LinearLayoutManager(context)
        holder.binding.recyclerViewComment.adapter = replyAdapter
        holder.binding.commentLayout.visibility = View.VISIBLE

        val replyRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("comments")
            .child(postId)
            .child("replies")
            .child(comment.commentId)

        // 🔁 Listen for real-time updates
        replyRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                replyList.clear()
                for (child in snapshot.children) {
                    val reply = child.getValue(Comment::class.java)
                    reply?.let { replyList.add(it) }
                }
                replyAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun getItemCount(): Int = commentList.size

    fun addComment(comment: Comment) {
        commentList.add(comment)
        notifyItemInserted(commentList.size - 1)
    }
}
