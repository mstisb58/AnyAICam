// models/tongue_detector/TfliteHelper.kt
package com.example.AnyAICam.models.tongue_detector

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.IOException

class TfliteHelper(context: Context) {

    private var interpreter: Interpreter? = null

    init {
        try {
            val model = FileUtil.loadMappedFile(context, "checker/tongue_checker.tflite")
            val options = Interpreter.Options()
            val nnApiDelegate = NnApiDelegate()
            options.addDelegate(nnApiDelegate)
            interpreter = Interpreter(model, options)
            Log.i("TfliteHelper", "NNAPI delegate applied.")
        } catch (e: Exception) {
            Log.e("TfliteHelper", "Error loading model with NNAPI delegate", e)
            // Fallback to CPU
            try {
                val model = FileUtil.loadMappedFile(context, "checker/tongue_checker.tflite")
                interpreter = Interpreter(model, Interpreter.Options())
                Log.i("TfliteHelper", "Fell back to CPU interpreter.")
            } catch (ioe: IOException) {
                Log.e("TfliteHelper", "Error loading model for CPU fallback", ioe)
            }
        }
    }

    fun classify(bitmap: Bitmap): Int {
        if (interpreter == null) return 2 // Return a "other" class if interpreter fails

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputBuffer = Array(1) { FloatArray(2) } // 2 classes

        try {
            interpreter?.run(tensorImage.buffer, outputBuffer)
        } catch (e: Exception) {
            Log.e("TfliteHelper", "Error running model inference", e)
            return 2 // Return a "other" class on error
        }

        val result = outputBuffer[0]
        // Return the index of the highest score
        return result.indices.maxByOrNull { result[it] } ?: 2
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}