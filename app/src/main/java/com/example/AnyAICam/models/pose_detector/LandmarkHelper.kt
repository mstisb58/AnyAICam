package com.example.AnyAICam.models.pose_detector

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.util.Optional

object LandmarkHelper {
    private val LANDMARK_COLOR = Scalar(0.0, 255.0, 0.0) // Green
    private val CONNECTION_COLOR = Scalar(255.0, 0.0, 0.0) // Blue

    private val POSE_CONNECTIONS = listOf(
        Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 7), Pair(0, 4), Pair(4, 5),
        Pair(5, 6), Pair(6, 8), Pair(9, 10), Pair(11, 12), Pair(11, 13),
        Pair(13, 15), Pair(15, 17), Pair(15, 19), Pair(15, 21), Pair(17, 19),
        Pair(12, 14), Pair(14, 16), Pair(16, 18), Pair(16, 20), Pair(16, 22),
        Pair(18, 20), Pair(11, 23), Pair(12, 24), Pair(23, 24), Pair(23, 25),
        Pair(24, 26), Pair(25, 27), Pair(26, 28), Pair(27, 29), Pair(28, 30),
        Pair(29, 31), Pair(30, 32), Pair(27, 31), Pair(28, 32)
    )

    fun drawPoseOnMat(landmarks: List<NormalizedLandmark>, outputMat: Mat) {
        val imageW = outputMat.width()
        val imageH = outputMat.height()

        for (connection in POSE_CONNECTIONS) {
            val start = landmarks[connection.first]
            val end = landmarks[connection.second]
            if (start.visibility().orElse(0.0f) > 0.5 && end.visibility().orElse(0.0f) > 0.5) {
                val startPoint = Point((start.x() * imageW).toDouble(), (start.y() * imageH).toDouble())
                val endPoint = Point((end.x() * imageW).toDouble(), (end.y() * imageH).toDouble())
                Imgproc.line(outputMat, startPoint, endPoint, CONNECTION_COLOR, 2)
            }
        }

        for (landmark in landmarks) {
            if (landmark.visibility().orElse(0.0f) > 0.5) {
                val point = Point((landmark.x() * imageW).toDouble(), (landmark.y() * imageH).toDouble())
                Imgproc.circle(outputMat, point, 5, LANDMARK_COLOR, -1)
            }
        }
    }

    fun getCsvHeader(): String {
        val header = StringBuilder()
        header.append("frame_index,")
        // 33 landmarks for PoseLandmarker
        for (i in 0 until 33) {
            header.append("landmark_${i}_x,landmark_${i}_y,landmark_${i}_z,landmark_${i}_visibility,")
        }
        return header.removeSuffix(",").toString()
    }

    fun landmarksToCsvRow(landmarks: List<NormalizedLandmark>, frameIndex: Int): String {
        val row = StringBuilder()
        row.append("$frameIndex,")
        if (landmarks.isNotEmpty()) {
            for (landmark in landmarks) {
                row.append("${landmark.x()},${landmark.y()},${landmark.z()},${landmark.visibility().orElse(0.0f)},")
            }
        }
        return row.removeSuffix(",").toString()
    }
}
