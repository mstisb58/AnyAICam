package com.example.MPdetector

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraXのセットアップとライフサイクルを管理するクラス
 * @param activity ライフサイクルオーナーとしてのアクティビティ
 * @param previewView プレビューを表示するView
 * @param onFrameReceived リアルタイムのカメラフレームを処理するためのコールバック関数
 */
class CameraManager(
    private val activity: AppCompatActivity,
    private val previewView: PreviewView,
    private val onFrameReceived: (ImageProxy) -> Unit
) {
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    init {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    /**
     * カメラを起動し、プレビューと画像解析を開始する
     */
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // プレビューのユースケース
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 撮影のユースケース
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()

            // リアルタイム画像解析のユースケース
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, onFrameReceived)
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    activity, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                // エラーハンドリング
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    /**
     * 写真を撮影する
     * @param onImageCaptured 撮影成功時のコールバック
     */
    fun takePhoto(onImageCaptured: (ImageProxy) -> Unit) {
        val imageCapture = imageCapture ?: return
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(activity),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    onImageCaptured(image)
                }
            }
        )
    }

    /**
     * カメラリソースを解放する
     */
    fun shutdown() {
        cameraExecutor.shutdown()
    }
}
