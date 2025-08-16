// file: app/src/main/java/com/example/MPdetector/MainActivity.kt
package com.example.MPdetector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap // これが正しいBitmap
import android.graphics.Canvas // ★★★ android.graphics.Canvas に変更 ★★★
import android.graphics.Color // ★★★ android.graphics.Color に変更 ★★★
import android.graphics.Matrix
import android.graphics.Paint // ★★★ android.graphics.Paint に変更 ★★★
import android.graphics.Path // ★★★ android.graphics.Path に変更 ★★★
import android.graphics.PointF // ★★★ android.graphics.PointF を確認 ★★★
import android.graphics.PorterDuff // ★★★ PorterDuff をインポート ★★★
import android.graphics.PorterDuffXfermode
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
import androidx.core.graphics.transform
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.example.MPdetector.ShapeBoundary
import kotlin.io.path.moveTo

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

                    if (result.boundary == null || result.boundary.points.isEmpty()) {
                        runOnUiThread { Toast.makeText(this@MainActivity, "検出結果に有効な境界情報がありません", Toast.LENGTH_SHORT).show() }
                        return
                    }

                    // ★★★ 新しい cropBitmapWithRotationAndPadding を使用 ★★★
                    val croppedBitmap = cropBitmapWithRotationAndPadding(
                        capturedBitmap,
                        result.boundary, // ShapeBoundary を渡す
                        result.rotationAngle // 回転角度を渡す
                    )

                    if (croppedBitmap == null) {
                        runOnUiThread { Toast.makeText(this@MainActivity, "画像のクロップまたは回転に失敗しました", Toast.LENGTH_SHORT).show() }
                        return
                    }
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

    private fun cropBitmap(source: Bitmap, normalizedBoundingBox: RectF): Bitmap? {
        if (normalizedBoundingBox.width() < 0 || normalizedBoundingBox.height() < 0) {
            return null
        }
        val cropRect = RectF(
            normalizedBoundingBox.left * source.width,
            normalizedBoundingBox.top * source.height,
            normalizedBoundingBox.right * source.width,
            normalizedBoundingBox.bottom * source.height
        )
        if (cropRect.width() < 0 || cropRect.height() < 0) {
            return null
        }
        return Bitmap.createBitmap(
            source,
            cropRect.left.toInt().coerceAtLeast(0),
            cropRect.top.toInt().coerceAtLeast(0),
            cropRect.width().toInt().coerceAtMost(source.width - cropRect.left.toInt()),
            cropRect.height().toInt().coerceAtMost(source.height - cropRect.top.toInt())
        )
    }

    private fun cropBitmapWithRotationAndPadding(
        originalBitmap: Bitmap,
        shapeBoundary: ShapeBoundary,
        rotationDegrees: Float // This is the angle to *undo* the rotation of the shape
    ): Bitmap? {
        if (shapeBoundary.points.isEmpty()) {
            Log.e(TAG, "ShapeBoundary has no points.")
            return null
        }

        val originalWidth = originalBitmap.width
        val originalHeight = originalBitmap.height

        // 1. Convert normalized points from ShapeBoundary to pixel coordinates in the originalBitmap
        val pixelPointsOriginal = shapeBoundary.points.map {
            android.graphics.PointF(it.x * originalWidth, it.y * originalHeight)
        }
        Log.d(TAG, "PixelPoints (in originalBitmap): $pixelPointsOriginal")
        if (pixelPointsOriginal.isEmpty()) return null

        // 2. Calculate the center of the shape in the originalBitmap
        val shapePathOriginal = Path()
        pixelPointsOriginal.forEachIndexed { i, p -> if (i == 0) shapePathOriginal.moveTo(p.x, p.y) else shapePathOriginal.lineTo(p.x, p.y) }
        shapePathOriginal.close()
        val shapeBoundsOriginal = RectF()
        shapePathOriginal.computeBounds(shapeBoundsOriginal, true)
        val shapeCenterXOriginal = shapeBoundsOriginal.centerX()
        val shapeCenterYOriginal = shapeBoundsOriginal.centerY()
        Log.d(TAG, "ShapeCenter (in originalBitmap): ($shapeCenterXOriginal, $shapeCenterYOriginal)")
        Log.d(TAG, "ShapeBounds (in originalBitmap): $shapeBoundsOriginal")


        // 3. Determine the dimensions of the new bitmap by rotating the original shape's path
        //    This rotation is to find the bounding box of the shape *after it's uprighted*.
        val matrixForBoundsCalculation = Matrix()
        // Rotate around the shape's own center in the original image
        matrixForBoundsCalculation.setRotate(-rotationDegrees, shapeCenterXOriginal, shapeCenterYOriginal)

        val uprightShapePath = Path()
        shapePathOriginal.transform(matrixForBoundsCalculation, uprightShapePath)
        val uprightShapeBounds = RectF() // Bounds of the shape *after* being counter-rotated
        uprightShapePath.computeBounds(uprightShapeBounds, true)

        val newWidth = uprightShapeBounds.width().toInt()
        val newHeight = uprightShapeBounds.height().toInt()
        Log.d(TAG, "New Bitmap Dimensions (for upright shape): $newWidth x $newHeight")
        Log.d(TAG, "Upright Shape Bounds (in originalBitmap's coord system, but rotated): $uprightShapeBounds")


        if (newWidth <= 0 || newHeight <= 0) {
            Log.e(TAG, "Invalid new dimensions after rotation: $newWidth x $newHeight")
            return null
        }

        val resultBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        canvas.drawColor(Color.WHITE) // Fill background

        // 4. Construct the transformation matrix (drawMatrix)
        // This matrix will transform originalBitmap content to be drawn on resultBitmap.
        // The goal: the shape (defined by pixelPointsOriginal) should appear
        // counter-rotated by `rotationDegrees` and centered in the `resultBitmap`.

        val drawMatrix = Matrix()

        // Step 4a: Translate the shape's center in originalBitmap to origin (0,0)
        drawMatrix.postTranslate(-shapeCenterXOriginal, -shapeCenterYOriginal)
        Log.d(TAG, "drawMatrix after translate to origin: $drawMatrix")

        // Step 4b: Rotate around origin (this counter-rotates the shape)
        drawMatrix.postRotate(-rotationDegrees) // rotationDegrees is the angle to UN-rotate
        Log.d(TAG, "drawMatrix after rotate: $drawMatrix")

        // Step 4c: Translate the (now uprighted) shape so its center is at the center of resultBitmap.
        // The shape was centered at (0,0) after 4a and rotated around (0,0) in 4b.
        // So, its center is still effectively at (0,0) in its local rotated coordinate system.
        // We want to move this (0,0) to (newWidth/2, newHeight/2) in resultBitmap.
        drawMatrix.postTranslate(newWidth / 2f, newHeight / 2f)
        Log.d(TAG, "drawMatrix after translate to new center: $drawMatrix")

        // --- Drawing and Masking (using saveLayer for PorterDuff) ---
        val imagePaint = Paint().apply { isAntiAlias = true; flags = flags or Paint.FILTER_BITMAP_FLAG }
        val layerRect = RectF(0f, 0f, newWidth.toFloat(), newHeight.toFloat())
        val saveCount = canvas.saveLayer(layerRect, null)

        canvas.drawBitmap(originalBitmap, drawMatrix, imagePaint)
        Log.d(TAG, "Drew originalBitmap onto layer.")

        val maskPath = Path()
        // Transform the original shape points using the final drawMatrix
        // These points should now form the uprighted shape, centered in resultBitmap.
        val transformedPointsForMask = pixelPointsOriginal.map { originalPoint ->
            val pts = floatArrayOf(originalPoint.x, originalPoint.y)
            drawMatrix.mapPoints(pts)
            PointF(pts[0], pts[1])
        }
        Log.d(TAG, "Transformed Points for Mask (should be uprighted and centered): $transformedPointsForMask")

        if (transformedPointsForMask.isNotEmpty()) {
            transformedPointsForMask.forEachIndexed { i, p -> if (i == 0) maskPath.moveTo(p.x, p.y) else maskPath.lineTo(p.x, p.y) }
            maskPath.close()

            val maskPaint = Paint().apply {
                isAntiAlias = true
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            canvas.drawPath(maskPath, maskPaint)
            Log.d(TAG, "Applied mask path with DST_IN.")

            val debugMaskPaint = Paint().apply {
                color = Color.BLUE; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true
            }
            canvas.drawPath(maskPath, debugMaskPaint) // Draw debug line on top
            Log.d(TAG, "Drew debug mask path outline.")
        }

        canvas.restoreToCount(saveCount)
        // --- End Drawing and Masking ---

        return resultBitmap
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
