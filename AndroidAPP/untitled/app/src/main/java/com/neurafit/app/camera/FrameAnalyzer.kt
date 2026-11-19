package com.neurafit.app.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.neurafit.app.pose.PoseLandmarkerHelper
import com.neurafit.app.pose.PoseOverlayResult
import com.neurafit.app.realtime.RealtimeVideoFrameSink

class FrameAnalyzer(
    context: Context,
    initialOnPoseResult: (PoseOverlayResult?) -> Unit = {},
    private var realtimeVideoFrameSink: RealtimeVideoFrameSink? = null,
) : ImageAnalysis.Analyzer {
    private val poseLandmarkerHelper = PoseLandmarkerHelper(
        context = context,
        onPoseResult = initialOnPoseResult,
    )
    var onPoseResult: (PoseOverlayResult?) -> Unit = initialOnPoseResult
        set(value) {
            field = value
            poseLandmarkerHelper.onPoseResult = value
        }
    private var lastAcceptedFrameAtMs = 0L

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAcceptedFrameAtMs < FRAME_INTERVAL_MS) {
                return
            }
            lastAcceptedFrameAtMs = now

            val bitmap = imageProxy.copyToBitmap()
            realtimeVideoFrameSink?.onCameraFrame(
                bitmap = bitmap,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                mirrorHorizontally = true,
                timestampNs = SystemClock.elapsedRealtimeNanos(),
            )
            poseLandmarkerHelper.detectBitmapLiveStream(
                bitmap = bitmap,
                mirrorHorizontally = true,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees,
            )
        } catch (throwable: Throwable) {
            // CameraX will continue with the next frame.
        } finally {
            imageProxy.close()
        }
    }

    fun updateRealtimeVideoFrameSink(sink: RealtimeVideoFrameSink?) {
        realtimeVideoFrameSink = sink
    }

    fun close() {
        poseLandmarkerHelper.close()
    }

    private fun ImageProxy.copyToBitmap(): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        buffer.rewind()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        if (pixelStride == BYTES_PER_RGBA_PIXEL && rowStride == width * pixelStride) {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.copyPixelsFromBuffer(buffer)
            }
        }

        // CameraX RGBA planes can include row padding; copy per pixel when stride is unusual.
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        val capacity = buffer.capacity()
        for (y in 0 until height) {
            val rowOffset = y * rowStride
            for (x in 0 until width) {
                val pixelOffset = rowOffset + x * pixelStride
                if (pixelOffset + 3 >= capacity) continue

                val red = buffer.get(pixelOffset).toInt() and 0xFF
                val green = buffer.get(pixelOffset + 1).toInt() and 0xFF
                val blue = buffer.get(pixelOffset + 2).toInt() and 0xFF
                val alpha = buffer.get(pixelOffset + 3).toInt() and 0xFF
                pixels[y * width + x] = Color.argb(alpha, red, green, blue)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private companion object {
        const val FRAME_INTERVAL_MS = 66L
        const val BYTES_PER_RGBA_PIXEL = 4
    }
}
