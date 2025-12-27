package com.example.dermascanai

import android.Manifest
import android.R.attr.bitmap
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
import java.io.File
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.media.ExifInterface
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.example.dermascanai.ml.AIv5
import org.json.JSONArray
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import showWarningDialog
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder


class MainPage : AppCompatActivity() {
    private lateinit var binding: ActivityMainPageBinding
    private lateinit var firebase: FirebaseAuth
    private lateinit var database: FirebaseDatabase
//    private var interpreter: Interpreter? = null

    private val PERMISSION_REQUEST_CODE = 1001
    private val PICK_IMAGE_REQUEST = 1002
    private val CAMERA_REQUEST = 1003

    private var imageUri: Uri? = null

    private lateinit var model: AIv5
    private lateinit var conditionLabels: List<String>

    private var selectedImageBase64: String? = null
    private var currentBitmap: Bitmap? = null
    private var currentTop3Text: String = ""


    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private lateinit var takePhotoLauncher: ActivityResultLauncher<Uri>
    private var photoUri: Uri? = null
    private val remedies = mapOf(
        "Acne" to "Cleanse face twice daily with mild cleanser and apply acne cream.",
        "Impetigo" to "Apply antibiotic ointment and keep area clean.",
        "Nail Fungus" to "Use antifungal cream and keep nails dry.",
        "Normal" to "Skin is healthy. Maintain skincare and hydration.",
        "Tinea or Ringworm" to "Apply antifungal cream and keep area dry.",
        "Urticaria Hives" to "Take antihistamines and avoid triggers.",
        "Vitiligo" to "Use sunscreen and consult dermatologist for treatment.",
        "Warts" to "Use salicylic acid or cryotherapy; avoid picking."
    )

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val inputStream = contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            selectedImageBase64 = encodeToBase64(bitmap)
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


//        loadClinicsFromFirebase()
        checkPermissions()

        try {
            model = AIv5.newInstance(this)
        } catch (e: Exception) {
            Log.e("MainPage", "Model load failed", e)
            Toast.makeText(this, "Model load failed: ${e.message}", Toast.LENGTH_LONG).show()
        }

        try {
            conditionLabels = loadConditionLabels()
        } catch (e: IOException) {
            Log.e("MainPage", "Error loading labels", e)
            Toast.makeText(this, "Failed to load labels: ${e.message}", Toast.LENGTH_LONG).show()
            conditionLabels = emptyList()
        }

        showWarningDialog(this)

        adapter = ClinicAdapter(this, clinicList) { clickedClinic ->
            val intent = Intent(this, ClinicDetails::class.java)
            intent.putExtra("email", clickedClinic.email)  // Pass just the email string
            startActivity(intent)
        }


        binding.nerbyClinic.layoutManager = LinearLayoutManager(this)
        binding.nerbyClinic.adapter = adapter

        binding.nerbyClinic.visibility = View.GONE
        binding.textClinic.visibility = View.GONE
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                handleImage(uri)
            }
        }

        takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && photoUri != null) {
                handleImage(photoUri!!)
            }
        }


        // ✅ Button: show choice dialog
        binding.scanButton.setOnClickListener {
            binding.arrow1.visibility = View.GONE
            binding.gifImageView.visibility = View.GONE
            showImagePickerDialog()
        }

        binding.reportScan.setOnClickListener {
            if (currentBitmap == null) {
                Toast.makeText(this, "No scan available to report.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showReportDialog(currentBitmap!!, currentTop3Text)
        }


        binding.backBTN.setOnClickListener {
            finish()
        }

    }

    private fun handleImage(uri: Uri) {
        binding.skinImageView.setImageURI(uri)
        binding.progressContainer.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.Main).launch {
            val bitmap = withContext(Dispatchers.IO) { MediaStore.Images.Media.getBitmap(contentResolver, uri) }
            currentBitmap = bitmap
            // Check if skin is detected
            val hasSkin = withContext(Dispatchers.Default) { detectSkinHSV(bitmap) }
            if (!hasSkin) {
                binding.progressContainer.visibility = View.GONE
                AlertDialog.Builder(this@MainPage)
                    .setTitle("No Skin Detected")
                    .setMessage("The image does not contain visible skin. Please choose another image.")
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
                return@launch
            }

            // Predict top-3 diseases
            val predictions = withContext(Dispatchers.Default) { predict(bitmap) }

            currentTop3Text = predictions.joinToString("\n") { it.first }

            // Update UI
            predictions.forEachIndexed { index, pair ->
                val diseaseWithConfidence = pair.first
                val condition = diseaseWithConfidence.substringBefore("(").trim()

                when (index) {
                    0 -> {
                        binding.resultTextView1.text = diseaseWithConfidence
                        binding.remedyTextView1.text = pair.second
                        binding.viewDetail1.visibility = View.VISIBLE

                        binding.viewDetail1.setOnClickListener {
                            openDiseaseDetail(condition, bitmap)
                        }
                    }

                    1 -> {
                        binding.resultTextView2.text = diseaseWithConfidence
                        binding.remedyTextView2.text = pair.second
                        binding.viewDetail2.visibility = View.VISIBLE

                        binding.viewDetail2.setOnClickListener {
                            openDiseaseDetail(condition, bitmap)
                        }
                    }

                    2 -> {
                        binding.resultTextView3.text = diseaseWithConfidence
                        binding.remedyTextView3.text = pair.second
                        binding.viewDetail3.visibility = View.VISIBLE

                        binding.viewDetail3.setOnClickListener {
                            openDiseaseDetail(condition, bitmap)
                        }
                    }
                }
            }



            binding.progressContainer.visibility = View.GONE
            binding.cardView14.visibility = View.VISIBLE
            binding.cardView15.visibility = View.VISIBLE
            binding.cardView16.visibility = View.VISIBLE
            binding.clinicRec.visibility = View.VISIBLE
            binding.saveScanButton.visibility = View.VISIBLE

            binding.saveScanButton.setOnClickListener {

                saveScanToFirebase(predictions, bitmap)
            }
            loadClinicsFromFirebase()

            // ✅ Show report dialog if the user is a dermatologist
            val userId = firebase.currentUser?.uid ?: return@launch
            isDermatologist(userId) { isDerma ->
                if (isDerma) {
                    val top3Text = predictions.joinToString("\n") { it.first } // generate text for top-3
                    binding.reportScan.visibility = View.VISIBLE

                }
            }
        }
    }
    private fun openDiseaseDetail(
        condition: String,
        bitmap: Bitmap
    ) {
        // Save bitmap to cache as file
        val imageFile = File.createTempFile("scan_", ".jpg", cacheDir)
        val fos = FileOutputStream(imageFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        fos.flush()
        fos.close()

        val intent = Intent(this, DiseaseDetails::class.java)
        intent.putExtra("condition", condition)
        intent.putExtra("imagePath", imageFile.absolutePath)
        startActivity(intent)
    }



    private fun isDermatologist(userId: String, callback: (Boolean) -> Unit) {
        val userRef = database.getReference("clinicInfo").child(userId).child("role") // assuming you store role
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val role = snapshot.getValue(String::class.java) ?: ""
                callback(role.lowercase() == "derma") // true if dermatologist
            }

            override fun onCancelled(error: DatabaseError) {
                callback(false)
            }
        })
    }

    fun detectSkinHSV(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        var skinPixels = 0
        val totalPixels = width * height

        for (y in 0 until height step 2) { // step to speed up
            for (x in 0 until width step 2) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val hsv = FloatArray(3)
                Color.RGBToHSV(r, g, b, hsv)
                val h = hsv[0]
                val s = hsv[1]
                val v = hsv[2]

                if (h in 0f..50f && s in 0.23f..0.68f && v in 0.35f..1.0f) {
                    skinPixels++
                }
            }
        }

        val skinRatio = skinPixels.toFloat() / (totalPixels / 4) // because of step 2
        return skinRatio > 0.1f // if >10% pixels are skin → skin detected
    }



    private fun loadClinicsFromFirebase() {
        databaseA.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                clinicList.clear()
                for (clinicSnapshot in snapshot.children) {
                    val map = clinicSnapshot.value as? Map<String, Any> ?: continue

                    // Safely convert clinicPhone to String
                    val phoneValue = map["clinicPhone"]
                    val clinicPhone = when (phoneValue) {
                        is String -> phoneValue
                        is Long -> phoneValue.toString()
                        is Int -> phoneValue.toString()
                        else -> null
                    }

                    val clinic = ClinicInfo(
                        clinicName = map["clinicName"] as? String,
                        email = map["email"] as? String,
                        clinicPhone = clinicPhone,
                        profileImage = map["logoImage"] as? String,
                        address = map["address"] as? String
                    )

                    clinicList.add(clinic)
                }

                adapter.notifyDataSetChanged()

                // ✅ make sure RecyclerView is visible if clinics exist
                if (clinicList.isNotEmpty()) {
                    binding.nerbyClinic.visibility = View.VISIBLE
                    binding.textClinic.visibility = View.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainPage, "Failed to load clinics: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun openDetailActivity(diseaseName: String, file: File) {
        val intent = Intent(this, DiseaseDetails::class.java)
        intent.putExtra("disease_name", diseaseName)
        intent.putExtra("image_file", file.absolutePath)
        startActivity(intent)
    }


    private fun loadModelFile(): MappedByteBuffer {
        assets.openFd("AIv5.tflite").use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                return fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.startOffset,
                    fileDescriptor.declaredLength
                )
            }
        }
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
    private fun createImageFile(): File {
        return File.createTempFile(
            "IMG_", ".jpg", cacheDir // saves in cache directory
        )
    }


    private fun showImagePickerDialog() {
        val options = arrayOf("Choose from Gallery", "Take a Photo")
        AlertDialog.Builder(this)
            .setTitle("Select Option")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> {
                        val photoFile = createImageFile()
                        photoUri = FileProvider.getUriForFile(
                            this,
                            "${applicationContext.packageName}.fileprovider",
                            photoFile
                        )
                        takePhotoLauncher.launch(photoUri!!) // ✅ now non-null
                    }
                }
            }
            .show()
    }

    private fun showReportDialog(bitmap: Bitmap, top3Result: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Send Feedback to Admin")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val input = EditText(this).apply {
            hint = "Write your message or review here"
        }

        layout.addView(input)
        builder.setView(layout)

        builder.setPositiveButton("Send") { dialog, _ ->
            val message = input.text.toString()
            if (message.isBlank()) {
                Toast.makeText(this, "Please enter a message.", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val imageBase64 = encodeToBase64(bitmap)

            // --- Build combined condition + remedy strings ---
            val lines = top3Result.lines().filter { it.isNotBlank() }

            val conditionString = lines.joinToString(", ") { it.trim() }

            val remedyString = lines.joinToString(", ") { line ->
                val name = line.substringBefore("(").trim()
                "$name - ${getRemedy(name)}"
            }

            saveReportFormatted(
                message = message,
                imageBase64 = imageBase64,
                conditionString = conditionString,
                remedyString = remedyString
            )

            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }
    
    private fun saveReportFormatted(
        message: String,
        imageBase64: String,
        conditionString: String,
        remedyString: String
    ) {
        val userId = firebase.currentUser?.uid ?: return
        val userNameRef = database.getReference("clinicInfo")
            .child(userId)
            .child("clinicName")

        userNameRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userName = snapshot.getValue(String::class.java) ?: "Unknown User"

                val reportTimestamp = System.currentTimeMillis()
                val timeFormatted = SimpleDateFormat(
                    "MMMM dd, yyyy HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())

                // EXACT FIREBASE STRUCTURE
                val scanResultMap = mapOf(
                    "condition" to conditionString,
                    "imageBase64" to imageBase64,
                    "remedy" to remedyString,
                    "timestamp" to timeFormatted
                )

                val reportData = mapOf(
                    "message" to message,
                    "reportTimestamp" to reportTimestamp,
                    "scanResult" to scanResultMap,
                    "userId" to userId,
                    "userName" to userName
                )

                val ref = database.getReference("scanReports")
                val newKey = ref.push().key ?: return

                ref.child(newKey).setValue(reportData)
                    .addOnSuccessListener {
                        Toast.makeText(
                            this@MainPage,
                            "Report sent successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(
                            this@MainPage,
                            "Failed to send report: ${it.message}",
                            Toast.LENGTH_SHORT
                        ).show()
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

//    private fun createImageFile(): File {
//        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
//        val storageDir: File = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)!!
//        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
//    }

    private fun loadLabels(): List<String> {
        val labels = mutableListOf<String>()
        val inputStream = assets.open("labels.json")
        val json = inputStream.bufferedReader().use { it.readText() }
        val jsonObject = org.json.JSONObject(json)

        for (i in 0 until jsonObject.length()) {
            labels.add(jsonObject.getString(i.toString()))
        }
        return labels
    }


    private fun predict(bitmap: Bitmap): List<Pair<String, String>> {
        val inputSize = 224
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val px = resizedBitmap.getPixel(x, y)
                byteBuffer.putFloat((px shr 16 and 0xFF) / 255f)
                byteBuffer.putFloat((px shr 8 and 0xFF) / 255f)
                byteBuffer.putFloat((px and 0xFF) / 255f)
            }
        }

        val input = TensorBuffer.createFixedSize(intArrayOf(1, inputSize, inputSize, 3), DataType.FLOAT32)
        input.loadBuffer(byteBuffer)
        val output = model.process(input).outputFeature0AsTensorBuffer.floatArray

        return output.mapIndexed { index, confidence -> index to confidence }
            .sortedByDescending { it.second }
            .take(3)
            .map { pair ->
                val label = conditionLabels.getOrElse(pair.first) { "Unknown" }
                val remedy = remedies[label] ?: "No specific remedy"
                val confidencePercent = String.format("%.1f%%", pair.second * 100)
                "$label ($confidencePercent)" to remedy
            }
    }



    private fun loadConditionLabels(): List<String> {
        val inputStream = assets.open("labels.json")
        val json = inputStream.bufferedReader().use { it.readText() }
        val jsonArray = org.json.JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) list.add(jsonArray.getString(i))
        return list
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * 300 * 300 * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(300 * 300)
        bitmap.getPixels(intValues, 0, 300, 0, 0, 300, 300)

        var pixelIndex = 0
        for (i in 0 until 300) {
            for (j in 0 until 300) {
                val pixel = intValues[pixelIndex++]
                byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f) // R
                byteBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)  // G
                byteBuffer.putFloat((pixel and 0xFF) / 255f)          // B
            }
        }
        return byteBuffer
    }


    private fun preprocessImage(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val result = Array(1) { Array(300) { Array(300) { FloatArray(3) } } }  // <-- updated
        for (i in 0 until 300) {
            for (j in 0 until 300) {
                val pixel = bitmap.getPixel(i, j)
                result[0][i][j][0] = Color.red(pixel) / 255.0f
                result[0][i][j][1] = Color.green(pixel) / 255.0f
                result[0][i][j][2] = Color.blue(pixel) / 255.0f
            }
        }
        return result
    }


    private fun getRemedy(condition: String): String {
        return when (condition) {

            "Acne" -> "Cleanse your face twice daily with a mild cleanser and apply over-the-counter benzoyl peroxide or salicylic acid products to reduce inflammation and bacteria."
            "Impetigo" -> "Keep the affected area clean and dry. Apply antibiotic ointment as prescribed by a doctor and avoid scratching to prevent spreading."
            "Nail Fungus" -> "Apply antifungal creams or medicated nail solutions. Keep nails dry and trimmed; oral medication may be required for persistent cases."
            "Normal" -> "Your skin is healthy. Maintain good hydration, a balanced diet, and regular skincare with sunscreen to keep it that way!"
            "Tinea or Ringworm" -> "Apply an over-the-counter antifungal cream (like clotrimazole or terbinafine) twice daily. Keep the affected area clean and dry."
            "Urticaria-Hives" -> "Avoid known triggers, take antihistamines if needed, and keep skin cool. Consult a doctor if hives persist or worsen."
            "Vitiligo" -> "Use broad-spectrum sunscreen to protect depigmented areas. Topical corticosteroids or phototherapy may be recommended by a dermatologist."
            "Warts or Viral Infection" -> "Use salicylic acid treatments or over-the-counter cryotherapy. Avoid picking to prevent spreading the virus."
            else -> "No specific remedy found. Consult a dermatologist for diagnosis and treatment."
        }
    }


    private fun saveScanToFirebase(predictions: List<Pair<String, String>>, bitmap: Bitmap) {
        val currentUser = firebase.currentUser ?: return
        val userId = currentUser.uid
        val dbRef = database.reference

        val imageBase64 = encodeToBase64(bitmap)
        val timestamp = SimpleDateFormat("MMMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val scanId = SimpleDateFormat("MM-dd-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())

        val predictionsMap = predictions.map { mapOf("disease" to it.first, "remedy" to it.second) }

        val scanData = mapOf(
            "timestamp" to timestamp,
            "imageBase64" to imageBase64,
            "predictions" to predictionsMap
        )

        dbRef.child("scanResults").child(userId).child(scanId).setValue(scanData)
            .addOnSuccessListener { Toast.makeText(this, "Scan saved successfully!", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { e -> Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show() }
    }


    private fun encodeToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
        return android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.DEFAULT)
    }

    private fun uriToBitmap(uri: Uri): Bitmap {
        val inputStream = contentResolver.openInputStream(uri)
        return BitmapFactory.decodeStream(inputStream)!!
    }

    // ✅ Cleanup model
    override fun onDestroy() {
        super.onDestroy()
        try {
            model.close()
        } catch (e: Exception) {
            Log.e("MainPage", "Error closing model", e)
        }
    }



    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }


//
//    private fun showProgress() {
//        binding.loadingProgressBar.visibility = View.VISIBLE
//    }
//
//    private fun hideProgress() {
//        binding.loadingProgressBar.visibility = View.GONE
//    }

}