// ProcessorRepository.kt
package com.example.AnyAICam

import android.util.Log

/**
 * アプリで利用可能な画像処理モジュールのリストを提供するリポジトリ。
 * リフレクションを用いて、プロセッサクラスを動的に読み込みます。
 */
object ProcessorRepository {

    // 新しいプロセッサを追加する際は、このリストに「完全修飾クラス名」の文字列を追加します。
    private val processorClassNames = listOf(
        "com.example.AnyAICam.models.raw.ImgAnalyzer",
        "com.example.AnyAICam.models.face_detector.ImgAnalyzer",
        "com.example.AnyAICam.models.pose_detector.ImgAnalyzer",
        "com.example.AnyAICam.models.tongue_detector.ImgAnalyzer",
        "com.example.AnyAICam.models.wink_detector.ImgAnalyzer",
    )

    private var cachedProcessors: List<ImgProcessor>? = null

    /**
     * 利用可能なすべての画像処理モジュールのリストを返します。
     */
    fun getProcessors(): List<ImgProcessor> {
        if (cachedProcessors != null) {
            return cachedProcessors!!
        }

        val processorList = mutableListOf<ImgProcessor>()
        processorClassNames.forEach { className ->
            try {
                val clazz = Class.forName(className)
                val processor = clazz.newInstance() as ImgProcessor
                processorList.add(processor)
                Log.d("ProcessorRepository", "Successfully loaded processor: $className")
            } catch (e: Exception) {
                Log.e("ProcessorRepository", "Failed to load processor: $className", e)
            }
        }
        cachedProcessors = processorList
        return processorList
    }
}
