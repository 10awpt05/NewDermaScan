package com.example.dermascanai

import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dermascanai.databinding.ItemUserChatBinding
import java.io.ByteArrayInputStream

class UserListAdapter(
    private val chatItems: List<ChatItem>,
    private val onUserClick: (ChatItem) -> Unit
) : RecyclerView.Adapter<UserListAdapter.UserViewHolder>() {

    inner class UserViewHolder(val binding: ItemUserChatBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatItem) {
            binding.userName.text = item.displayName

            if (item.isRead) {
                binding.root.setBackgroundColor(binding.root.context.getColor(R.color.white))
            } else {
                binding.root.setBackgroundColor(binding.root.context.getColor(R.color.nav_item_icon_tint)) // Unread color
            }


            // Load image from Base64
            item.profileBase64?.let {
                try {
                    val decodedBytes = Base64.decode(it, Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    binding.userProfileImage.setImageBitmap(bitmap)
                } catch (_: Exception) {
                    binding.userProfileImage.setImageResource(R.drawable.default_profile)
                }
            } ?: run {
                binding.userProfileImage.setImageResource(R.drawable.default_profile)
            }

            binding.root.setOnClickListener {
                onUserClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(chatItems[position])
    }

    override fun getItemCount(): Int = chatItems.size
}
