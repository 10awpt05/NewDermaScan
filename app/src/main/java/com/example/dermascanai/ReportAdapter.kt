package com.example.dermascanai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dermascanai.databinding.ItemReportBinding

class ReportAdapter(
    private val list: List<PatientRecord>,
    private val onClick: (PatientRecord) -> Unit
) : RecyclerView.Adapter<ReportAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemReportBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PatientRecord) {
            binding.patientName.text = item.patientName
            binding.patientEmail.text = item.patientEmail

            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReportBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount() = list.size
}
