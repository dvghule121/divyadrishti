package com.dynocodes.divyadrishti

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: Button
    private lateinit var imageCapture: ImageCapture
    private lateinit var apiClient: ApiClient
    private lateinit var outputImage: ImageView
    private lateinit var textToSpeech: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
            } else {
                Toast.makeText(this, "Text to speech initialization failed", Toast.LENGTH_SHORT).show()
            }
        }


        previewView = findViewById(R.id.previewView)
        btnCapture = findViewById(R.id.btnSendRequest)
        apiClient = ApiClient()
        outputImage = findViewById(R.id.ivOutput)

        btnCapture.setOnClickListener {
            lifecycleScope.launch {
                repeat(100){
                    captureImage()
                    delay(4000)
                }
            }

        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            imageCapture = ImageCapture.Builder()

                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        val photoFile = File(externalMediaDirs.firstOrNull(), "${System.currentTimeMillis()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: photoFile.toUri()
                    val savedFile = File(savedUri.path ?: return)

                    // Process the saved image file here or pass it to your API
                    processImage(savedFile)
                    outputImage.setImageURI(Uri.fromFile(savedFile))

                }
            }
        )
    }

    private fun processImage(imageFile: File) {
        // Send the image file to your API for further processing
        GlobalScope.launch(Dispatchers.IO) {
            val imageBytes = imageFile.readBytes()
            apiClient.sendFrame(imageBytes){detectedObjects ->
                Log.d(TAG, "processImage: $detectedObjects")
                val outputSentence = ObjectDetection().generateSentence(detectedObjects)
                speak(outputSentence)


                // Inside your activity or fragment where you have access to the ImageView

                // Get the current set image from the ImageView
                val drawable = outputImage.drawable
                val bitmap = (drawable as? BitmapDrawable)?.bitmap

                // Check if bitmap is not null
                bitmap?.let {
                    // Create a mutable copy of the bitmap to allow drawing on it
                    val mutableBitmap = it.copy(Bitmap.Config.ARGB_8888, true)

                    // Use a Canvas object to draw on the bitmap
                    val canvas = Canvas(mutableBitmap)
                    val paint = Paint().apply {
                        color = Color.BLUE
                        style = Paint.Style.STROKE
                        strokeWidth = 5f
                        textSize = 30f
                    }

                    // Draw bounding boxes and labels
                    detectedObjects.forEach { detection ->
                        val bbox = detection.bbox
                        // Draw bounding box
                        canvas.drawRect(bbox[0].toFloat(), bbox[1].toFloat(),bbox[0].toFloat()+ bbox[2].toFloat(), bbox[1].toFloat()+ bbox[3].toFloat(), paint)
                        // Draw label text
                        val labelText = "${detection.label}: ${detection.position}"
                        canvas.drawText(labelText, bbox[0].toFloat(), bbox[1].toFloat() - 20f, paint)
                    }
                    runOnUiThread{
                        // Set the modified bitmap with drawn overlays back to the ImageView
                        outputImage.setImageBitmap(mutableBitmap)
                    }

                }

            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
        private const val TAG = "MainActivity"
    }
}
