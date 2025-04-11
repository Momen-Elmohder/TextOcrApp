package com.example.textocrapp

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract.Data
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.provider.MediaStore
import android.widget.EditText
import androidx.cardview.widget.CardView
import java.io.FileOutputStream
import java.io.FileWriter


class MainActivity : AppCompatActivity() {

    // Declare UI components
    private lateinit var captureImageButton: CardView
    private lateinit var resultText: EditText
    private lateinit var copyTxtBtn: CardView
    private lateinit var galleryBtn: CardView
    private lateinit var convertToExl: CardView
    private lateinit var convertToPDF: CardView
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>


    // Launchers for permission, capturing images, and picking images from gallery
    private var currentPhotoPaths: String? = null
    private lateinit var requestPermissionLancher: ActivityResultLauncher<String>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize UI components
        captureImageButton = findViewById(R.id.captureImageButton)
        resultText = findViewById(R.id.resultText)
        copyTxtBtn = findViewById(R.id.copyTxtBtn)
        galleryBtn = findViewById(R.id.galleryBtn)
        convertToExl = findViewById(R.id.convertToExl)
        convertToPDF = findViewById(R.id.convertToPDF)

        // Set EditText properties for multiline and selectable text
        resultText.setTextIsSelectable(true)
        resultText.isFocusable = true
        resultText.isFocusableInTouchMode = true
        resultText.setSingleLine(false)


        // Handle image selection from gallery
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageUri: Uri? = result.data?.data
                imageUri?.let {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                    recognizeText(bitmap)
                }
            }
        }

        // Handle camera permission request
        requestPermissionLancher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
            isGranted ->
            if (isGranted){
                captureImage()
            }else{
                Toast.makeText(this,"Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle image capture
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()){
            success ->
            if (success){
                currentPhotoPaths?.let { path ->
                    val bitmap = BitmapFactory.decodeFile(path)
                    recognizeText(bitmap)
                }
            }
        }


        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageUri: Uri? = result.data?.data
                imageUri?.let {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                    recognizeText(bitmap)
                }
            }
        }

        // Set click listeners
        captureImageButton.setOnClickListener{
            requestPermissionLancher.launch(Manifest.permission.CAMERA)
        }

        galleryBtn.setOnClickListener {
            openGallery()
        }

        convertToExl.setOnClickListener{exportToCSV()}
        convertToPDF.setOnClickListener{exportToPDF()}

    }

    // Create a file for storing captured image
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_",".jpg", storageDir).apply {
            currentPhotoPaths = absolutePath
        }
    }

    // Capture image using camera
    private fun captureImage(){
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            Toast.makeText(this, "Error occurred while creating a file", Toast.LENGTH_SHORT).show()
            null
        }
        photoFile?.also {
            val photoUri: Uri = FileProvider.getUriForFile(this,"${applicationContext.packageName}.provider", it)
            takePictureLauncher.launch(photoUri)
        }
    }


    // Open gallery to pick an image
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }


    // Recognize text from image
    private fun recognizeText(bitmap: Bitmap){
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image).addOnSuccessListener { ocrText ->
            resultText.setText(ocrText.text)
            copyTxtBtn.visibility = CardView.VISIBLE
            copyTxtBtn.setOnClickListener {
                val clipboard = ContextCompat.getSystemService(this, android.content.ClipboardManager::class.java)
                val clip = android.content.ClipData.newPlainText("recognized text", ocrText.text)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(this, "Text Copied", Toast.LENGTH_SHORT).show()
            }
        } .addOnFailureListener{ e ->
            Toast.makeText(this, "Failed to recognize text: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    // Export recognized text to CSV
    private fun exportToCSV(){
        val text = resultText.text.toString().trim()

        if (text.isEmpty()){
            Toast.makeText(this, "Please enter Text", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "data.csv")
        try {
            val writer = FileWriter(file, true)
            val isNewFile = !file.exists()

            if (isNewFile) {
                writer.append("Text Data\n")
            }

            writer.append("\"$text\"\n")
            writer.flush()
            writer.close()

            Toast.makeText(this,"CSV saving at: ${file.absolutePath}",Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving CSV file", Toast.LENGTH_LONG).show()
        }

    }

    // Export recognized text to PDF
    private fun exportToPDF(){
        val text =  resultText.text.toString().trim()

        if (text.isEmpty()){
            Toast.makeText(this, "Please enter Text", Toast.LENGTH_SHORT).show()
            return
        }

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(300, 600, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawText(text, 50f, 50f, Paint())
        pdfDocument.finishPage(page)

        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "data.pdf")

        try {
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()

            Toast.makeText(this, "PDF saved at: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving PDF file", Toast.LENGTH_LONG).show()
        }


    }

}