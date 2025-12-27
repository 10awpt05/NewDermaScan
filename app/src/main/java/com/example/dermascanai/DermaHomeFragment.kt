package com.example.dermascanai

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.util.Base64
import android.util.Log
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
import com.example.dermascanai.RatingView
import com.example.dermascanai.databinding.FragmentDermaHomeBinding
import com.example.dermascanai.databinding.LayoutNotificationPopupBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.model.Review
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import com.prolificinteractive.materialcalendarview.spans.DotSpan
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class DermaHomeFragment : Fragment() {
    private var _binding: FragmentDermaHomeBinding? = null
    private val binding get() = _binding
    private lateinit var mAuth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var notificationBinding: LayoutNotificationPopupBinding
    private lateinit var notificationAdapter: NotificationAdapter
    private val notificationList = mutableListOf<Notification>()
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var googleMap: GoogleMap
    private val confirmedDates = HashSet<CalendarDay>()
    private lateinit var ratingsRef: DatabaseReference
    private val feedbackList = mutableListOf<RatingModel>()
    private lateinit var adapter: RatingsAdapter


//    private val notificationRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
//        .getReference("notifications")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDermaHomeBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy hh:mm a")
        val formatted = current.format(formatter)

        val drawerLayout = binding?.drawerLayout
        val navView = binding?.navigationView
        adapter = RatingsAdapter(feedbackList)
        binding?.recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        binding?.recyclerView?.adapter = adapter
        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        mAuth = FirebaseAuth.getInstance()
        val clinicId = mAuth.currentUser?.uid ?: return

        ratingsRef = database.getReference("ratings").child(clinicId.toString())

        val headerView = navView?.getHeaderView(0)
        val closeDrawerBtn = headerView?.findViewById<ImageView>(R.id.closeDrawerBtn)

        binding?.viewReviews?.setOnClickListener {
            val clinicId = mAuth.currentUser?.uid
            val intent = Intent(requireContext(), RatingView::class.java)
            intent.putExtra("clinicId", clinicId)
            startActivity(intent)
        }

        fetchConfirmedBookingsAndDecorateCalendar()


        binding?.dateTimeText?.text = formatted

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

//        val userId = mAuth.currentUser?.uid
//        val userNotificationsRef = notificationRef.child(userId!!)
//        userNotificationsRef.addValueEventListener(object : ValueEventListener {
//            override fun onDataChange(snapshot: DataSnapshot) {
//                notificationList.clear()
//                var hasUnread = false
//                for (notifSnapshot in snapshot.children) {
//                    val notif = notifSnapshot.getValue(Notification::class.java)
//                    notif?.let {
//                        notificationList.add(it)
//                        if (!it.isRead) hasUnread = true
//                    }
//                }
//                notificationList.sortByDescending { it.timestamp }
//                notificationAdapter.notifyDataSetChanged()
//                binding.notificationDot.visibility = if (hasUnread) View.VISIBLE else View.GONE
//            }
//            override fun onCancelled(error: DatabaseError) {
//                Toast.makeText(requireContext(), "Failed to load notifications", Toast.LENGTH_SHORT).show()
//            }
//        })

//        binding.notificationIcon.setOnClickListener {
//            popupWindow.showAsDropDown(binding.notificationIcon, -100, 20)
//            binding.notificationDot.visibility = View.GONE
//            userNotificationsRef.get().addOnSuccessListener { snapshot ->
//                for (notifSnapshot in snapshot.children) {
//                    notifSnapshot.ref.child("isRead").setValue(true)
//                }
//            }
//        }
        displayTopBlogPost(binding!!) // ---------------------------------------------------------------------


//        binding.menuIcon.setOnClickListener {
//            drawerLayout.openDrawer(GravityCompat.END)
//        }

        closeDrawerBtn?.setOnClickListener {
            drawerLayout?.closeDrawer(GravityCompat.END)
            }

        navView?.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {

                R.id.nav_terms -> startActivity(Intent(requireContext(), TermsConditions::class.java))
                R.id.privacy -> startActivity(Intent(requireContext(), PrivacyPolicy::class.java))
                R.id.nav_logout -> logoutUser()
            }
            drawerLayout?.closeDrawers()
            true
        }

        binding?.appointmentDate?.setOnClickListener {
            val intent = Intent(requireContext(), BookingApprovalRecords::class.java)
            startActivity(intent)
        }

        val dateText = getCurrentFormattedDate()
        binding?.currentTime?.text = dateText


        checkApprovedBookingCountForToday(clinicId, requireContext()) { count ->
            binding?.nameAppoint?.text = when {
                count == 0 -> "No approved bookings today"
                count == 1 -> "You have 1 approved booking today"
                else -> "You have $count approved bookings today"
            }
        }

        fetchUserData()
        loadFeaturedClinic() // Load a random featured clinic on start

        ratingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                feedbackList.clear()
                val usersRef = database.getReference("userInfo")

                val tempList = mutableListOf<RatingModel>()
                var remaining = snapshot.childrenCount

                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key ?: continue
                    val message = userSnapshot.child("message").getValue(String::class.java) ?: ""
                    val rating = userSnapshot.child("rating").getValue(Float::class.java) ?: 0f
                    val timestamp = userSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                    val ratingModel = RatingModel(rating, message, timestamp, userId)

                    usersRef.child(userId).child("name").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnap: DataSnapshot) {
                            ratingModel.userName = userSnap.getValue(String::class.java) ?: "Anonymous"
                            tempList.add(ratingModel)
                            remaining--

                            if (remaining == 0L) {
                                feedbackList.clear()
                                feedbackList.addAll(tempList)
                                adapter.notifyDataSetChanged()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            remaining--
                            if (remaining == 0L) {
                                feedbackList.clear()
                                feedbackList.addAll(tempList)
                                adapter.notifyDataSetChanged()
                            }
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
//                Toast.makeText(this@DermaHomeFragment, "Failed: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })

        fetchBookingSummaryForClinic(clinicId, requireContext()) { pending, approved, total ->
            binding?.noPedning?.text = "$pending"
            binding?.noApproved?.text = "$approved"
            binding?.noTotal?.text = "$total"
        }


    }

    private fun loadReviewsForCurrentClinic(adapter: ReviewsAdapter) {
        val currentClinicId = FirebaseAuth.getInstance().currentUser?.uid

        if (currentClinicId == null) {
            Toast.makeText(context, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val dbUrl = "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        val ratingsRef = FirebaseDatabase.getInstance(dbUrl)
            .getReference("ratings")
            .child(currentClinicId)

        val usersRef = FirebaseDatabase.getInstance(dbUrl).getReference("userInfo")

        ratingsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reviews = mutableListOf<RatingModel>()
                val totalChildren = snapshot.childrenCount
                var processed = 0

                if (totalChildren == 0L) {
                    adapter.submitList(emptyList())
                    return
                }

                for (reviewSnap in snapshot.children) {
                    val message = reviewSnap.child("message").getValue(String::class.java) ?: ""
                    val rating = reviewSnap.child("rating").getValue(Float::class.java) ?: 0f
                    val timestamp = reviewSnap.child("timestamp").getValue(Long::class.java) ?: 0
                    val userId = reviewSnap.key ?: ""

                    // fetch reviewer details
                    usersRef.child(userId).get().addOnSuccessListener { userSnap ->
                        val reviewerName = userSnap.child("fullName").getValue(String::class.java) ?: "Anonymous"
                        val reviewerPhoto = userSnap.child("profileImage").getValue(String::class.java)


                        reviews.add(
                            RatingModel(
                                message = message,
                                rating = rating,
                                timestamp = timestamp,
                                userName = reviewerName,
                                reviewerPhoto = reviewerPhoto,
                                userId = userId
                            )
                        )

                        processed++
                        if (processed == totalChildren.toInt()) {
                            // update once when all lookups are done
                            reviews.sortByDescending { it.timestamp }
                            adapter.submitList(reviews.toList())
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load reviews", Toast.LENGTH_SHORT).show()
            }
        })
    }




    private fun loadFeaturedClinic() {
        val clinicRef = database.getReference("clinicInfo")
        clinicRef.get().addOnSuccessListener { snapshot ->
            val clinics = snapshot.children.mapNotNull { it.getValue(ClinicInfo::class.java) }
            if (clinics.isNotEmpty()) {
                val featuredClinic = clinics.random()
                binding?.name?.text = featuredClinic.clinicName
                binding?.totalR?.text = String.format("%.1f", featuredClinic?.rating ?: 0f)

                if (!featuredClinic.logoImage.isNullOrEmpty()) {
                    val decodedBytes = Base64.decode(featuredClinic.logoImage, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    binding?.profileImageView?.setImageBitmap(bitmap)
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

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun fetchUserData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val dermaRef: DatabaseReference = database.getReference("clinicInfo").child(userId ?: return)

        dermaRef.get().addOnSuccessListener { snapshot ->
            if (!isAdded || _binding == null) return@addOnSuccessListener  // ✅ Check fragment is active and view exists

            if (snapshot.exists()) {
                val clinicInfo = snapshot.getValue(ClinicInfo::class.java)

                _binding?.apply {
                    fullName.setText(clinicInfo?.clinicName ?: "")

                    // ✅ Set rating correctly
                    ratingBar.rating = clinicInfo?.rating ?: 0f
                    ratingBar.scaleX = 0.6f
                    ratingBar.scaleY = 0.6f

                    if (clinicInfo?.status == "pending") {
                        status.setImageDrawable(resources.getDrawable(R.drawable.pending))
                    } else if (clinicInfo?.status == "verified") {
                        status.setImageDrawable(resources.getDrawable(R.drawable.verified))
                    } else {
                        status.setImageDrawable(resources.getDrawable(R.drawable.decline))
                    }

                    clinicInfo?.logoImage?.let {
                        if (it.isNotEmpty()) {
                            val decodedBytes = Base64.decode(it, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            profileView.setImageBitmap(bitmap)
                        }
                    }
                }
            }
        }
            .addOnFailureListener {
            if (isAdded && _binding != null) {
                Toast.makeText(context, "Failed to fetch clinic info", Toast.LENGTH_SHORT).show()
            }
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

    private fun checkApprovedBookingCountForToday(
        clinicId: String,
        context: Context,
        callback: (Int) -> Unit
    ) {
        val bookingsRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("clinicInfo")
            .child(clinicId)
            .child("bookings") // ✅ your structure

        // Firebase saves as "Aug 30,2025"
        val firebaseFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        val today = LocalDate.now()

        bookingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var count = 0

                for (bookingSnapshot in snapshot.children) {
                    val dateStr = bookingSnapshot.child("date").getValue(String::class.java) ?: ""
                    val status = bookingSnapshot.child("status").getValue(String::class.java) ?: ""

                    try {
                        val bookingDate = LocalDate.parse(dateStr, firebaseFormatter)
                        if (bookingDate == today && status.equals("confirmed", ignoreCase = true)) {
                            count++
                        }
                    } catch (e: Exception) {
                        Log.e("BookingDebug", "Date parse error for '$dateStr': ${e.message}")
                    }
                }

                callback(count)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Error checking bookings: ${error.message}", Toast.LENGTH_SHORT).show()
                callback(0)
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

//                topPost?.let { post ->
//                    binding.contentDiscuss.text = post.content
//                    binding.byNameTextView.text = "By: ${post.fullName}"
//
//                    binding.topDiscussion.setOnClickListener {
//                        val intent = Intent(binding.root.context, BlogView::class.java).apply {
//                            putExtra("fullName", post.fullName)
//                            putExtra("content", post.content)
//                            putExtra("id", post.postId)
//                        }
//                        binding.root.context.startActivity(intent)
//                    }
//                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(binding.root.context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    private fun fetchConfirmedBookingsAndDecorateCalendar() {
        val calendarView = binding?.calendarView
        confirmedDates.clear()

        val clinicId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val bookingsRef = FirebaseDatabase.getInstance(
            "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).getReference("clinicInfo")
            .child(clinicId)
            .child("bookings")

        bookingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (bookingSnapshot in snapshot.children) {
                    val status = bookingSnapshot.child("status").getValue(String::class.java)
                    val dateStr = bookingSnapshot.child("date").getValue(String::class.java)

                    if (status.equals("confirmed", ignoreCase = true) && dateStr != null) {
                        try {
                            // Adjust format to match your DB
                            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
                            val localDate = LocalDate.parse(dateStr, formatter)
                            val calendarDay = CalendarDay.from(
                                localDate.year,
                                localDate.monthValue - 1,
                                localDate.dayOfMonth
                            )

                            confirmedDates.add(calendarDay)
                        } catch (e: Exception) {
                            Log.e("CalendarDebug", "Failed to parse date: $dateStr", e)
                        }
                    }
                }

                // Decorate calendar
                calendarView?.addDecorator(ConfirmedBookingDecorator(confirmedDates))
                calendarView?.addDecorator(TodayDecorator())

                // Click listener to open booking page
                calendarView?.setOnDateChangedListener { _, date, _ ->
                    if (confirmedDates.contains(date)) {
                        val intent = Intent(requireContext(), BookingApprovalRecords::class.java)
                        intent.putExtra("selectedDate", date.date.toString())
                        intent.putExtra("openApprovedTab", true)
                        startActivity(intent)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    requireContext(),
                    "Failed to load bookings: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun fetchBookingSummaryForClinic(clinicId: String, context: Context, callback: (pending: Int, approved: Int, total: Int) -> Unit) {
        val bookingsRef = FirebaseDatabase.getInstance(
            "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).getReference("clinicInfo")
            .child(clinicId)
            .child("bookings")

        val today = LocalDate.now()
        var pendingCount = 0
        var approvedCount = 0
        var totalCount = 0

        bookingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (bookingSnapshot in snapshot.children) {
                    val status = bookingSnapshot.child("status").getValue(String::class.java) ?: continue
                    val dateStr = bookingSnapshot.child("date").getValue(String::class.java) ?: continue

                    try {
                        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
                        val bookingDate = LocalDate.parse(dateStr, formatter)

                        // Only count bookings from today onward
                        if (!bookingDate.isBefore(today)) {
                            totalCount++

                            when (status.lowercase()) {
                                "pending" -> pendingCount++
                                "confirmed" -> approvedCount++
                            }
                        }

                    } catch (e: Exception) {
                        Log.e("BookingSummary", "Failed to parse booking date: $dateStr", e)
                    }
                }

                callback(pendingCount, approvedCount, totalCount)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load bookings: ${error.message}", Toast.LENGTH_SHORT).show()
                callback(0, 0, 0)
            }
        })
    }







}
class ConfirmedBookingDecorator(private val dates: HashSet<CalendarDay>) : DayViewDecorator {
    override fun shouldDecorate(day: CalendarDay): Boolean = dates.contains(day)

    override fun decorate(view: DayViewFacade) {
        view.addSpan(CircleSpan(Color.GREEN)) // Green circle
    }
}


class TodayDecorator : DayViewDecorator {
    private val today = CalendarDay.today()

    override fun shouldDecorate(day: CalendarDay): Boolean = day == today

    override fun decorate(view: DayViewFacade) {
        view.addSpan(CircleSpan(Color.RED)) // Red circle
    }
}


