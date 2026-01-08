package com.example.AnyAICam.models.face_detector

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.AnyAICam.ImgProcessor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import org.opencv.android.Utils
import org.opencv.core.Mat

class ImgAnalyzer : ImgProcessor {
    override val name: String = "face_detector"
    override val saveDirectoryName: String = "face_detector_results"

    private var faceLandmarker: FaceLandmarker? = null
    private var lastLandmarks: List<NormalizedLandmark>? = null
    var isSaveLandmarksEnabled: Boolean = false

    override fun setup(context: Context) {
        if (faceLandmarker != null) return
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("mediapipe/face_landmarker.task")
            .build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .build()
        faceLandmarker = FaceLandmarker.createFromOptions(context, options)
    }

    override fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean> {
        val landmarks = detectLandmarks(frame)
        landmarks?.let {
            LandmarkHelper.drawFaceLandmarksOnMat(it, frame, false)
        }
        return Pair(frame, landmarks != null)
    }

    override fun processFrameForSaving(frame: Bitmap): Bitmap {
        val landmarks = detectLandmarks(frame)
        landmarks?.let {
            val mat = Mat()
            Utils.bitmapToMat(frame, mat)
            LandmarkHelper.drawFaceLandmarksOnMat(it, mat, false)
            val resultBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, resultBitmap)
            return resultBitmap
        }
        return frame
    }

    private fun detectLandmarks(frame: Any): List<NormalizedLandmark>? {
        val landmarker = faceLandmarker ?: return null
        val mpImage = when (frame) {
            is Bitmap -> BitmapImageBuilder(frame).build()
            is Mat -> {
                val bmp = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(frame, bmp)
                BitmapImageBuilder(bmp).build()
            }
            else -> return null
        }

        val result = landmarker.detect(mpImage)?.faceLandmarks()?.getOrNull(0)
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

