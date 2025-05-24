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
                        Toast.makeText(this@Booking, "Clinic not found", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@Booking, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
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
            if (selectedDate == 0L || selectedServiceText.isEmpty()) {
                Toast.makeText(this, "Please select a date and service", Toast.LENGTH_SHORT).show()
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

                    if ((status == "Pending" || status == "Confirmed") && clinic == clinicName) {
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
                    Toast.makeText(this@Booking, "Error loading services: ${error.message}", Toast.LENGTH_SHORT).show()
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

        // Create layout parameters for service buttons
        val buttonLayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            140 // Fixed height for consistency
        )
        buttonLayoutParams.setMargins(0, 0, 0, 16) // Bottom margin between buttons

        for (service in services) {
            val button = Button(this)
            button.text = service
            button.layoutParams = buttonLayoutParams

            // Style the button to match your design
            button.setBackgroundColor(Color.parseColor("#7A7A7A"))
            button.setTextColor(Color.WHITE)
            button.gravity = Gravity.CENTER
            button.setPadding(16, 16, 16, 16)
            button.textSize = 16f

            button.background = resources.getDrawable(R.drawable.service_button_bg, null)

            button.setOnClickListener {
                if (button.isEnabled) {
                    // Reset previous selection
                    selectedService?.setBackgroundColor(Color.parseColor("#7A7A7A"))

                    // Set new selection
                    button.setBackgroundColor(Color.parseColor("#FFBB86FC"))
                    selectedService = button
                    selectedServiceText = button.text.toString()

                    Toast.makeText(this, "Selected: $selectedServiceText", Toast.LENGTH_SHORT).show()
                }
            }

            binding.servicesContainer.addView(button)
            serviceButtons.add(button)
        }

        println("Successfully created ${serviceButtons.size} service buttons")
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
        Toast.makeText(this, "Selected date: $formattedDate", Toast.LENGTH_SHORT).show()
    }

    private fun proceedWithBooking() {
        val bookingId = "${System.currentTimeMillis()}"
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(selectedDate))

        val intent = Intent(this, ConfirmBooking::class.java)
        intent.putExtra("selectedDate", formattedDate)
        intent.putExtra("selectedService", selectedServiceText)
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
                        if (status == "Pending" || status == "Confirmed") {
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
                                binding.bookingsAvailableText.text = "Please select another date"
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
                Toast.makeText(this@Booking, "Failed to check availability: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    })
}


private fun resetServiceSelection() {
        for (btn in serviceButtons) {
            btn.isEnabled = true
            btn.setBackgroundColor(Color.parseColor("#7A7A7A"))
            btn.setTextColor(Color.WHITE)
        }

        selectedService = null
        selectedServiceText = ""
    }

    private fun checkAvailableBookingsForDate(selectedDateMillis: Long) {
        bookingsRef.orderByChild("date").equalTo(selectedDateMillis.toDouble())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var bookingsCount = 0

                    for (bookingSnapshot in snapshot.children) {
                        val status = bookingSnapshot.child("status").getValue(String::class.java)
                        val clinic = bookingSnapshot.child("clinicName").getValue(String::class.java)

                        if ((status == "Pending" || status == "Confirmed") && clinic == clinicName) {
                            bookingsCount++
                        }
                    }

                    val remainingSlots = MAX_BOOKINGS_PER_DAY - bookingsCount
                    binding.bookingsAvailableText.text = "$remainingSlots slots available for this date"

                    // Disable service buttons if no slots
                    if (remainingSlots <= 0) {
                        disableAllServiceButtons()
                    } else {
                        enableAllServiceButtons()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@Booking, "Error checking date availability: ${error.message}", Toast.LENGTH_SHORT).show()
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
}