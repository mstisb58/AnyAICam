// ProcessorRepository.kt
package com.example.MPdetector

import android.util.Log

/**
 * アプリで利用可能な画像処理モジュールのリストを提供するリポジトリ。
 * リフレクションを用いて、プロセッサクラスを動的に読み込みます。
 */
object ProcessorRepository {

    // 新しいプロセッサを追加する際は、このリストに「完全修飾クラス名」の文字列を追加します。
    private val processorClassNames = listOf(
        "com.example.MPdetector.models.raw.ImgAnalyzer",
        "com.example.MPdetector.models.wink_detector.ImgAnalyzer",
        "com.example.MPdetector.models.tongue_detector.ImgAnalyzer"
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
