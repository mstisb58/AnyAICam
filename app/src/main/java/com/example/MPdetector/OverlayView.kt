// file: app/src/main/java/com/example/MPdetector/OverlayView.kt
package com.example.MPdetector

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.max

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var results: DetectionResult? = null
    private val boxPaint = Paint()
    private val textPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = 8f

        textPaint.color = Color.WHITE
        textPaint.textSize = 50f
        textPaint.textAlign = Paint.Align.LEFT
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        results?.let { result ->
            // ★修正点: 状態に応じて描画色を決定するロジックを更新
            boxPaint.color = when (result.status) {
                "未検出" -> Color.WHITE // 未検出状態は白
                else -> Color.GREEN      // それ以外（"検出中"など）は緑
            }

            // プレビューは左右反転しているので、描画も反転させる
            val invertedLeft = 1f - result.boundingBox.right
            val invertedRight = 1f - result.boundingBox.left

            val scaledBoundingBox = RectF(
                invertedLeft * width,
                result.boundingBox.top * height,
                invertedRight * width,
                result.boundingBox.bottom * height
            )

            canvas.drawRect(scaledBoundingBox, boxPaint)
            // 状態テキストの色も枠に合わせる
            textPaint.color = boxPaint.color
            canvas.drawText(result.status, scaledBoundingBox.left, scaledBoundingBox.top - 10, textPaint)
        }
    }

    fun setResults(detectionResult: DetectionResult, imageHeight: Int, imageWidth: Int) {
        results = detectionResult
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
        invalidate()
    }

    fun clear() {
        results = null
        invalidate()
    }
}

