// file: app/src/main/java/com/example/MPdetector/OverlayView.kt
package com.example.MPdetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import androidx.core.text.color
import kotlin.math.min

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentDetectionResult: DetectionResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private var scaleFactorX: Float = 1f // X軸用のスケールファクター
    private var scaleFactorY: Float = 1f // Y軸用のスケールファクター
    private var postScaleWidthOffset: Float = 0f
    private var postScaleHeightOffset: Float = 0f

    private val boundaryPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f // 枠線の太さ (以前のコードから)
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        textSize = 50f
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
    }

    // 形状の中心を描画するためのデバッグ用ペイント (オプション)
    // private val centerPaint = Paint().apply {
    //     color = Color.RED
    //     style = Paint.Style.FILL
    // }

    fun setResults(
        result: DetectionResult?,
        imgHeight: Int,
        imgWidth: Int
    ) {
        currentDetectionResult = result
        imageHeight = imgHeight
        imageWidth = imgWidth

        if (imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0) {
            scaleFactorX = 1f
            scaleFactorY = 1f
            postScaleWidthOffset = 0f
            postScaleHeightOffset = 0f
            invalidate()
            return
        }

        // 画像の正規化座標をViewの座標に変換するためのスケールとオフセットを計算
        // アスペクト比を維持しつつ、View内にフィットさせる (letterbox or pillarbox)
        val imageAspectRatio = imgWidth.toFloat() / imgHeight.toFloat()
        val viewAspectRatio = width.toFloat() / height.toFloat()

        if (imageAspectRatio > viewAspectRatio) { // 画像がViewより横長 (pillarbox)
            scaleFactorX = width.toFloat() / imgWidth.toFloat()
            scaleFactorY = scaleFactorX // アスペクト比維持のためXと同じスケール
            postScaleWidthOffset = 0f
            postScaleHeightOffset = (height.toFloat() - (imgHeight.toFloat() * scaleFactorY)) / 2f
        } else { // 画像がViewより縦長または同じ (letterbox)
            scaleFactorY = height.toFloat() / imgHeight.toFloat()
            scaleFactorX = scaleFactorY // アスペクト比維持のためYと同じスケール
            postScaleHeightOffset = 0f
            postScaleWidthOffset = (width.toFloat() - (imgWidth.toFloat() * scaleFactorX)) / 2f
        }

        // Log.d("OverlayView", "w=$width, h=$height, iw=$imageWidth, ih=$imageHeight")
        // Log.d("OverlayView", "sfX=$scaleFactorX, sfY=$scaleFactorY, offX=$postScaleWidthOffset, offY=$postScaleHeightOffset")

        invalidate()
    }

    fun clear() {
        currentDetectionResult = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val resultToDraw = currentDetectionResult ?: return

        // 1. ステータスに基づいて枠の色とテキストの色を設定
        val statusColor = when (resultToDraw.status) {
            DetectionStatus.NOT_DETECTED -> Color.WHITE
            DetectionStatus.DETECTING -> Color.GREEN // 例: 検出中は緑
            DetectionStatus.UNKNOWN -> Color.YELLOW
            else -> Color.GRAY
        }
        boundaryPaint.color = statusColor
        textPaint.color = statusColor // テキストも同じ色にする

        // 2. 境界線 (クロップエリア) の描画
        if (resultToDraw.boundary.points.isNotEmpty()) {
            val cropAreaPath = Path()
            var firstTransformedPoint: PointF? = null // 閉じるために最初の点を保存

            resultToDraw.boundary.points.forEachIndexed { index, normalizedPoint ->
                // 正規化座標をViewのピクセル座標に変換
                // 注意: MediaPipeからの座標は通常、(0,0)が左上でY軸が下向き。
                //       カメラプレビューが左右反転している場合は、X座標を反転させる (1.0f - x) 必要があったりする。
                //       今回は Detector が直接座標を渡しているので、その座標系に従う。
                //       TangueDetector は (0.5,0.5) 中心で、X右向き、Y下向きの標準的な正規化座標と仮定。

                val transformedX = normalizedPoint.x * imageWidth * scaleFactorX + postScaleWidthOffset
                val transformedY = normalizedPoint.y * imageHeight * scaleFactorY + postScaleHeightOffset
                val transformedPoint = PointF(transformedX, transformedY)

                if (index == 0) {
                    cropAreaPath.moveTo(transformedPoint.x, transformedPoint.y)
                    firstTransformedPoint = transformedPoint
                } else {
                    cropAreaPath.lineTo(transformedPoint.x, transformedPoint.y)
                }
            }

            // パスを閉じる (リストの最後と最初の点を結ぶ)
            firstTransformedPoint?.let {
                cropAreaPath.close() // moveToした最初の点にlineToして閉じる
            }

            canvas.drawPath(cropAreaPath, boundaryPaint)

            // 3. ステータステキストの描画
            // 枠の左上の点 (最も小さい x, y を持つ点) の近くに表示する
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            if (resultToDraw.boundary.points.isNotEmpty()) { // 描画する点がある場合のみ
                resultToDraw.boundary.points.forEach { normalizedPoint ->
                    val transformedX = normalizedPoint.x * imageWidth * scaleFactorX + postScaleWidthOffset
                    val transformedY = normalizedPoint.y * imageHeight * scaleFactorY + postScaleHeightOffset
                    minX = min(minX, transformedX)
                    minY = min(minY, transformedY)
                }
            }


            if (minX != Float.MAX_VALUE && minY != Float.MAX_VALUE) {
                val statusLabel = DetectionStatus.labels[resultToDraw.status] ?: "不明な状態 (${resultToDraw.status})"
                canvas.drawText(statusLabel, minX, minY - textPaint.descent() - 5, textPaint) // descent()でベースラインより少し上に
            } else if (resultToDraw.boundary.points.isEmpty()) {
                // 点がない場合は中央などに表示も可能
                val statusLabel = DetectionStatus.labels[resultToDraw.status] ?: "不明な状態 (${resultToDraw.status})"
                val textWidth = textPaint.measureText(statusLabel)
                canvas.drawText(statusLabel, (width - textWidth) / 2f, height / 2f, textPaint)
            }
        } else {
            // boundary.points が空でもステータスは表示したい場合
            val statusLabel = DetectionStatus.labels[resultToDraw.status] ?: "不明な状態 (${resultToDraw.status})"
            val textWidth = textPaint.measureText(statusLabel)
            canvas.drawText(statusLabel, (width - textWidth) / 2f, height / 2f, textPaint)
        }
    }
}
