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

//        private val TARGET_POLYGONS = mapOf(
//            "Forehead_01" to listOf(109, 108, 151, 10),
//            "Forehead_02" to listOf(10, 151, 337, 338),
//
//        )
        private val TARGET_POLYGONS = mapOf(
                "LFace_01" to listOf(108, 107, 9, 151),
                "LFace_02" to listOf(69, 66, 107, 108),
                "LFace_03" to listOf(104, 63, 66, 69),
                "LFace_04" to listOf(46, 111, 117, 63),
                "LFace_05" to listOf(111, 187, 205, 117),
                "LFace_06" to listOf(117, 205, 36, 119),
                "LFace_07" to listOf(119, 36, 220, 174),
                "LFace_08" to listOf(174, 220, 4, 197),
                "LFace_09" to listOf(193, 174, 197, 168),
                "LFace_10" to listOf(107, 193, 168, 9),
                "LFace_11" to listOf(187, 214, 216, 205),
                "LFace_12" to listOf(205, 216, 203, 36),
                "LFace_13" to listOf(36, 203, 239, 220),
                "LFace_14" to listOf(203, 216, 39, 167),
                "LFace_15" to listOf(167, 39, 0, 164),
                "LFace_16" to listOf(214, 210, 212, 216),
                "LFace_17" to listOf(212, 210, 194, 181),
                "LFace_18" to listOf(181, 194, 200, 17),
                "RFace_01" to listOf(151, 9, 336, 337),
                "RFace_02" to listOf(337, 336, 296, 299),
                "RFace_03" to listOf(299, 296, 293, 333),
                "RFace_04" to listOf(293, 346, 340, 276),
                "RFace_05" to listOf(346, 425, 411, 340),
                "RFace_06" to listOf(348, 266, 425, 346),
                "RFace_07" to listOf(399, 440, 266, 348),
                "RFace_08" to listOf(197, 4, 440, 399),
                "RFace_09" to listOf(168, 197, 399, 417),
                "RFace_10" to listOf(9, 168, 417, 336),
                "RFace_11" to listOf(425, 436, 434, 411),
                "RFace_12" to listOf(266, 423, 436, 425),
                "RFace_13" to listOf(440, 459, 423, 266),
                "RFace_14" to listOf(393, 269, 436, 423),
                "RFace_15" to listOf(164, 0, 269, 393),
                "RFace_16" to listOf(436, 432, 430, 434),
                "RFace_17" to listOf(405, 418, 430, 432),
                "RFace_18" to listOf(17, 200, 418, 405),
        )
        private val MASK_POLYGONS = mapOf(
            "eye_mask" to listOf(225,31,448,445)
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

    enum class OperatingMode {
        REPORT,
        HEATMAP
    }
    var operatingMode: OperatingMode = OperatingMode.REPORT
    var heatmapMinMoisture: Double = 0.0
    var heatmapMaxMoisture: Double = 1.7
    var isCsvExportEnabled: Boolean = false

    private var faceLandmarker: FaceLandmarker? = null
    private var lastLandmarks: List<NormalizedLandmark>? = null
    private var conversionBitmap: Bitmap? = null
    @Volatile
    private var lastAnalysisResults: List<PolygonAnalysisInfo>? = null

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

    private data class PolygonAnalysisInfo(
        val name: String,
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
        val inputMat = Mat()
        Utils.bitmapToMat(frame, inputMat)
        val landmarks = detectLandmarks(inputMat)
        
        // Analyze for CSV if enabled, even if we are in HEATMAP mode
        if (landmarks != null) {
            val analysisResults = mutableListOf<PolygonAnalysisResult>()
            for ((name, ids) in TARGET_POLYGONS) {
                val extracted = extractPolygonRegion(inputMat, landmarks, ids)
                if (extracted != null && extracted.roi.width() >= 10 && extracted.roi.height() >= 10) {
                    val analysis = analyzeColors(extracted.roi)
                    analysisResults.add(PolygonAnalysisResult(name, extracted.roi, analysis))
                    extracted.mask.release()
                } else {
                    extracted?.roi?.release()
                    extracted?.mask?.release()
                }
            }
            this.lastAnalysisResults = analysisResults.map { PolygonAnalysisInfo(it.name, it.analysisResult) }
            Log.d("ShowAqua", "Analysis completed: ${analysisResults.size} polygons analyzed")
            
            val resultBitmap = when (operatingMode) {
                OperatingMode.REPORT -> {
                    val reportRows = analysisResults.map { createReportRow(it) }
                    val finalMat = stackMatsVertically(reportRows)
                    reportRows.forEach { it.release() }
                    
                    if (finalMat != null && !finalMat.empty()) {
                        val bmp = createBitmap(finalMat.cols(), finalMat.rows(), Bitmap.Config.ARGB_8888)
                        Utils.matToBitmap(finalMat, bmp)
                        finalMat.release()
                        bmp
                    } else {
                        frame
                    }
                }
                OperatingMode.HEATMAP -> {
                    val heatmapMat = createHeatmapViewFromResults(inputMat, landmarks, analysisResults)
                    val bmp = createBitmap(heatmapMat.cols(), heatmapMat.rows(), Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(heatmapMat, bmp)
                    heatmapMat.release()
                    bmp
                }
            }
            
            // Cleanup
            analysisResults.forEach { it.croppedImage.release() }
            inputMat.release()
            return resultBitmap
        } else {
            inputMat.release()
            return frame
        }
    }

    private fun createHeatmapViewFromResults(inputMat: Mat, landmarks: List<NormalizedLandmark>, results: List<PolygonAnalysisResult>): Mat {
        val outputMat = inputMat.clone()
        val overlay = Mat.zeros(inputMat.size(), inputMat.type())

        for (result in results) {
            val moisture = calculateMoistureValue(result.analysisResult)
            if (!moisture.isNaN()) {
                val normalizedValue = (((moisture - heatmapMinMoisture) / (heatmapMaxMoisture - heatmapMinMoisture) * 255.0))
                    .coerceIn(0.0, 255.0).toInt().toByte()

                val valueMat = Mat(1, 1, CvType.CV_8UC1)
                valueMat.put(0, 0, byteArrayOf(normalizedValue))
                val colorMat = Mat()
                Imgproc.applyColorMap(valueMat, colorMat, Imgproc.COLORMAP_JET)
                val colorScalarBGR = Scalar(colorMat.get(0, 0))
                val colorScalarBGRA = Scalar(colorScalarBGR.`val`[0], colorScalarBGR.`val`[1], colorScalarBGR.`val`[2], 255.0)

                val ids = TARGET_POLYGONS[result.name] ?: continue
                val points = getLandmarkPoints(inputMat.cols(), inputMat.rows(), landmarks, ids)
                if (points.isNotEmpty()) {
                    val matOfPoint = MatOfPoint().apply { fromList(points) }
                    Imgproc.fillPoly(overlay, listOf(matOfPoint), colorScalarBGRA)
                    
                    val boundingRect = Imgproc.boundingRect(matOfPoint)
                    val textOrigin = Point(boundingRect.x.toDouble()+5, (boundingRect.y + boundingRect.height-20).toDouble())
                    val moistureText = String.format(Locale.US, "%.2f", moisture)

                    val fontScale = (outputMat.width() / 1000.0).coerceAtLeast(0.5)
                    val thickness = (outputMat.width() / 400.0).coerceAtLeast(1.0).toInt()

                    Imgproc.putText(outputMat, moistureText, textOrigin, Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, COLOR_BLACK, thickness)
                    matOfPoint.release()
                }
                valueMat.release()
                colorMat.release()
            }
        }

        Core.addWeighted(outputMat, 1.0, overlay, 0.6, 0.0, outputMat)
        overlay.release()

        val finalWithColorbar = addColorbar(outputMat, heatmapMinMoisture, heatmapMaxMoisture)
        outputMat.release()
        return finalWithColorbar
    }

    private fun addColorbar(image: Mat, minVal: Double, maxVal: Double): Mat {
        val colorbarHeight = 80
        val textHeight = 30
        val totalHeight = image.rows() + colorbarHeight
        val resultMat = Mat(totalHeight, image.cols(), image.type(), COLOR_WHITE)
        val imageRoi = resultMat.submat(0, image.rows(), 0, image.cols())
        image.copyTo(imageRoi)
        imageRoi.release()

        val colorbarRoi = resultMat.submat(image.rows(), totalHeight, 0, image.cols())
        val gradientMat = Mat(1, 256, CvType.CV_8UC1)
        val gradientData = ByteArray(256)

        for (i in 0 until 256) {
            gradientData[i] = i.toByte()
        }
        gradientMat.put(0, 0, gradientData)
        val colorGradient = Mat()
        Imgproc.applyColorMap(gradientMat, colorGradient, Imgproc.COLORMAP_JET)
        val resizedGradient = Mat()
        Imgproc.resize(colorGradient, resizedGradient, Size(colorbarRoi.cols().toDouble(), (colorbarHeight - textHeight).toDouble()))
        val resizedGradientBGRA = Mat()
        Imgproc.cvtColor(resizedGradient, resizedGradientBGRA, Imgproc.COLOR_BGR2BGRA)
        val gradientTargetRoi = colorbarRoi.submat(0, resizedGradientBGRA.rows(), 0, resizedGradientBGRA.cols())
        resizedGradientBGRA.copyTo(gradientTargetRoi)
        gradientTargetRoi.release()
        val fontScale = 0.8
        val thickness = 2
        val textColor = COLOR_BLACK
        val textY = image.rows() + resizedGradientBGRA.rows() + textHeight - 10
        val minText = String.format(Locale.US, "%.1f", minVal)
        Imgproc.putText(resultMat, minText, Point(10.0, textY.toDouble()), Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, textColor, thickness)
        val maxText = String.format(Locale.US, "%.1f", maxVal)
        val maxTextSize = Imgproc.getTextSize(maxText, Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, thickness, null)
        Imgproc.putText(resultMat, maxText, Point((resultMat.cols() - maxTextSize.width - 10), textY.toDouble()), Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, textColor, thickness)
        val midVal = (minVal + maxVal) / 2
        val midText = String.format(Locale.US, "%.1f", midVal)
        val midTextSize = Imgproc.getTextSize(midText, Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, thickness, null)
        Imgproc.putText(resultMat, midText, Point((resultMat.cols() / 2 - midTextSize.width / 2), textY.toDouble()), Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, textColor, thickness)
        gradientMat.release()
        colorGradient.release()
        resizedGradient.release()
        resizedGradientBGRA.release()
        colorbarRoi.release()
        return resultMat
    }

    private fun calculateMoistureValue(result: AnalysisResult?): Double {
        if (result == null) return Double.NaN
        val median = result.getMedianCentroid()
        val gVal = median.`val`[1]
        val bVal = median.`val`[2]
        val denominator = bVal
        return if (denominator.absoluteValue > 1e-6) {
            2-(gVal / denominator)
        } else {
            Double.NaN
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

    override fun getReportCsv(): String? {
        if (!isCsvExportEnabled) return null
        val results = lastAnalysisResults ?: return null
        val header = buildString {
            append("area_name,")
            for (i in 1..K_MEANS_CLUSTERS) {
                append("cluster${i}_area,cluster${i}_r,cluster${i}_g,cluster${i}_b,")
            }
        }.removeSuffix(",")

        val csvData = StringBuilder()
        csvData.appendLine(header)

        results.forEach { result ->
            csvData.append("${result.name},")
            if (result.analysisResult != null) {
                for (i in 0 until K_MEANS_CLUSTERS) {
                    val count = result.analysisResult.sortedCounts.getOrNull(i) ?: 0
                    val r = result.analysisResult.sortedCentroids.getOrNull(i)?.`val`?.get(0) ?: 0.0
                    val g = result.analysisResult.sortedCentroids.getOrNull(i)?.`val`?.get(1) ?: 0.0
                    val b = result.analysisResult.sortedCentroids.getOrNull(i)?.`val`?.get(2) ?: 0.0
                    csvData.append("$count,${r.toInt()},${g.toInt()},${b.toInt()},")
                }
            } else {
                for (i in 0 until K_MEANS_CLUSTERS) {
                    csvData.append("0,0,0,0,")
                }
            }
            csvData.setLength(csvData.length - 1) // Remove last comma
            csvData.appendLine()
        }
        return csvData.toString()
    }
}
