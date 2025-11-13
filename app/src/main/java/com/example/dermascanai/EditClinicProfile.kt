package com.example.dermascanai

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.ActivityDermaEditInfoBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditClinicProfile : AppCompatActivity() {
    private lateinit var binding: ActivityDermaEditInfoBinding
    private lateinit var database: FirebaseDatabase

    private var selectedLogoImage: Bitmap? = null
    private var selectedBIRImage: Bitmap? = null
    private var selectedPermitImage: Bitmap? = null
    private var selectedValidIdImage: Bitmap? = null

    private var existingLogoImage: String? = null
    private var existingBIRImage: String? = null
    private var existingPermitImage: String? = null
    private var existingValidIdImage: String? = null

    private var cameraImageUri: Uri? = null
    private val userId = FirebaseAuth.getInstance().currentUser?.uid

    // RecyclerView adapters
    private lateinit var servicesAdapter: ServicesAdapter
    private lateinit var dermatologistsAdapter: DermatologistsAdapter
    private val servicesList = mutableListOf<String>()
    private val dermatologistsList = mutableListOf<Dermatologist>()

    // New ActivityResultLaunchers
    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>
    private lateinit var galleryLauncher: ActivityResultLauncher<String>
    private var currentImageType: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDermaEditInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")

        setupActivityResultLaunchers()
        setupViews()
        setupRecyclerViews()
        fetchClinicData()
        setupClickListeners()
    }

    /**
     * Setup modern activity result launchers
     */
    private fun setupActivityResultLaunchers() {
        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                cameraImageUri?.let { uri ->
                    val bitmap = getBitmapFromUri(uri)
                    handleImageResult(bitmap, currentImageType)
                }
            }
        }

        galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val bitmap = getBitmapFromUri(it)
                handleImageResult(bitmap, currentImageType)
            }
        }
    }

    private fun setupViews() {
        val daysList = listOf(
            "Monday",
            "Tuesday",
            "Wednesday",
            "Thursday",
            "Friday",
            "Saturday",
            "Sunday"
        )

        val dayAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            daysList
        )

        binding.editOpeningDay.setAdapter(dayAdapter)
        binding.editClosingDay.setAdapter(dayAdapter)

        // Optional: close dropdown automatically when user selects
        binding.editOpeningDay.setOnItemClickListener { parent, _, position, _ ->
            binding.editOpeningDay.clearFocus()
        }
        binding.editClosingDay.setOnItemClickListener { parent, _, position, _ ->
            binding.editClosingDay.clearFocus()
        }
    }


    private fun setupRecyclerViews() {
        // Services RecyclerView
        servicesAdapter = ServicesAdapter(servicesList) { position ->
            removeService(position)
        }
        binding.servicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@EditClinicProfile)
            adapter = servicesAdapter
        }

        // Dermatologists RecyclerView
        dermatologistsAdapter = DermatologistsAdapter(dermatologistsList) { position ->
            removeDermatologist(position)
        }
        binding.dermatologistsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@EditClinicProfile)
            adapter = dermatologistsAdapter
        }

        updateVisibility()
    }

    private fun setupClickListeners() {
        binding.backBtn.setOnClickListener {
            onBackPressed()
        }

        binding.saveBtn.setOnClickListener {
            saveClinicProfile()
        }

        binding.changePhotoBtn.setOnClickListener {
            showImagePickerDialog(IMAGE_TYPE_LOGO)
        }

        binding.editOpeningTime.setOnClickListener {
            showTimePicker(true)
        }

        binding.editClosingTime.setOnClickListener {
            showTimePicker(false)
        }

        binding.addServiceBtn.setOnClickListener {
            showAddServiceDialog()
        }

        binding.addDermatologistBtn.setOnClickListener {
            showAddDermatologistDialog()
        }

        binding.uploadBirBtn.setOnClickListener {
            showImagePickerDialog(IMAGE_TYPE_BIR)
        }

        binding.uploadPermitBtn.setOnClickListener {
            showImagePickerDialog(IMAGE_TYPE_PERMIT)
        }

        binding.idDoc.setOnClickListener {
            showImagePickerDialog(IMAGE_TYPE_VALIDID)
        }
    }

    private fun showTimePicker(isOpeningTime: Boolean) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
            calendar.set(Calendar.MINUTE, selectedMinute)
            val formattedTime = timeFormat.format(calendar.time)

            if (isOpeningTime) {
                binding.editOpeningTime.setText(formattedTime)
            } else {
                binding.editClosingTime.setText(formattedTime)
            }
        }, hour, minute, false).show()
    }

    private fun showAddServiceDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Enter service name"

        AlertDialog.Builder(this)
            .setTitle("Add Service")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val serviceName = input.text.toString().trim()
                if (serviceName.isNotEmpty()) {
                    servicesList.add(serviceName)
                    servicesAdapter.notifyItemInserted(servicesList.size - 1)
                    updateVisibility()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("MissingInflatedId")
    private fun showAddDermatologistDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_dermatologist, null)
        val nameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.dermatologistName)
        val specializationInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.dermatologistSpecialization)

        AlertDialog.Builder(this)
            .setTitle("Add Dermatologist")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val specialization = specializationInput.text.toString().trim()
                if (name.isNotEmpty() && specialization.isNotEmpty()) {
                    val dermatologist = Dermatologist(name, specialization)
                    dermatologistsList.add(dermatologist)
                    dermatologistsAdapter.notifyItemInserted(dermatologistsList.size - 1)
                    updateVisibility()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeService(position: Int) {
        servicesList.removeAt(position)
        servicesAdapter.notifyItemRemoved(position)
        updateVisibility()
    }

    private fun removeDermatologist(position: Int) {
        dermatologistsList.removeAt(position)
        dermatologistsAdapter.notifyItemRemoved(position)
        updateVisibility()
    }

    private fun updateVisibility() {
        binding.noServicesText.visibility = if (servicesList.isEmpty()) View.VISIBLE else View.GONE
        binding.noDermatologistsText.visibility = if (dermatologistsList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showImagePickerDialog(imageType: Int) {
        currentImageType = imageType
        val options = arrayOf("Take Photo", "Choose from Gallery")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Image")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> openCamera()
                1 -> openGallery()
            }
        }
        builder.show()
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), PERMISSION_CODE)
        } else {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.TITLE, "New Picture")
                put(MediaStore.Images.Media.DESCRIPTION, "From Camera")
            }
            cameraImageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            cameraImageUri?.let { uri ->
                cameraLauncher.launch(uri)
            }

        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
        return bitmap.copy(Bitmap.Config.ARGB_8888, true) // normalize format
    }




    private fun handleImageResult(bitmap: Bitmap, imageType: Int) {
        when (imageType) {
            IMAGE_TYPE_LOGO -> {
                selectedLogoImage = bitmap
                binding.clinicLogo.setImageBitmap(bitmap)
                Toast.makeText(this, "Logo updated", Toast.LENGTH_SHORT).show()
            }
            IMAGE_TYPE_BIR -> {
                selectedBIRImage = bitmap
                binding.birDocument.setImageBitmap(bitmap)
                Toast.makeText(this, "BIR document updated", Toast.LENGTH_SHORT).show()
            }
            IMAGE_TYPE_PERMIT -> {
                selectedPermitImage = bitmap
                binding.permitDocument.setImageBitmap(bitmap)
                Toast.makeText(this, "Business permit updated", Toast.LENGTH_SHORT).show()
            }
            IMAGE_TYPE_VALIDID -> {
                selectedValidIdImage = bitmap
                binding.idPic.setImageBitmap(bitmap)
                Toast.makeText(this, "Valid ID updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchClinicData() {
        val clinicRef: DatabaseReference = database.getReference("clinicInfo").child(userId ?: return)

        clinicRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val clinicInfo = snapshot.getValue(ClinicInfo::class.java)
                clinicInfo?.let { populateFields(it) }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch clinic data: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun populateFields(clinicInfo: ClinicInfo) {
        binding.editClinicName.setText(clinicInfo.name ?: "")
        binding.editTagline.setText(clinicInfo.tagline ?: "")
        binding.acceptingPatientsCheckbox.isChecked = clinicInfo.acceptingPatients ?: false
        binding.editContact.setText(clinicInfo.contact ?: "")
        binding.editEmail.setText(clinicInfo.email ?: "")
        binding.editAddress.setText(clinicInfo.address ?: "")
        val operatingDays = clinicInfo.operatingDays ?: ""
        if (operatingDays.contains("to")) {
            val parts = operatingDays.split("to").map { it.trim() }
            binding.editOpeningDay.setText(parts.getOrNull(0) ?: "", false)
            binding.editClosingDay.setText(parts.getOrNull(1) ?: "", false)

        } else if (operatingDays.contains("-")) {
            val parts = operatingDays.split("-").map { it.trim() }
            binding.editOpeningDay.setText(parts.getOrNull(0) ?: "", false)
            binding.editClosingDay.setText(parts.getOrNull(1) ?: "", false)

        } else {
            binding.editOpeningDay.setText(operatingDays)
            binding.editClosingDay.setText("")
        }

        binding.editOpeningTime.setText(clinicInfo.openingTime ?: "")
        binding.editClosingTime.setText(clinicInfo.closingTime ?: "")
        binding.editAbout.setText(clinicInfo.about ?: "")

        // Load images
        clinicInfo.logoImage?.let {
            if (it.isNotEmpty()) {
                existingLogoImage = it
                val decodedBytes = Base64.decode(it, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                binding.clinicLogo.setImageBitmap(bitmap)
            }
        }

        clinicInfo.birImage?.let {
            if (it.isNotEmpty()) {
                existingBIRImage = it
                val decodedBytes = Base64.decode(it, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                binding.birDocument.setImageBitmap(bitmap)
                binding.uploadBirBtn.visibility = View.VISIBLE
            }
        }

        clinicInfo.businessPermitImage?.let {
            if (it.isNotEmpty()) {
                existingPermitImage = it
                val decodedBytes = Base64.decode(it, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                binding.permitDocument.setImageBitmap(bitmap)
                binding.uploadPermitBtn.visibility = View.VISIBLE
            }
        }

        clinicInfo.validIdImage?.let {
            if (it.isNotEmpty()) {
                existingValidIdImage = it
                val decodedBytes = Base64.decode(it, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                binding.idPic.setImageBitmap(bitmap)
                binding.idDoc.visibility = View.VISIBLE
            }
        }

        // Load services
        clinicInfo.services?.let {
            servicesList.clear()
            servicesList.addAll(it)
            servicesAdapter.notifyDataSetChanged()
        }

        // Load dermatologists
        clinicInfo.dermatologists?.let {
            dermatologistsList.clear()
            dermatologistsList.addAll(it)
            dermatologistsAdapter.notifyDataSetChanged()
        }

        updateVisibility()
    }

    private fun saveClinicProfile() {
        val clinicRef = database.getReference("clinicInfo")
        val userRef = userId?.let { clinicRef.child(it) } ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Fetch current status first
        userRef.child("status").get().addOnSuccessListener { snapshot ->
            val currentStatus = snapshot.getValue(String::class.java)

            // Default behavior
            var newStatus = "pending"

            if (currentStatus == "verified") {
                // ✅ If already verified, keep it verified
                newStatus = "verified"
            } else if (currentStatus == "rejected") {
                // 🔄 If rejected, change to pending
                newStatus = "pending"
            }

            // Basic fields
            val openingDay = binding.editOpeningDay.text?.toString()
            val closingDay = binding.editClosingDay.text?.toString()
            val clinicInfoMap = mutableMapOf<String, Any?>()
            clinicInfoMap["name"] = binding.editClinicName.text?.toString()
            clinicInfoMap["tagline"] = binding.editTagline.text?.toString()
            clinicInfoMap["acceptingPatients"] = binding.acceptingPatientsCheckbox.isChecked
            clinicInfoMap["contact"] = binding.editContact.text?.toString()
            clinicInfoMap["email"] = binding.editEmail.text?.toString()
            clinicInfoMap["address"] = binding.editAddress.text?.toString()
            clinicInfoMap["status"] = newStatus
            clinicInfoMap["operatingDays"] = "$openingDay - $closingDay"
            clinicInfoMap["openingTime"] = binding.editOpeningTime.text?.toString()
            clinicInfoMap["closingTime"] = binding.editClosingTime.text?.toString()
            clinicInfoMap["about"] = binding.editAbout.text?.toString()
            clinicInfoMap["services"] = servicesList
            clinicInfoMap["dermatologists"] = dermatologistsList

            // Images (only overwrite if changed)
            selectedLogoImage?.let { clinicInfoMap["logoImage"] = encodeImage(it) }
            selectedBIRImage?.let { clinicInfoMap["birImage"] = encodeImage(it) }
            selectedPermitImage?.let { clinicInfoMap["businessPermitImage"] = encodeImage(it) }
            selectedValidIdImage?.let { clinicInfoMap["validIdImage"] = encodeImage(it) }

            // Write all changes
            userRef.updateChildren(clinicInfoMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Clinic profile saved successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch current status: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }




    private fun encodeImage(bitmap: Bitmap): String {
        val maxWidth = 800
        val maxHeight = 800

        val ratio = minOf(
            maxWidth.toFloat() / bitmap.width,
            maxHeight.toFloat() / bitmap.height,
            1f
        )
        val scaledBitmap = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)

        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }

    companion object {
        private const val PERMISSION_CODE = 100

        private const val IMAGE_TYPE_LOGO = 0
        private const val IMAGE_TYPE_BIR = 1
        private const val IMAGE_TYPE_PERMIT = 2
        private const val IMAGE_TYPE_VALIDID = 3
    }
}
