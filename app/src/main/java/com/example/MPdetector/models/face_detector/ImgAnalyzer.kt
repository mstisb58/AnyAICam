// models/face_detector/ImgAnalyzer.kt
package com.example.MPdetector.models.face_detector

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.MPdetector.ImgProcessor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

class ImgAnalyzer : ImgProcessor {
    override val name: String = "Face"
    override val saveDirectoryName: String = "FaceDetector"

    private var faceLandmarker: FaceLandmarker? = null
    private val LANDMARK_COLOR = Scalar(0.0, 255.0, 0.0) // Green

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

    override fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean> {
        if (faceLandmarker == null) return Pair(frame, false)

        val outputFrame = frame.clone()
        val bmp = Bitmap.createBitmap(outputFrame.cols(), frame.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputFrame, bmp)
        val mpImage = BitmapImageBuilder(bmp).build()
        val results = faceLandmarker?.detect(mpImage)

        if (results != null && results.faceLandmarks().isNotEmpty()) {
            for (landmarks in results.faceLandmarks()) {
                drawFaceLandmarksOnMat(landmarks, outputFrame)
            }
        }

        return Pair(outputFrame, true)
    }

    override fun processFrameForSaving(frame: Bitmap): Bitmap {
        if (faceLandmarker == null) return frame

        val mpImage = BitmapImageBuilder(frame).build()
        val results = faceLandmarker?.detect(mpImage)

        val outputMat = Mat()
        Utils.bitmapToMat(frame, outputMat)

        if (results != null && results.faceLandmarks().isNotEmpty()) {
            for (landmarks in results.faceLandmarks()) {
                drawFaceLandmarksOnMat(landmarks, outputMat)
            }
        }

        val resultBitmap = Bitmap.createBitmap(outputMat.cols(), outputMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputMat, resultBitmap)
        outputMat.release()

        return resultBitmap
    }

    private fun drawFaceLandmarksOnMat(landmarks: List<NormalizedLandmark>, outputMat: Mat) {
        val imageW = outputMat.width()
        val imageH = outputMat.height()

        for (landmark in landmarks) {
            val point = Point((landmark.x() * imageW).toDouble(), (landmark.y() * imageH).toDouble())
            Imgproc.circle(outputMat, point, 2, LANDMARK_COLOR, -1)
        }
    }
}
