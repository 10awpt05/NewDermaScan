package com.example.dermascanai

import android.content.Intent
import android.graphics.Color
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.dermascanai.databinding.ActivityBookingBinding
import com.google.firebase.database.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class Booking : AppCompatActivity() {
    private lateinit var binding: ActivityBookingBinding
    private var selectedService: Button? = null
    private var selectedDate: Long = 0L
    private var selectedServiceText: String = ""
    private var patientEmail: String = ""
    private var clinicEmail: String = ""
    private var clinicName: String = ""
    private lateinit var database: FirebaseDatabase
    private lateinit var bookingsRef: DatabaseReference
    private val MAX_BOOKINGS_PER_DAY = 3
    private val disabledDates = mutableSetOf<String>()

    private val serviceButtons = mutableListOf<Button>()

    private var selectedTimeSlot: String = ""

    // Clinic working hours (example: 8AM - 7PM)
    private val CLINIC_OPEN_HOUR = 8
    private val CLINIC_CLOSE_HOUR = 19
    private val MAX_BOOKINGS_PER_SLOT = 2
    private val MAX_SLOTS_PER_HOUR = 2


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase
        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        bookingsRef = database.getReference("bookings")

        // Get intent data
        patientEmail = intent.getStringExtra("patientEmail") ?: ""
        clinicEmail = intent.getStringExtra("clinicEmail") ?: ""

        val clinicsRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("clinicInfo")

        clinicsRef.orderByChild("email").equalTo(clinicEmail)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val clinicSnapshot = snapshot.children.first()
                        val clinicInfo = clinicSnapshot.getValue(ClinicInfo::class.java)

                        if (clinicInfo != null) {
                            val clinicName = clinicInfo.name ?: "Unknown Clinic"
                            val clinicId = clinicSnapshot.key ?: ""

                            // ✅ Now you can use clinicName and clinicId
                            displayClinicName(clinicName)
                        }
                    } else {
                        //Toast.makeText(this@Booking, "Clinic not found", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    //Toast.makeText(this@Booking, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })



        println("Patient Email: $patientEmail")
        println("Clinic email: $clinicEmail")


        // Initialize components
        setupToolbar()
        loadBookedDates() // Load fully booked dates first
        setupCalendar()
        fetchClinicServices()
        checkExistingBooking(patientEmail)

        // Set up listeners
        setupListeners()
    }

    private fun setupToolbar() {
        binding.backBTN.setOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        // Calendar date selection
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            handleDateSelection(year, month, dayOfMonth)
        }

        binding.btnNext.setOnClickListener {
            if (selectedDate == 0L || selectedServiceText.isEmpty() || selectedTimeSlot.isEmpty()) {
                Toast.makeText(this, "Please select a date, service, and time slot", Toast.LENGTH_SHORT).show()
            } else {
                proceedWithBooking()
            }
        }
    }

    private fun loadBookedDates() {
        // Query all bookings to find fully booked dates
        bookingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val bookingsByDate = mutableMapOf<String, Int>()
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

                for (bookingSnapshot in snapshot.children) {
                    val dateValue = bookingSnapshot.child("date").value
                    val dateMillis = when (dateValue) {
                        is Long -> dateValue
                        is Int -> dateValue.toLong()
                        is String -> dateValue.toLongOrNull() ?: continue
                        else -> continue
                    }



                    val status = bookingSnapshot.child("status").getValue(String::class.java) ?: continue
                    val clinic = bookingSnapshot.child("clinicName").getValue(String::class.java) ?: continue

                    val dateStr = dateFormat.format(Date(dateMillis))

                    if ((status == "pending" || status == "confirmed") && clinic == clinicName) {
                        bookingsByDate[dateStr] = (bookingsByDate[dateStr] ?: 0) + 1
                    }
                }

                for ((date, count) in bookingsByDate) {
                    if (count >= MAX_BOOKINGS_PER_DAY) {
                        disabledDates.add(date)
                    }
                }
                updateCalendarWithDisabledDates()
            }


            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@Booking, "Error loading booking data: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateCalendarWithDisabledDates() {
        // If the calendar view doesn't directly support disabling dates, we'll handle it when a date is selected
        if (disabledDates.isNotEmpty()) {
            Toast.makeText(this, "Some dates are unavailable due to being fully booked", Toast.LENGTH_LONG).show()
        }
    }

    private fun isDateFullyBooked(dateMillis: Long): Boolean {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val dateStr = dateFormat.format(Date(dateMillis))
        return disabledDates.contains(dateStr)
    }

    private fun fetchClinicServices() {
        val clinicsRef = database.getReference("clinicInfo")

        // Clear existing services and show loading
        binding.servicesContainer.removeAllViews()
        showLoadingForServices()

        // Query the clinic by email
        clinicsRef.orderByChild("email").equalTo(clinicEmail)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val clinicSnapshot = snapshot.children.firstOrNull()
                        val clinicInfo = clinicSnapshot?.getValue(ClinicInfo::class.java)

                        if (clinicInfo != null) {
                            clinicName = clinicInfo.name ?: "Unknown Clinic"
                            displayClinicName(clinicName)

                            val services = clinicInfo.services ?: listOf()
                            if (services.isNotEmpty()) {
                                setupDynamicServiceButtons(services)
                            } else {
                                showNoServicesMessage()
                            }
                        } else {
                            showNoClinicDataMessage()
                        }
                    } else {
                        showNoClinicDataMessage()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    //Toast.makeText(this@Booking, "Error loading services: ${error.message}", Toast.LENGTH_SHORT).show()
                    clearServicesContainer()
                }
            })
    }


    private fun displayClinicName(clinicName: String) {
        // You can update the toolbar title or show a toast
        supportActionBar?.title = clinicName
    }

    private fun showLoadingForServices() {
        val loadingText = android.widget.TextView(this)
        loadingText.text = "Loading services..."
        loadingText.gravity = Gravity.CENTER
        loadingText.setPadding(16, 32, 16, 32)
        loadingText.setTextColor(Color.GRAY)
        loadingText.textSize = 16f
        binding.servicesContainer.addView(loadingText)
    }

    private fun showNoServicesMessage() {
        clearServicesContainer()
        val messageText = android.widget.TextView(this)
        messageText.text = "No services available for this clinic"
        messageText.gravity = Gravity.CENTER
        messageText.setPadding(16, 32, 16, 32)
        messageText.setTextColor(Color.RED)
        messageText.textSize = 16f
        binding.servicesContainer.addView(messageText)

        Toast.makeText(this, "No services found for this clinic", Toast.LENGTH_LONG).show()
    }

    private fun showNoClinicDataMessage() {
        clearServicesContainer()
        val messageText = android.widget.TextView(this)
        messageText.text = "No clinic information available"
        messageText.gravity = Gravity.CENTER
        messageText.setPadding(16, 32, 16, 32)
        messageText.setTextColor(Color.RED)
        messageText.textSize = 16f
        binding.servicesContainer.addView(messageText)

        Toast.makeText(this, "No clinic information available", Toast.LENGTH_SHORT).show()
    }

    private fun clearServicesContainer() {
        binding.servicesContainer.removeAllViews()
        serviceButtons.clear()
    }

    private fun setupDynamicServiceButtons(services: List<String>) {
        clearServicesContainer()

        val buttonLayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            140
        )
        buttonLayoutParams.setMargins(0, 0, 0, 16)

        var selectedServiceButton: Button? = null

        for (service in services) {
            val button = Button(this)
            button.text = service
            button.layoutParams = buttonLayoutParams
            button.gravity = Gravity.CENTER
            button.setPadding(16, 16, 16, 16)
            button.textSize = 16f

            // Default gray color
            button.setBackgroundColor(Color.parseColor("#7A7A7A"))
            button.setTextColor(Color.WHITE)
            button.background = resources.getDrawable(R.drawable.service_button_bg, null)

            button.setOnClickListener {
                // Reset previous selected
                selectedServiceButton?.setBackgroundColor(Color.parseColor("#7A7A7A"))

                // Highlight current selected
                button.setBackgroundColor(Color.parseColor("#06923E"))
                selectedServiceButton = button

                selectedService = button
                selectedServiceText = button.text.toString()

                //Toast.makeText(this, "Selected Service: $selectedServiceText", Toast.LENGTH_SHORT).show()
            }

            binding.servicesContainer.addView(button)
            serviceButtons.add(button)
        }
    }


    private fun setupCalendar() {
        // Style the calendar
        binding.calendarView.setWeekSeparatorLineColor(Color.BLACK)
        binding.calendarView.setFocusedMonthDateColor(Color.BLACK)
        binding.calendarView.setUnfocusedMonthDateColor(Color.BLACK)

        // Set minimum date to tomorrow
        val tomorrow = Calendar.getInstance()
        tomorrow.set(Calendar.HOUR_OF_DAY, 0)
        tomorrow.set(Calendar.MINUTE, 0)
        tomorrow.set(Calendar.SECOND, 0)
        tomorrow.set(Calendar.MILLISECOND, 0)
        tomorrow.add(Calendar.DAY_OF_MONTH, 1)

        binding.calendarView.minDate = tomorrow.timeInMillis
    }

    private fun handleDateSelection(year: Int, month: Int, dayOfMonth: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, dayOfMonth)
        val selectedDateMillis = calendar.timeInMillis

        // Check if the date is already known to be fully booked
        if (isDateFullyBooked(selectedDateMillis)) {
            Toast.makeText(this, "This date is fully booked. Please select another date.", Toast.LENGTH_SHORT).show()
            // Reset date selection
            binding.calendarView.date = Calendar.getInstance().timeInMillis
            return
        }

        // Update selected date
        selectedDate = selectedDateMillis

        // Reset service selection when date changes
        resetServiceSelection()

        // Check availability for the selected date
        checkAvailableBookingsForDate(selectedDate)

        // Format and show selected date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(selectedDate))
//        Toast.makeText(this, "Selected date: $formattedDate", Toast.LENGTH_SHORT).show()
    }

    private fun proceedWithBooking() {
        val bookingId = "${System.currentTimeMillis()}"
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(selectedDate))

        val intent = Intent(this, ConfirmBooking::class.java)
        intent.putExtra("selectedDate", formattedDate)
        intent.putExtra("selectedService", selectedServiceText)
        intent.putExtra("selectedTimeSlot", selectedTimeSlot) // ✅ new line
        intent.putExtra("patientEmail", patientEmail)
        intent.putExtra("clinicName", clinicName)
        intent.putExtra("bookingId", bookingId)
        intent.putExtra("timestampMillis", selectedDate)

        startActivity(intent)
    }


    private fun checkExistingBooking(patientEmail: String) {
        bookingsRef.orderByChild("patientEmail").equalTo(patientEmail)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var hasActiveBooking = false
                    var bookingStatus = ""

                    for (bookingSnapshot in snapshot.children) {
                        val status = bookingSnapshot.child("status").getValue(String::class.java)
                        if (status == "pending" || status == "confirmed") {
                            hasActiveBooking = true
                            bookingStatus = status
                            break
                        }
                    }

                    if (hasActiveBooking) {
                        AlertDialog.Builder(this@Booking)
                            .setTitle("No Availability")
                            .setMessage("All slots for this date are fully booked. Please select another date.")
                            .setPositiveButton("OK") { dialog, _ ->
                                dialog.dismiss()
                                // Reset the calendar date to today to prompt reselection
                                binding.calendarView.date = Calendar.getInstance().timeInMillis
//                                binding.bookingsAvailableText.text = "Please select another date"
                            }
                            .setCancelable(false)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show()

                    }else {
                    // Re-enable service buttons in case they were previously disabled
                    enableAllServiceButtons()
                }
            }

                    override fun onCancelled(error: DatabaseError) {
              //  Toast.makeText(this@Booking, "Failed to check availability: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    })
}

    private fun parseHour(time: String): Int {
        val sdf = SimpleDateFormat("h:mm a", Locale.ENGLISH)
        val date = sdf.parse(time)
        val cal = java.util.Calendar.getInstance()
        cal.time = date!!
        return cal.get(java.util.Calendar.HOUR_OF_DAY) // returns 9 for "9:00 AM"
    }

    private fun resetServiceSelection() {
        for (btn in serviceButtons) {
            if (btn == selectedService) {
                btn.isEnabled = true
                btn.setBackgroundColor(Color.parseColor("#06923E")) // active green
                btn.setTextColor(Color.WHITE)
            } else {
                btn.isEnabled = true
                btn.setBackgroundColor(Color.parseColor("#7A7A7A")) // gray
                btn.setTextColor(Color.WHITE)
            }
        }
    }


    private fun checkAvailableBookingsForDate(selectedDateMillis: Long) {
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
        val selectedDateStr = Instant.ofEpochMilli(selectedDateMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(formatter)

        val database = FirebaseDatabase.getInstance()
        val clinicRef = database.getReference("clinicInfo")

        // ✅ Step 1: Get clinicId using the clinic email (snapshot key = clinicId)
        clinicRef.orderByChild("email").equalTo(clinicEmail)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(clinicSnap: DataSnapshot) {
                    if (!clinicSnap.exists()) {
                        //Log.e("Booking", "❌ No clinic found with email $clinicEmail")
                        return
                    }

                    // The key is the clinicId
                    val clinicNode = clinicSnap.children.first()
                    val clinicId = clinicNode.key

                    if (clinicId == null) {
                        //Log.e("Booking", "❌ Clinic key (ID) is null")
                        return
                    }

                    // ✅ Step 2: Query bookings for this clinicId
                    val bookingsRef = database.getReference("bookings")
                    val bookingsQuery = bookingsRef.orderByChild("clinicId").equalTo(clinicId)

                    bookingsQuery.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val bookingsPerSlot = mutableMapOf<String, Int>()

                            for (bookingSnapshot in snapshot.children) {
                                val status = bookingSnapshot.child("status").getValue(String::class.java) ?: continue
                                val date = bookingSnapshot.child("date").getValue(String::class.java) ?: continue
                                val timeSlot = bookingSnapshot.child("timeSlot").getValue(String::class.java)
                                    ?: bookingSnapshot.child("time").getValue(String::class.java)
                                    ?: continue

                                // ✅ Count only same clinic, same date, and active bookings
                                if (date == selectedDateStr && (status == "pending" || status == "confirmed")) {
                                    bookingsPerSlot[timeSlot] = (bookingsPerSlot[timeSlot] ?: 0) + 1
                                }
                            }

                            // ✅ Step 3: Display slots
                            val clinicInfo = clinicNode.getValue(ClinicInfo::class.java)
                            if (clinicInfo != null) {
                                val openingHour = parseHour(clinicInfo.openingTime ?: "9:00 AM")
                                val closingHour = parseHour(clinicInfo.closingTime ?: "5:00 PM")

                                binding.slotsContainer.removeAllViews()
                                var selectedSlotButton: Button? = null

                                for (hour in openingHour until closingHour) {
                                    if (hour == 12) continue // skip lunch

                                    val startTime = formatTo12Hour(hour)
                                    val endTime = formatTo12Hour(hour + 1)
                                    val slotLabel = "$startTime - $endTime"

                                    val currentCount = bookingsPerSlot[slotLabel] ?: 0
                                    val remaining = MAX_BOOKINGS_PER_SLOT - currentCount

                                    val slotButton = Button(this@Booking).apply {
                                        text = if (remaining > 0) {
                                            "$slotLabel | $remaining slot(s) left"
                                        } else {
                                            "$slotLabel | FULL"
                                        }

                                        isEnabled = remaining > 0
                                        setBackgroundColor(if (isEnabled) Color.parseColor("#7A7A7A") else Color.LTGRAY)
                                        setTextColor(Color.WHITE)
                                        setOnClickListener {
                                            selectedSlotButton?.setBackgroundColor(Color.parseColor("#7A7A7A"))
                                            setBackgroundColor(Color.parseColor("#06923E"))
                                            selectedSlotButton = this
                                            selectedTimeSlot = slotLabel
                                        }
                                    }

                                    binding.slotsContainer.addView(slotButton)
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            //Log.e("Booking", "❌ Error fetching bookings: ${error.message}")
                        }
                    })
                }

                override fun onCancelled(error: DatabaseError) {
                    //Log.e("Booking", "❌ Error fetching clinic info: ${error.message}")
                }
            })
    }





    private fun disableAllServiceButtons() {
        for (btn in serviceButtons) {
            btn.isEnabled = false
            btn.setBackgroundColor(Color.LTGRAY)
            btn.setTextColor(Color.DKGRAY)
        }

        selectedService = null
        selectedServiceText = ""
    }

    private fun enableAllServiceButtons() {
        for (btn in serviceButtons) {
            btn.isEnabled = true
            if (btn != selectedService) {
                btn.setBackgroundColor(Color.parseColor("#7A7A7A"))
                btn.setTextColor(Color.WHITE)
            }
        }
    }
    private fun formatTo12Hour(hour: Int): String {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, 0)
        val sdf = SimpleDateFormat("h:mm a", Locale.ENGLISH)
        return sdf.format(cal.time)
    }

}