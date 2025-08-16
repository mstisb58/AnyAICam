// file: app/src/main/java/com/example/MPdetector/models/tangue_detector/tangue_detector.kt
package com.example.MPdetector.models.tangue_detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.example.MPdetector.DetectionResult
import com.example.MPdetector.IDetector
import com.example.MPdetector.ShapeBoundary
import com.example.MPdetector.DetectionStatus // ★ DetectionStatus をインポート
import com.google.mediapipe.tasks.vision.core.RunningMode
import android.util.Log

class TangueDetector : IDetector {
    override val name: String = "tangue_detector"

    private var detectorListener: ((result: Result<DetectionResult>, height: Int, width: Int) -> Unit)? = null

    override fun setup(
        context: Context,
        runningMode: RunningMode,
        listener: ((result: Result<DetectionResult>, height: Int, width: Int) -> Unit)?
    ) {
        this.detectorListener = listener
    }

    override fun detect(imageBitmap: Bitmap, imageRotation: Int): DetectionResult {
        return createDummyResult()
    }



    override fun detectLiveStream(imageBitmap: Bitmap, imageHeight: Int, imageWidth: Int) {
        // ダミーデータをリアルタイムに送り続けると仮定
        val dummyResult = createDummyResult()
        // ここでステータスを周期的に変更するロジックも追加可能 (例: 0, 1, 2 を交互に)
        // 例: val currentStatus = (System.currentTimeMillis() / 2000 % 3).toInt()
        // val updatedResult = dummyResult.copy(status = currentStatus)
        // detectorListener?.invoke(Result.success(updatedResult), imageHeight, imageWidth)

        detectorListener?.invoke(Result.success(dummyResult), imageHeight, imageWidth)
    }

    override fun close() {}

    private fun createDummyResult(): DetectionResult {
        val centerX = 0.5f
        val centerY = 0.5f
        val actualHalfDiagonal = 0.1f

        val diamondShapePoints = listOf(
            PointF(centerX, centerY - actualHalfDiagonal),
            PointF(centerX + actualHalfDiagonal, centerY),
            PointF(centerX, centerY + actualHalfDiagonal),
            PointF(centerX - actualHalfDiagonal, centerY)
        )
        // Log.d("TangueDetector", "Outputting Boundary Points: $diamondShapePoints") // 必要に応じて

        val reportedRotationDegrees = 30f

        // ダミーのステータス (例: "検出中")
        val currentDummyStatus = DetectionStatus.DETECTING // Int 型のステータスを使用

        return DetectionResult(
            boundary = ShapeBoundary(diamondShapePoints),
            status = currentDummyStatus, // ★ Int 型のステータスを設定
            rotationAngle = reportedRotationDegrees,
            landmarks = null
        )
    }
}