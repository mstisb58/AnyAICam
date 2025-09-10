package com.example.AnyAICam.models.face_detector

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

object LandmarkHelper {
    private val LANDMARK_COLOR = Scalar(0.0, 255.0, 0.0) // Green

    fun drawFaceLandmarksOnMat(landmarks: List<NormalizedLandmark>, outputMat: Mat) {
        val imageW = outputMat.width()
        val imageH = outputMat.height()

        for (landmark in landmarks) {
            val point = Point((landmark.x() * imageW).toDouble(), (landmark.y() * imageH).toDouble())
            Imgproc.circle(outputMat, point, 2, LANDMARK_COLOR, -1)
        }
    }

    fun getCsvHeader(): String {
        val header = StringBuilder()
        // Mediapipe FaceLandmarker has 478 landmarks with blendshapes
        for (i in 0 until 478) {
            header.append("landmark_${i}_x,landmark_${i}_y,landmark_${i}_z,")
        }
        return header.removeSuffix(",").toString()
    }

    fun landmarksToCsvRow(landmarks: List<NormalizedLandmark>): String {
        val row = StringBuilder()
        if (landmarks.isNotEmpty()) {
            for (landmark in landmarks) {
                row.append("${landmark.x()},${landmark.y()},${landmark.z()},")
            }
        }
        return row.removeSuffix(",").toString()
    }
}