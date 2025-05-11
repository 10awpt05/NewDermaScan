package com.example.dermascanai

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Base64
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.FragmentDermaProfileBinding
import com.example.dermascanai.databinding.FragmentProfileUserBinding
import com.example.dermascanai.databinding.LayoutNotificationPopupBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class DermaProfileFragment : Fragment() {
    private var _binding: FragmentDermaProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: FirebaseDatabase
    private lateinit var mAuth: FirebaseAuth

    private lateinit var notificationBinding: LayoutNotificationPopupBinding
    private lateinit var notificationAdapter: NotificationAdapter
    private val notificationList = mutableListOf<Notification>()

    private val notificationRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .getReference("notifications")


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDermaProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val drawerLayout = binding.drawerLayout
        val navView = binding.navigationView

        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        mAuth = FirebaseAuth.getInstance()

        val headerView = navView.getHeaderView(0)
        val closeDrawerBtn = headerView.findViewById<ImageView>(R.id.closeDrawerBtn)

        notificationBinding = LayoutNotificationPopupBinding.inflate(layoutInflater)
        val popupWindow = PopupWindow(
            notificationBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        binding.Records.setOnClickListener {
            val intent = Intent(requireContext(), DermaRecords::class.java)
            startActivity(intent)
        }

        binding.appointmentBtn.setOnClickListener {
            val intent = Intent(requireContext(), BookingApprovalRecords::class.java)
            startActivity(intent)
        }

        val notifRecyclerView = notificationBinding.notificationRecyclerView
        notifRecyclerView.layoutManager = LinearLayoutManager(requireContext())


        notificationAdapter = NotificationAdapter(requireContext(), notificationList)
        notifRecyclerView.adapter = notificationAdapter

        val userId = mAuth.currentUser?.uid

        val userNotificationsRef = notificationRef.child(userId!!)
        userNotificationsRef.addListenerForSingleValueEvent(object : ValueEventListener {
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

                binding.notificationDot.visibility = if (hasUnread) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to load notifications", Toast.LENGTH_SHORT).show()
            }
        })

        binding.notificationIcon.setOnClickListener {
            popupWindow.showAsDropDown(binding.notificationIcon, -100, 20)

            binding.notificationDot.visibility = View.GONE

            userNotificationsRef.get().addOnSuccessListener { snapshot ->
                for (notifSnapshot in snapshot.children) {
                    notifSnapshot.ref.child("isRead").setValue(true)
                }
            }

        }

        binding.menuIcon.setOnClickListener {
            val drawerLayout = requireActivity().findViewById<DrawerLayout>(R.id.drawerLayout)
            drawerLayout.openDrawer(GravityCompat.END)
        }


        closeDrawerBtn.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.settings -> {
                    Toast.makeText(context, "Settings Clicked", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_terms -> {
                    val intent = Intent(requireContext(), TermsConditions::class.java)
                    startActivity(intent)
                }
                R.id.privacy -> {
                    val intent = Intent(requireContext(), PrivacyPolicy::class.java)
                    startActivity(intent)
                }
                R.id.nav_logout -> {
                    logoutUser()

                }
            }
            drawerLayout.closeDrawers()
            true
        }


        fetchUserData()
        val imageView = binding.bannerImg

        imageView.drawable?.let { drawable ->
            val bitmap = (drawable as BitmapDrawable).bitmap
            Palette.from(bitmap).generate { palette ->
                palette?.let {
                    val dominantColor = it.getDominantColor(Color.WHITE)
                    val isDark = isColorDark(dominantColor)
                    val textColor = if (isDark) Color.WHITE else Color.BLACK
                    binding.fullName.setTextColor(textColor)
                    binding.status.setTextColor(textColor)

                }
            }
        }

        binding.logout.setOnClickListener {
            logoutUser()
        }

        binding.btnToggleInfo.setOnClickListener {
            val intent = Intent(requireContext(), DermaEditInfo::class.java)
            startActivity(intent)
        }
        binding.editBannerIcon.setOnClickListener {
            val banners = listOf(
                R.drawable.banner1,
                R.drawable.banner2,
                R.drawable.banner3,
                R.drawable.banner4,
                R.drawable.banner5,
                R.drawable.banner6,
                R.drawable.banner7,
                R.drawable.banner8,
                R.drawable.banner9,
                R.drawable.banner10,
                R.drawable.banner11,
                R.drawable.banner12

            )

            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Choose a Banner")
                .setItems(arrayOf("Banner 1", "Banner 2", "Banner 3", "Banner 4", "Banner 5", "Banner 6", "Banner 7", "Banner 8", "Banner 9", "Banner 10", "Banner 11", "Banner 12")) { _, which ->
                    binding.bannerImg.setImageResource(banners[which])
                }
                .create()

            dialog.show()
        }



    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) +
                0.587 * Color.green(color) +
                0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }

    private fun fetchUserData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val dermaRef: DatabaseReference = database.getReference("dermaInfo").child(userId ?: return)

        dermaRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val dermaInfo = snapshot.getValue(DermaInfo::class.java)


                binding.fullName.setText(dermaInfo?.name ?: "")
                val text = (dermaInfo?.status ?: "")
                binding.status.text = "Status: $text "

                dermaInfo?.profileImage?.let {
                    if (it.isNotEmpty()) {
                        val decodedBytes = Base64.decode(it, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        binding.profPic.setImageBitmap(bitmap)
                    }
                }
            } else {
//                Toast.makeText(this, "No user data found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
//            Toast.makeText(this, "Failed to fetch user data: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logoutUser() {
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Logout")
        builder.setMessage("Are you sure you want to logout?")

        builder.setPositiveButton("Yes") { dialog, _ ->
            FirebaseAuth.getInstance().signOut()
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()

            val intent = Intent(requireActivity(), Login::class.java)
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