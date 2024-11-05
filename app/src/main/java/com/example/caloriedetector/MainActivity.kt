package com.example.caloriedetector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.ImageView
import androidx.camera.core.ImageCaptureException
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.caloriedetector.models.Constants.LABELS_PATH
import com.example.caloriedetector.models.Constants.MODEL_PATH
import com.example.caloriedetector.databinding.ActivityMainBinding
import com.example.caloriedetector.models.BoundingBox
import com.example.caloriedetector.models.DetectedItem
import com.example.caloriedetector.utils.Detector


class MainActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var binding: ActivityMainBinding
    private val isFrontCamera = false

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var detector: Detector

    // Global variable to hold bounding boxes for the captured image
    private var capturedBoundingBoxes: List<BoundingBox> = emptyList()

    private lateinit var cameraExecutor: ExecutorService

    private var calorieTotal: Int = 0
    private var isImageCaptured: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        isImageCaptured = false
        calorieTotal = 0
        binding.overlay.clear()
        detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
        detector.setup()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        binding.captureButton.setOnClickListener {
            capturePhoto()
        }

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageCapture = ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmapBuffer =
                Bitmap.createBitmap(
                    imageProxy.width,
                    imageProxy.height,
                    Bitmap.Config.ARGB_8888
                )
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

                if (isFrontCamera) {
                    postScale(
                        -1f,
                        1f,
                        imageProxy.width.toFloat(),
                        imageProxy.height.toFloat()
                    )
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )

            detector.detect(rotatedBitmap)
        }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer,
                imageCapture
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it[Manifest.permission.CAMERA] == true) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.clear()
        cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun capturePhoto() {
        // Specify file to save the captured photo
        val photoFile = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Photo capture succeeded: ${photoFile.path}")
                    // Display the captured image
                    displayCapturedImage(photoFile)

                }
            })
    }

    private fun displayCapturedImage(photoFile: File) {
        cameraProvider?.unbind(imageAnalyzer)
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        detector.detect(bitmap)
        isImageCaptured = true
    }

    private fun calculateCalories(boundingBoxes: List<BoundingBox>) {
        val calorieValues = mapOf(
            "chicken" to 239,
            "curd" to 98,
            "basundi" to 201,
            "beans" to 31,
            "bindi" to 45,
            "brinjal_gravy" to 150,
            "brinji" to 265,
            "cabbage" to 25,
            "califlower" to 28,
            "channa_masala" to 210,
            "chapati" to 71,
            "chicken_biriyani" to 290,
            "chicken_gravy" to 180,
            "chicken_noodles" to 215,
            "chicken_tandoori" to 265,
            "cocunut_chutney" to 100,
            "cocunut_rice" to 120,
            "curd_rice" to 150,
            "dhall" to 120,
            "dosa" to 133,
            "egg" to 68,
            "egg_gravy" to 190,
            "fish_curry" to 180,
            "fish_fry" to 220,
            "gulab_jamun" to 150,
            "idly" to 39,
            "kesari" to 350,
            "kuzhi_paniyaram" to 50,
            "laddoo" to 180,
            "lemon_rice" to 200,
            "mint_chutney" to 30,
            "momos" to 45,
            "nan" to 260,
            "paneer" to 120,
            "papad" to 60,
            "parotta" to 300,
            "payasam" to 300,
            "pongal" to 200,
            "poori" to 150,
            "potato" to 77,
            "prawn" to 85,
            "pulav" to 200,
            "rasam" to 30,
            "salad" to 25,
            "salna" to 120,
            "sambar" to 100,
            "tamarind_rice" to 170,
            "tomato_chutney" to 40,
            "vada" to 140,
            "vegies" to 50,
            "white_rice" to 130
        )

        var detectedItems = arrayListOf<DetectedItem>()
        for (box in boundingBoxes) {
            calorieValues[box.clsName]?.let { DetectedItem(box.clsName, it) }
                ?.let { detectedItems.add(it) }
        }
        showServingSizeDialog(detectedItems)
    }

    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA

        ).toTypedArray()
    }

    override fun onEmptyDetect() {
        binding.overlay.invalidate()
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        capturedBoundingBoxes = boundingBoxes
        runOnUiThread {
            binding.overlay.apply {
                setResults(boundingBoxes)
                invalidate()
            }
            if (isImageCaptured) {
                calculateCalories(boundingBoxes)
            }
        }
    }

    private fun showServingSizeDialog(detectedItems: ArrayList<DetectedItem>) {
        val servingSizes = arrayOf(1, 2, 3, 4, 5) // Define integer serving sizes

        AlertDialog.Builder(this)
            .setTitle("Select Serving Size")
            .setItems(servingSizes.map { it.toString() }.toTypedArray()) { _, which ->
                val selectedServingSize = servingSizes[which]
                startTargetActivity(detectedItems, selectedServingSize)
            }
            .setCancelable(true)
            .show()
    }

    private fun startTargetActivity(detectedItems: ArrayList<DetectedItem>, servingSize: Int) {
        val intent = Intent(this, CalorieActivity::class.java).apply {
            putParcelableArrayListExtra("detectedItems", detectedItems)
            putExtra("servingSize", servingSize)
        }
        startActivity(intent)
    }
}
