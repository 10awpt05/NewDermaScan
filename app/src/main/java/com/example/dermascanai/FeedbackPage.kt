package com.example.dermascanai

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.dermascanai.databinding.ActivityFeedbackPageBinding

class FeedbackPage : AppCompatActivity() {
    private lateinit var binding: ActivityFeedbackPageBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       binding = ActivityFeedbackPageBinding.inflate(layoutInflater)
        setContentView(binding.root)


    }
}