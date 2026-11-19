package com.neurafit.app.realtime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame

object BitmapVideoFrameConverter {
    fun toVideoFrame(
        bitmap: Bitmap,
        rotationDegrees: Int,
        mirrorHorizontally: Boolean,
        timestampNs: Long,
        maxLongEdge: Int = 720,
    ): VideoFrame {
        val preparedBitmap = bitmap.prepareForWebRtc(
            rotationDegrees = rotationDegrees,
            mirrorHorizontally = mirrorHorizontally,
            maxLongEdge = maxLongEdge,
        )
        val buffer = preparedBitmap.toI420Buffer()
        preparedBitmap.recycle()
        return VideoFrame(buffer, 0, timestampNs)
    }

    private fun Bitmap.prepareForWebRtc(
        rotationDegrees: Int,
        mirrorHorizontally: Boolean,
        maxLongEdge: Int,
    ): Bitmap {
        val rotatedWidth = if (rotationDegrees % 180 == 0) width else height
        val rotatedHeight = if (rotationDegrees % 180 == 0) height else width
        val scale = if (maxOf(rotatedWidth, rotatedHeight) > maxLongEdge) {
            maxLongEdge.toFloat() / maxOf(rotatedWidth, rotatedHeight).toFloat()
        } else {
            1f
        }

        val targetWidth = (rotatedWidth * scale).toInt().coerceAtLeast(2).makeEven()
        val targetHeight = (rotatedHeight * scale).toInt().coerceAtLeast(2).makeEven()
        val matrix = Matrix().apply {
            postTranslate(-width / 2f, -height / 2f)
            postRotate(rotationDegrees.toFloat())
            if (mirrorHorizontally) {
                postScale(-1f, 1f)
            }
            postScale(scale, scale)
            postTranslate(targetWidth / 2f, targetHeight / 2f)
        }

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(
            this,
            matrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return output
    }

    private fun Bitmap.toI420Buffer(): JavaI420Buffer {
        val output = JavaI420Buffer.allocate(width, height)
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)

        val dataY = output.dataY
        val dataU = output.dataU
        val dataV = output.dataV
        val strideY = output.strideY
        val strideU = output.strideU
        val strideV = output.strideV

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val red = pixel shr 16 and 0xFF
                val green = pixel shr 8 and 0xFF
                val blue = pixel and 0xFF
                val luma = ((66 * red + 129 * green + 25 * blue + 128) shr 8) + 16
                dataY.put(y * strideY + x, luma.coerceIn(0, 255).toByte())

                if (y % 2 == 0 && x % 2 == 0) {
                    val chromaU = ((-38 * red - 74 * green + 112 * blue + 128) shr 8) + 128
                    val chromaV = ((112 * red - 94 * green - 18 * blue + 128) shr 8) + 128
                    dataU.put((y / 2) * strideU + (x / 2), chromaU.coerceIn(0, 255).toByte())
                    dataV.put((y / 2) * strideV + (x / 2), chromaV.coerceIn(0, 255).toByte())
                }
            }
        }

        return output
    }

    private fun Int.makeEven(): Int =
        if (this % 2 == 0) this else this - 1
}
