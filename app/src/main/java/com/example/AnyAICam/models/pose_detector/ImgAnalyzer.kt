package com.example.AnyAICam.models.pose_detector

import android.content.Context
import android.graphics.Bitmap
import com.example.AnyAICam.ImgProcessor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import org.opencv.android.Utils
import org.opencv.core.Mat

class ImgAnalyzer : ImgProcessor {
    override val name: String = "pose_detector"
    override val saveDirectoryName: String = "pose_detector_results"

    private var poseLandmarker: PoseLandmarker? = null
    private var lastLandmarks: List<NormalizedLandmark>? = null
    var isSaveLandmarksEnabled: Boolean = false

    override fun setup(context: Context) {
        if (poseLandmarker != null) return
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("mediapipe/pose_landmarker_full.task")
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumPoses(1)
            .build()
        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
    }

    override fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean> {
        val landmarks = detectLandmarks(frame)
        landmarks?.let {
            LandmarkHelper.drawPoseOnMat(it, frame)
        }
        return Pair(frame, landmarks != null)
    }

    override fun processFrameForSaving(frame: Bitmap): Bitmap {
        val landmarks = detectLandmarks(frame)
        landmarks?.let {
            val mat = Mat()
            Utils.bitmapToMat(frame, mat)
            LandmarkHelper.drawPoseOnMat(it, mat)
            val resultBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, resultBitmap)
            return resultBitmap
        }
        return frame // Return original frame
    }

    private fun detectLandmarks(frame: Any): List<NormalizedLandmark>? {
        val landmarker = poseLandmarker ?: return null
        val mpImage = when (frame) {
            is Bitmap -> BitmapImageBuilder(frame).build()
            is Mat -> {
                val bmp = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(frame, bmp)
                BitmapImageBuilder(bmp).build()
            }
            else -> return null
        }
        val detectionResult: PoseLandmarkerResult? = landmarker.detect(mpImage)
        val result = detectionResult?.landmarks()?.firstOrNull()
        lastLandmarks = result
        return result
    }

    override fun getReportCsv(): String? {
        if (!isSaveLandmarksEnabled) return null
        val landmarks = lastLandmarks ?: return null
        val header = LandmarkHelper.getCsvHeader()
        val row = LandmarkHelper.landmarksToCsvRow(landmarks, 0) // Foto mode uses index 0
        return "$header\n$row"
    }
}
