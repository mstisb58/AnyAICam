package com.example.AnyAICam

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.io.File

class VideoRecorder(width: Int, height: Int, private val outputFile: File) {

    private companion object {
        private const val TAG = "VideoRecorder"
        private const val MIME_TYPE = "video/avc" // H.264/AVC
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 5 // seconds
        private const val BIT_RATE = 6_000_000 // 6 Mbps
    }

    private val mediaCodec: MediaCodec
    private val mediaMuxer: MediaMuxer
    val inputSurface: Surface
    private var isRecording = false
    private var videoTrackIndex = -1
    private val bufferInfo = MediaCodec.BufferInfo()
    private lateinit var encoderThread: Thread

    init {
        // Ensure width and height are even numbers, required by many encoders
        val videoWidth = if (width % 2 == 0) width else width - 1
        val videoHeight = if (height % 2 == 0) height else height - 1

        val format = MediaFormat.createVideoFormat(MIME_TYPE, videoWidth, videoHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = mediaCodec.createInputSurface()
        mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun start() {
        if (isRecording) {
            Log.w(TAG, "start() called while already recording")
            return
        }
        isRecording = true
        encoderThread = Thread { drainEncoder() }
        mediaCodec.start()
        encoderThread.start()
        Log.d(TAG, "Recorder started")
    }

    fun stop() {
        if (!isRecording) {
            Log.w(TAG, "stop() called while not recording")
            return
        }
        isRecording = false
        mediaCodec.signalEndOfInputStream()
        try {
            encoderThread.join() // Wait for encoder thread to finish
        } catch (e: InterruptedException) {
            Log.e(TAG, "Encoder thread join interrupted", e)
        }

        try {
            mediaCodec.stop()
            mediaCodec.release()
            mediaMuxer.stop()
            mediaMuxer.release()
            Log.d(TAG, "Recorder stopped and released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder", e)
        }
    }

    private fun drainEncoder() {
        while (isRecording) {
            val status = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000) // 10ms timeout

            when {
                status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (videoTrackIndex >= 0) {
                        throw RuntimeException("Format changed twice")
                    }
                    videoTrackIndex = mediaMuxer.addTrack(mediaCodec.outputFormat)
                    mediaMuxer.start()
                    Log.d(TAG, "Muxer started")
                }
                status >= 0 -> {
                    val encodedData = mediaCodec.getOutputBuffer(status)
                    if (encodedData == null) {
                        Log.w(TAG, "encodedData was null")
                        continue
                    }

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && bufferInfo.size != 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        mediaMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }

                    mediaCodec.releaseOutputBuffer(status, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        Log.d(TAG, "End of stream reached")
                        break
                    }
                }
            }
        }
    }
}
