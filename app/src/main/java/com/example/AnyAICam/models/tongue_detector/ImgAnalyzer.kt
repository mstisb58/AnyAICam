// models/tongue_detector/ImgAnalyzer.kt
package com.example.AnyAICam.models.tongue_detector

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
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

class ImgAnalyzer : ImgProcessor {
    override val name: String = "Tongue"
    override val saveDirectoryName: String = "TongueDetector"

    private var faceLandmarker: FaceLandmarker? = null
    private var tfliteHelper: TfliteHelper? = null // TfliteHelperを追加

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
        // TfliteHelperを初期化
        if (tfliteHelper == null) {
            tfliteHelper = TfliteHelper(context)
        }
    }

    override fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean> {
        if (faceLandmarker == null) return Pair(frame, false)
        val outputFrame = frame.clone()

        val bmp = Bitmap.createBitmap(outputFrame.cols(), frame.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputFrame, bmp)
        val mpImage = BitmapImageBuilder(bmp).build()
        val results = faceLandmarker?.detect(mpImage)

        var status = false // デフォルトはfalse
        if (results != null && results.faceLandmarks().isNotEmpty()) {
            for (landmarks in results.faceLandmarks()) {
                val info = calculateTongueSquare(landmarks, outputFrame.cols(), outputFrame.rows())

                val rotMat = Imgproc.getRotationMatrix2D(info.center, info.angleDegrees.toDouble(), 1.0)
                val rotatedMat = Mat()
                Imgproc.warpAffine(frame, rotatedMat, rotMat, frame.size())
                rotMat.release()

                val cropX = (info.center.x - info.length / 2).toInt()
                val cropY = info.center.y.toInt()
                val cropSize = info.length.toInt()

                var result = -1 // 初期値
                if (cropX >= 0 && cropY >= 0 && cropX + cropSize <= rotatedMat.width() && cropY + cropSize <= rotatedMat.height()) {
                    val croppedImg = Mat(rotatedMat, Rect(cropX, cropY, cropSize, cropSize))
                    if (croppedImg.width() > 0 && croppedImg.height() > 0) {
                        // stateDiscriminatorをtfliteHelperに置き換え
                        result = stateDiscriminator(croppedImg)
                    }
                    croppedImg.release()
                }
                rotatedMat.release()

                status = result == 0 // resultが0の場合のみstatusがtrue

                // Draw the box on the output frame
                val color = if (status) Scalar(0.0, 255.0, 0.0) else Scalar(255.0, 0.0, 0.0) // Green for true, Blue for false
                val points = MatOfPoint(*info.points.toTypedArray())
                Imgproc.polylines(outputFrame, listOf(points), true, color, 2)

                // Display the result value for debugging
                val text = "Result: $result"
                val textOrigin = Point(info.points.minOf { it.x }, info.points.minOf { it.y } - 10)
                Imgproc.putText(
                    outputFrame,
                    text,
                    textOrigin,
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    1.0,
                    Scalar(0.0, 255.0, 255.0),
                    2
                )
            }
        }
        return Pair(outputFrame, status)
    }

    // tfliteモデルで判定するように変更
    private fun stateDiscriminator(croppedImg: Mat): Int {
        if (tfliteHelper == null) return -1 // Helperが初期化されていない場合

        // MatをBitmapに変換
        val bmp = Bitmap.createBitmap(croppedImg.cols(), croppedImg.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(croppedImg, bmp)

        // TFLiteモデルで分類
        return tfliteHelper!!.classify(bmp)
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