// file: app/src/main/java/com/example/MPdetector/MainActivity.kt
package com.example.MPdetector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
// ★★★ このimport文が、XMLのIDを解決するために不可欠です ★★★
import com.example.MPdetector.R

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var captureButton: ImageButton
    private lateinit var selectModelButton: Button

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

    private var currentDetector: IDetector? = null
    private lateinit var availableModels: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.preview_view)
        overlayView = findViewById(R.id.overlay_view)
        captureButton = findViewById(R.id.capture_button)
        selectModelButton = findViewById(R.id.select_model_button)

        cameraExecutor = Executors.newSingleThreadExecutor()

        loadAvailableModels()

        selectModelButton.setOnClickListener { showModelSelectionDialog() }
        captureButton.setOnClickListener { takePhotoAndCrop() }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }

    private fun loadAvailableModels() {
        availableModels = listOf("tangue_detector", "wink_detector")
        if (availableModels.isNotEmpty()) {
            setupDetector(availableModels[0])
        } else {
            Toast.makeText(this, "利用可能なモデルがありません", Toast.LENGTH_LONG).show()
        }
    }

    private fun showModelSelectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("モデルを選択")
            .setItems(availableModels.toTypedArray()) { _, which ->
                val selectedModel = availableModels[which]
                if (currentDetector?.name != selectedModel) {
                    setupDetector(selectedModel)
                }
            }
            .show()
    }

    private fun setupDetector(modelName: String) {
        currentDetector?.close()
        overlayView.clear()

        currentDetector = DetectorFactory.create(modelName)
        currentDetector?.setup(this, RunningMode.LIVE_STREAM) { result, height, width ->
            runOnUiThread {
                result.onSuccess { detectionResult ->
                    overlayView.setResults(detectionResult, height, width)
                }.onFailure {
                    overlayView.clear()
                }
            }
        }
        startCamera()
        Toast.makeText(this, "$modelName を選択しました", Toast.LENGTH_SHORT).show()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setTargetRotation(previewView.display.rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetRotation(previewView.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        val rotatedBitmap = imageProxyToBitmap(imageProxy, true)
                        currentDetector?.detectLiveStream(rotatedBitmap, imageProxy.height, imageProxy.width)
                    } finally {
                        imageProxy.close()
                    }
                }
            }

        cameraProvider.unbindAll()
        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun takePhotoAndCrop() {
        val imageCapture = this.imageCapture ?: return
        val targetDetectorName = currentDetector?.name ?: run {
            Toast.makeText(this, "モデルを選択してください", Toast.LENGTH_SHORT).show()
            return
        }

        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val capturedBitmap = imageProxyToBitmap(image, false)
                    image.close()

                    val detectorForImage = DetectorFactory.create(targetDetectorName)
                    detectorForImage?.setup(this@MainActivity, RunningMode.IMAGE)
                    val result = detectorForImage?.detect(capturedBitmap, 0)
                    detectorForImage?.close()

                    if (result == null) {
                        runOnUiThread { Toast.makeText(this@MainActivity, "対象物を検出できませんでした", Toast.LENGTH_SHORT).show() }
                        return
                    }

                    val croppedBitmap = cropBitmap(capturedBitmap, result.boundingBox)
                    saveAndLaunchPreview(croppedBitmap)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy, isLiveStream: Boolean): Bitmap {
        val bitmap = image.toBitmap()
        val matrix = Matrix().apply {
            postRotate(image.imageInfo.rotationDegrees.toFloat())
            if (isLiveStream && cameraSelector.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cropBitmap(source: Bitmap, normalizedBoundingBox: RectF): Bitmap {
        val cropRect = RectF(
            normalizedBoundingBox.left * source.width,
            normalizedBoundingBox.top * source.height,
            normalizedBoundingBox.right * source.width,
            normalizedBoundingBox.bottom * source.height
        )
        return Bitmap.createBitmap(
            source,
            cropRect.left.toInt().coerceAtLeast(0),
            cropRect.top.toInt().coerceAtLeast(0),
            cropRect.width().toInt().coerceAtMost(source.width - cropRect.left.toInt()),
            cropRect.height().toInt().coerceAtMost(source.height - cropRect.top.toInt())
        )
    }

    private fun saveAndLaunchPreview(bitmap: Bitmap) {
        try {
            val tempFile = File.createTempFile("cropped_", ".jpg", cacheDir)
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            runOnUiThread {
                val intent = Intent(this, PreviewActivity::class.java).apply {
                    putExtra("image_path", tempFile.absolutePath)
                }
                startActivity(intent)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save cropped image", e)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        currentDetector?.close()
    }

    companion object {
        private const val TAG = "MPdetector"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
