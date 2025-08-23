package com.example.dermascanai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.dermascanai.databinding.ActivityMainPageBinding
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.content.res.AssetFileDescriptor
import android.media.ExifInterface
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dermascanai.Login
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.tasks.await
import showWarningDialog


class MainPage : AppCompatActivity() {
    private lateinit var binding: ActivityMainPageBinding
    private lateinit var firebase: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var interpreter: Interpreter? = null


    private val PERMISSION_REQUEST_CODE = 1001
    private val PICK_IMAGE_REQUEST = 1002
    private val CAMERA_REQUEST = 1003

    private var imageUri: Uri? = null

    private var selectedImageBase64: String? = null
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val inputStream = contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            selectedImageBase64 = encodeImageToBase64(bitmap)
            selectedImageView?.setImageBitmap(bitmap)
        }
    }

    private var selectedImageView: ImageView? = null

    private lateinit var databaseA: DatabaseReference
    private lateinit var adapter: ClinicAdapter
    private val clinicList = mutableListOf<ClinicInfo>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebase = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")

        databaseA = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("clinicInfo")

        loadClinicsFromFirebase()
        checkPermissions()

        try {
            interpreter = Interpreter(loadModelFile())
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Model loading failed", Toast.LENGTH_LONG).show()
        }

//        clinicList.add(
//            ClinicInfo(
//                clinicName = "Wellness Clinic",
//                email = "wellness@email.com",
//                clinicPhone = "09251234567",
//                address = "Manila, Philippines",
//                profileImage = null // or your Base64 string
//            )
//        )


        showWarningDialog(this)

        adapter = ClinicAdapter(this, clinicList) { clickedClinic ->
            val intent = Intent(this, ClinicDetails::class.java)
            intent.putExtra("email", clickedClinic.email)  // Pass just the email string
            startActivity(intent)
        }


        binding.nerbyClinic.layoutManager = LinearLayoutManager(this)
        binding.nerbyClinic.adapter = adapter

        binding.scanButton.setOnClickListener {
            showImagePickerDialog()
        }

        binding.backBTN.setOnClickListener {
            finish()
        }
        val userId = firebase.currentUser?.uid ?: return
        val roleRef = database.getReference("clinicInfo").child(userId).child("role")

        roleRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val role = snapshot.getValue(String::class.java)
                if (role == "derma") {
                    binding.reportScan.visibility = View.VISIBLE
                } else {
                    binding.reportScan.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainPage, "Failed to load user role", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadClinicsFromFirebase() {
        databaseA.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                clinicList.clear()
                for (clinicSnapshot in snapshot.children) {
                    val map = clinicSnapshot.value as? Map<String, Any> ?: continue

                    // Safely convert clinicPhone to String regardless of stored type
                    val phoneValue = map["clinicPhone"]
                    val clinicPhone = when (phoneValue) {
                        is String -> phoneValue
                        is Long -> phoneValue.toString()
                        is Int -> phoneValue.toString()
                        else -> null
                    }

                    // Manually construct ClinicInfo
                    val clinic = ClinicInfo(
                        clinicName = map["clinicName"] as? String,
                        email = map["email"] as? String,
                        clinicPhone = clinicPhone,
                        profileImage = map["profileImage"] as? String,
                        address = map["address"] as? String,
                        // Add other fields as needed, safely casted
                    )

                    clinicList.add(clinic)


                }

                adapter.notifyDataSetChanged()


            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainPage, "Failed to load clinics: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
        adapter = ClinicAdapter(this, clinicList) { clickedClinic ->
            val intent = Intent(this, ClinicDetails::class.java)
            intent.putExtra("email", clickedClinic)
            startActivity(intent)
        }
    }



    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = assets.openFd("ModelAI2.tflite")
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Choose from Gallery", "Take a Photo")
        android.app.AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> getSkinImageFromGallery()
                    1 -> takePhoto()
                }
            }
            .show()
    }

    private fun getSkinImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun takePhoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            val photoFile = createImageFile()
            imageUri = FileProvider.getUriForFile(this, "com.example.dermascanai.fileprovider", photoFile)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            startActivityForResult(intent, CAMERA_REQUEST)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            val bitmap: Bitmap = when (requestCode) {
                PICK_IMAGE_REQUEST -> {
                    imageUri = data?.data
                    MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                }
                CAMERA_REQUEST -> {
                    BitmapFactory.decodeStream(contentResolver.openInputStream(imageUri!!))
                }
                else -> return
            }

            CoroutineScope(Dispatchers.Main).launch {
                showProgress()

                val result = withContext(Dispatchers.IO) {
                    val rotatedBitmap = rotateImageIfNeeded(bitmap, imageUri)
                    val predictionResult = predict(rotatedBitmap)
                    predictionResult
                }

//                val rotatedBitmap = rotateImageIfNeeded(bitmap, imageUri)
//                val predictionResult = segmentAndPredict(rotatedBitmap)
//
//                if (predictionResult == null) {
//                    Toast.makeText(this@MainPage, "No skin detected in image.", Toast.LENGTH_LONG).show()
//                    hideProgress()
//                    return@launch
//                }



                binding.reportScan.setOnClickListener {
                    showReportDialog()
                }

                hideProgress()
//                val result = predictionResult
                binding.detailBtn.visibility = View.VISIBLE
                binding.skinImageView.setImageBitmap(bitmap)
                binding.resultTextView.text = "You might have $result"
                binding.remedyTextView.text = getRemedy(result)

                binding.detailBtn.setOnClickListener {
                    val intent = Intent(this@MainPage, DiseaseDetails::class.java)
                    intent.putExtra("condition", result)
                    intent.putExtra("image", selectedImageBase64)
                    startActivity(intent)
                }
                binding.saveScanButton.visibility = View.VISIBLE
                binding.nerbyClinic.visibility = View.VISIBLE
                binding.textClinic.visibility = View.VISIBLE

                binding.saveScanButton.setOnClickListener {

                    val condition = result
                    val remedy = getRemedy(result)

                    saveScanResultToFirebase(condition, remedy, bitmap)
                }



            }
        }
    }

    private fun showReportDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Report Reminder")
        builder.setMessage("Please make a screenshot and upload it as part of your report.")
        builder.setPositiveButton("Proceed") { dialog, _ ->
            dialog.dismiss()
            // Proceed to your report logic here
            openReportScreen() // Optional: call another function or activity
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }



    private fun openReportScreen() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Send Feedback to Admin")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val input = EditText(this).apply {
            hint = "Write your message or review here"
        }

        selectedImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                500
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }

        val selectImageButton = Button(this).apply {
            text = "Select Screenshot Image"
            setOnClickListener {
                imagePickerLauncher.launch("image/*")
                selectedImageView?.visibility = View.VISIBLE
            }
        }

        layout.addView(input)
        layout.addView(selectImageButton)
        layout.addView(selectedImageView)

        builder.setView(layout)

        builder.setPositiveButton("Send") { dialog, _ ->
            val message = input.text.toString()
            if (message.isBlank()) {
                Toast.makeText(this, "Please enter a message.", Toast.LENGTH_SHORT).show()
            } else {
                reportScan(message, selectedImageBase64)
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }


    private fun reportScan(userMessage: String, imageBase64: String?) {
        val userId = firebase.currentUser?.uid ?: return
        val userNameRef = database.getReference("clinicInfo").child(userId).child("name")

        userNameRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userName = snapshot.getValue(String::class.java) ?: "Unknown User"

                val report = hashMapOf(
                    "userId" to userId,
                    "userName" to userName,
                    "message" to userMessage,
                    "imageBase64" to imageBase64,
                    "timestamp" to System.currentTimeMillis()
                )

                val reportsRef = database.getReference("scanReports")
                val newReportKey = reportsRef.push().key ?: return

                reportsRef.child(newReportKey).setValue(report)
                    .addOnSuccessListener {
                        Toast.makeText(this@MainPage, "Report sent successfully!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@MainPage, "Failed to send report.", Toast.LENGTH_SHORT).show()
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainPage, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }



    private fun rotateImageIfNeeded(bitmap: Bitmap, uri: Uri?): Bitmap {
        val inputStream = contentResolver.openInputStream(uri!!)
        val exif = ExifInterface(inputStream!!)
        val rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        return when (rotation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            else -> bitmap
        }
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun predict(bitmap: Bitmap): String {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val input = preprocessImage(resizedBitmap)

        val output = Array(1) { FloatArray(15) } //FloatArray(7) = number of datasets
        interpreter?.run(input, output)

        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
        return if (maxIndex != -1) getConditionLabel(maxIndex) else "Unknown"
    }

    private fun preprocessImage(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val result = Array(1) { Array(224) { Array(224) { FloatArray(3) } } }
        for (i in 0 until 224) {
            for (j in 0 until 224) {
                val pixel = bitmap.getPixel(i, j)
                result[0][i][j][0] = Color.red(pixel) / 255.0f
                result[0][i][j][1] = Color.green(pixel) / 255.0f
                result[0][i][j][2] = Color.blue(pixel) / 255.0f
            }
        }
        return result
    }

    private fun getConditionLabel(index: Int): String {
//        val conditionLabels = listOf(
//            "Acne", "Actinic Keratosis", "Atopic Dermatitis", "Basal Cell Carcinoma",
//            "Benign Keratosis", "Cellulitis", "Dermatofibroma", "Eczema", "Hemangioma",
//            "Herpes", "Impetigo", "Lichen Planus", "Melanoma", "Molluscum Contagiosum",
//            "Nevus (Mole)", "Psoriasis", "Rosacea", "Scabies", "Seborrheic Keratosis",
//            "Shingles (Herpes Zoster)", "Tinea (Ringworm)", "Urticaria (Hives)", "Vitiligo"
//
//        )
        val conditionLabels = listOf(
//            "Acne",
//            "Ezcema",
//            "Melanoma",
////            "Normal",
//            "Psoriasis",
//            "Serborrheic Keratoses",
//            "Tinea Ringworm",
//            "Warts or Viral Infection"

            "Acne",
            "Actinic Keratosis (AK)",
            "Basal Cell Carcinoma (BCC)",
            "Chickenpox (Varicella)",
            "Eczema  or Atopic Dermatitis",
            "Melanocytic Nevi (Moles)",
            "Melanoma",
            "Monkeypox",
            "Nail Fungus (Onychomycosis)",
            "Normal Skin",
            "Psoriasis",
            "Rosacea",
            "Seborrheic Keratosis",
            "Tinea or Ringworm",
            "Warts (Verruca, Viral Infection)"
        )

        return conditionLabels.getOrElse(index) { "Unknown" }
    }

    private fun getRemedy(condition: String): String {
        return when (condition) {
//            "Acne" -> "Cleanse your face twice daily with a mild cleanser and apply over-the-counter benzoyl peroxide or salicylic acid products to reduce inflammation and bacteria."
//            "Eczema" -> "Keep the skin moisturized with fragrance-free creams or ointments; apply a cool compress to relieve itching and avoid known irritants."
//            "Melanoma" -> "Seek immediate medical attention. Melanoma is a serious form of skin cancer and cannot be treated with home remedies."
////            "Normal" -> "You have a Normal skin. Keep it Up."
//            "Psoriasis" -> " Apply aloe vera gel or a moisturizer with coal tar or salicylic acid; take short daily baths with oatmeal or Epsom salt to soothe itching."
//            "Serborrheic Keratoses" -> "These are generally harmless; however, moisturizers and gentle exfoliation may help reduce irritation. For removal, consult a dermatologist."
//            "Tinea Ringworm"->"Apply an over-the-counter antifungal cream (like clotrimazole or terbinafine) twice daily and keep the affected area clean and dry."
//            "Warts or Viral Infection" -> "Use salicylic acid treatments or cryotherapy products available over the counter. Avoid picking to prevent spreading the virus.      "
//            else -> "No specific remedy found. Consult a dermatologist for diagnosis and treatment."


            "Acne" -> "Cleanse your face twice daily with a mild cleanser and apply over-the-counter benzoyl peroxide or salicylic acid products to reduce inflammation and bacteria."
            "Actinic Keratosis (AK)" -> "Apply sunscreen regularly, avoid excessive sun exposure, and see a dermatologist for possible cryotherapy or prescription treatments."
            "Basal Cell Carcinoma (BCC)" -> "Seek immediate medical attention. BCC is a form of skin cancer and requires professional treatment, such as surgery or topical medications."
            "Chickenpox (Varicella)" -> "Use calamine lotion or oatmeal baths to relieve itching. Keep nails trimmed to prevent scratching and secondary infections."
            "Eczema  or Atopic Dermatitis" -> "Keep skin moisturized with fragrance-free creams or ointments. Apply cool compresses to relieve itching and avoid known irritants."
            "Melanocytic Nevi (Moles)" -> "Most moles are harmless, but monitor for changes in size, shape, or color. Consult a dermatologist if you notice abnormalities."
            "Melanoma" -> "Seek immediate medical attention. Melanoma is a serious form of skin cancer and cannot be treated with home remedies."
            "Monkeypox" -> "Isolate yourself, keep rashes clean and dry, and take pain relievers or fever reducers if needed. Consult a healthcare provider for monitoring."
            "Nail Fungus (Onychomycosis)" -> "Apply antifungal creams or medicated nail solutions. Keep nails dry and trimmed; oral medication may be required for persistent cases."
            "Normal Skin" -> "Your skin is healthy. Maintain good hydration, a balanced diet, and regular skincare with sunscreen to keep it that way!"
            "Psoriasis" -> "Apply aloe vera gel or a moisturizer with coal tar or salicylic acid. Short daily baths with oatmeal or Epsom salt may soothe itching."
            "Rosacea" -> "Avoid triggers such as spicy foods, alcohol, and extreme temperatures. Use gentle skincare products and consult a dermatologist for medication if severe."
            "Seborrheic Keratosis" -> "These are generally harmless. Moisturizers and gentle exfoliation may help irritation. For removal, consult a dermatologist."
            "Tinea or Ringworm" -> "Apply an over-the-counter antifungal cream (like clotrimazole or terbinafine) twice daily. Keep the affected area clean and dry."
            "Warts (Verruca, Viral Infection)" -> "Use salicylic acid treatments or over-the-counter cryotherapy. Avoid picking to prevent spreading the virus."
            else -> "No specific remedy found. Consult a dermatologist for diagnosis and treatment."
        }

    }

//    private fun convertToHSV(bitmap: Bitmap, hsvBitmap: Bitmap) {
//        for (x in 0 until bitmap.width) {
//            for (y in 0 until bitmap.height) {
//                val pixel = bitmap.getPixel(x, y)
//                val hsv = FloatArray(3)
//                Color.colorToHSV(pixel, hsv)
//                hsvBitmap.setPixel(x, y, Color.HSVToColor(hsv))
//            }
//        }
//    }
//
//    private fun applySkinColorMask(hsvBitmap: Bitmap): Bitmap {
//        val width = hsvBitmap.width
//        val height = hsvBitmap.height
//        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//
//        for (x in 0 until width) {
//            for (y in 0 until height) {
//                val pixel = hsvBitmap.getPixel(x, y)
//                val hsv = FloatArray(3)
//                Color.colorToHSV(pixel, hsv)
//
//                val h = hsv[0]
//                val s = hsv[1]
//                val v = hsv[2]
//
//                if (h in 0f..50f && s >= 0.23f && s <= 0.68f && v >= 0.35f && v <= 1f) {
//                    resultBitmap.setPixel(x, y, Color.WHITE)
//                } else {
//                    resultBitmap.setPixel(x, y, Color.BLACK)
//                }
//            }
//        }
//
//        return resultBitmap
//    }


    private fun saveScanResultToFirebase(condition: String, remedy: String, bitmap: Bitmap) {
        val databaseReference = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").reference
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val imageBase64 = encodeImageToBase64(bitmap)
        val timestamp = SimpleDateFormat("MMMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val scanId = SimpleDateFormat("MM-dd-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())

        val scanResult = ScanResult(condition, remedy, imageBase64, timestamp)

        databaseReference.child("scanResults").child(userId).child(scanId).setValue(scanResult)
            .addOnSuccessListener {
                Toast.makeText(this, "Scan result saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, Base64.DEFAULT)
    }



    private fun showProgress() {
        binding.loadingProgressBar.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        binding.loadingProgressBar.visibility = View.GONE
    }

    private suspend fun segmentAndPredict(bitmap: Bitmap): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Resize the bitmap for better ML performance
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true)

                val inputImage = InputImage.fromBitmap(resizedBitmap, 0)

                val options = SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                    .enableRawSizeMask()
                    .build()

                val segmenter = Segmentation.getClient(options)
                val result = segmenter.process(inputImage).await()

                val mask = result.buffer
                val width = result.width
                val height = result.height

                val maskArray = FloatArray(width * height)
                mask.rewind()
                mask.asFloatBuffer().get(maskArray)

                val skinPixels = maskArray.count { it > 0.5f }
                val skinRatio = skinPixels.toFloat() / maskArray.size

                Log.d("SegmentationDebug", "Skin Ratio: $skinRatio, Pixels: $skinPixels")
                Log.d("SegmentationDebug", "Mask sample: ${maskArray.take(20)}")

                if (skinRatio < 0.05f) {
                    return@withContext null
                }

                return@withContext predict(resizedBitmap)

            } catch (e: Exception) {
                Log.e("SegmentationError", "Error in segmentAndPredict", e)
                return@withContext null
            }
        }
    }


}