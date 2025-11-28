package com.example.dermascanai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dermascanai.databinding.ItemVisitDateBinding

class VisitDateAdapter(
    private val dates: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<VisitDateAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemVisitDateBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVisitDateBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val date = dates[position]
        holder.binding.dateText.text = date
        holder.binding.root.setOnClickListener { onClick(date) }
    }

    override fun getItemCount() = dates.size
}

