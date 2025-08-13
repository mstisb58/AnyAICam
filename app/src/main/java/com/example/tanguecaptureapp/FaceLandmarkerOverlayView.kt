package com.example.tanguecaptureapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class FaceLandmarkerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: FaceLandmarkerResult? = null
    private val pointPaint = Paint()
    private val linePaint = Paint()
    private val guideBoxPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var shouldDrawLandmarks: Boolean = true

    init {
        initPaints()
    }

    fun clear() {
        results = null
        linePaint.reset()
        pointPaint.reset()
        guideBoxPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color = ContextCompat.getColor(context, R.color.mp_color_primary)
        linePaint.strokeWidth = 8f
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = 8f
        pointPaint.style = Paint.Style.FILL

        guideBoxPaint.color = Color.WHITE
        guideBoxPaint.strokeWidth = 6f
        guideBoxPaint.style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        results?.let { faceLandmarkerResult ->
            if (faceLandmarkerResult.faceLandmarks().isEmpty()) {
                return@let
            }
            val landmarks = faceLandmarkerResult.faceLandmarks()[0]

            val scaledImageHeight = imageHeight * scaleFactor
            val scaledImageWidth = imageWidth * scaleFactor
            val offsetX = (width - scaledImageWidth) / 2
            val offsetY = (height - scaledImageHeight) / 2

            val rightCorner = landmarks[61]
            val leftCorner = landmarks[291]

            val leftCornerX = leftCorner.x() * scaledImageWidth + offsetX
            val leftCornerY = leftCorner.y() * scaledImageHeight + offsetY
            val rightCornerX = rightCorner.x() * scaledImageWidth + offsetX
            val rightCornerY = rightCorner.y() * scaledImageHeight + offsetY

            val dx = rightCornerX - leftCornerX
            val dy = rightCornerY - leftCornerY

            val boxWidth = sqrt((dx * dx + dy * dy))

            // 修正: 正方形が「下方向」に伸びるように、90度時計回りのベクトルを計算
            val perpendicularDx = dy
            val perpendicularDy = -dx

            val p1 = Pair(leftCornerX, leftCornerY)
            val p2 = Pair(rightCornerX, rightCornerY)
            val p3 = Pair(rightCornerX + perpendicularDx, rightCornerY + perpendicularDy)
            val p4 = Pair(leftCornerX + perpendicularDx, leftCornerY + perpendicularDy)

            val path = Path().apply {
                moveTo(p1.first, p1.second)
                lineTo(p2.first, p2.second)
                lineTo(p3.first, p3.second)
                lineTo(p4.first, p4.second)
                close()
            }
            canvas.drawPath(path, guideBoxPaint)


            if (shouldDrawLandmarks) {
                for (normalizedLandmark in landmarks) {
                    val x = normalizedLandmark.x() * scaledImageWidth + offsetX
                    val y = normalizedLandmark.y() * scaledImageHeight + offsetY
                    canvas.drawPoint(x, y, pointPaint)
                }
                FaceLandmarker.FACE_LANDMARKS_CONNECTORS.forEach {
                    val start = landmarks[it.start()]
                    val end = landmarks[it.end()]
                    val startX = start.x() * scaledImageWidth + offsetX
                    val startY = start.y() * scaledImageHeight + offsetY
                    val endX = end.x() * scaledImageWidth + offsetX
                    val endY = end.y() * scaledImageHeight + offsetY
                    canvas.drawLine(startX, startY, endX, endY, linePaint)
                }
            }
        }
    }

    fun setResults(
        faceLandmarkerResult: FaceLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE,
        shouldDrawLandmarks: Boolean
    ) {
        results = faceLandmarkerResult
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth
        this.shouldDrawLandmarks = shouldDrawLandmarks

        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
        invalidate()
    }
}
