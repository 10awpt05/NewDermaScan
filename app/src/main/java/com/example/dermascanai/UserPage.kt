package com.example.dermascanai

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.dermascanai.databinding.ActivityUserPageBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase

class UserPage : AppCompatActivity() {
    private lateinit var binding: ActivityUserPageBinding
    private var isFabMenuOpen = false
    private lateinit var notificationListener: ChildEventListener
    private var notificationListenerStartTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserPageBinding.inflate(layoutInflater)
        setContentView(binding.root)



        listenForNotifications()

        // Show initial Home text and fragment
        showHomeText()
        binding.fabMain.bringToFront()
        binding.fabMain.translationZ = 16f
        binding.fabMain.elevation = 12f

        // Click outside FAB menu closes it
        binding.coordinatorLayout.setOnClickListener {
            if (isFabMenuOpen) closeFabMenu()
        }

        // Nav Home click - show UserHomeFragment
        binding.navHome.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, UserHomeFragment())
                .commit()
            showHomeText()
            closeFabMenu()
            binding.homeImg.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_home2))
            binding.profileImg.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_profile2))
        }

        // Nav Profile click - show UserProfileFragment
        binding.navProfile.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, UserProfileFragment())
                .commit()
            showProfileText()
            closeFabMenu()
            binding.profileImg.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_profile2a))
            binding.homeImg.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_home))
        }

        // FAB Blog click
        binding.fabBlog.setOnClickListener {
            startActivity(Intent(this, BlogActivity::class.java))
        }

        // Load initial fragment as HomeFragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, UserHomeFragment())
            .commit()

        // FAB Main click toggles FAB menu with bounce animation
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

        // FAB Scan click
        binding.fabScan.setOnClickListener {
            startActivity(Intent(this, MainPage::class.java))
        }
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
}