// ImgProcessor.kt
package com.example.AnyAICam

import android.content.Context
import android.graphics.Bitmap
import org.opencv.core.Mat

/**
 * すべての画像処理モジュールが実装するインターフェース。
 */
interface ImgProcessor {
    val name: String
    val saveDirectoryName: String

    fun setup(context: Context)
    fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean>
    fun processFrameForSaving(frame: Bitmap): Bitmap
    fun getReportCsv(): String?
}
