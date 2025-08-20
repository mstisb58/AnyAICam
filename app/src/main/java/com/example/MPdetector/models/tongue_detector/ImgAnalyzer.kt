// models/tongue_detector/ImgAnalyzer.kt
package com.example.MPdetector.models.tongue_detector

import android.content.Context
import android.graphics.Bitmap
import com.example.MPdetector.ImgProcessor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

class ImgAnalyzer : ImgProcessor {
    override val name: String = "Tongue"
    override val saveDirectoryName: String = "TongueDetector" // このプロセッサ用の保存先フォルダ名

    private var faceLandmarker: FaceLandmarker? = null

    private val MOUTH_LEFT_CORNER = 61
    private val MOUTH_RIGHT_CORNER = 291

    private data class TongueSquareInfo(
        val points: List<Point>,
        val center: Point,
        val angleDegrees: Float,
        val length: Float
    )

    // ImgProcessorインターフェースのsetupメソッドをオーバーライド
    override fun setup(context: Context) {
        if (faceLandmarker == null) {
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
    }

    override fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean> {
        if (faceLandmarker == null) return Pair(frame, false)
        val outputFrame = frame.clone()

        val bmp = Bitmap.createBitmap(outputFrame.cols(), frame.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputFrame, bmp)
        val mpImage = BitmapImageBuilder(bmp).build()
        val results = faceLandmarker?.detect(mpImage)

        var status = false
        if (results != null && results.faceLandmarks().isNotEmpty()) {
            for (landmarks in results.faceLandmarks()) {
                val info = calculateTongueSquare(landmarks, outputFrame.cols(), outputFrame.rows())
                val points = MatOfPoint(*info.points.toTypedArray())
                Imgproc.polylines(outputFrame, listOf(points), true, Scalar(0.0, 255.0, 255.0), 2)
                status = true
            }
        }
        return Pair(outputFrame, status)
    }

    override fun processFrameForSaving(frame: Bitmap): Bitmap {
        if (faceLandmarker == null) return frame

        val imageWidth = frame.width
        val imageHeight = frame.height
        val mpImage = BitmapImageBuilder(frame).build()
        val results = faceLandmarker?.detect(mpImage)

        if (results != null && results.faceLandmarks().isNotEmpty()) {
            val landmarks = results.faceLandmarks()[0]
            val info = calculateTongueSquare(landmarks, imageWidth, imageHeight)

            val originalMat = Mat()
            Utils.bitmapToMat(frame, originalMat)

            val rotMat = Imgproc.getRotationMatrix2D(info.center, info.angleDegrees.toDouble(), 1.0)
            val rotatedMat = Mat()
            Imgproc.warpAffine(originalMat, rotatedMat, rotMat, Size(imageWidth.toDouble(), imageHeight.toDouble()))

            val cropX = (info.center.x - info.length / 2).toInt()
            val cropY = info.center.y.toInt()
            val cropSize = info.length.toInt()

            if (cropX >= 0 && cropY >= 0 && cropX + cropSize <= rotatedMat.width() && cropY + cropSize <= rotatedMat.height()) {
                val croppedMat = Mat(rotatedMat, Rect(cropX, cropY, cropSize, cropSize))
                val resultBitmap = Bitmap.createBitmap(croppedMat.cols(), croppedMat.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(croppedMat, resultBitmap)
                return resultBitmap
            }
        }
        return frame
    }

    private fun calculateTongueSquare(
        faceLandmarks: List<NormalizedLandmark>,
        imageW: Int,
        imageH: Int
    ): TongueSquareInfo {
        val leftCorner = faceLandmarks[MOUTH_LEFT_CORNER]
        val rightCorner = faceLandmarks[MOUTH_RIGHT_CORNER]

        val leftCornerPx = Point((leftCorner.x() * imageW).toDouble(), (leftCorner.y() * imageH).toDouble())
        val rightCornerPx = Point((rightCorner.x() * imageW).toDouble(), (rightCorner.y() * imageH).toDouble())

        val dx = rightCornerPx.x - leftCornerPx.x
        val dy = rightCornerPx.y - leftCornerPx.y

        val angleRad = atan2(dy, dx)
        val length = sqrt(dx * dx + dy * dy).toFloat()

        val p1 = leftCornerPx
        val p2 = rightCornerPx
        val p3 = Point(rightCornerPx.x - length * sin(angleRad), rightCornerPx.y + length * cos(angleRad))
        val p4 = Point(leftCornerPx.x - length * sin(angleRad), leftCornerPx.y + length * cos(angleRad))

        return TongueSquareInfo(
            points = listOf(p1, p2, p3, p4),
            center = Point((leftCornerPx.x + rightCornerPx.x) / 2, (leftCornerPx.y + rightCornerPx.y) / 2),
            angleDegrees = Math.toDegrees(angleRad).toFloat(),
            length = length
        )
    }
}
