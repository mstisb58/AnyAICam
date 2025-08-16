package com.example.MPdetector

import android.graphics.PointF

// ステータスコードの定義 (例)
object DetectionStatus {
    const val NOT_DETECTED = 0
    const val DETECTING = 1
    const val UNKNOWN = 2
    // 必要に応じて他のステータスを追加

    // ステータスコードに対応するテキストの辞書
    val labels = mapOf(
        NOT_DETECTED to "未検出",
        DETECTING to "検出中",
        UNKNOWN to "不明"
    )
}

data class DetectionResult(
    val boundary: ShapeBoundary,
    val status: Int, // Int型に変更
    val rotationAngle: Float,
    val landmarks: List<PointF>? = null // ランドマークもPointFのリストが良いかもしれません
)

data class ShapeBoundary(
    val points: List<PointF> // List<PointF> に統一
)
