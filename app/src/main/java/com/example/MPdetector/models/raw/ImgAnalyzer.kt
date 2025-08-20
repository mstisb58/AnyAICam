// models/raw/ImgAnalyzer.kt
package com.example.MPdetector.models.raw

import android.graphics.Bitmap
import com.example.MPdetector.ImgProcessor
import org.opencv.core.Mat

/**
 * Raw画像をそのまま返すプロセッサ。
 * プレビューや保存のために、未加工の画像を他の処理結果と並べて表示したい場合に使用します。
 */
class ImgAnalyzer : ImgProcessor {
    override val name: String = "Raw"
    override val saveDirectoryName: String = "Raw"

    /**
     * リアルタイム表示用の処理。
     * 何も加工せず、受け取ったフレームをそのまま返します。
     */
    override fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean> {
        // 常にシャッターOKとします。
        return Pair(frame, true)
    }

    /**
     * 保存用の処理。
     * 何も加工せず、受け取ったBitmapをそのまま返します。
     */
    override fun processFrameForSaving(frame: Bitmap): Bitmap {
        return frame
    }
}
