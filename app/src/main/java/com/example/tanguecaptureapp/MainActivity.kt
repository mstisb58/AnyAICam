package com.example.tanguecaptureapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.tanguecaptureapp.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        binding.shutterButton.setOnClickListener { takePhoto() }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "ユースケースのバインドに失敗しました", exc)
            }

        }, ContextCompat.getMainExecutor(this))
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
                    }

                    val correctedBitmap = Bitmap.createBitmap(
                        originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                    )

                    val croppedBitmap = cropImage(correctedBitmap)

                    if (croppedBitmap == null) {
                        Toast.makeText(this@MainActivity, "画像の処理に失敗しました。もう一度お試しください。", Toast.LENGTH_SHORT).show()
                        return
                    }

                    try {
                        val cacheDir = applicationContext.cacheDir
                        val tempFile = File.createTempFile("cropped_", ".png", cacheDir)

                        val fos = FileOutputStream(tempFile)
                        croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        fos.flush()
                        fos.close()

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

    private fun cropImage(source: Bitmap): Bitmap? {
        val previewView = binding.cameraPreview
        val cropGuideView = binding.cropGuideView

        val previewWidth = previewView.width.toFloat()
        val previewHeight = previewView.height.toFloat()

        if (previewWidth == 0f || previewHeight == 0f) {
            Log.e(TAG, "プレビューのサイズが0です。切り抜きをスキップします。")
            return null
        }

        val sourceWidth = source.width.toFloat()
        val sourceHeight = source.height.toFloat()

        if (sourceWidth == 0f || sourceHeight == 0f) {
            Log.e(TAG, "ソース画像のサイズが0です。処理を中断します。")
            return null
        }

        val scaleX = previewWidth / sourceWidth
        val scaleY = previewHeight / sourceHeight

        val scale = max(scaleX, scaleY)

        val scaledWidth = sourceWidth * scale
        val scaledHeight = sourceHeight * scale
        val offsetX = (previewWidth - scaledWidth) / 2f
        val offsetY = (previewHeight - scaledHeight) / 2f

        val guideLeftRelativeToScaledBitmap = cropGuideView.left - offsetX
        val guideTopRelativeToScaledBitmap = cropGuideView.top - offsetY

        val cropX = (guideLeftRelativeToScaledBitmap / scale).toInt()
        val cropY = (guideTopRelativeToScaledBitmap / scale).toInt()
        val cropWidth = (cropGuideView.width / scale).toInt()
        val cropHeight = (cropGuideView.height / scale).toInt()

        val finalX = max(0, cropX)
        val finalY = max(0, cropY)

        val finalWidth = if (finalX + cropWidth > source.width) source.width - finalX else cropWidth
        val finalHeight = if (finalY + cropHeight > source.height) source.height - finalY else cropHeight

        if (finalWidth <= 0 || finalHeight <= 0) {
            Log.e(TAG, "切り出しサイズが0以下になりました。")
            return null
        }

        return Bitmap.createBitmap(source, finalX, finalY, finalWidth, finalHeight)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
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
