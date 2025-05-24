package com.example.dermascanai

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.FragmentDermaHomeBinding
import com.example.dermascanai.databinding.LayoutNotificationPopupBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class DermaHomeFragment : Fragment() {
    private var _binding: FragmentDermaHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var mAuth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var notificationBinding: LayoutNotificationPopupBinding
    private lateinit var notificationAdapter: NotificationAdapter
    private val notificationList = mutableListOf<Notification>()
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var googleMap: GoogleMap

    private val notificationRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .getReference("notifications")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDermaHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy hh:mm a")
        val formatted = current.format(formatter)

        val drawerLayout = binding.drawerLayout
        val navView = binding.navigationView
        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        mAuth = FirebaseAuth.getInstance()

        val headerView = navView.getHeaderView(0)
        val closeDrawerBtn = headerView.findViewById<ImageView>(R.id.closeDrawerBtn)

        binding.ratings.setOnClickListener {
            val clinicId = mAuth.currentUser?.uid
            val intent = Intent(requireContext(), RatingView::class.java)
            intent.putExtra("clinicId", clinicId)
            startActivity(intent)
        }

        binding.dateTimeText.text = formatted

        notificationBinding = LayoutNotificationPopupBinding.inflate(layoutInflater)
        val popupWindow = PopupWindow(
            notificationBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        val notifRecyclerView = notificationBinding.notificationRecyclerView
        notifRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        notificationAdapter = NotificationAdapter(requireContext(), notificationList)
        notifRecyclerView.adapter = notificationAdapter

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
                        if (!it.isRead) hasUnread = true
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
        displayTopBlogPost(binding) // ---------------------------------------------------------------------


        binding.menuIcon.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        closeDrawerBtn.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.settings -> Toast.makeText(context, "Settings Clicked", Toast.LENGTH_SHORT).show()
                R.id.nav_terms -> startActivity(Intent(requireContext(), TermsConditions::class.java))
                R.id.privacy -> startActivity(Intent(requireContext(), PrivacyPolicy::class.java))
                R.id.nav_logout -> logoutUser()
            }
            drawerLayout.closeDrawers()
            true
        }

        binding.appointmentDate.setOnClickListener {
            val intent = Intent(requireContext(), BookingApprovalRecords::class.java)
            startActivity(intent)
        }

        val dateText = getCurrentFormattedDate()
        binding.currentTime.text = dateText

        checkApprovedBookingForToday("Derma_Clinic_Dummy", requireContext()) { isApproved ->
            binding.nameAppoint.text = if (isApproved) "You have an approved booking today" else "No Approved Booking Today"
        }
        fetchUserData()
        loadFeaturedClinic() // Load a random featured clinic on start
    }

    private fun loadFeaturedClinic() {
        val clinicRef = database.getReference("clinicInfo")
        clinicRef.get().addOnSuccessListener { snapshot ->
            val clinics = snapshot.children.mapNotNull { it.getValue(ClinicInfo::class.java) }
            if (clinics.isNotEmpty()) {
                val featuredClinic = clinics.random()
                binding.name.text = featuredClinic.name
                binding.totalR.text = String.format("%.1f", featuredClinic?.rating ?: 0f)

                if (!featuredClinic.logoImage.isNullOrEmpty()) {
                    val decodedBytes = Base64.decode(featuredClinic.logoImage, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    binding.profileImageView.setImageBitmap(bitmap)
                }

                val address = featuredClinic.clinicAddress ?: ""
                if (address.isNotEmpty()) {
                    showClinicOnMap(address)
                }


            } else {
                Toast.makeText(requireContext(), "No clinics found.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to load clinics.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showClinicOnMap(address: String) {
        mapFragment = childFragmentManager.findFragmentById(R.id.popupMapFragment) as SupportMapFragment
        mapFragment.getMapAsync { gMap ->
            googleMap = gMap
            showLocationOnMap(address)
        }
    }

    private fun fetchUserData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val dermaRef: DatabaseReference = database.getReference("clinicInfo").child(userId ?: return)

        dermaRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val clinicInfo = snapshot.getValue(ClinicInfo::class.java)

                binding.fullName.setText(clinicInfo?.name ?: "")
                binding.totalRatings.text = String.format("%.1f", clinicInfo?.rating ?: 0f)

                clinicInfo?.logoImage?.let {
                    if (it.isNotEmpty()) {
                        val decodedBytes = Base64.decode(it, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        binding.profileView.setImageBitmap(bitmap)
                    }
                }

            }
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to fetch clinic info", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLocationOnMap(address: String) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        Thread {
            try {
                val addresses = geocoder.getFromLocationName(address, 1)
                if (!addresses.isNullOrEmpty()) {
                    val location = addresses[0]
                    val latLng = LatLng(location.latitude, location.longitude)
                    requireActivity().runOnUiThread {
                        googleMap.clear()
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        googleMap.addMarker(MarkerOptions().position(latLng).title("Derma Location"))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun logoutUser() {
        val builder = AlertDialog.Builder(requireContext())
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
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    private fun getCurrentFormattedDate(): String {
        val currentDate = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("MMM dd - E", Locale.ENGLISH)
        return currentDate.format(formatter)
    }

    private fun checkApprovedBookingForToday(clinicId: String, context: Context, callback: (Boolean) -> Unit) {
        val bookingsRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("clinicBookings")
            .child(clinicId)
        val formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy")
        val todayFormatted = LocalDate.now().format(formatter)

        bookingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val found = snapshot.children.any {
                    val date = it.child("date").getValue(String::class.java) ?: ""
                    val status = it.child("status").getValue(String::class.java) ?: ""
                    date.startsWith(todayFormatted) && status.equals("confirmed", true)
                }

                callback(found)
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Error checking bookings: ${error.message}", Toast.LENGTH_SHORT).show()
                callback(false)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun displayTopBlogPost(binding: FragmentDermaHomeBinding) {
        val blogPostsRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("blogPosts")

        blogPostsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var topPost: BlogPost? = null

                for (postSnapshot in snapshot.children) {
                    val blogPost = postSnapshot.getValue(BlogPost::class.java)
                    blogPost?.let {
                        if (topPost == null ||
                            (it.commentCount + it.likeCount) > (topPost!!.commentCount + topPost!!.likeCount)
                        ) {
                            topPost = it
                        }
                    }
                }

                topPost?.let { post ->
                    binding.contentDiscuss.text = post.content
                    binding.byNameTextView.text = "By: ${post.fullName}"

                    binding.topDiscussion.setOnClickListener {
                        val intent = Intent(binding.root.context, BlogView::class.java).apply {
                            putExtra("fullName", post.fullName)
                            putExtra("content", post.content)
                            putExtra("id", post.postId)
                        }
                        binding.root.context.startActivity(intent)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(binding.root.context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }



}
