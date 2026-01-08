package com.example.AnyAICam.models.raw

import android.content.Context
import android.graphics.Bitmap
import com.example.AnyAICam.ImgProcessor
import org.opencv.core.Mat

class ImgAnalyzer : ImgProcessor {
    override val name: String = "raw"
    override val saveDirectoryName: String = "raw_images"

    override fun setup(context: Context) {
        // No setup needed for raw image processor
    }

    override fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean> {
        return Pair(frame, true)
    }

    override fun processFrameForSaving(frame: Bitmap): Bitmap {
        return frame
    }

    override fun getReportCsv(): String? {
        // This processor does not generate a CSV report.
        return null
    }
}