// models/tongue_detector/ImgAnalyzer.kt
package com.example.MPdetector.models.tongue_detector

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
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

class ImgAnalyzer : ImgProcessor {
    override val name: String = "Tongue"
    override val saveDirectoryName: String = "TongueDetector"

    private var faceLandmarker: FaceLandmarker? = null

    private val MOUTH_LEFT_CORNER = 61
    private val MOUTH_RIGHT_CORNER = 291

    private data class TongueSquareInfo(
        val points: List<Point>,
        val center: Point,
        val angleDegrees: Float,
        val length: Float
    )

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
                Log.e("TongueImgAnalyzer", "Failed to initialize FaceLandmarker", e)
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

        var result = 1 // Default to 1 (not found)
        if (results != null && results.faceLandmarks().isNotEmpty()) {
            for (landmarks in results.faceLandmarks()) {
                val info = calculateTongueSquare(landmarks, outputFrame.cols(), outputFrame.rows())

                val rotMat = Imgproc.getRotationMatrix2D(info.center, info.angleDegrees.toDouble(), 1.0)
                val rotatedMat = Mat()
                // Use the original frame for analysis, not the cloned outputFrame
                Imgproc.warpAffine(frame, rotatedMat, rotMat, frame.size())
                rotMat.release()

                val cropX = (info.center.x - info.length / 2).toInt()
                val cropY = info.center.y.toInt()
                val cropSize = info.length.toInt()

                if (cropX >= 0 && cropY >= 0 && cropX + cropSize <= rotatedMat.width() && cropY + cropSize <= rotatedMat.height()) {
                    val croppedImg = Mat(rotatedMat, Rect(cropX, cropY, cropSize, cropSize))
                    if (croppedImg.width() > 0 && croppedImg.height() > 0) {
                        result = stateDiscriminator(croppedImg)
                    }
                    croppedImg.release()
                }

                rotatedMat.release()

                // Draw the box on the output frame
                val status = result == 0
                val color = if (status) Scalar(0.0, 0.0, 0.0) else Scalar(255.0, 255.0, 255.0) // Green for true, White for false
                val points = MatOfPoint(*info.points.toTypedArray())
                Imgproc.polylines(outputFrame, listOf(points), true, color, 1)

                // Display the result value for debugging
                val text = "Result: $result"
                // Place text near the top-left corner of the box
                val textOrigin = Point(info.points.minOf { it.x }, info.points.minOf { it.y } - 10)
                Imgproc.putText(
                    outputFrame,
                    text,
                    textOrigin,
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    1.0, // Font scale
                    Scalar(0.0, 255.0, 255.0), // Yellow color
                    2 // Thickness
                )
            }
        }
        val status = true
        return Pair(outputFrame, status)
    }

    private fun stateDiscriminator(croppedImg: Mat): Int {
        val hsvMat = Mat()
        Imgproc.cvtColor(croppedImg, hsvMat, Imgproc.COLOR_BGR2HSV)

        // Define lower and upper bounds for reddish/pinkish tones (tongue color) in HSV
        // OpenCV HSV ranges: H: 0-180, S: 0-255, V: 0-255
        // Red can wrap around 0 and 180, so we check two ranges
        // 赤は0付近なので0の前後で設定するからしきい値はふたつ
        val lowerRed1 = Scalar(0.0, 40.0, 50.0)
        val upperRed1 = Scalar(50.0, 255.0, 255.0)
        val lowerRed2 = Scalar(140.0, 40.0, 50.0)
        val upperRed2 = Scalar(180.0, 255.0, 255.0)

        val mask1 = Mat()
        val mask2 = Mat()
        Core.inRange(hsvMat, lowerRed1, upperRed1, mask1)
        Core.inRange(hsvMat, lowerRed2, upperRed2, mask2)

        val combinedMask = Mat()
        Core.add(mask1, mask2, combinedMask)

        // Clean up the mask a bit with morphological operations
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(combinedMask, combinedMask, Imgproc.MORPH_OPEN, kernel)
        Imgproc.morphologyEx(combinedMask, combinedMask, Imgproc.MORPH_CLOSE, kernel)
        kernel.release()

        val matchingPixels = Core.countNonZero(combinedMask)
        val totalPixels = croppedImg.rows() * croppedImg.cols()

        hsvMat.release()
        mask1.release()
        mask2.release()
        combinedMask.release()

        if (totalPixels == 0) return 1 // Avoid division by zero, return "not found"

        val percentage = (matchingPixels.toDouble() / totalPixels) * 100.0

        return if (percentage >= 50.0) 0 else 1
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
            rotMat.release()

            val cropX = (info.center.x - info.length / 2).toInt()
            val cropY = info.center.y.toInt()
            val cropSize = info.length.toInt()

            if (cropX >= 0 && cropY >= 0 && cropX + cropSize <= rotatedMat.width() && cropY + cropSize <= rotatedMat.height()) {
                val croppedMat = Mat(rotatedMat, Rect(cropX, cropY, cropSize, cropSize))
                val resultBitmap = Bitmap.createBitmap(croppedMat.cols(), croppedMat.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(croppedMat, resultBitmap)

                originalMat.release()
                rotatedMat.release()
                croppedMat.release()
                return resultBitmap
            }
            originalMat.release()
            rotatedMat.release()
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