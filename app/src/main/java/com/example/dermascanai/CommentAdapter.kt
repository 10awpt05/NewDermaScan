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

        // Hide like/heart bars for parent comment if needed
        holder.binding.linearLayout2.visibility = View.GONE

        // Set comment text
        holder.binding.textView33.text = comment.comment

        // Load user or clinic info
        val dbRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")

        fun loadUserOrClinic(uid: String) {
            dbRef.getReference("userInfo").child(uid)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            holder.binding.name.text = snapshot.child("name").getValue(String::class.java) ?: "Unknown"
                            val base64Image = snapshot.child("profileImage").getValue(String::class.java)
                            if (!base64Image.isNullOrEmpty()) {
                                try {
                                    val decodedBytes = Base64.decode(base64Image, Base64.DEFAULT)
                                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                    holder.binding.profile.setImageBitmap(bitmap)
                                } catch (_: Exception) {
                                    holder.binding.profile.setImageResource(R.drawable.ic_profile2)
                                }
                            } else {
                                holder.binding.profile.setImageResource(R.drawable.ic_profile2)
                            }
                        } else {
                            dbRef.getReference("clinicInfo").child(uid)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(clinicSnap: DataSnapshot) {
                                        if (clinicSnap.exists()) {
                                            val clinicName = clinicSnap.child("clinicName").getValue(String::class.java) ?: "Unknown Clinic"
                                            holder.binding.name.text = clinicName
                                            val logoImage = clinicSnap.child("logoImage").getValue(String::class.java)
                                            if (!logoImage.isNullOrEmpty()) {
                                                try {
                                                    val decodedBytes = Base64.decode(logoImage, Base64.DEFAULT)
                                                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                                    holder.binding.profile.setImageBitmap(bitmap)
                                                } catch (_: Exception) {
                                                    holder.binding.profile.setImageResource(R.drawable.ic_profile2)
                                                }
                                            } else {
                                                holder.binding.profile.setImageResource(R.drawable.ic_profile2)
                                            }
                                        }
                                    }
                                    override fun onCancelled(error: DatabaseError) {}
                                })
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }

        comment.userId?.let { loadUserOrClinic(it) }

        // Reply click listener
        holder.binding.linearLayout.setOnClickListener {
            replyListener.onReply(comment.commentId)
        }

        // Nested RecyclerView for replies
        val replyList = mutableListOf<Comment>()
        val replyAdapter = ReplyAdapter(replyList)
        holder.binding.recyclerViewComment.layoutManager = LinearLayoutManager(context)
        holder.binding.recyclerViewComment.adapter = replyAdapter

        // Real-time load replies
        val replyRef = dbRef.getReference("comments").child(postId).child(comment.commentId).child("replies")
        replyRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                replyList.clear()
                for (child in snapshot.children) {
                    val reply = child.getValue(Comment::class.java)
                    reply?.let { replyList.add(it) }
                }
                // Sort by timestamp descending
                replyList.sortByDescending { it.timestamp }
                replyAdapter.notifyDataSetChanged()

                // Show/hide nested RecyclerView based on replies
                if (replyList.isNotEmpty()) {
                    holder.binding.commentLayout.visibility = View.VISIBLE
                    holder.binding.recyclerViewComment.visibility = View.VISIBLE
                } else {
                    holder.binding.commentLayout.visibility = View.GONE
                    holder.binding.recyclerViewComment.visibility = View.GONE
                }
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
