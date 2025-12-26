package com.example.dermascanai

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import androidx.recyclerview.widget.GridLayoutManager
import com.example.dermascanai.databinding.FragmentDermaHomeBinding
import com.example.dermascanai.databinding.FragmentHomeUserBinding
import com.example.dermascanai.databinding.LayoutNotificationPopupBinding
import com.example.dermascanai.databinding.NavHeaderBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import android.Manifest


class UserHomeFragment : Fragment() {
    private var _binding: FragmentHomeUserBinding? = null
    private val binding get() = _binding!!

    private lateinit var mDatabase: DatabaseReference
    private lateinit var mAuth: FirebaseAuth


    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var fullscreenMapContainer: ConstraintLayout
    private lateinit var backFromMap: ImageView
    private lateinit var fullMapContainer: FrameLayout

//    private lateinit var notificationBinding: LayoutNotificationPopupBinding
//    private lateinit var notificationAdapter: NotificationAdapter
//    private val notificationList = mutableListOf<Notification>()

    // Store event listeners so we can remove them in onDestroyView
    private var clinicEventListener: ValueEventListener? = null
    private var tipEventListener: ValueEventListener? = null
    private var notificationEventListener: ValueEventListener? = null


    private val LOCATION_REQUEST_CODE = 1001

//    private val notificationRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
//        .getReference("notifications")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fullscreenMapContainer = binding.root.findViewById(R.id.fullscreenMapContainer)
        backFromMap = binding.root.findViewById(R.id.backFromMap)
        fullMapContainer = binding.root.findViewById(R.id.fullMapContainer)


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        binding.viewAllNearby.setOnClickListener {
            fullscreenMapContainer.visibility = View.VISIBLE
            setupFullscreenMap()
        }

        backFromMap.setOnClickListener {
            fullscreenMapContainer.visibility = View.GONE
        }

        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy hh:mm a")
        val formatted = current.format(formatter)

        val drawerLayout = binding.drawerLayout
        val navView = binding.navigationView

        mAuth = FirebaseAuth.getInstance()
        mDatabase = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("userInfo")

//        val clinicList = mutableListOf<ClinicInfo>()
        val databaseRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("clinicInfo")

        val headerBinding = NavHeaderBinding.bind(navView.getHeaderView(0))
        val closeDrawerBtn = headerBinding.closeDrawerBtn


//        headerBinding.closeDrawerBtn.setOnClickListener {
//            drawerLayout.closeDrawer(GravityCompat.END)
//        }

//        displayTopBlogPost(binding)


        binding.dateTimeText.text = formatted

        val userId = mAuth.currentUser?.uid
        getUserData(userId.toString())

//        notificationBinding = LayoutNotificationPopupBinding.inflate(layoutInflater)
//        val popupWindow = PopupWindow(
//            notificationBinding.root,
//            ViewGroup.LayoutParams.WRAP_CONTENT,
//            ViewGroup.LayoutParams.WRAP_CONTENT,
//            true
//        )

//        val gridLayoutManager = GridLayoutManager(requireContext(), 2)
//        binding.dermaRecycleView.layoutManager = gridLayoutManager
//        binding.dermaRecycleView.adapter = AdapterDoctorList(clinicList)
//        binding.dermaRecycleView.setHasFixedSize(true)


//        val notifRecyclerView = notificationBinding.notificationRecyclerView
//        notifRecyclerView.layoutManager = LinearLayoutManager(requireContext())
//
//        notificationAdapter = NotificationAdapter(requireContext(), notificationList)
//        notifRecyclerView.adapter = notificationAdapter
//
//        if (userId != null) {
//            notificationEventListener = object : ValueEventListener {
//                override fun onDataChange(snapshot: DataSnapshot) {
//                    if (_binding == null) return // Check if view is still valid
//
//                    notificationList.clear()
//                    var hasUnread = false
//                    for (notifSnapshot in snapshot.children) {
//                        val notif = notifSnapshot.getValue(Notification::class.java)
//                        notif?.let {
//                            notificationList.add(it)
//                            if (!it.isRead) {
//                                hasUnread = true
//                            }
//                        }
//                    }
//
//                    notificationList.sortByDescending { it.timestamp }
//
//                    notificationAdapter.notifyDataSetChanged()
//
////                    binding.notificationDot.visibility = if (hasUnread) View.VISIBLE else View.GONE
//                }
//
//                override fun onCancelled(error: DatabaseError) {
//                    if (isAdded && context != null) { // Check if fragment is still attached
//                        Toast.makeText(requireContext(), "Failed to load notifications", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            }
//
//            notificationRef.child(userId).addValueEventListener(notificationEventListener!!)
//
//            getUserData(userId)
//        } else {
//            if (isAdded && context != null) {
//                Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
//            }
//        }

//        binding.notificationIcon.setOnClickListener {
//            popupWindow.showAsDropDown(binding.notificationIcon, -100, 20)
//            binding.notificationDot.visibility = View.GONE
//
//            if (userId != null) {
//                notificationRef.child(userId).get().addOnSuccessListener { snapshot ->
//                    for (notifSnapshot in snapshot.children) {
//                        notifSnapshot.ref.child("isRead").setValue(true)
//                    }
//                }
//            }
//        }

        checkLocationPermission()

        binding.dermaList.setOnClickListener {
            val intent = Intent(requireContext(), DoctorLists::class.java)
            startActivity(intent)
        }
        val clinicList = mutableListOf<ClinicInfo>()
        val adapter = AdapterDermaHomeList(clinicList)
        binding.dermaRecycleView.setHasFixedSize(true)
        binding.dermaRecycleView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.dermaRecycleView.adapter = adapter


//        binding.dermaRecycleView.layoutManager =
//            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)



//        binding.menuIcon.setOnClickListener {
//            val drawerLayout = requireActivity().findViewById<DrawerLayout>(R.id.drawerLayout)
//            drawerLayout.openDrawer(GravityCompat.END)
//        }

//        closeDrawerBtn.setOnClickListener {
//            drawerLayout.closeDrawer(GravityCompat.END)
//        }
//
//        navView.setNavigationItemSelectedListener { menuItem ->
//            when (menuItem.itemId) {
//                R.id.settings -> {
//                    Toast.makeText(context, "Settings Clicked", Toast.LENGTH_SHORT).show()
//                }
//                R.id.nav_terms -> {
//                    val intent = Intent(requireContext(), TermsConditions::class.java)
//                    startActivity(intent)
//                }
//                R.id.privacy -> {
//                    val intent = Intent(requireContext(), PrivacyPolicy::class.java)
//                    startActivity(intent)
//                }
//                R.id.nav_logout -> {
//                    logoutUser()
//                }
//            }
//            drawerLayout.closeDrawers()
//            true
//        }
        val cardView = binding.cardGradientBackground

// Pick consistent color based on current day
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val colors = listOf(
            R.drawable.gradient_red,
            R.drawable.gradient_green,
            R.drawable.gradient_black,
            R.drawable.gradient_blue
        )
        val selectedGradient = colors[dayOfYear % colors.size]

// Set gradient as background
        cardView.setBackgroundResource(selectedGradient)


        val gradientLayout = binding.cardGradientBackground
        gradientLayout.setBackgroundResource(selectedGradient)

        val tipRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("dailyTips")

        tipEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return

                val tipsList = mutableListOf<DataSnapshot>()

                for (tipSnapshot in snapshot.children) {
                    tipsList.add(tipSnapshot)
                }

                if (tipsList.isNotEmpty()) {
                    val randomTip = tipsList.random()

                    val tipText = randomTip.child("text").getValue(String::class.java)
                    val imageBase64 = randomTip.child("image_base64").getValue(String::class.java)

                    binding.dailyTips.text = tipText ?: "Stay tuned for more skin care tips!"

                    if (!imageBase64.isNullOrEmpty()) {
                        try {
                            val decodedBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            binding.dailyImage.setImageBitmap(bitmap)
                            binding.dailyImage.visibility = View.VISIBLE
                        } catch (e: Exception) {
                            Log.e("DailyTips", "Image decode failed: ${e.message}")
                            binding.dailyImage.visibility = View.GONE
                        }
                    } else {
                        binding.dailyImage.visibility = View.GONE
                    }
                } else {
                    binding.dailyTips.text = "No tips available right now!"
                    binding.dailyImage.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding == null) return

                binding.dailyTips.text = "Failed to load tip."
                binding.dailyImage.visibility = View.GONE
                Log.e("DailyTips", "Error: ${error.message}")
            }
        }

        tipRef.addListenerForSingleValueEvent(tipEventListener!!)


//
//        binding.dermaRecycleView.layoutManager = LinearLayoutManager(context)

        // More robust error handling for the database query
        clinicEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return

                clinicList.clear()
                for (userSnap in snapshot.children) {
                    val user = userSnap.getValue(ClinicInfo::class.java)
                    if (user?.role?.lowercase(Locale.ROOT) == "derma") {
                        clinicList.add(user)
                    }
                }

                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Database", "Database error: ${error.message}")
            }
        }
        databaseRef.addValueEventListener(clinicEventListener!!)

    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Remove all event listeners to prevent callbacks after view is destroyed
        val databaseRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")

        clinicEventListener?.let {
            databaseRef.getReference("clinicInfo").removeEventListener(it)
        }

        tipEventListener?.let {
            databaseRef.getReference("dailyTips").removeEventListener(it)
        }

        val userId = mAuth.currentUser?.uid
//        if (userId != null && notificationEventListener != null) {
//            notificationRef.child(userId).removeEventListener(notificationEventListener!!)
//        }

        // Clear the binding
        _binding = null
    }

    private fun getUserData(userId: String) {
        val userRef = mDatabase.child(userId)

        userRef.get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener

            if (snapshot.exists()) {
                val user = snapshot.getValue(UserInfo::class.java)

                if (user != null) {
                    binding.fullName.text = "${user.name}"

                    if (user.profileImage != null) {
                        try {
                            val decodedByteArray = Base64.decode(user.profileImage, Base64.DEFAULT)
                            val decodedBitmap = BitmapFactory.decodeByteArray(decodedByteArray, 0, decodedByteArray.size)

                            binding.profileView.setImageBitmap(decodedBitmap)
                        } catch (e: Exception) {
                            Log.e("UserProfileFragment", "Error decoding Base64 image", e)
                            if (isAdded && context != null) {
                                Toast.makeText(context, "Error loading profile image", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        if (isAdded && context != null) {
                            Glide.with(this)
                                .load(R.drawable.ic_profile)
                                .into(binding.profileView)
                        }
                    }
                } else {
                    if (isAdded && context != null) {
//                        Toast.makeText(context, "User data not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                if (isAdded && context != null) {
//                    Toast.makeText(context, "User not found in database", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener { e ->
            Log.e("UserProfileFragment", "Error fetching user data", e)
            if (isAdded && context != null) {
                Toast.makeText(context, "Failed to retrieve user data", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // --------------------------------------------
    // LOCATION PERMISSION
    // --------------------------------------------
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
        } else {
            setupMap()
        }
    }
    // --------------------------------------------
    // MINI MAP (HOME SCREEN)
    // --------------------------------------------
    @SuppressLint("ClickableViewAccessibility")
    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapContainer)
                as? SupportMapFragment ?: SupportMapFragment.newInstance().also {
            childFragmentManager.beginTransaction().replace(R.id.mapContainer, it).commit()
        }

        mapFragment.getMapAsync { googleMap ->

            // Fix scroll issue when inside ScrollView
            mapFragment.view?.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }

            googleMap.uiSettings.apply {
                isZoomControlsEnabled = true
                isScrollGesturesEnabled = true
            }

            val defaultLocation = LatLng(7.4478, 125.8097)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 13f))

            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                googleMap.isMyLocationEnabled = true
            }

            // Load registered/partnered clinics from Firebase using addresses and geocode
            loadClinicsUsingAddress(googleMap)
        }
    }

    // --------------------------------------------
    // FULLSCREEN MAP
    // --------------------------------------------
    private fun setupFullscreenMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.fullMapContainer)
                as? SupportMapFragment ?: SupportMapFragment.newInstance().also {
            childFragmentManager.beginTransaction().replace(R.id.fullMapContainer, it).commit()
        }

        mapFragment.getMapAsync { googleMap ->
            googleMap.uiSettings.apply {
                isZoomControlsEnabled = true
                isScrollGesturesEnabled = true
            }

            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                googleMap.isMyLocationEnabled = true
            }

            val defaultLocation = LatLng(7.4478, 125.8097)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 13f))

            // Load clinics dynamically by address geocoding
            loadClinicsUsingAddress(googleMap)
        }
    }

    // --------------------------------------------
    // LOAD CLINICS FROM FIREBASE USING ADDRESS + GEOCODER
    // --------------------------------------------
    private fun loadClinicsUsingAddress(googleMap: GoogleMap) {
        val clinicRef = FirebaseDatabase.getInstance(
            "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).getReference("clinicInfo")

        val geocoder = Geocoder(requireContext(), Locale.getDefault())

        clinicRef.get().addOnSuccessListener { snapshot ->
            for (snap in snapshot.children) {
                val clinic = snap.getValue(ClinicInfo::class.java)

                // Only load clinics with role derma and address present
                if (clinic != null &&
                    clinic.role?.lowercase() == "derma" &&
                    !clinic.address.isNullOrEmpty()
                ) {
                    try {
                        val results = geocoder.getFromLocationName(clinic.address!!, 1)

                        if (results != null && results.isNotEmpty()) {
                            val location = results[0]
                            val latLng = LatLng(location.latitude, location.longitude)

                            googleMap.addMarker(
                                MarkerOptions()
                                    .position(latLng)
                                    .title(clinic.name)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE))
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // --------------------------------------------
    // PERMISSION RESULT
    // --------------------------------------------
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            setupMap()
        }
    }
    // --------------------------------------------
    // TIMESTAMP
    // --------------------------------------------
    private fun setupDateTime() {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy hh:mm a")
        binding.dateTimeText.text = current.format(formatter)
    }

    // --------------------------------------------
    // FIREBASE USER + CLINIC LIST (HORIZONTAL)
    // --------------------------------------------
    private fun setupFirebase() {
        mAuth = FirebaseAuth.getInstance()
        mDatabase = FirebaseDatabase.getInstance(
            "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).getReference("userInfo")

        mAuth.currentUser?.uid?.let { getUserData(it) }

        val clinicList = mutableListOf<ClinicInfo>()
        val adapter = AdapterDermaHomeList(clinicList)
        binding.dermaRecycleView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.dermaRecycleView.adapter = adapter

        val clinicRef = FirebaseDatabase.getInstance(
            "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).getReference("clinicInfo")

        clinicEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                clinicList.clear()

                for (snap in snapshot.children) {
                    val clinic = snap.getValue(ClinicInfo::class.java)
                    if (clinic?.role?.lowercase() == "derma") clinicList.add(clinic)
                }

                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        clinicRef.addValueEventListener(clinicEventListener!!)

        binding.dermaList.setOnClickListener {
            startActivity(Intent(requireContext(), DoctorLists::class.java))
        }
    }

//    private fun logoutUser() {
//        val builder = AlertDialog.Builder(requireContext())
//        builder.setTitle("Logout")
//        builder.setMessage("Are you sure you want to logout?")
//
//        builder.setPositiveButton("Yes") { dialog, _ ->
//            FirebaseAuth.getInstance().signOut()
//            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
//
//            val intent = Intent(requireActivity(), Login::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//            startActivity(intent)
//            dialog.dismiss()
//        }
//
//        builder.setNegativeButton("Cancel") { dialog, _ ->
//            dialog.dismiss()
//        }
//
//        val dialog = builder.create()
//        dialog.show()
//    }


}