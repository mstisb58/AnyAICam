// models/tongue_detector/TfliteHelper.kt
package com.example.MPdetector.models.tongue_detector

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
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
            val model = FileUtil.loadMappedFile(context, "checker/tangue_discriminator.tflite")
            interpreter = Interpreter(model, Interpreter.Options())
        } catch (e: IOException) {
            Log.e("TfliteHelper", "Error loading model", e)
        }
    }

    fun classify(bitmap: Bitmap): Boolean {
        if (interpreter == null) return false

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputBuffer = Array(1) { FloatArray(2) }

        try {
            interpreter?.run(tensorImage.buffer, outputBuffer)
        } catch (e: Exception) {
            Log.e("TfliteHelper", "Error running model inference", e)
            return false
        }

        val result = outputBuffer[0]
        return result[1] > 0.5
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
