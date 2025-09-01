// CameraFragment.kt
package com.example.AnyAICam

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.AnyAICam.databinding.FragmentCameraBinding
import com.google.common.util.concurrent.ListenableFuture
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment(), ProcessorSelectionListener {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var cameraExecutor: ExecutorService
    private val availableProcessors = ProcessorRepository.getProcessors()

    private var imageCapture: ImageCapture? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var isManualFocus = false

    // Video recording
    private var videoRecorder: VideoRecorder? = null
    private var isRecording = false
    private var videoFile: File? = null

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            for (isGranted in permissions.values) {
                allGranted = allGranted && isGranted
            }
            if (allGranted) {
                initializeCamera()
            } else {
                Toast.makeText(requireContext(), "Camera and Audio permissions are required.", Toast.LENGTH_LONG).show()
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
        checkPermissionsAndInitialize()
        setupUIListeners()
        setupZoomAndFocus()
    }

    private fun checkPermissionsAndInitialize() {
        val permissionsToRequest = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)

        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNotGranted.isEmpty()) {
            initializeCamera()
        } else {
            activityResultLauncher.launch(permissionsNotGranted.toTypedArray())
        }
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
        binding.detectorButton.setOnClickListener { openProcessorSelectionDialog() }
        binding.cameraSwitchButton.setOnClickListener {
            if (isRecording) {
                Toast.makeText(requireContext(), "Stop recording before switching camera.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
            startCamera()
        }
        binding.recordButton.setOnClickListener { toggleRecording() }

        binding.focusModeButton.setOnClickListener {
            isManualFocus = !isManualFocus
            updateFocusUi()
            updateFocusMode()
        }
    }

    private fun updateFocusUi() {
        binding.focusModeButton.text = if(isManualFocus) "MF" else "AF"
        binding.focusSlider.visibility = if (isManualFocus) View.VISIBLE else View.GONE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupZoomAndFocus() {
        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                camera?.let {
                    val currentZoomRatio = it.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                    val delta = detector.scaleFactor
                    it.cameraControl.setZoomRatio((currentZoomRatio * delta).coerceIn(
                        it.cameraInfo.zoomState.value?.minZoomRatio ?: 1f,
                        it.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                    ))
                    return true
                }
                return false
            }
        }
        scaleGestureDetector = ScaleGestureDetector(requireContext(), listener)

        binding.overlay.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }
    }

    private fun toggleRecording() {
        isRecording = !isRecording
        if (isRecording) {
            binding.recordButton.setImageResource(android.R.drawable.ic_media_pause)
            // Recorder is initialized in the analyzer when the first frame is available
        } else {
            binding.recordButton.setImageResource(android.R.drawable.ic_media_play)
            videoRecorder?.stop() // This stops and releases everything
            videoRecorder = null
            videoFile?.let {
                saveVideoToGallery(it)
            }
        }
    }

    private fun saveVideoToGallery(videoFile: File) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + File.separator + "AnyAiCamera")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = requireContext().contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    videoFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
                Toast.makeText(requireContext(), "Video saved to gallery", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("CameraFragment", "Failed to save video", e)
                Toast.makeText(requireContext(), "Failed to save video", Toast.LENGTH_SHORT).show()
            } finally {
                videoFile.delete() // delete temp file
                this.videoFile = null
            }
        }
    }

    private fun openProcessorSelectionDialog() {
        val dialog = ProcessorSelectionDialogFragment(
            allProcessors = availableProcessors,
            initiallySelected = sharedViewModel.activeProcessors.value ?: emptyList(),
            listener = this
        )
        dialog.show(parentFragmentManager, "ProcessorSelectionDialog")
    }

    override fun onProcessorsSelected(selectedProcessors: List<ImgProcessor>) {
        sharedViewModel.setActiveProcessors(selectedProcessors)
        binding.detectorButton.text = "Processors (${selectedProcessors.size})"
    }

    @SuppressLint("UnsafeOptInUsageError")
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

                        val rotatedMat: Mat
                        if (rotationDegrees != 0) {
                            val temp = Mat()
                            when (rotationDegrees) {
                                90 -> Core.rotate(frameMat, temp, Core.ROTATE_90_CLOCKWISE)
                                180 -> Core.rotate(frameMat, temp, Core.ROTATE_180)
                                270 -> Core.rotate(frameMat, temp, Core.ROTATE_90_COUNTERCLOCKWISE)
                            }
                            rotatedMat = temp.clone() // Clone to ensure continuity
                            temp.release()
                            frameMat.release()
                        } else {
                            rotatedMat = frameMat // No rotation, just use the original.
                        }

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

                            if (isRecording) {
                                if (videoRecorder == null) {
                                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    videoFile = File(requireContext().cacheDir, "temp_video_${'$'}{timestamp}.mp4")
                                    // Use bitmap dimensions for recorder
                                    videoRecorder = VideoRecorder(displayBitmap.width, displayBitmap.height, videoFile!!)
                                    videoRecorder?.start()
                                }

                                // Draw the final display bitmap onto the recorder's surface
                                videoRecorder?.inputSurface?.let { surface ->
                                    val canvas = surface.lockCanvas(null)
                                    try {
                                        canvas.drawBitmap(displayBitmap, 0f, 0f, null)
                                    } finally {
                                        surface.unlockCanvasAndPost(canvas)
                                    }
                                }
                            }

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
                camera = cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer)
                camera?.let { setupManualFocusSlider(it) }
                updateFocusMode() // Apply initial focus mode
            } catch (exc: Exception) {
                Log.e("CameraFragment", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setupManualFocusSlider(camera: Camera) {
        val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = Camera2CameraInfo.from(camera.cameraInfo).cameraId
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val minFocusDist = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)

        if (minFocusDist == null || minFocusDist == 0f) {
            // Device does not support manual focus
            binding.focusModeButton.visibility = View.GONE
            binding.focusSlider.visibility = View.GONE
            return
        }

        // The slider value is inverted to be more intuitive (0=infinity, max=closest)
        // We map it so that slider's 0 is the closest focus, and slider's max is infinity.
        binding.focusSlider.valueFrom = 0f // Represents minFocusDist (closest)
        binding.focusSlider.valueTo = minFocusDist // Represents infinity (0.0f)

        binding.focusSlider.addOnChangeListener { _, value, _ ->
            // Invert the slider value for the API
            val focusDistance = minFocusDist - value
            setManualFocus(focusDistance)
        }
        // Set initial focus to infinity
        binding.focusSlider.value = minFocusDist
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setManualFocus(focusValue: Float) {
        camera?.let {
            val camera2Control = Camera2CameraControl.from(it.cameraControl)
            val captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusValue)
                .build()
            camera2Control.setCaptureRequestOptions(captureRequestOptions)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun updateFocusMode() {
        camera?.let {
            val camera2Control = Camera2CameraControl.from(it.cameraControl)
            val builder = CaptureRequestOptions.Builder()

            if (isManualFocus) {
                // When switching to MF, just turn off AF. The slider will handle setting the distance.
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            } else {
                // When switching to AF, set it to continuous auto focus and cancel any previous triggers.
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                it.cameraControl.cancelFocusAndMetering() // Cancel any ongoing focus operations
            }
            camera2Control.setCaptureRequestOptions(builder.build())
        }
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
                    Log.e("CameraFragment", "Photo capture failed: ${'$'}{exc.message}", exc)
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
        videoRecorder?.stop()
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
