ackage com.example.MPdetector.models.wink_detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.example.MPdetector.DetectionResult
import com.example.MPdetector.IDetector
import com.example.MPdetector.ShapeBoundary
import com.example.MPdetector.DetectionStatus // ★ DetectionStatus をインポート
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * ウィンク検出のダミー実装
 * 常に「未検出」状態で、20度回転したダミーの正五角形を返すように変更
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

    /**
     * ダミーの回転した正五角形の境界情報を生成します。
     * @param rotationDegrees 回転角度（度数法）。正の値で時計回り（右回転）。
     * @param scale 形状のスケール（0.0 から 1.0 の間）。
     * @param centerX 中心のX座標（正規化）。
     * @param centerY 中心のY座標（正規化）。
     * @return ShapeBoundary オブジェクト。
     */
    private fun createDummyPentagonBoundary(
        rotationDegrees: Float,
        scale: Float = 0.3f, // 画像の中央に表示されるように小さめのスケール
        centerX: Float = 0.5f,
        centerY: Float = 0.5f
    ): ShapeBoundary {
        val points = mutableListOf<PointF>()
        val rotationRadians = Math.toRadians(rotationDegrees.toDouble()).toFloat()
        val numVertices = 5 // 正五角形

        for (i in 0 until numVertices) {
            val angle = (2 * Math.PI * i / numVertices).toFloat() + rotationRadians
            // スケールを適用し、中心座標にオフセット
            val x = centerX + scale * cos(angle)
            val y = centerY + scale * sin(angle)
            points.add(PointF(x, y))
        }
        return ShapeBoundary(points)
    }

    override fun detect(imageBitmap: Bitmap, imageRotation: Int): DetectionResult {
        // ダミーの正五角形を生成（20度右回転）
        val dummyBoundary = createDummyPentagonBoundary(rotationDegrees = 20f)

        return DetectionResult(
            boundary = dummyBoundary,
            status = DetectionStatus.NOT_DETECTED, // ★★★ Int 型のステータスに変更 ★★★
            rotationAngle = 20f, // 形状自体の回転とは別に、結果としての回転角度も設定可能
            landmarks = null
        )
    }

    override fun detectLiveStream(imageBitmap: Bitmap, imageHeight: Int, imageWidth: Int) {
        // ダミーの正五角形を生成（20度右回転）
        val dummyBoundary = createDummyPentagonBoundary(rotationDegrees = 20f)

        val dummyResult = DetectionResult(
            boundary = dummyBoundary,
            status = DetectionStatus.NOT_DETECTED, // ★★★ Int 型のステータスに変更 ★★★
            rotationAngle = 20f,
            landmarks = null
        )
        detectorListener?.invoke(Result.success(dummyResult), imageHeight, imageWidth)
    }

    override fun close() {
        // このダミー実装では特に解放するリソースはない
    }
}