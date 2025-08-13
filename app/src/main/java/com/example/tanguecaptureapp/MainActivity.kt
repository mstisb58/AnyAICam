package com.example.tanguecaptureapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.tanguecaptureapp.databinding.ActivityMainBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var faceLandmarker: FaceLandmarker

    private var isLandmarksVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            setupFaceLandmarker()
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        binding.shutterButton.setOnClickListener { takePhoto() }

        binding.toggleButton?.setOnClickListener {
            isLandmarksVisible = !isLandmarksVisible
            binding.overlay.clear()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupFaceLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setResultListener(this::onResult)
            .setErrorListener { error ->
                Log.e(TAG, "MediaPipe Error: ${error.message}")
            }
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(this, options)
    }

    private fun onResult(result: FaceLandmarkerResult, image: MPImage) {
        runOnUiThread {
            binding.overlay.setResults(
                result,
                image.height,
                image.width,
                RunningMode.LIVE_STREAM,
                isLandmarksVisible
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(binding.cameraPreview.display.rotation)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, this::detectFace)
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "ユースケースのバインドに失敗しました", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun detectFace(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            postScale(-1f, 1f, imageProxy.width / 2f, imageProxy.height / 2f)
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        faceLandmarker.detectAsync(mpImage, System.currentTimeMillis())
        imageProxy.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        faceLandmarker.close()
        cameraExecutor.shutdown()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val rotationDegrees = image.imageInfo.rotationDegrees.toFloat()
                    image.close()

                    val matrix = Matrix().apply {
                        postRotate(rotationDegrees)
                        postScale(-1f, 1f, originalBitmap.width / 2f, originalBitmap.height / 2f)
                    }

                    val correctedBitmap = Bitmap.createBitmap(
                        originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                    )

                    val croppedBitmap = cropToTongueArea(this@MainActivity, correctedBitmap)

                    if (croppedBitmap == null) {
                        Toast.makeText(this@MainActivity, "口の検出に失敗しました。もう一度お試しください。", Toast.LENGTH_SHORT).show()
                        return
                    }

                    try {
                        val cacheDir = applicationContext.cacheDir
                        val tempFile = File.createTempFile("cropped_", ".png", cacheDir)

                        FileOutputStream(tempFile).use { fos ->
                            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        }

                        val intent = Intent(this@MainActivity, PreviewActivity::class.java).apply {
                            putExtra(PreviewActivity.EXTRA_CROPPED_IMAGE_PATH, tempFile.absolutePath)
                        }
                        startActivity(intent)

                    } catch (e: IOException) {
                        Log.e(TAG, "一時ファイルの作成に失敗しました", e)
                        Toast.makeText(this@MainActivity, "エラーが発生しました", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "写真の撮影に失敗しました: ${exc.message}", exc)
                }
            }
        )
    }

    // 修正: 傾いた領域をまっすぐに切り抜くロジックを修正
    private fun cropToTongueArea(context: Context, source: Bitmap): Bitmap? {
        val baseOptions = BaseOptions.builder().setModelAssetPath("face_landmarker.task").build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .build()
        val imageLandmarker = FaceLandmarker.createFromOptions(context, options)

        val mpImage = BitmapImageBuilder(source).build()
        val result = imageLandmarker.detect(mpImage)

        if (result.faceLandmarks().isEmpty()) {
            imageLandmarker.close()
            return null
        }

        val landmarks = result.faceLandmarks()[0]

        val rightCorner = landmarks[61]
        val leftCorner = landmarks[291]

        val leftCornerX = leftCorner.x() * source.width
        val leftCornerY = leftCorner.y() * source.height
        val rightCornerX = rightCorner.x() * source.width
        val rightCornerY = rightCorner.y() * source.height

        val dx = rightCornerX - leftCornerX
        val dy = rightCornerY - leftCornerY
        val boxWidth = sqrt((dx * dx + dy * dy))
        if (boxWidth <= 0) {
            imageLandmarker.close()
            return null
        }

        // 正方形の下方向の頂点を計算
        val p_dx = dy
        val p_dy = -dx
        val leftCornerBottomX = leftCornerX + p_dx
        val leftCornerBottomY = leftCornerY + p_dy
        val rightCornerBottomX = rightCornerX + p_dx
        val rightCornerBottomY = rightCornerY + p_dy

        // 結果を格納するBitmapを作成
        val resultBitmap = Bitmap.createBitmap(boxWidth.toInt(), boxWidth.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        // 座標変換用のMatrixを作成
        val matrix = Matrix()

        // 変換元（ソース画像上の傾いた四角形）の4頂点を設定
        val srcPts = floatArrayOf(
            leftCornerX, leftCornerY,
            rightCornerX, rightCornerY,
            rightCornerBottomX, rightCornerBottomY,
            leftCornerBottomX, leftCornerBottomY
        )
        // 変換先（結果Bitmap上のまっすぐな四角形）の4頂点を設定
        val dstPts = floatArrayOf(
            0f, 0f,
            boxWidth, 0f,
            boxWidth, boxWidth,
            0f, boxWidth
        )

        // 変換元から変換先へのマッピングを設定
        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        // Matrixを使って、ソース画像を変形させながら結果Bitmapに描画
        canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        imageLandmarker.close()
        return resultBitmap
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                setupFaceLandmarker()
                startCamera()
            } else {
                Toast.makeText(this, "カメラのパーミッションが許可されませんでした。", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "TangueCaptureApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}
