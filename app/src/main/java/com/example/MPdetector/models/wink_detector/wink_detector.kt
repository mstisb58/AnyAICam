// file: app/src/main/java/com/example/MPdetector/models/wink_detector/WinkDetector.kt
package com.example.MPdetector.models.wink_detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.example.MPdetector.DetectionResult
import com.example.MPdetector.IDetector
import com.google.mediapipe.tasks.vision.core.RunningMode

/**
 * ウィンク検出のダミー実装
 * ★修正点: 常に「未検出」状態を返すように変更
 */
class WinkDetector : IDetector {
    override val name: String = "wink_detector"
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
        detectorListener?.invoke(Result.success(createDummyResult()), imageHeight, imageWidth)
    }

    override fun close() {}

    private fun createDummyResult(): DetectionResult {
        // 枠は表示させたいので、座標はそのまま残す
        val boundingBox = RectF(0.2f, 0.2f, 0.8f, 0.8f)
        return DetectionResult(
            boundingBox = boundingBox,
            status = "未検出", // ★修正点: ステータス文字列を変更
            landmarks = null
        )
    }
}