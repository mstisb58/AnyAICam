package com.example.AnyAICam.models.pose_detector

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.AnyAICam.ImgProcessor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import org.opencv.android.Utils
import org.opencv.core.Mat

class ImgAnalyzer : ImgProcessor {
    override val name: String = "Pose"
    override val saveDirectoryName: String = "PoseDetector"
    override var isDummyPreviewEnabled: Boolean = false

    override var showLandmarks: Boolean = true
    override var saveLandmarks: Boolean = false

    private var poseLandmarker: PoseLandmarker? = null
    private var lastLandmarks: List<NormalizedLandmark>? = null

    override fun setup(context: Context) {
        if (poseLandmarker == null) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("mediapipe/pose_landmarker_full.task")
                    .build()
                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumPoses(1)
                    .build()
                poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            } catch (e: Exception) {
                Log.e("PoseImgAnalyzer", "Failed to initialize PoseLandmarker", e)
            }
        }
    }

    private fun detect(frame: Mat): List<NormalizedLandmark>? {
        if (poseLandmarker == null) return null
        val bmp = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(frame, bmp)
        val mpImage = BitmapImageBuilder(bmp).build()
        val results = poseLandmarker?.detect(mpImage)
        lastLandmarks = results?.landmarks()?.getOrNull(0)
        return lastLandmarks
    }

    override fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean> {
        val outputFrame = frame.clone()
        val landmarks = detect(outputFrame)

        if (showLandmarks && landmarks != null) {
            LandmarkHelper.drawPoseOnMat(landmarks, outputFrame)
        }

        return Pair(outputFrame, true)
    }

    override fun processFrameForSaving(frame: Bitmap): Bitmap {
        if (poseLandmarker == null) return frame

        val outputMat = Mat()
        Utils.bitmapToMat(frame, outputMat)

        val landmarks = detect(outputMat)

        if (showLandmarks && landmarks != null) {
            LandmarkHelper.drawPoseOnMat(landmarks, outputMat)
        }

        val resultBitmap = Bitmap.createBitmap(outputMat.cols(), outputMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputMat, resultBitmap)
        outputMat.release()

        return resultBitmap
    }

    override fun getLandmarksForCsv(): String? {
        return lastLandmarks?.let { LandmarkHelper.landmarksToCsvRow(it) }
    }

    override fun getCsvHeader(): String {
        return LandmarkHelper.getCsvHeader()
    }
}
