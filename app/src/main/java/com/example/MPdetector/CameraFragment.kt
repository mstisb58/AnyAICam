// CameraFragment.kt
package com.example.MPdetector

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.MPdetector.databinding.FragmentCameraBinding
import com.google.common.util.concurrent.ListenableFuture
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// ### 修正箇所 1: ProcessorSelectionListenerを実装します ###
class CameraFragment : Fragment(), ProcessorSelectionListener {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var cameraExecutor: ExecutorService
    private val availableProcessors = ProcessorRepository.getProcessors()

    private var imageCapture: ImageCapture? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var cameraProvider: ProcessCameraProvider? = null

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                initializeCamera()
            } else {
                Toast.makeText(requireContext(), "Camera permission is required.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initializeCamera()
        } else {
            activityResultLauncher.launch(Manifest.permission.CAMERA)
        }
        setupUIListeners()
    }

    private fun initializeCamera() {
        if (OpenCVLoader.initDebug()) {
            startCamera()
        } else {
            // Handle OpenCV load error
        }
        availableProcessors.forEach { it.setup(requireContext()) }
    }

    private fun setupUIListeners() {
        binding.shutterButton.setOnClickListener { takePhoto() }
        // ### 修正箇所 2: 新しいダイアログを呼び出すメソッドに変更します ###
        binding.detectorButton.setOnClickListener { openProcessorSelectionDialog() }
        binding.cameraSwitchButton.setOnClickListener {
            lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
            startCamera()
        }
    }

    // ### 修正箇所 3: 古いポップアップの代わりに、新しいダイアログを開くメソッドを定義します ###
    private fun openProcessorSelectionDialog() {
        val dialog = ProcessorSelectionDialogFragment(
            allProcessors = availableProcessors,
            initiallySelected = sharedViewModel.activeProcessors.value ?: emptyList(),
            listener = this
        )
        dialog.show(parentFragmentManager, "ProcessorSelectionDialog")
    }

    // ### 修正箇所 4: ダイアログから順序付けされたリストを受け取るためのコールバックメソッドです ###
    override fun onProcessorsSelected(selectedProcessors: List<ImgProcessor>) {
        sharedViewModel.setActiveProcessors(selectedProcessors)
        binding.detectorButton.text = "Processors (${selectedProcessors.size})"
    }

    private fun startCamera() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            updateCameraSwitchButtonVisibility()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.cameraPreview.surfaceProvider) }
            imageCapture = ImageCapture.Builder().build()
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        val frameMat = imageProxyToMat(imageProxy)
                        val rotatedMat = when (rotationDegrees) {
                            90 -> { val mat = Mat(); Core.rotate(frameMat, mat, Core.ROTATE_90_CLOCKWISE); mat }
                            180 -> { val mat = Mat(); Core.rotate(frameMat, mat, Core.ROTATE_180); mat }
                            270 -> { val mat = Mat(); Core.rotate(frameMat, mat, Core.ROTATE_90_COUNTERCLOCKWISE); mat }
                            else -> frameMat
                        }
                        if (rotatedMat != frameMat) frameMat.release()

                        var displayMat = rotatedMat
                        var allStatusesOk = true
                        try {
                            // ViewModelから「順序付けされた」プロセッサリストを取得して適用
                            val activeProcessors = sharedViewModel.activeProcessors.value
                            if (!activeProcessors.isNullOrEmpty()) {
                                activeProcessors.forEach { processor ->
                                    val (processedMat, status) = processor.processFrameForDisplay(displayMat)
                                    if (processedMat !== displayMat && displayMat !== rotatedMat) displayMat.release()
                                    displayMat = processedMat
                                    if (!status) allStatusesOk = false
                                }
                            }
                            val displayBitmap = matToBitmap(displayMat)
                            activity?.runOnUiThread {
                                if (_binding != null) {
                                    binding.shutterButton.isEnabled = allStatusesOk
                                    binding.overlay.setImageBitmap(displayBitmap)
                                }
                            }
                        } finally {
                            if (displayMat !== rotatedMat) displayMat.release()
                            rotatedMat.release()
                            imageProxy.close()
                        }
                    }
                }
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e("CameraFragment", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun updateCameraSwitchButtonVisibility() {
        try {
            val hasBackCamera = cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
            val hasFrontCamera = cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
            activity?.runOnUiThread {
                if (_binding != null) binding.cameraSwitchButton.isVisible = hasBackCamera && hasFrontCamera
            }
        } catch (e: CameraInfoUnavailableException) {
            activity?.runOnUiThread {
                if (_binding != null) binding.cameraSwitchButton.isVisible = false
            }
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    val unrotatedBitmap = imageProxyToBitmap(image)
                    image.close()
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val rotatedBitmap = Bitmap.createBitmap(unrotatedBitmap, 0, 0, unrotatedBitmap.width, unrotatedBitmap.height, matrix, true)
                    sharedViewModel.setRawFrame(rotatedBitmap)
                    (activity as? MainActivity)?.navigateToPreview()
                }
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraFragment", "Photo capture failed: ${exc.message}", exc)
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }

    private fun imageProxyToMat(image: ImageProxy): Mat {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1)
        yuvImage.put(0, 0, nv21)
        val rgbaMat = Mat()
        Imgproc.cvtColor(yuvImage, rgbaMat, Imgproc.COLOR_YUV2RGBA_NV21, 4)
        yuvImage.release()
        return rgbaMat
    }

    private fun matToBitmap(mat: Mat): Bitmap {
        val bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bmp)
        return bmp
    }
}
