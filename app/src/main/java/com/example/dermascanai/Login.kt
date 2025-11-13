package com.example.dermascanai

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.dermascanai.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DatabaseError
import com.google.firebase.messaging.FirebaseMessaging
import pl.droidsonroids.gif.GifDrawable
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.PackageInfoCompat


class Login : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var mAuth: FirebaseAuth
    private lateinit var mDatabase: DatabaseReference
    private lateinit var dDatabase: DatabaseReference

    private var updateCheckCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAuth = FirebaseAuth.getInstance()
        val database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        mDatabase = database.getReference("userInfo")
        dDatabase = database.getReference("clinicInfo")


        checkForUpdate()
        PermissionHelper.requestNotificationPermission(this)

//        val currentUser = mAuth.currentUser
//
//        if (currentUser != null){
//
//            redirectToRolePage()
//        }



        binding.loginButton.setOnClickListener {
            loginUser()
        }

        binding.forgotPassword.setOnClickListener {
            forgotPassword()
        }
        binding.toRegister.setOnClickListener {
            val intent = Intent(this, ChooseUser::class.java)
            startActivity(intent)
        }
    }

    private fun loginUser() {
        val email = binding.email.text.toString().trim()
        val password = binding.password.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in both fields", Toast.LENGTH_SHORT).show()
            return
        }
        showProgressBar()
        // Show progress or loading animation here
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    redirectToRolePage()  // Redirection based on the role after successful login
                } else {
                    hideProgressBar()
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun redirectToRolePage() {
        val userId = mAuth.currentUser?.uid
        if (userId != null) {
            showProgressBar()
            mDatabase.child(userId).child("role")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        val role = dataSnapshot.getValue(String::class.java)
                        Log.d("LoginActivity", "User role: $role")

                        if (role != null) {
                            // 🔹 Save FCM token under correct node
                            FirebaseMessaging.getInstance().token.addOnCompleteListener { tokenTask ->
                                if (tokenTask.isSuccessful) {
                                    val token = tokenTask.result
                                    val node = if (role == "derma") "clinicInfo" else "userInfo"
                                    FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
                                        .getReference(node)
                                        .child(userId)
                                        .child("fcmToken")
                                        .setValue(token)
                                }
                            }

                            val intent = when (role) {
                                "derma" -> Intent(this@Login, DermaPage::class.java)
                                "user" -> Intent(this@Login, UserPage::class.java)
                                else -> null
                            }

                            intent?.let {
                                startActivity(it)
                                finish()
                                hideProgressBar()
                            }
                        } else {
                            // check in clinicInfo if not found in userInfo
                            dDatabase.child(userId).child("role")
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                                        val role = dataSnapshot.getValue(String::class.java)

                                        if (role != null) {
                                            // 🔹 Save token for derma users
                                            FirebaseMessaging.getInstance().token.addOnCompleteListener { tokenTask ->
                                                if (tokenTask.isSuccessful) {
                                                    val token = tokenTask.result
                                                    val node = if (role == "derma") "clinicInfo" else "userInfo"
                                                    FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
                                                        .getReference(node)
                                                        .child(userId)
                                                        .child("fcmToken")
                                                        .setValue(token)
                                                }
                                            }

                                            val intent = when (role) {
                                                "derma" -> Intent(this@Login, DermaPage::class.java)
                                                "user" -> Intent(this@Login, UserPage::class.java)
                                                else -> null
                                            }

                                            intent?.let {
                                                startActivity(it)
                                                finish()
                                                hideProgressBar()
                                            }
                                        } else {
                                            hideProgressBar()
                                        }
                                    }

                                    override fun onCancelled(error: DatabaseError) {
                                        hideProgressBar()
                                    }
                                })
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        hideProgressBar()
                    }
                })
        }
    }


    private fun forgotPassword() {
        val email = binding.email.text.toString().trim()

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
            return
        }

        mAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Password reset email sent", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showProgressBar() {
        val gifDrawable = binding.animatedProgressBar.drawable as? GifDrawable
        gifDrawable?.apply {

            start()
        }
        binding.progressContainer.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        val gifDrawable = binding.animatedProgressBar.drawable as? GifDrawable
        gifDrawable?.stop() // optional: stop playback if still running
        binding.progressContainer.visibility = View.GONE
    }


    private fun checkForUpdate() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toInt()

            val db = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("app_info")

            db.get().addOnSuccessListener { snapshot ->
                val latestVersionCode = snapshot.child("latest_version_code").getValue(Int::class.java) ?: currentVersionCode
                val downloadUrl = snapshot.child("download_url").getValue(String::class.java)

                if (latestVersionCode > currentVersionCode) {
                    // Show update popup — do not auto-login yet
                    showUpdateDialog(downloadUrl)
                } else {
                    updateCheckCompleted = true
                    proceedIfReady()
                }
            }.addOnFailureListener {
                Log.e("UpdateCheck", "Failed to fetch version info: ${it.message}")
                updateCheckCompleted = true
                proceedIfReady()
            }

        } catch (e: Exception) {
            Log.e("UpdateCheck", "Error checking app version: ${e.message}")
            updateCheckCompleted = true
            proceedIfReady()
        }
    }

    private fun showUpdateDialog(downloadUrl: String?) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Update Available")
        builder.setMessage("A new version of DermaScanAI is available. Please update to get the latest features and fixes.")

        builder.setPositiveButton("Update") { _, _ ->
            if (!downloadUrl.isNullOrEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Download link not available", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
            updateCheckCompleted = true
            proceedIfReady()
        }

        builder.setCancelable(false)
        builder.create().show()
    }


    private fun proceedIfReady() {
        // ✅ This runs only after update check is done
        if (updateCheckCompleted) {
            val currentUser = mAuth.currentUser
            if (currentUser != null) {
                redirectToRolePage()
            }
        }
    }
}