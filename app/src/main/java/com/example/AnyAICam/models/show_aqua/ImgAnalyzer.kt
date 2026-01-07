package com.example.AnyAICam.models.show_aqua

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.util.Log
import com.example.AnyAICam.ImgProcessor
import com.example.AnyAICam.models.face_detector.LandmarkHelper
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.Locale
import kotlin.math.absoluteValue

class ImgAnalyzer : ImgProcessor {

    companion object {
        private const val TAG = "ShowAquaImgAnalyzer"
        private const val MODEL_PATH = "mediapipe/face_landmarker.task"

        private val TARGET_POLYGONS = mapOf(
            "Right Eyebrow" to listOf(109, 108, 151, 10),
            "Left Eyebrow" to listOf(10, 151, 337, 338)
        )

        private val DRAW_COLOR_LANDMARKS = Scalar(0.0, 255.0, 0.0, 255.0) // Green
        private const val DRAW_THICKNESS_LANDMARKS = 3
        private const val K_MEANS_CLUSTERS = 9
        private val COLOR_R = Scalar(255.0, 0.0, 0.0, 255.0)
        private val COLOR_G = Scalar(0.0, 180.0, 0.0, 255.0)
        private val COLOR_B = Scalar(0.0, 0.0, 255.0, 255.0)
        private val COLOR_BLACK = Scalar(0.0, 0.0, 0.0, 255.0)
        private val COLOR_WHITE = Scalar(255.0, 255.0, 255.0, 255.0)
        private val COLOR_GRAY = Scalar(200.0, 200.0, 200.0, 255.0)
    }

    override val name: String = "show_aqua"
    override val saveDirectoryName: String = "show_aqua_results"
    override var isDummyPreviewEnabled: Boolean = false
    override var showLandmarks: Boolean = false
    override var saveLandmarks: Boolean = false

    enum class OperatingMode {
        REPORT,
        HEATMAP
    }
    var operatingMode: OperatingMode = OperatingMode.REPORT
    var heatmapMinMoisture: Double = 0.5
    var heatmapMaxMoisture: Double = 1.5

    private var faceLandmarker: FaceLandmarker? = null
    private var lastLandmarks: List<NormalizedLandmark>? = null
    private var conversionBitmap: Bitmap? = null

    private data class AnalysisResult(
        val sortedCentroids: List<Scalar>,
        val sortedCounts: List<Int>,
        val totalPixelCount: Int
    ) {
        fun getMedianCentroid(): Scalar {
            return if (sortedCentroids.isNotEmpty()) {
                sortedCentroids[sortedCentroids.size / 2]
            } else {
                Scalar(0.0, 0.0, 0.0, 0.0)
            }
        }
    }

    private data class PolygonAnalysisResult(
        val name: String,
        val croppedImage: Mat,
        val analysisResult: AnalysisResult?
    )

    private data class ExtractedRegion(
        val roi: Mat,
        val mask: Mat,
        val rect: Rect
    )

    override fun setup(context: Context) {
        if (faceLandmarker != null) return
        try {
            val baseOptions = BaseOptions.builder().setModelAssetPath(MODEL_PATH).build()
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .build()
            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e(TAG, "FaceLandmarker initialization failed", e)
        }
    }

    override fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean> {
        val landmarks = detectLandmarks(frame)
        landmarks?.let {
            TARGET_POLYGONS.values.forEach { ids ->
                drawPolygon(frame, it, ids)
            }
        }
        return Pair(frame, true)
    }

    override fun processFrameForSaving(frame: Bitmap): Bitmap {
        return when (operatingMode) {
            OperatingMode.REPORT -> createAnalysisReport(frame)
            OperatingMode.HEATMAP -> createHeatmapView(frame)
        }
    }

    private fun createHeatmapView(frame: Bitmap): Bitmap {
        val inputMat = Mat()
        Utils.bitmapToMat(frame, inputMat)
        val outputMat = inputMat.clone()

        val landmarks = detectLandmarks(inputMat)
        if (landmarks == null) {
            Log.w(TAG, "No landmarks for heatmap, returning original frame.")
            inputMat.release()
            outputMat.release()
            return frame
        }

        val overlay = Mat.zeros(inputMat.size(), inputMat.type())

        for ((_, ids) in TARGET_POLYGONS) {
            val extracted = extractPolygonRegion(inputMat, landmarks, ids)
            if (extracted != null && extracted.roi.width() >= 10 && extracted.roi.height() >= 10) {
                val analysis = analyzeColors(extracted.roi)
                val moisture = calculateMoistureValue(analysis)

                if (!moisture.isNaN()) {
                    val normalizedValue = (((moisture - heatmapMinMoisture) / (heatmapMaxMoisture - heatmapMinMoisture) * 255.0))
                        .coerceIn(0.0, 255.0).toInt().toByte()

                    val valueMat = Mat(1, 1, CvType.CV_8UC1)
                    valueMat.put(0, 0, byteArrayOf(normalizedValue))
                    val colorMat = Mat()
                    Imgproc.applyColorMap(valueMat, colorMat, Imgproc.COLORMAP_JET)
                    val colorScalarBGR = Scalar(colorMat.get(0, 0))
                    val colorScalarBGRA = Scalar(colorScalarBGR.`val`[0], colorScalarBGR.`val`[1], colorScalarBGR.`val`[2], 255.0)

                    val points = getLandmarkPoints(inputMat.cols(), inputMat.rows(), landmarks, ids)
                    if (points.isNotEmpty()) {
                        val matOfPoint = MatOfPoint().apply { fromList(points) }
                        Imgproc.fillPoly(overlay, listOf(matOfPoint), colorScalarBGRA)
                        
                        val boundingRect = Imgproc.boundingRect(matOfPoint)
                        val textOrigin = Point(boundingRect.x.toDouble(), (boundingRect.y + boundingRect.height + 20).toDouble())
                        val moistureText = String.format(Locale.US, "%.2f", moisture)
                        Imgproc.putText(outputMat, moistureText, textOrigin, Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, COLOR_BLACK, 2)
                        
                        matOfPoint.release()
                    }
                    valueMat.release()
                    colorMat.release()
                }
                extracted.roi.release()
                extracted.mask.release()
            }
        }

        Core.addWeighted(outputMat, 1.0, overlay, 0.6, 0.0, outputMat)
        overlay.release()
        inputMat.release()

        val resultBitmap = createBitmap(outputMat.cols(), outputMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputMat, resultBitmap)
        outputMat.release()

        return resultBitmap
    }

    private fun calculateMoistureValue(result: AnalysisResult?): Double {
        if (result == null) return Double.NaN
        val median = result.getMedianCentroid()
        val rVal = median.`val`[0]
        val gVal = median.`val`[1]
        val bVal = median.`val`[2]
        val denominator = rVal - bVal
        return if (denominator.absoluteValue > 1e-6) {
            (gVal - bVal) / denominator
        } else {
            Double.NaN
        }
    }

    private fun createAnalysisReport(frame: Bitmap): Bitmap {
        val inputMat = Mat()
        Utils.bitmapToMat(frame, inputMat)
        val landmarks = detectLandmarks(inputMat)
        val allPolygonResults = mutableListOf<PolygonAnalysisResult>()
        var finalMat: Mat? = null

        try {
            if (landmarks == null) return frame

            for ((name, ids) in TARGET_POLYGONS) {
                val extracted = extractPolygonRegion(inputMat, landmarks, ids)
                if (extracted != null && extracted.roi.width() >= 10 && extracted.roi.height() >= 10) {
                    val analysis = analyzeColors(extracted.roi)
                    allPolygonResults.add(PolygonAnalysisResult(name, extracted.roi, analysis))
                    extracted.mask.release()
                } else {
                    extracted?.roi?.release()
                    extracted?.mask?.release()
                }
            }

            if (allPolygonResults.isEmpty()) return frame

            val reportRows = allPolygonResults.map { createReportRow(it) }
            finalMat = stackMatsVertically(reportRows)
            reportRows.forEach { it.release() }

            if (finalMat == null || finalMat.empty()) return frame

            val resultBitmap = createBitmap(finalMat.cols(), finalMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(finalMat, resultBitmap)
            return resultBitmap
        } finally {
            inputMat.release()
            allPolygonResults.forEach { it.croppedImage.release() }
            finalMat?.release()
        }
    }

    private fun detectLandmarks(frame: Mat): List<NormalizedLandmark>? {
        val landmarker = faceLandmarker ?: return null
        if (conversionBitmap == null || conversionBitmap?.width != frame.cols() || conversionBitmap?.height != frame.rows()) {
            conversionBitmap?.recycle()
            conversionBitmap = createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888)
        }
        conversionBitmap?.let { bmp ->
            Utils.matToBitmap(frame, bmp)
            val mpImage = BitmapImageBuilder(bmp).build()
            val result = landmarker.detect(mpImage).faceLandmarks().getOrNull(0)
            lastLandmarks = result
            return result
        }
        return null
    }

    private fun extractPolygonRegion(frame: Mat, landmarks: List<NormalizedLandmark>, ids: List<Int>): ExtractedRegion? {
        val points = getLandmarkPoints(frame.cols(), frame.rows(), landmarks, ids)
        if (points.isEmpty()) return null

        val matOfPoint = MatOfPoint().apply { fromList(points) }
        val boundingRect = Imgproc.boundingRect(matOfPoint)
        val safeRect = Rect(0, 0, frame.cols(), frame.rows()).intersect(boundingRect)

        if (safeRect.width <= 0 || safeRect.height <= 0) {
            matOfPoint.release()
            return null
        }

        val originalRoi = frame.submat(safeRect)
        val mask = Mat.zeros(safeRect.size(), CvType.CV_8UC1)
        val localPoints = points.map { Point(it.x - safeRect.x, it.y - safeRect.y) }
        val localMatOfPoint = MatOfPoint().apply { fromList(localPoints) }

        Imgproc.fillPoly(mask, listOf(localMatOfPoint), Scalar(255.0))

        val maskedRoi = Mat()
        originalRoi.copyTo(maskedRoi, mask)

        matOfPoint.release()
        localMatOfPoint.release()

        return ExtractedRegion(roi = maskedRoi, mask = mask, rect = safeRect)
    }

    private fun analyzeColors(image: Mat): AnalysisResult? {
        if (image.empty()) return null

        val bgrMat = Mat()
        val pointsMat = Mat()
        val labels = Mat()
        val centers = Mat()

        try {
            if (image.channels() == 4) {
                Imgproc.cvtColor(image, bgrMat, Imgproc.COLOR_BGRA2BGR)
            } else {
                image.copyTo(bgrMat)
            }

            val numPixels = bgrMat.rows() * bgrMat.cols()
            if (numPixels == 0) return null

            val data = ByteArray(numPixels * bgrMat.channels())
            bgrMat.get(0, 0, data)

            val validPixels = ArrayList<Float>()
            for (i in 0 until numPixels) {
                val index = i * 3
                val b = data[index].toInt() and 0xFF
                val g = data[index + 1].toInt() and 0xFF
                val r = data[index + 2].toInt() and 0xFF
                if (b > 10 || g > 10 || r > 10) {
                    validPixels.add(r.toFloat())
                    validPixels.add(g.toFloat())
                    validPixels.add(b.toFloat())
                }
            }

            if (validPixels.size / 3 < K_MEANS_CLUSTERS) return null

            pointsMat.create(validPixels.size / 3, 3, CvType.CV_32F)
            pointsMat.put(0, 0, validPixels.toFloatArray())

            val criteria = TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 100, 0.1)
            Core.kmeans(pointsMat, K_MEANS_CLUSTERS, labels, criteria, 3, Core.KMEANS_PP_CENTERS, centers)

            val counts = IntArray(K_MEANS_CLUSTERS)
            val labelsArray = IntArray(labels.rows() * labels.cols())
            labels.get(0, 0, labelsArray)
            labelsArray.forEach { counts[it]++ }

            val indexedCentroids = (0 until centers.rows()).map { i ->
                val centroid = Scalar(centers.get(i, 0)[0], centers.get(i, 1)[0], centers.get(i, 2)[0])
                Pair(centroid, i)
            }

            val sortedIndexedCentroids = indexedCentroids.sortedBy { (it.first.`val`[0] + it.first.`val`[1] + it.first.`val`[2]) }
            val sortedCentroids = sortedIndexedCentroids.map { it.first }
            val sortedCounts = sortedIndexedCentroids.map { counts[it.second] }
            return AnalysisResult(sortedCentroids, sortedCounts, labelsArray.size)
        } finally {
            bgrMat.release()
            pointsMat.release()
            labels.release()
            centers.release()
        }
    }

    private fun createReportRow(result: PolygonAnalysisResult): Mat {
        val imgWidth = 400
        val graphWidth = 250
        val textWidth = 250
        val rowHeight = 250
        val resizedImage = Mat()
        Imgproc.resize(result.croppedImage, resizedImage, Size(imgWidth.toDouble(), rowHeight.toDouble()))
        val graphMat = createReportGraph(result.analysisResult, Size(graphWidth.toDouble(), rowHeight.toDouble()))
        val textMat = createReportText(result.name, result.analysisResult, Size(textWidth.toDouble(), rowHeight.toDouble()))
        val rowMat = combineMatsHorizontally(listOf(resizedImage, graphMat, textMat))
        resizedImage.release()
        graphMat.release()
        textMat.release()
        return rowMat
    }

    private fun createReportGraph(result: AnalysisResult?, size: Size): Mat {
        val graphMat = Mat(size, CvType.CV_8UC4, COLOR_WHITE)
        if (result == null) {
            Imgproc.putText(graphMat, "Analysis Failed", Point(size.width / 2 - 100, size.height / 2), Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, COLOR_BLACK, 2)
            return graphMat
        }
        val margin = 40
        val graphWidth = size.width.toInt() - margin * 2
        val graphHeight = size.height.toInt() - margin * 2
        val origin = Point(margin.toDouble(), (size.height - margin).toDouble())
        Imgproc.rectangle(graphMat, Point(margin.toDouble(), margin.toDouble()), Point((origin.x + graphWidth), origin.y - graphHeight), Scalar(250.0, 250.0, 250.0, 255.0), -1)
        Imgproc.rectangle(graphMat, Point(margin.toDouble(), margin.toDouble()), Point((origin.x + graphWidth), origin.y), COLOR_GRAY, 1)
        val stepX = graphWidth.toDouble() / (K_MEANS_CLUSTERS - 1).coerceAtLeast(1)
        val maxCount = result.sortedCounts.maxOrNull()?.toDouble() ?: 1.0
        val barColor = Scalar(200.0, 200.0, 200.0, 200.0)
        result.sortedCounts.forEachIndexed { index, count ->
            val barHeight = (count / maxCount) * graphHeight
            val x = origin.x + index * stepX
            Imgproc.rectangle(graphMat, Point(x - stepX / 2.5, origin.y - barHeight), Point(x + stepX / 2.5, origin.y), barColor, -1)
        }
        val scaleY = graphHeight.toDouble() / 255.0
        val pointsR = mutableListOf<Point>()
        val pointsG = mutableListOf<Point>()
        val pointsB = mutableListOf<Point>()
        result.sortedCentroids.forEachIndexed { index, centroid ->
            val x = origin.x + index * stepX
            pointsR.add(Point(x, origin.y - centroid.`val`[0] * scaleY))
            pointsG.add(Point(x, origin.y - centroid.`val`[1] * scaleY))
            pointsB.add(Point(x, origin.y - centroid.`val`[2] * scaleY))
            Imgproc.putText(graphMat, "${index + 1}", Point(x - 5, origin.y + 15), Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, COLOR_BLACK, 1)
        }
        drawPolyline(graphMat, pointsR, COLOR_R)
        drawPolyline(graphMat, pointsG, COLOR_G)
        drawPolyline(graphMat, pointsB, COLOR_B)
        pointsR.forEach { Imgproc.circle(graphMat, it, 3, COLOR_R, -1) }
        pointsG.forEach { Imgproc.circle(graphMat, it, 3, COLOR_G, -1) }
        pointsB.forEach { Imgproc.circle(graphMat, it, 3, COLOR_B, -1) }
        Imgproc.putText(graphMat, "255", Point(margin - 35.0, margin + 5.0), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, COLOR_BLACK, 1)
        Imgproc.putText(graphMat, "128", Point(margin - 35.0, origin.y - 128.0 * scaleY + 5.0), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, COLOR_GRAY, 1)
        Imgproc.putText(graphMat, "0", Point(margin - 20.0, origin.y + 5.0), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, COLOR_BLACK, 1)
        return graphMat
    }

    private fun createReportText(name: String, result: AnalysisResult?, size: Size): Mat {
        val textMat = Mat(size, CvType.CV_8UC4, COLOR_WHITE)
        val textStartX = 20.0
        val lineHeight = 35.0
        Imgproc.putText(textMat, name, Point(textStartX, lineHeight), Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, COLOR_BLACK, 2)
        if (result != null) {
            val moistureValue = calculateMoistureValue(result)
            val moistureText = if (moistureValue.isNaN()) "Moisture: N/A" else String.format(Locale.US, "Moisture: %.1f", moistureValue)
            Imgproc.putText(textMat, "Median Analysis (5/9)", Point(textStartX, lineHeight * 2), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, COLOR_BLACK, 1)
            Imgproc.putText(textMat, moistureText, Point(textStartX, lineHeight * 3), Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, COLOR_BLACK, 2)
        } else {
            Imgproc.putText(textMat, "No data", Point(textStartX, lineHeight * 2.5), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, COLOR_BLACK, 1)
        }
        return textMat
    }

    private fun stackMatsVertically(mats: List<Mat>): Mat? {
        if (mats.isEmpty()) return null
        val width = mats.maxOfOrNull { it.width() } ?: 0
        val height = mats.sumOf { it.height() }
        if (width <= 0 || height <= 0) return null
        val result = Mat.zeros(height, width, mats.first().type())
        result.setTo(COLOR_WHITE)
        var currentY = 0
        for (mat in mats) {
            if (!mat.empty()) {
                val roi = result.submat(Rect(0, currentY, mat.width(), mat.height()))
                mat.copyTo(roi)
                roi.release()
            }
            currentY += mat.height()
        }
        return result
    }

    private fun combineMatsHorizontally(mats: List<Mat>): Mat {
        if (mats.isEmpty()) return Mat()
        val height = mats.maxOfOrNull { it.height() } ?: 0
        val width = mats.sumOf { it.width() }
        if (width <= 0 || height <= 0) return Mat()
        val result = Mat.zeros(height, width, mats.first().type())
        result.setTo(COLOR_WHITE)
        var currentX = 0
        for (mat in mats) {
            if (!mat.empty()) {
                val y = (height - mat.height()) / 2
                val roi = result.submat(Rect(currentX, y, mat.width(), mat.height()))
                mat.copyTo(roi)
                roi.release()
            }
            currentX += mat.width()
        }
        return result
    }

    private fun drawPolygon(frame: Mat, landmarks: List<NormalizedLandmark>, ids: List<Int>) {
        val points = getLandmarkPoints(frame.cols(), frame.rows(), landmarks, ids)
        if (points.size > 1) {
            val matOfPoint = MatOfPoint().apply { fromList(points) }
            Imgproc.polylines(frame, listOf(matOfPoint), true, DRAW_COLOR_LANDMARKS, DRAW_THICKNESS_LANDMARKS)
            matOfPoint.release()
        }
    }

    private fun drawPolyline(mat: Mat, points: List<Point>, color: Scalar) {
        if (points.size < 2) return
        val matOfPoint = MatOfPoint().apply { fromList(points) }
        Imgproc.polylines(mat, listOf(matOfPoint), false, color, 2, Imgproc.LINE_AA)
        matOfPoint.release()
    }

    private fun getLandmarkPoints(width: Int, height: Int, landmarks: List<NormalizedLandmark>, ids: List<Int>): List<Point> {
        return ids.mapNotNull { id ->
            if (id in landmarks.indices) {
                val lm = landmarks[id]
                Point((lm.x() * width).toDouble(), (lm.y() * height).toDouble())
            } else {
                null
            }
        }
    }

    private fun Rect.intersect(other: Rect): Rect {
        val x1 = maxOf(this.x, other.x)
        val y1 = maxOf(this.y, other.y)
        val x2 = minOf(this.x + this.width, other.x + other.width)
        val y2 = minOf(this.y + this.height, other.y + other.height)
        return Rect(x1, y1, maxOf(0, x2 - x1), maxOf(0, y2 - y1))
    }

    override fun getLandmarksForCsv(): String? {
        return lastLandmarks?.let { LandmarkHelper.landmarksToCsvRow(it) }
    }

    override fun getCsvHeader(): String? {
        return LandmarkHelper.getCsvHeader()
    }
}
