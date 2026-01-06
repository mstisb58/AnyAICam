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
    override val name: String = "Face"
    override val saveDirectoryName: String = "FaceDetector"
    override var isDummyPreviewEnabled: Boolean = false

    override var showLandmarks: Boolean = true
    override var saveLandmarks: Boolean = false
    var showNumbers: Boolean = false

    private var faceLandmarker: FaceLandmarker? = null
    private var lastLandmarks: List<NormalizedLandmark>? = null

    override fun setup(context: Context) {
        if (faceLandmarker == null) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("mediapipe/face_landmarker.task")
                    .build()
                val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumFaces(1)
                    .build()
                faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            } catch (e: Exception) {
                Log.e("FaceImgAnalyzer", "Failed to initialize FaceLandmarker", e)
            }
        }
    }

    private fun detect(frame: Mat): List<NormalizedLandmark>? {
        if (faceLandmarker == null) return null
        val bmp = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(frame, bmp)
        val mpImage = BitmapImageBuilder(bmp).build()
        val results = faceLandmarker?.detect(mpImage)
        lastLandmarks = results?.faceLandmarks()?.getOrNull(0)
        return lastLandmarks
    }

    override fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean> {
        val outputFrame = frame.clone()
        val landmarks = detect(outputFrame)

        if (showLandmarks && landmarks != null) {
            LandmarkHelper.drawFaceLandmarksOnMat(landmarks, outputFrame, showNumbers)
        }

        return Pair(outputFrame, true)
    }

    override fun processFrameForSaving(frame: Bitmap): Bitmap {
        if (faceLandmarker == null) return frame

        val outputMat = Mat()
        Utils.bitmapToMat(frame, outputMat)

        // Re-detect landmarks for saving.
        val landmarks = detect(outputMat)

        if (showLandmarks && landmarks != null) {
            LandmarkHelper.drawFaceLandmarksOnMat(landmarks, outputMat, showNumbers)
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

