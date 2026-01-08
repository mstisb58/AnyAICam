package com.example.AnyAICam.models.wink_detector

import android.content.Context
import android.graphics.Bitmap
import com.example.AnyAICam.ImgProcessor
import org.opencv.core.Mat

class ImgAnalyzer : ImgProcessor {
    override val name: String = "wink_detector"
    override val saveDirectoryName: String = "wink_detector_results"

    override fun setup(context: Context) {
        // No setup needed for this simple version
    }

    override fun processFrameForDisplay(frame: Mat): Pair<Mat, Boolean> {
        // Just return the original frame
        return Pair(frame, true)
    }

    override fun processFrameForSaving(frame: Bitmap): Bitmap {
        // Just return the original frame
        return frame
    }

    override fun getReportCsv(): String? {
        return null
    }
}