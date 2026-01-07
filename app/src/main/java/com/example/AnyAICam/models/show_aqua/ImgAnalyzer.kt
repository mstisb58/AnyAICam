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

class ImgAnalyzer : ImgProcessor {

    companion object {
        private const val TAG = "ShowAquaImgAnalyzer"
        private const val MODEL_PATH = "mediapipe/face_landmarker.task"

        // 抽出対象のランドマークID (リファクタリング)
        private val TARGET_POLYGONS = mapOf(
            "Right Eyebrow" to listOf(109, 108, 151, 10),
            "Left Eyebrow" to listOf(10, 151, 337, 338)
        )

        // 描画設定
        private val DRAW_COLOR_LANDMARKS = Scalar(0.0, 255.0, 0.0, 255.0) // Green
        private const val DRAW_THICKNESS_LANDMARKS = 3

        // 分析設定
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

    private var faceLandmarker: FaceLandmarker? = null
    private var lastLandmarks: List<NormalizedLandmark>? = null
    private var conversionBitmap: Bitmap? = null

    // 分析結果保持用データクラス
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

    // ポリゴンごとの分析結果を保持するデータクラス (リファクタリング)
    private data class PolygonAnalysisResult(
        val name: String,
        val croppedImage: Mat,
        val analysisResult: AnalysisResult?
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

    // --- メイン処理フロー ---

    override fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean> {
        val landmarks = detectLandmarks(frame)
        landmarks?.let {
            // 定義された全てのポリゴンを描画
            TARGET_POLYGONS.values.forEach { ids ->
                drawPolygon(frame, it, ids)
            }
        }
        return Pair(frame, true)
    }

    override fun processFrameForSaving(frame: Bitmap): Bitmap {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "processFrameForSaving: Start. Bitmap size: ${frame.width}x${frame.height}")

        val inputMat = Mat()
        Utils.bitmapToMat(frame, inputMat)
        val landmarks = detectLandmarks(inputMat)
        val allPolygonResults = mutableListOf<PolygonAnalysisResult>()
        var finalMat: Mat? = null

        try {
            if (landmarks == null) {
                Log.w(TAG, "No landmarks detected. Returning original frame.")
                return frame
            }

            // 1. 各ポリゴンを切り出して分析
            for ((name, ids) in TARGET_POLYGONS) {
                val cropped = extractPolygonRegion(inputMat, landmarks, ids)
                if (cropped != null && cropped.width() >= 10 && cropped.height() >= 10) {
                    val analysis = analyzeColors(cropped)
                    allPolygonResults.add(PolygonAnalysisResult(name, cropped, analysis))
                } else {
                    Log.w(TAG, "Skipping polygon '$name' due to small or empty crop.")
                    cropped?.release() // 不要なMatはすぐに解放
                }
            }

            if (allPolygonResults.isEmpty()) {
                Log.w(TAG, "No valid polygons could be processed.")
                return frame
            }

            // 2. ポリゴンごとにレポート行を生成
            val reportRows = allPolygonResults.map { createReportRow(it) }

            // 3. 全ての行を縦に結合
            finalMat = stackMatsVertically(reportRows)
            reportRows.forEach { it.release() } // 結合後は不要なので解放

            if (finalMat == null || finalMat.empty()) {
                Log.e(TAG, "Final combined image is empty.")
                return frame
            }

            // 4. Bitmapに変換して返す
            val resultBitmap = createBitmap(finalMat.cols(), finalMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(finalMat, resultBitmap)
            Log.d(TAG, "processFrameForSaving: Finished. Total time: ${System.currentTimeMillis() - startTime}ms")
            return resultBitmap

        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR in processFrameForSaving", e)
            return frame
        } finally {
            // 全てのリソースを解放
            inputMat.release()
            allPolygonResults.forEach { it.croppedImage.release() }
            finalMat?.release()
        }
    }

    // --- 内部ロジック (検出・抽出) ---

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

    private fun extractPolygonRegion(frame: Mat, landmarks: List<NormalizedLandmark>, ids: List<Int>): Mat? {
        val points = getLandmarkPoints(frame.cols(), frame.rows(), landmarks, ids)
        if (points.isEmpty()) return null

        val matOfPoint = MatOfPoint().apply { fromList(points) }
        val boundingRect = Imgproc.boundingRect(matOfPoint)

        val safeRect = Rect(0, 0, frame.cols(), frame.rows()).intersect(boundingRect)

        if (safeRect.width <= 0 || safeRect.height <= 0) {
            matOfPoint.release()
            return null
        }

        val roi = frame.submat(safeRect)
        val mask = Mat.zeros(roi.size(), CvType.CV_8UC1)
        val result = Mat()

        try {
            // マスク作成のために、ROIのローカル座標に変換
            val localPoints = points.map { Point(it.x - safeRect.x, it.y - safeRect.y) }
            val localMatOfPoint = MatOfPoint().apply { fromList(localPoints) }

            // マスクで塗りつぶし、元のROIから該当領域をコピー
            Imgproc.fillPoly(mask, listOf(localMatOfPoint), Scalar(255.0))
            roi.copyTo(result, mask)

            localMatOfPoint.release()
            return result
        } finally {
            roi.release()
            mask.release()
            matOfPoint.release()
        }
    }

    // --- 内部ロジック (分析) ---

    private fun analyzeColors(image: Mat): AnalysisResult? {
        if (image.empty()) {
            Log.w(TAG, "analyzeColors: Input image is empty")
            return null
        }

        val rgbMat = Mat()
        val pointsMat = Mat()
        val labels = Mat()
        val centers = Mat()

        try {
            if (image.channels() == 4) {
                Imgproc.cvtColor(image, rgbMat, Imgproc.COLOR_RGBA2RGB)
            } else {
                image.copyTo(rgbMat)
            }

            val numPixels = rgbMat.rows() * rgbMat.cols()
            val data = ByteArray(numPixels * rgbMat.channels())
            rgbMat.get(0, 0, data)

            val validPixels = ArrayList<Float>()
            for (i in 0 until numPixels) {
                val index = i * rgbMat.channels()
                val r = data[index].toInt() and 0xFF
                val g = data[index + 1].toInt() and 0xFF
                val b = data[index + 2].toInt() and 0xFF
                if (r > 10 || g > 10 || b > 10) {
                    validPixels.add(r.toFloat())
                    validPixels.add(g.toFloat())
                    validPixels.add(b.toFloat())
                }
            }

            if (validPixels.size / 3 < K_MEANS_CLUSTERS) {
                Log.w(TAG, "analyzeColors: Not enough data for K-means. Need ${K_MEANS_CLUSTERS}, got ${validPixels.size / 3}")
                return null
            }

            pointsMat.create(validPixels.size / 3, 3, CvType.CV_32F)
            pointsMat.put(0, 0, validPixels.toFloatArray())

            val criteria = TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 100, 0.1)
            Core.kmeans(pointsMat, K_MEANS_CLUSTERS, labels, criteria, 3, Core.KMEANS_PP_CENTERS, centers)

            // 各クラスタのピクセル数を数える
            val counts = IntArray(K_MEANS_CLUSTERS)
            val labelsArray = IntArray(labels.rows() * labels.cols())
            labels.get(0, 0, labelsArray)
            labelsArray.forEach { counts[it]++ }

            // 重心と元のインデックスをペアにする
            val indexedCentroids = (0 until centers.rows()).map { i ->
                val centroid = Scalar(centers.get(i, 0)[0], centers.get(i, 1)[0], centers.get(i, 2)[0])
                Pair(centroid, i)
            }

            // 明るさでソート
            val sortedIndexedCentroids = indexedCentroids.sortedBy { (it.first.`val`[0] + it.first.`val`[1] + it.first.`val`[2]) }

            // ソートされた順に重心とカウントをリスト化
            val sortedCentroids = sortedIndexedCentroids.map { it.first }
            val sortedCounts = sortedIndexedCentroids.map { counts[it.second] }

            return AnalysisResult(sortedCentroids, sortedCounts, labelsArray.size)

        } catch (e: Exception) {
            Log.e(TAG, "analyzeColors: Exception occurred", e)
            return null
        } finally {
            rgbMat.release()
            pointsMat.release()
            labels.release()
            centers.release()
        }
    }

    // --- 内部ロジック (レポート生成) ---

    private fun createReportRow(result: PolygonAnalysisResult): Mat {
        val imgWidth = 400
        val graphWidth = 250
        val textWidth = 250
        val rowHeight = 250

        // 1. 切り抜き画像
        val resizedImage = Mat()
        Imgproc.resize(result.croppedImage, resizedImage, Size(imgWidth.toDouble(), rowHeight.toDouble()))

        // 2. グラフ画像
        val graphMat = createReportGraph(result.analysisResult, Size(graphWidth.toDouble(), rowHeight.toDouble()))

        // 3. テキスト画像
        val textMat = createReportText(result.name, result.analysisResult, Size(textWidth.toDouble(), rowHeight.toDouble()))

        // 4. 横に結合
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

        // グラフエリア背景と枠線
        Imgproc.rectangle(graphMat, Point(margin.toDouble(), margin.toDouble()), Point((origin.x + graphWidth), origin.y - graphHeight), Scalar(250.0, 250.0, 250.0, 255.0), -1)
        Imgproc.rectangle(graphMat, Point(margin.toDouble(), margin.toDouble()), Point((origin.x + graphWidth), origin.y), COLOR_GRAY, 1)

        val stepX = graphWidth.toDouble() / (K_MEANS_CLUSTERS - 1).coerceAtLeast(1)

        // --- 棒グラフの描画 ---
        val maxCount = result.sortedCounts.maxOrNull()?.toDouble() ?: 1.0
        val barColor = Scalar(200.0, 200.0, 200.0, 200.0) // Semi-transparent gray
        result.sortedCounts.forEachIndexed { index, count ->
            val barHeight = (count / maxCount) * graphHeight
            val x = origin.x + index * stepX
            Imgproc.rectangle(
                graphMat,
                Point(x - stepX / 2.5, origin.y - barHeight),
                Point(x + stepX / 2.5, origin.y),
                barColor, -1
            )
        }

        // --- 折れ線グラフの描画 ---
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

        // Y軸の目盛りを描画
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
            val median = result.getMedianCentroid()
            val rVal = median.`val`[0]
            val gVal = median.`val`[1]
            val bVal = median.`val`[2]

            val denominator = rVal - gVal
            val moistureValue: Double = if (denominator != 0.0) {
                (bVal - gVal) / denominator
            } else {
                Double.NaN
            }

            val moistureText = if (moistureValue.isNaN()) {
                "Moisture: N/A"
            } else {
                String.format(Locale.US, "Moisture: %.1f", moistureValue)
            }
            Imgproc.putText(textMat, "Median Analysis (5/9)", Point(textStartX, lineHeight * 2), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, COLOR_BLACK, 1)
            Imgproc.putText(textMat, moistureText, Point(textStartX, lineHeight * 3), Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, COLOR_BLACK, 2)
        } else {
            Imgproc.putText(textMat, "No data", Point(textStartX, lineHeight * 2.5), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, COLOR_BLACK, 1)
        }
        return textMat
    }

    // --- ヘルパー関数 ---

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
                val y = (height - mat.height()) / 2 // 中央揃え
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
