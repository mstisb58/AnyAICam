package com.example.MPdetector.models.wink_detector

import android.graphics.Bitmap
import com.example.MPdetector.ImgProcessor
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.cos
import kotlin.math.sin

class ImgAnalyzer : ImgProcessor {
    override val name: String = "Wink"
    override val saveDirectoryName: String = "WinkDetector"

    override fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean> {
        // 入力されたMatのクローンを作成し、副作用を防ぐ
        val outputFrame = frame.clone()

        // 実際のウインク検出ロジックをここに実装します。
        val center = org.opencv.core.Point(outputFrame.cols() / 2.0, outputFrame.rows() / 2.0)
        Imgproc.circle(outputFrame, center, 50, Scalar(255.0, 0.0, 0.0), 3)

        // 常にシャッターOKとし、処理後のフレームを返す
        return Pair(outputFrame, true)
    }

    override fun processFrameForSaving(frame: Bitmap): Bitmap {
        // Just return the original bitmap for saving
        return frame
    }
}
