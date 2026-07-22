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
                "LFace_01" to listOf(109, 108, 151, 10),
                "LFace_02" to listOf(67, 69, 108, 109),
                "LFace_03" to listOf(103, 68, 69, 67),
                "LFace_04" to listOf(46, 124, 222, 65),
                "LFace_05" to listOf(65, 222, 193, 55),
                "LFace_06" to listOf(70, 143, 124, 46),
                "LFace_07" to listOf(143, 117, 31, 124),
                "LFace_08" to listOf(31, 117, 119, 230),
                "LFace_09" to listOf(230, 119, 174, 245),
                "LFace_10" to listOf(143, 187, 205, 117),
                "LFace_11" to listOf(117, 205, 36, 119),
                "LFace_12" to listOf(119, 36, 220, 174),
                "LFace_13" to listOf(174, 220, 1, 197),
                "LFace_14" to listOf(245, 174, 197, 6),
                "LFace_15" to listOf(193, 245, 6, 168),
                "LFace_16" to listOf(55, 193, 168, 8),
                "LFace_17" to listOf(108, 55, 8, 151),
                "LFace_18" to listOf(187, 214, 57, 205),
                "LFace_19" to listOf(205, 57, 186, 36),
                "LFace_20" to listOf(36, 186, 235, 220),
                "LFace_21" to listOf(235, 186, 37, 167),
                "LFace_22" to listOf(167, 37, 0, 164),
                "LFace_23" to listOf(214, 211, 182, 57),
                "LFace_24" to listOf(211, 199, 18, 182),
                "RFace_01" to listOf(10, 151, 337, 338),
                "RFace_02" to listOf(338, 337, 299, 297),
                "RFace_03" to listOf(297, 299, 298, 332),
                "RFace_04" to listOf(151, 8, 285, 337),
                "RFace_05" to listOf(8, 168, 417, 285),
                "RFace_06" to listOf(285, 417, 442, 295),
                "RFace_07" to listOf(295, 442, 353, 276),
                "RFace_08" to listOf(276, 353, 372, 300),
                "RFace_09" to listOf(261, 346, 372, 353),
                "RFace_10" to listOf(450, 348, 346, 261),
                "RFace_11" to listOf(465, 399, 348, 450),
                "RFace_12" to listOf(6, 197, 399, 465),
                "RFace_13" to listOf(168, 6, 465, 417),
                "RFace_14" to listOf(197, 1, 440, 399),
                "RFace_15" to listOf(399, 440, 266, 348),
                "RFace_16" to listOf(348, 266, 425, 346),
                "RFace_17" to listOf(346, 425, 411, 372),
                "RFace_18" to listOf(440, 455, 410, 266),
                "RFace_19" to listOf(266, 410, 287, 425),
                "RFace_20" to listOf(425, 287, 434, 411),
                "RFace_21" to listOf(393, 267, 410, 455),
                "RFace_22" to listOf(164, 0, 267, 393),
                "RFace_23" to listOf(287, 406, 431, 434),
                "RFace_24" to listOf(18, 199, 431, 406)
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

        val COLOR_MAPS = mapOf(
            "JET" to Imgproc.COLORMAP_JET,
            "VIRIDIS" to Imgproc.COLORMAP_VIRIDIS,
            "PLASMA" to Imgproc.COLORMAP_PLASMA,
            "INFERNO" to Imgproc.COLORMAP_INFERNO,
            "MAGMA" to Imgproc.COLORMAP_MAGMA,
            "HOT" to Imgproc.COLORMAP_HOT,
            "YlGnBu" to -100 // Custom implementation
        )
        private const val COLORMAP_CUSTOM_YLGNBU = -100
    }

    private fun getYlGnBuLut(): Mat {
        val lut = Mat(256, 1, CvType.CV_8UC3)
        val colors = arrayOf(
            doubleArrayOf(217.0, 255.0, 255.0), // BGR for #ffffd9
            doubleArrayOf(177.0, 248.0, 237.0), // #edf8b1
            doubleArrayOf(180.0, 233.0, 199.0), // #c7e9b4
            doubleArrayOf(187.0, 205.0, 127.0), // #7fcdbb
            doubleArrayOf(196.0, 182.0, 65.0),  // #41b6c4
            doubleArrayOf(192.0, 145.0, 29.0),  // #1d91c0
            doubleArrayOf(168.0, 94.0, 34.0),   // #225ea8
            doubleArrayOf(148.0, 52.0, 37.0),   // #253494
            doubleArrayOf(88.0, 29.0, 8.0)      // #081d58
        )

        for (i in 0 until 256) {
            val findex = i / 255.0 * (colors.size - 1)
            val idx = findex.toInt()
            val frac = findex - idx
            val c1 = colors[idx]
            val c2 = if (idx < colors.size - 1) colors[idx + 1] else colors[idx]

            val b = c1[0] * (1 - frac) + c2[0] * frac
            val g = c1[1] * (1 - frac) + c2[1] * frac
            val r = c1[2] * (1 - frac) + c2[2] * frac

            lut.put(i, 0, b, g, r)
        }
        return lut
    }

    private fun applyColorMapWithCustom(src: Mat, dst: Mat, colorMap: Int) {
        if (colorMap == COLORMAP_CUSTOM_YLGNBU) {
            val lut = getYlGnBuLut()
            Imgproc.applyColorMap(src, dst, lut)
            lut.release()
        } else {
            Imgproc.applyColorMap(src, dst, colorMap)
        }
    }

    fun generateColorbarBitmap(colorMap: Int, width: Int, height: Int): Bitmap {
        val gradientMat = Mat(1, 256, CvType.CV_8UC1)
        val gradientData = ByteArray(256)
        for (i in 0 until 256) {
            gradientData[i] = i.toByte()
        }
        gradientMat.put(0, 0, gradientData)

        val colorGradient = Mat()
        applyColorMapWithCustom(gradientMat, colorGradient, colorMap)

        val resizedGradient = Mat()
        Imgproc.resize(colorGradient, resizedGradient, Size(width.toDouble(), height.toDouble()))

        val resizedGradientRGBA = Mat()
        Imgproc.cvtColor(resizedGradient, resizedGradientRGBA, Imgproc.COLOR_BGR2RGBA)

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(resizedGradientRGBA, bmp)

        gradientMat.release()
        colorGradient.release()
        resizedGradient.release()
        resizedGradientRGBA.release()

        return bmp
    }

    override val name: String = "show_aqua"
    override val saveDirectoryName: String = "show_aqua_results"

    enum class OperatingMode {
        REPORT,
        HEATMAP
    }
    var operatingMode: OperatingMode = OperatingMode.REPORT
    var heatmapMinMoisture: Double = 0.5
    var heatmapMaxMoisture: Double = 1.5
    var heatmapColorMap: Int = Imgproc.COLORMAP_JET
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

        val range = heatmapMaxMoisture - heatmapMinMoisture

        for (result in results) {
            val moisture = calculateMoistureValue(result.analysisResult)
            if (!moisture.isNaN()) {
                val normalizedValue = if (range.absoluteValue < 1e-6) {
                    0.0
                } else {
                    ((moisture - heatmapMinMoisture) / range * 255.0)
                }.coerceIn(0.0, 255.0).toInt().toByte()

                val valueMat = Mat(1, 1, CvType.CV_8UC1)
                valueMat.put(0, 0, byteArrayOf(normalizedValue))
                val colorMat = Mat()
                applyColorMapWithCustom(valueMat, colorMat, heatmapColorMap)
                val colorScalarBGR = Scalar(colorMat.get(0, 0))
                val colorScalarBGRA = Scalar(
                    colorScalarBGR.`val`[2], // Red (OpenCVの2番目) を 0番目へ
                    colorScalarBGR.`val`[1], // Green
                    colorScalarBGR.`val`[0], // Blue (OpenCVの0番目) を 2番目へ
                    255.0
                )

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
        applyColorMapWithCustom(gradientMat, colorGradient, heatmapColorMap)
        val resizedGradient = Mat()
        Imgproc.resize(colorGradient, resizedGradient, Size(colorbarRoi.cols().toDouble(), (colorbarHeight - textHeight).toDouble()))
        val resizedGradientBGRA = Mat()
        Imgproc.cvtColor(resizedGradient, resizedGradientBGRA, Imgproc.COLOR_BGR2RGBA)
        val gradientTargetRoi = colorbarRoi.submat(0, resizedGradientBGRA.rows(), 0, resizedGradientBGRA.cols())
        resizedGradientBGRA.copyTo(gradientTargetRoi)
        gradientTargetRoi.release()
        val fontScale = 0.8
        val thickness = 2
        val textColor = COLOR_BLACK
        val textY = image.rows() + resizedGradientBGRA.rows() + textHeight - 10
        val minText = String.format(Locale.US, "%.1f (Dry)", minVal)
        Imgproc.putText(resultMat, minText, Point(10.0, textY.toDouble()), Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, textColor, thickness)
        val maxText = String.format(Locale.US, "%.1f (Moisture)", maxVal)
        val maxTextSize = Imgproc.getTextSize(maxText, Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, thickness, null)
        Imgproc.putText(resultMat, maxText, Point((resultMat.cols() - maxTextSize.width - 10), textY.toDouble()), Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, textColor, thickness)
        val midVal = (minVal + maxVal) / 2
        val midText = String.format(Locale.US, "%.1f", midVal)
        val midTextSize = Imgproc.getTextSize(midText, Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, thickness, null)
        //Imgproc.putText(resultMat, midText, Point((resultMat.cols() / 2 - midTextSize.width / 2), textY.toDouble()), Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, textColor, thickness)
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
            (gVal / denominator)
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
            // Optimize by sampling pixels if there are too many
            val maxPixelsToAnalyze = 10000
            val sampleStep = (numPixels / maxPixelsToAnalyze).coerceAtLeast(1)

            for (i in 0 until numPixels step sampleStep) {
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
        // 【修正1】背景を「完全不透明な白」で初期化 (Alpha=255.0)
        val opaqueWhite = Scalar(255.0, 255.0, 255.0, 255.0)
        val graphMat = Mat(size, CvType.CV_8UC4, opaqueWhite)

        if (result == null) {
            Imgproc.putText(graphMat, "Analysis Failed", Point(size.width / 2 - 100, size.height / 2), Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, COLOR_BLACK, 2)
            return graphMat
        }

        val margin = 40
        val graphWidth = size.width.toInt() - margin * 2
        val graphHeight = size.height.toInt() - margin * 2
        val origin = Point(margin.toDouble(), (size.height - margin).toDouble())

        // グラフエリアの背景（ごく薄いグレー、不透明）
        Imgproc.rectangle(graphMat, Point(margin.toDouble(), margin.toDouble()), Point((origin.x + graphWidth), origin.y - graphHeight), Scalar(250.0, 250.0, 250.0, 255.0), -1)
        // 枠線
        Imgproc.rectangle(graphMat, Point(margin.toDouble(), margin.toDouble()), Point((origin.x + graphWidth), origin.y), COLOR_GRAY, 1)

        val stepX = graphWidth.toDouble() / (K_MEANS_CLUSTERS - 1).coerceAtLeast(1)
        val maxCount = result.sortedCounts.maxOrNull()?.toDouble() ?: 1.0

        // 【修正2】棒グラフの色：RGBはグレー(200)のまま、Alphaを255(不透明)に変更
        // これにより背景が透けなくなります
        val barColor = Scalar(200.0, 200.0, 200.0, 255.0)

        result.sortedCounts.forEachIndexed { index, count ->
            val barHeight = (count / maxCount) * graphHeight
            val x = origin.x + index * stepX
            // 棒グラフを描画（不透明なグレーで上書き）
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

            // X軸インデックス
            Imgproc.putText(graphMat, "${index + 1}", Point(x - 5, origin.y + 15), Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, COLOR_BLACK, 1)
        }

        // 折れ線グラフと点（COLOR_R等はAlpha255の定義であることを前提としています）
        drawPolyline(graphMat, pointsR, COLOR_R)
        drawPolyline(graphMat, pointsG, COLOR_G)
        drawPolyline(graphMat, pointsB, COLOR_B)

        pointsR.forEach { Imgproc.circle(graphMat, it, 3, COLOR_R, -1) }
        pointsG.forEach { Imgproc.circle(graphMat, it, 3, COLOR_G, -1) }
        pointsB.forEach { Imgproc.circle(graphMat, it, 3, COLOR_B, -1) }

        // Y軸ラベル
        Imgproc.putText(graphMat, "255", Point(margin - 35.0, margin + 5.0), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, COLOR_BLACK, 1)
        Imgproc.putText(graphMat, "128", Point(margin - 35.0, origin.y - 128.0 * scaleY + 5.0), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, COLOR_GRAY, 1)
        Imgproc.putText(graphMat, "0", Point(margin - 20.0, origin.y + 5.0), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, COLOR_BLACK, 1)

        return graphMat
    }

    private fun createReportText(name: String, result: AnalysisResult?, size: Size): Mat {
        // 【修正1】こちらも同様に完全不透明な白で初期化
        val opaqueWhite = Scalar(255.0, 255.0, 255.0, 255.0)
        val textMat = Mat(size, CvType.CV_8UC4, opaqueWhite)

        val textStartX = 20.0
        val lineHeight = 35.0

        Imgproc.putText(textMat, name, Point(textStartX, lineHeight), Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, COLOR_BLACK, 2)

        if (result != null) {
            val moistureValue = calculateMoistureValue(result)
            val moistureText = if (moistureValue.isNaN()) "Moisture: N/A" else String.format(Locale.US, "Moisture: %.2f", moistureValue)

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
