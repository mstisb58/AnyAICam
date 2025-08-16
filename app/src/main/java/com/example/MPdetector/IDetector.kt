// file: app/src/main/java/com/example/MPdetector/IDetector.kt
package com.example.MPdetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult


/**
 * 検出器のインターフェース
 * 全ての検出ロジック（WinkDetectorなど）はこのインターフェースを実装する
 */
interface IDetector {
    val name: String

    fun setup(
        context: Context,
        runningMode: RunningMode = RunningMode.IMAGE,
        // リスナーが画像の高さと幅を受け取れるように変更
        listener: ((result: Result<DetectionResult>, height: Int, width: Int) -> Unit)? = null
    )

    fun detect(imageBitmap: Bitmap, imageRotation: Int): DetectionResult?

    // メソッドが画像の高さと幅を受け取れるように変更
    fun detectLiveStream(imageBitmap: Bitmap, imageHeight: Int, imageWidth: Int)

    fun close()
}

/**
 * モデル名に応じて適切なIDetectorインスタンスを生成するファクトリ
 */
object DetectorFactory {
    fun create(modelName: String): IDetector? {
        return when (modelName) {
            "tangue_detector" -> com.example.MPdetector.models.tangue_detector.TangueDetector()
            "wink_detector" -> com.example.MPdetector.models.wink_detector.WinkDetector()
            else -> null
        }
    }
}