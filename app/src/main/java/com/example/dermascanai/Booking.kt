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
    private lateinit var database: FirebaseDatabase
    private lateinit var bookingsRef: DatabaseReference

    private var selectedService: Button? = null
    private var selectedServiceText = ""
    private var selectedDateMillis: Long = 0L
    private var selectedTimeSlot = ""
    private val ACTIVE_STATUSES = listOf("pending", "confirmed", "approved")

    private var patientEmail = ""
    private var clinicEmail = ""
    private var clinicId = ""
    private var clinicName = ""

    private val serviceButtons = mutableListOf<Button>()

    private var selectedSlotId = ""


    private val MAX_BOOKINGS_PER_SLOT = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance(
            "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )
        bookingsRef = database.getReference("bookings")

        patientEmail = intent.getStringExtra("patientEmail") ?: ""
        clinicEmail = intent.getStringExtra("clinicEmail") ?: ""

        setupToolbar()
        setupCalendar()
        setupListeners()
        loadClinicInfo()
        checkExistingBooking()
    }

    private fun setupToolbar() {
        binding.backBTN.setOnClickListener { finish() }
    }

    private fun setupListeners() {
        binding.calendarView.setOnDateChangeListener { _, y, m, d ->
            val cal = Calendar.getInstance()
            cal.set(y, m, d, 0, 0, 0)
            selectedDateMillis = cal.timeInMillis
            selectedTimeSlot = ""
            selectedSlotId = ""
            resetServiceSelection()
            loadSlotsForDate()
        }

        binding.btnNext.setOnClickListener {
            if (selectedDateMillis == 0L || selectedServiceText.isEmpty() || selectedTimeSlot.isEmpty()) {
                Toast.makeText(this, "Please complete all selections", Toast.LENGTH_SHORT).show()
            } else {
                checkSlotAvailabilityBeforeProceeding()
            }
        }
    }

    private fun setupCalendar() {
        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        binding.calendarView.minDate = tomorrow.timeInMillis
    }

    private fun loadClinicInfo() {
        database.getReference("clinicInfo")
            .orderByChild("email").equalTo(clinicEmail)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return
                    val clinicSnap = snapshot.children.first()
                    clinicId = clinicSnap.key ?: return
                    val info = clinicSnap.getValue(ClinicInfo::class.java) ?: return
                    clinicName = info.name ?: ""
                    supportActionBar?.title = clinicName
                    setupServiceButtons(info.services ?: emptyList())
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupServiceButtons(services: List<String>) {
        binding.servicesContainer.removeAllViews()
        serviceButtons.clear()

        for (service in services) {
            val btn = Button(this).apply {
                text = service
                setBackgroundColor(Color.parseColor("#7A7A7A"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    serviceButtons.forEach {
                        it.setBackgroundColor(Color.parseColor("#7A7A7A"))
                    }
                    setBackgroundColor(Color.parseColor("#06923E"))
                    selectedService = this
                    selectedServiceText = service
                }
            }
            binding.servicesContainer.addView(btn)
            serviceButtons.add(btn)
        }
    }

    private fun resetServiceSelection() {
        selectedService = null
        selectedServiceText = ""
        serviceButtons.forEach {
            it.isEnabled = true
            it.setBackgroundColor(Color.parseColor("#7A7A7A"))
        }
    }

    private fun loadSlotsForDate() {
        if (clinicId.isEmpty() || selectedDateMillis == 0L) return

        val dateStr = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
            .format(Instant.ofEpochMilli(selectedDateMillis).atZone(ZoneId.systemDefault()))

        bookingsRef.orderByChild("clinicId").equalTo(clinicId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val countPerSlot = mutableMapOf<String, Int>()

                    for (b in snapshot.children) {
                        val status = b.child("status").getValue(String::class.java) ?: continue
                        val date = b.child("date").getValue(String::class.java) ?: continue
                        val slotId = b.child("slotId").getValue(String::class.java) ?: continue

                        if (date == dateStr && status in ACTIVE_STATUSES) {
                            countPerSlot[slotId] = (countPerSlot[slotId] ?: 0) + 1
                        }

                    }


                    showSlotButtons(countPerSlot)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun showSlotButtons(bookingsPerSlot: Map<String, Int>) {
        binding.slotsContainer.removeAllViews()
        var selectedBtn: Button? = null

        val hours = 9 until 17
        for (h in hours) {

            val id = slotId(h)
            val used = bookingsPerSlot[id] ?: 0
            val remaining = MAX_BOOKINGS_PER_SLOT - used

            val label = "${formatHour(h)} - ${formatHour(h + 1)}"

            val btn = Button(this).apply {
                text = when {
                    remaining <= 0 -> "$label\nFULL"
                    remaining == 1 -> "$label\n1 slot left"
                    else -> "$label\n$remaining slots left"
                }

                isEnabled = remaining > 0
                setTextColor(Color.WHITE)
                setBackgroundColor(
                    if (remaining <= 0) Color.LTGRAY else Color.parseColor("#7A7A7A")
                )

                setOnClickListener {
                    selectedBtn?.setBackgroundColor(Color.parseColor("#7A7A7A"))
                    setBackgroundColor(Color.parseColor("#06923E"))
                    selectedBtn = this

                    selectedTimeSlot = label   // DISPLAY
                    selectedSlotId = id        // LOGIC
                }
            }

            binding.slotsContainer.addView(btn)
        }
    }



    private fun checkExistingBooking() {
        bookingsRef.orderByChild("patientEmail").equalTo(patientEmail)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (b in snapshot.children) {
                        val status = b.child("status").getValue(String::class.java)
                        if (status in ACTIVE_STATUSES) {
                            AlertDialog.Builder(this@Booking)
                                .setTitle("Existing Booking")
                                .setMessage("You already have an active booking.")
                                .setPositiveButton("OK", null)
                                .show()
                            return
                        }

                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun checkSlotAvailabilityBeforeProceeding() {
        val dateStr = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
            .format(Instant.ofEpochMilli(selectedDateMillis).atZone(ZoneId.systemDefault()))

        bookingsRef.orderByChild("clinicId").equalTo(clinicId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var count = 0
                    for (b in snapshot.children) {
                        val status = b.child("status").getValue(String::class.java)
                        val date = b.child("date").getValue(String::class.java)
                        val slotId = b.child("slotId").getValue(String::class.java)

                        if (
                            status in ACTIVE_STATUSES &&
                            date == dateStr &&
                            slotId == selectedSlotId
                        ) {
                            count++
                        }

                    }

                    if (count >= MAX_BOOKINGS_PER_SLOT) {
                        Toast.makeText(this@Booking, "Slot already full", Toast.LENGTH_LONG).show()
                    } else {
                        proceedToConfirm(dateStr)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun proceedToConfirm(dateStr: String) {
        startActivity(
            Intent(this, ConfirmBooking::class.java).apply {
                putExtra("selectedDate", dateStr)
                putExtra("selectedService", selectedServiceText)
                putExtra("selectedTimeSlot", selectedTimeSlot)
                putExtra("patientEmail", patientEmail)
                putExtra("clinicId", clinicId)
                putExtra("clinicName", clinicName)
                putExtra("slotId", selectedSlotId)
                putExtra("timeSlot", selectedTimeSlot)

            }
        )
    }

    private fun slotId(hour: Int): String {
        return String.format("%02d-%02d", hour, hour + 1)
    }


    private fun formatHour(hour: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        return SimpleDateFormat("h:mm a", Locale.ENGLISH).format(cal.time)
    }
}
