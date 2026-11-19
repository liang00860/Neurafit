package com.neurafit.app.food

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.roundToInt

class FoodClassifier(
    context: Context,
) : Closeable {
    private val appContext = context.applicationContext
    private val labelMap: FoodAiLabelMap = loadLabelMap()
    private val calorieInfo: Map<String, Int> = loadCalorieInfo()
    private val interpreter: Interpreter = Interpreter(
        loadModelFile(),
        Interpreter.Options().setNumThreads(NUM_THREADS),
    )

    fun classify(bitmap: Bitmap): FoodClassifyResult {
        val detections = detectAcrossInferenceWindows(bitmap)
        if (detections.isEmpty()) {
            throw FoodClassifierException("未识别到食物，请重新拍摄清晰的食物照片")
        }

        return FoodLabelMapper.toClassifyResult(
            detections = detections,
            caloriesFor = { label -> FoodAiCalorieTable.caloriesFor(label, calorieInfo) },
            dataSourceLabel = DATA_SOURCE_LABEL,
        )
    }

    override fun close() {
        interpreter.close()
    }

    private fun detectAcrossInferenceWindows(bitmap: Bitmap): List<FoodAiDetection> {
        val rawDetections = buildInferenceWindows(bitmap).flatMap { imageWindow ->
            val windowBitmap = bitmapForWindow(bitmap, imageWindow)
            try {
                detectOnBitmap(windowBitmap, imageWindow)
            } finally {
                if (windowBitmap !== bitmap) {
                    windowBitmap.recycle()
                }
            }
        }

        return FoodAiDetectionSelector
            .suppressOverlaps(
                detections = rawDetections,
                iouThreshold = NMS_IOU_THRESHOLD,
                maxResults = MAX_CANDIDATE_DETECTIONS,
            )
            .take(MAX_FOOD_ITEMS)
    }

    private fun detectOnBitmap(
        bitmap: Bitmap,
        imageWindow: FoodAiImageWindow,
    ): List<FoodAiDetection> {
        val inputBuffer = bitmapToFoodAiInput(bitmap)
        val outputLocations = Array(1) { Array(NUM_DETECTIONS) { FloatArray(BOX_COORDINATES) } }
        val outputClasses = Array(1) { FloatArray(NUM_DETECTIONS) }
        val outputScores = Array(1) { FloatArray(NUM_DETECTIONS) }
        val outputCount = FloatArray(1)
        val outputs = hashMapOf<Int, Any>(
            0 to outputLocations,
            1 to outputClasses,
            2 to outputScores,
            3 to outputCount,
        )

        interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

        val modelDetectionCount = outputCount.firstOrNull()
            ?.roundToInt()
            ?.takeIf { it > 0 }
            ?: NUM_DETECTIONS
        return FoodAiDetectionSelector.selectAll(
            labelMap = labelMap,
            classes = outputClasses[0],
            scores = outputScores[0],
            locations = outputLocations[0],
            minConfidence = MIN_CONFIDENCE,
            detectionCount = modelDetectionCount.coerceAtMost(NUM_DETECTIONS),
            imageWindow = imageWindow,
        )
    }

    private fun buildInferenceWindows(bitmap: Bitmap): List<FoodAiImageWindow> {
        return buildList {
            add(FoodAiImageWindow.Full)
            centeredSquareWindow(bitmap)?.let(::add)
            addAll(EXTRA_INFERENCE_WINDOWS)
        }.distinct()
    }

    private fun centeredSquareWindow(bitmap: Bitmap): FoodAiImageWindow? {
        val width = bitmap.width.takeIf { it > 0 } ?: return null
        val height = bitmap.height.takeIf { it > 0 } ?: return null
        val largerSide = maxOf(width, height).toFloat()
        val smallerSide = minOf(width, height).toFloat()
        if (largerSide / smallerSide < MIN_ASPECT_FOR_SQUARE_CROP) return null

        return if (width > height) {
            val cropWidth = height.toFloat() / width
            val horizontalInset = (1f - cropWidth) / 2f
            FoodAiImageWindow(
                name = "center_square",
                left = horizontalInset,
                top = 0f,
                right = 1f - horizontalInset,
                bottom = 1f,
            )
        } else {
            val cropHeight = width.toFloat() / height
            val verticalInset = (1f - cropHeight) / 2f
            FoodAiImageWindow(
                name = "center_square",
                left = 0f,
                top = verticalInset,
                right = 1f,
                bottom = 1f - verticalInset,
            )
        }
    }

    private fun bitmapForWindow(
        bitmap: Bitmap,
        imageWindow: FoodAiImageWindow,
    ): Bitmap {
        if (imageWindow == FoodAiImageWindow.Full) return bitmap

        val left = (imageWindow.left * bitmap.width)
            .roundToInt()
            .coerceIn(0, bitmap.width - 1)
        val top = (imageWindow.top * bitmap.height)
            .roundToInt()
            .coerceIn(0, bitmap.height - 1)
        val right = (imageWindow.right * bitmap.width)
            .roundToInt()
            .coerceIn(left + 1, bitmap.width)
        val bottom = (imageWindow.bottom * bitmap.height)
            .roundToInt()
            .coerceIn(top + 1, bitmap.height)

        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            right - left,
            bottom - top,
        )
    }

    private fun bitmapToFoodAiInput(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        try {
            resizedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        } finally {
            if (resizedBitmap !== bitmap) {
                resizedBitmap.recycle()
            }
        }

        val inputBuffer = ByteBuffer
            .allocateDirect(INPUT_BYTES)
            .order(ByteOrder.nativeOrder())
        pixels.forEach { pixel ->
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            inputBuffer.putFloat((red - IMAGE_MEAN) / IMAGE_STD)
            inputBuffer.putFloat((green - IMAGE_MEAN) / IMAGE_STD)
            inputBuffer.putFloat((blue - IMAGE_MEAN) / IMAGE_STD)
        }
        inputBuffer.rewind()
        return inputBuffer
    }

    private fun loadLabelMap(): FoodAiLabelMap {
        return runCatching {
            appContext.assets.open(LABEL_FILE_NAME)
                .bufferedReader()
                .useLines(FoodAiLabelMap::parse)
        }.getOrElse { throwable ->
            if (throwable is FileNotFoundException) {
                throw FoodClassifierException("未找到 food_labelmap.txt，请检查 Food.AI 标签文件", throwable)
            }
            if (throwable is FoodClassifierException) throw throwable
            throw FoodClassifierException("Food.AI 标签文件加载失败，请检查 food_labelmap.txt", throwable)
        }
    }

    private fun loadCalorieInfo(): Map<String, Int> {
        return runCatching {
            appContext.assets.open(CALORIE_FILE_NAME)
                .bufferedReader()
                .useLines(FoodAiCalorieTable::parse)
        }.getOrElse { throwable ->
            if (throwable is FileNotFoundException) {
                throw FoodClassifierException("未找到 calorie_info.txt，请检查 Food.AI 热量表", throwable)
            }
            throw FoodClassifierException("Food.AI 热量表加载失败，请检查 calorie_info.txt", throwable)
        }.also { loadedCalories ->
            if (loadedCalories.isEmpty()) {
                throw FoodClassifierException("calorie_info.txt 为空，请检查 Food.AI 热量表")
            }
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        return runCatching {
            appContext.assets.openFd(MODEL_FILE_NAME).use { descriptor ->
                descriptor.createInputStream().channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength,
                    )
                }
            }
        }.getOrElse { throwable ->
            if (throwable is FileNotFoundException) {
                throw FoodClassifierException("未找到 food_detect.tflite，请检查 Food.AI 模型文件", throwable)
            }
            throw FoodClassifierException("Food.AI 模型加载失败，请检查 food_detect.tflite", throwable)
        }
    }

    private companion object {
        const val MODEL_FILE_NAME = "food_detect.tflite"
        const val LABEL_FILE_NAME = "food_labelmap.txt"
        const val CALORIE_FILE_NAME = "calorie_info.txt"
        const val DATA_SOURCE_LABEL = "Food.AI 热量表 + 本地宏量估算"
        const val INPUT_SIZE = 300
        const val RGB_CHANNELS = 3
        const val FLOAT_BYTES = 4
        const val INPUT_BYTES = INPUT_SIZE * INPUT_SIZE * RGB_CHANNELS * FLOAT_BYTES
        const val NUM_DETECTIONS = 10
        const val BOX_COORDINATES = 4
        const val MAX_FOOD_ITEMS = 5
        const val MAX_CANDIDATE_DETECTIONS = 12
        const val NUM_THREADS = 4
        const val MIN_CONFIDENCE = 0.35f
        const val NMS_IOU_THRESHOLD = 0.45f
        const val MIN_ASPECT_FOR_SQUARE_CROP = 1.15f
        const val IMAGE_MEAN = 128f
        const val IMAGE_STD = 128f
        val EXTRA_INFERENCE_WINDOWS = listOf(
            FoodAiImageWindow(name = "center", left = 0.12f, top = 0.12f, right = 0.88f, bottom = 0.88f),
            FoodAiImageWindow(name = "top_left", left = 0f, top = 0f, right = 0.62f, bottom = 0.62f),
            FoodAiImageWindow(name = "top_right", left = 0.38f, top = 0f, right = 1f, bottom = 0.62f),
            FoodAiImageWindow(name = "bottom_left", left = 0f, top = 0.38f, right = 0.62f, bottom = 1f),
            FoodAiImageWindow(name = "bottom_right", left = 0.38f, top = 0.38f, right = 1f, bottom = 1f),
        )
    }
}

class FoodClassifierException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
