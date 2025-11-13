package com.example.dermascanai

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.ActivityUserPageBinding
import com.example.dermascanai.databinding.LayoutNotificationPopupBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

import androidx.appcompat.app.AlertDialog



class UserPage : AppCompatActivity() {

        private lateinit var binding: ActivityUserPageBinding
        private var isFabMenuOpen = false
        private lateinit var notificationListener: ChildEventListener
        private var notificationListenerStartTime: Long = 0

        private lateinit var database: FirebaseDatabase
        private lateinit var mAuth: FirebaseAuth

        private lateinit var notificationBinding: LayoutNotificationPopupBinding
        private lateinit var notificationAdapter: NotificationAdapter
        private val notificationList = mutableListOf<Notification>()

        private var noInternetDialog: AlertDialog? = null
        private lateinit var networkReceiver: BroadcastReceiver
        private var retryCount = 0
        private val maxRetries = 3



    private val notificationRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("notifications")

        private var selectedTab = "home"

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            binding = ActivityUserPageBinding.inflate(layoutInflater)
            setContentView(binding.root)

            val drawerLayout = binding.drawerLayout
            val navView = binding.navigationView

            database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            mAuth = FirebaseAuth.getInstance()

            val headerView = navView.getHeaderView(0)
            val closeDrawerBtn = headerView.findViewById<ImageView>(R.id.closeDrawerBtn)

            PermissionHelper.requestNotificationPermission(this)

            notificationBinding = LayoutNotificationPopupBinding.inflate(layoutInflater)
            val popupWindow = PopupWindow(
                notificationBinding.root,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )

            val notifRecyclerView = notificationBinding.notificationRecyclerView
            notifRecyclerView.layoutManager = LinearLayoutManager(this)
            notificationAdapter = NotificationAdapter(this, notificationList)
            notifRecyclerView.adapter = notificationAdapter

            listenForNotifications()

            val userId = mAuth.currentUser?.uid
            val userNotificationsRef = notificationRef.child(userId!!)
            userNotificationsRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    notificationList.clear()
                    var hasUnread = false
                    for (notifSnapshot in snapshot.children) {
                        val notif = notifSnapshot.getValue(Notification::class.java)
                        notif?.let {
                            notificationList.add(it)
                            if (!it.isRead) {
                                hasUnread = true
                            }
                        }
                    }
                    notificationList.sortByDescending { it.timestamp }
                    notificationAdapter.notifyDataSetChanged()
                    binding.notificationDot.visibility = if (!hasUnread) View.GONE else View.VISIBLE
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            binding.notificationIcon.setOnClickListener {
                popupWindow.showAsDropDown(binding.notificationIcon, -100, 20)
                binding.notificationDot.visibility = View.GONE
                userNotificationsRef.get().addOnSuccessListener { snapshot ->
                    for (notifSnapshot in snapshot.children) {
                        notifSnapshot.ref.child("isRead").setValue(true)
                    }

                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.notificationDot.visibility = View.GONE
                    }, 300)
                }
            }

            binding.menuIcon.setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.END)
            }

            closeDrawerBtn.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
            }

            navView.setNavigationItemSelectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.nav_terms -> startActivity(Intent(this, TermsConditions::class.java))
                    R.id.privacy -> startActivity(Intent(this, PrivacyPolicy::class.java))
                    R.id.nav_logout -> logoutUser()
                }
                drawerLayout.closeDrawers()
                true
            }

            // Initial UI
            showHomeText()
            binding.fabMain.bringToFront()
            binding.fabMain.translationZ = 16f
            binding.fabMain.elevation = 12f

            binding.coordinatorLayout.setOnClickListener {
                if (isFabMenuOpen) closeFabMenu()
            }

            // Home Navigation
            binding.navHome.setOnClickListener {
                if (selectedTab != "home") {
                    selectedTab = "home"
                    binding.navHome.isEnabled = false
                    binding.navProfile.isEnabled = true

                    supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, UserHomeFragment())
                        .commit()
                    showHomeText()
                    closeFabMenu()
                    binding.homeImg.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_home2_g))
                    binding.profileImg.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_profile2))
                }
            }

            // Profile Navigation
            binding.navProfile.setOnClickListener {
                if (selectedTab != "profile") {
                    selectedTab = "profile"
                    binding.navProfile.isEnabled = false
                    binding.navHome.isEnabled = true

                    supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, UserProfileFragment())
                        .commit()
                    showProfileText()
                    closeFabMenu()
                    binding.profileImg.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_profile2a_g))
                    binding.homeImg.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_home))
                }
            }

            binding.fabBlog.setOnClickListener {
                startActivity(Intent(this, BlogActivity::class.java))
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, UserHomeFragment())
                .commit()

            binding.fabMain.setOnClickListener {
                val fabCard = binding.fabCard
                val upAnim = ObjectAnimator.ofFloat(fabCard, "translationY", fabCard.translationY, fabCard.translationY - 20f)
                val downAnim = ObjectAnimator.ofFloat(fabCard, "translationY", fabCard.translationY - 20f, fabCard.translationY)
                upAnim.duration = 100
                downAnim.duration = 100
                AnimatorSet().apply {
                    playSequentially(upAnim, downAnim)
                    start()
                }
                toggleFabMenu()
            }

            binding.fabScan.setOnClickListener {
                startActivity(Intent(this, MainPage::class.java))
            }

            registerNetworkReceiver()


        }

    private fun registerNetworkReceiver() {
        networkReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (NetworkUtil.isConnected(context)) {
                    retryCount = 0
                    noInternetDialog?.dismiss()
                } else {
                    showNoInternetDialog()
                }
            }
        }

        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkReceiver, filter)
    }


    private fun showNoInternetDialog() {
        if (noInternetDialog?.isShowing == true) return

        val builder = AlertDialog.Builder(this)
        builder.setTitle("No Internet Connection")
        builder.setMessage("Please connect to the internet to continue using the app.")
        builder.setCancelable(false)

        builder.setPositiveButton("Retry") { _, _ ->
            showLoadingAndRetry()
        }

        builder.setNegativeButton("Close App") { _, _ ->
            finishAffinity()
        }

        noInternetDialog = builder.create()
        noInternetDialog?.show()
    }
    private fun showLoadingAndRetry() {
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("Retrying...")
            .setMessage("Checking internet connection...")
            .setCancelable(false)
            .create()

        loadingDialog.show()

        // Wait 5 seconds
        window.decorView.postDelayed({
            loadingDialog.dismiss()

            if (NetworkUtil.isConnected(this)) {
                retryCount = 0
                noInternetDialog?.dismiss()
            } else {
                retryCount++
                if (retryCount >= maxRetries) {
                    val finalDialog = AlertDialog.Builder(this)
                        .setTitle("No Internet")
                        .setMessage("The app will now close due to repeated failures.")
                        .setCancelable(false)
                        .setPositiveButton("Close App") { _, _ ->
                            finishAffinity()
                        }
                        .create()
                    finalDialog.show()
                } else {
                    showNoInternetDialog()
                }
            }
        }, 5000)
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(networkReceiver)
    }






    private fun toggleFabMenu() {
        val fabTranslationDistance = resources.getDimension(R.dimen.fab_translation_distance)

        if (!isFabMenuOpen) {
            binding.fabMenuLayout.apply {
                visibility = View.VISIBLE
                alpha = 0f
                translationY = fabTranslationDistance
                animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }
            binding.fabMain.setImageResource(R.drawable.ic_expandu)
            binding.fabMain.animate().rotation(180f).setDuration(300).start()
        } else {
            binding.fabMenuLayout.animate()
                .translationY(fabTranslationDistance)
                .alpha(0f)
                .setDuration(300)
                .withEndAction { binding.fabMenuLayout.visibility = View.GONE }
                .start()
            binding.fabMain.setImageResource(R.drawable.ic_expandu)
            binding.fabMain.animate().rotation(0f).setDuration(300).start()
        }

        isFabMenuOpen = !isFabMenuOpen
    }

    private fun closeFabMenu() {
        val fabTranslationDistance = resources.getDimension(R.dimen.fab_translation_distance)

        binding.fabMenuLayout.animate()
            .alpha(0f)
            .translationY(fabTranslationDistance)
            .setDuration(300)
            .withEndAction {
                binding.fabMenuLayout.visibility = View.GONE
                binding.fabMenuLayout.alpha = 1f
                binding.fabMenuLayout.translationY = 0f
            }
            .start()

        binding.fabScan.animate()
            .translationY(0f)
            .setDuration(300)
            .start()
        binding.fabBlog.animate()
            .translationY(0f)
            .setDuration(300)
            .start()

        ObjectAnimator.ofFloat(binding.fabMain, "rotation", 45f, 0f)
            .setDuration(300)
            .start()

        binding.fabMain.setImageResource(R.drawable.ic_expandu)
        isFabMenuOpen = false
    }

    private fun showHomeText() {
        binding.homeText.apply {
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(250)
                .start()
        }
        binding.profileText.animate()
            .alpha(0f)
            .translationY(20f)
            .setDuration(250)
            .withEndAction { binding.profileText.visibility = View.GONE }
            .start()
    }

    private fun showProfileText() {
        binding.profileText.apply {
            visibility = View.VISIBLE
            alpha = 0f
            translationY = 20f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(250)
                .start()
        }
        binding.homeText.animate()
            .alpha(0f)
            .translationY(20f)
            .setDuration(250)
            .withEndAction { binding.homeText.visibility = View.GONE }
            .start()
    }

    private fun listenForNotifications() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val notifRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("notifications")
            .child(currentUserId)

        notificationListenerStartTime = System.currentTimeMillis()

        notificationListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val notification = snapshot.getValue(Notification::class.java)
                if (notification != null && !notification.isRead && notification.timestamp > notificationListenerStartTime) {
                    playNotificationSound()

                    Toast.makeText(
                        this@UserPage, // or your correct context
                        notification.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }


        notifRef.addChildEventListener(notificationListener)
    }

    private fun playNotificationSound() {
        val mediaPlayer = MediaPlayer.create(this, R.raw.ding)
        mediaPlayer.setOnCompletionListener {
            it.release()
        }
        mediaPlayer.start()
    }



    private fun logoutUser() {
        val builder = android.app.AlertDialog.Builder(this@UserPage)
        builder.setTitle("Logout")
        builder.setMessage("Are you sure you want to logout?")

        builder.setPositiveButton("Yes") { dialog, _ ->
            FirebaseAuth.getInstance().signOut()
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, Login::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }
}