package com.example.dermascanai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.ActivityReportsHistoryBinding
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class   ReportsHistory : AppCompatActivity() {

    private lateinit var binding: ActivityReportsHistoryBinding
    private val db = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").reference.child("reports")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadPatients()
    }

    private fun loadPatients() {
        db.get().addOnSuccessListener { snapshot ->

            val map = HashMap<String, PatientRecord>() // avoid duplicates

            for (child in snapshot.children) {

                val name = child.child("patientName").value?.toString() ?: continue
                val email = child.child("patientEmail").value?.toString() ?: continue

                map[email] = PatientRecord(
                    patientName = name,
                    patientEmail = email,

                )
            }

            val patientList = map.values.toList()

            if (patientList.isEmpty()) {
                binding.emptyStateLayout.visibility = View.VISIBLE
                binding.patientList.visibility = View.GONE
            } else {
                binding.emptyStateLayout.visibility = View.GONE
                binding.patientList.visibility = View.VISIBLE
            }


            binding.patientList.layoutManager = LinearLayoutManager(this)
            binding.patientList.adapter = ReportAdapter(patientList) { selected ->
                val intent = Intent(this, ReportInfo::class.java)
                intent.putExtra("patientEmail", selected.patientEmail)
                intent.putExtra("patientName", selected.patientName)
                startActivity(intent)
            }
        }
    }

}