// このファイルは pose_detector ディレクトリに配置されることを想定
package com.example.AnyAICam.models.pose_detector // パッケージ名も環境に合わせて変更してください

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.AnyAICam.ImgProcessor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.PoseLandmarkerOptions
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

// クラス名は変更せず ImgAnalyzer のまま
class ImgAnalyzer : ImgProcessor {
    // 内部的な名前と保存ディレクトリ名のみ変更
    override val name: String = "Pose"
    override val saveDirectoryName: String = "PoseDetector"
    override var isDummyPreviewEnabled: Boolean = false

    private var poseLandmarker: PoseLandmarker? = null

    // 姿勢描画用の色定義
    private val LANDMARK_COLOR = Scalar(0.0, 255.0, 0.0) // 緑 (ランドマーク/ボール)
    private val CONNECTION_COLOR = Scalar(255.0, 0.0, 0.0) // 青 (コネクション/スティック)

    override fun setup(context: Context) {
        if (poseLandmarker == null) {
            try {
                // 注意: 'pose_landmarker_full.task' が assets/mediapipe フォルダに必要です
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("mediapipe/pose_landmarker_full.task")
                    .build()
                val options = PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumPoses(1)
                    .build()
                poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            } catch (e: Exception) {
                Log.e("PoseImgAnalyzer", "Failed to initialize PoseLandmarker", e)
            }
        }
    }

    override fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean> {
        if (poseLandmarker == null) return Pair(frame, true) // 初期化失敗時もtrueを返す

        val outputFrame = frame.clone()
        val bmp = Bitmap.createBitmap(outputFrame.cols(), frame.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputFrame, bmp)
        val mpImage = BitmapImageBuilder(bmp).build()

        // 姿勢を検出
        val results = poseLandmarker?.detect(mpImage)

        // 検出結果（ランドマークとコネクション）をフレームに描画
        if (results != null && results.landmarks().isNotEmpty()) {
            for (landmarks in results.landmarks()) {
                drawPoseOnMat(landmarks, outputFrame)
            }
        }

        // ご要望通り、statusは常にtrue
        return Pair(outputFrame, true)
    }

    override fun processFrameForSaving(frame: Bitmap): Bitmap {
        if (poseLandmarker == null) return frame

        val mpImage = BitmapImageBuilder(frame).build()

        // 姿勢を検出
        val results = poseLandmarker?.detect(mpImage)

        // 描画用にBitmapからMatへ変換
        val outputMat = Mat()
        Utils.bitmapToMat(frame, outputMat)

        // 検出された場合、クロップせずに全体に描画
        if (results != null && results.landmarks().isNotEmpty()) {
            for (landmarks in results.landmarks()) {
                drawPoseOnMat(landmarks, outputMat)
            }
        }

        // 描画後のMatをBitmapに戻して返す
        val resultBitmap = Bitmap.createBitmap(outputMat.cols(), outputMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputMat, resultBitmap)
        outputMat.release()

        return resultBitmap
    }

    /**
     * 姿勢のランドマークとコネクションをMatオブジェクトに描画するヘルパー関数
 */
    private val POSE_CONNECTIONS = listOf(
    Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 7), Pair(0, 4), Pair(4, 5),
    Pair(5, 6), Pair(6, 8), Pair(9, 10), Pair(11, 12), Pair(11, 13),
    Pair(13, 15), Pair(15, 17), Pair(15, 19), Pair(15, 21), Pair(17, 19),
    Pair(12, 14), Pair(14, 16), Pair(16, 18), Pair(16, 20), Pair(16, 22),
    Pair(18, 20), Pair(11, 23), Pair(12, 24), Pair(23, 24), Pair(23, 25),
    Pair(24, 26), Pair(25, 27), Pair(26, 28), Pair(27, 29), Pair(28, 30),
    Pair(29, 31), Pair(30, 32), Pair(27, 31), Pair(28, 32)
    )
    private fun drawPoseOnMat(landmarks: List<NormalizedLandmark>, outputMat: Mat) {
        val imageW = outputMat.width()
        val imageH = outputMat.height()

        // コネクション（スティック）を描画
             for (connection in POSE_CONNECTIONS) {
                 val start = landmarks[connection.first]
                 val end = landmarks[connection.second]

            // 座標が画像範囲内にあることを確認してから描画 (より安全)
            if (start.visibility().orElse(0.0f) > 0.5 && end.visibility().orElse(0.0f) > 0.5) {
                val startPoint = Point((start.x() * imageW).toDouble(), (start.y() * imageH).toDouble())
                val endPoint = Point((end.x() * imageW).toDouble(), (end.y() * imageH).toDouble())
                Imgproc.line(outputMat, startPoint, endPoint, CONNECTION_COLOR, 2)
            }
        }

        // ランドマーク（ボール）を描画
        for (landmark in landmarks) {
            if (landmark.visibility().orElse(0.0f) > 0.5) {
                val point = Point((landmark.x() * imageW).toDouble(), (landmark.y() * imageH).toDouble())
                Imgproc.circle(outputMat, point, 5, LANDMARK_COLOR, -1) // -1で塗りつぶし
            }
        }
    }
}