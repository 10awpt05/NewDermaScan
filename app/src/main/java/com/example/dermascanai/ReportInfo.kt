package com.example.dermascanai

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.ActivityReportInfoBinding
import com.google.firebase.database.FirebaseDatabase

class ReportInfo : AppCompatActivity() {

    private lateinit var binding: ActivityReportInfoBinding


        private val db = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").reference.child("reports")

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            binding = ActivityReportInfoBinding.inflate(layoutInflater)
            setContentView(binding.root)

            val email = intent.getStringExtra("patientEmail")!!
            val name = intent.getStringExtra("patientName")!!

            binding.patientHeader.text = "$name's Visit Dates"
            binding.backBTN.setOnClickListener {
                finish()
            }

            loadDates(email)
        }

    private fun loadDates(email: String) {
        db.orderByChild("patientEmail").equalTo(email)
            .get().addOnSuccessListener { snapshot ->

                val dates = HashSet<String>()
                for (child in snapshot.children) {
                    val date = child.child("bookedDate").value?.toString()
                    Log.d("ReportInfo", "Found date: $date") // LOG
                    if (!date.isNullOrEmpty()) dates.add(date)
                }

                val dateList = dates.toList()
                Log.d("ReportInfo", "Dates list: $dateList") // LOG

                binding.dateList.apply {
                    layoutManager = LinearLayoutManager(this@ReportInfo)
                    adapter = VisitDateAdapter(dateList) { selectedDate ->
                        showDetailsDialog(email, selectedDate)
                    }
                }
            }
            .addOnFailureListener {
                Log.e("ReportInfo", "Failed to fetch data", it)
            }
    }



    private fun showDetailsDialog(email: String, date: String) {

        db.orderByChild("patientEmail").equalTo(email)
            .get().addOnSuccessListener { snapshot ->

                for (child in snapshot.children) {
                    if (child.child("bookedDate").value == date) {

                        val dialogView = layoutInflater.inflate(R.layout.dialog_visit_details, null)

                        val tvEmail = dialogView.findViewById<TextView>(R.id.tvPatientEmail)
                        val tvService = dialogView.findViewById<TextView>(R.id.tvService)
                        val tvDate = dialogView.findViewById<TextView>(R.id.tvDate)
                        val tvTime = dialogView.findViewById<TextView>(R.id.tvTime)
                        val tvStatus = dialogView.findViewById<TextView>(R.id.tvStatus)
                        val tvMessage = dialogView.findViewById<TextView>(R.id.tvMessage)

                        // SET VALUES
                        tvEmail.text = child.child("patientName").value.toString()
                        tvService.text = child.child("service").value.toString()
                        tvDate.text = child.child("bookedDate").value.toString()
                        tvTime.text = child.child("bookedTime").value.toString()
                        tvStatus.text = child.child("status").value.toString()
                        tvMessage.text = child.child("message").value.toString()

                        val dialog = AlertDialog.Builder(this)
                            .setView(dialogView)
                            .setPositiveButton("Close", null)
                            .create()

                        dialog.show()
                        break
                    }
                }
            }
    }


}