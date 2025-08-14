// file: app/src/main/java/com/example/MPdetector/models/tangue_detector/TangueDetector.kt
package com.example.MPdetector.models.tangue_detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.example.MPdetector.DetectionResult
import com.example.MPdetector.IDetector
import com.google.mediapipe.tasks.vision.core.RunningMode

/**
 * 舌検出のダミー実装
 * 常に画面中央に固定の四角と「検出中」というステータスを返す
 */
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
        detectorListener?.invoke(Result.success(createDummyResult()), imageHeight, imageWidth)
    }

    override fun close() {}

    private fun createDummyResult(): DetectionResult {
        val boundingBox = RectF(0.4f, 0.4f, 0.6f, 0.6f)
        return DetectionResult(
            boundingBox = boundingBox,
            status = "検出中",
            landmarks = null
        )
    }
}