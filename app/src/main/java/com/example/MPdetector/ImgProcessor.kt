// ImgProcessor.kt
package com.example.MPdetector

import android.content.Context
import android.graphics.Bitmap
import org.opencv.core.Mat
import java.io.File

/**
 * すべての画像処理モジュールが実装するインターフェース。
 */
interface ImgProcessor {
    /**
     * プロセッサーの名前。UIの選択肢などで使用されます。
     */
    val name: String

    /**
     * このプロセッサーが画像を保存する際のサブディレクトリ名。
     * 例: "wink_detector_results"
     */
    val saveDirectoryName: String

    /**
     * 初期化処理。モデルの読み込みなど、重い処理をここで行います。
     * @param context アプリケーションコンテキスト。アセットファイルの読み込みなどに使用します。
     */
    fun setup(context: Context) {
        // デフォルトでは何もしない。必要なクラスのみオーバーライドする。
    }

    /**
     * カメラのリアルタイムプレビュー用にフレームを処理します。
     * 処理は高速であることが求められます。
     *
     * @param frame カメラからの入力フレーム (OpenCVのMat形式)
     * @return 処理後のフレーム(Mat)と、処理が成功したか（シャッターを押せる状態か）を示すステータス(Boolean)のペア。
     */
    fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean>

    /**
     * 最終的に保存するための画像を生成します。
     * こちらはプレビューと異なり、時間をかけて精度の高い処理を行うことも可能です。
     *
     * @param frame 撮影された元の高解像度フレーム (Bitmap形式)
     * @return 処理後の画像 (Bitmap形式)
     */
    fun processFrameForSaving(frame: Bitmap): Bitmap
}
