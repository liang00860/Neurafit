package com.neurafit.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.neurafit.app.food.FoodAnalyzeViewModel
import com.neurafit.app.food.FoodLabelMapper
import com.neurafit.app.food.FoodClassifier
import com.neurafit.app.food.FoodClassifierException
import com.neurafit.app.food.FoodRecognitionState
import com.neurafit.app.ui.FoodAnalyzeScreen
import com.neurafit.app.ui.NeurafitTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FoodAnalyzeActivity : ComponentActivity() {
    private val foodAnalyzeViewModel: FoodAnalyzeViewModel by viewModels()
    private var foodClassifier: FoodClassifier? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var activePreviewView: PreviewView? = null
    private var activeLifecycleOwner: LifecycleOwner? = null
    private var cameraPermissionRequested = false
    private var bindingInProgress = false

    private val galleryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            foodAnalyzeViewModel.onImageSelected(
                imageUri = uri.toString(),
                sourceLabel = "来自相册",
            )
        } else {
            foodAnalyzeViewModel.showMessage("未选择图片")
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            bindCameraPreviewIfReady()
        } else {
            foodAnalyzeViewModel.showMessage("未授予相机权限，无法拍照")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NeurafitTheme {
                FoodAnalyzeScreen(
                    onBack = ::finish,
                    onPreviewReady = ::onPreviewReady,
                    onTakePhoto = ::requestCameraCapture,
                    onChooseGallery = ::openGalleryPicker,
                    onUseTestPhoto = ::useTestFoodPhoto,
                    onAnalyze = ::analyzeSelectedFood,
                    onComplete = ::finishWithFoodResult,
                    viewModel = foodAnalyzeViewModel,
                )
            }
        }
    }

    override fun onDestroy() {
        stopCameraPreview()
        foodClassifier?.close()
        foodClassifier = null
        super.onDestroy()
    }

    private fun openGalleryPicker() {
        galleryPickerLauncher.launch("image/*")
    }

    private fun useTestFoodPhoto() {
        runCatching {
            createTestFoodPhotoOutput()
        }.onSuccess { output ->
            foodAnalyzeViewModel.onImageSelected(
                imageUri = output.uri.toString(),
                sourceLabel = "测试照片",
            )
        }.onFailure {
            foodAnalyzeViewModel.showMessage("测试照片加载失败")
        }
    }

    private fun onPreviewReady(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
    ) {
        val alreadyBound = cameraProvider != null &&
            imageCapture != null &&
            activePreviewView === previewView &&
            activeLifecycleOwner == lifecycleOwner
        activePreviewView = previewView
        activeLifecycleOwner = lifecycleOwner
        if (alreadyBound) return

        ensureCameraPermissionThenBind()
    }

    private fun ensureCameraPermissionThenBind() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            foodAnalyzeViewModel.showMessage("当前设备没有可用相机")
            return
        }

        if (hasCameraPermission()) {
            bindCameraPreviewIfReady()
        } else if (!cameraPermissionRequested) {
            cameraPermissionRequested = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun bindCameraPreviewIfReady() {
        val previewView = activePreviewView ?: return
        val lifecycleOwner = activeLifecycleOwner ?: return
        if (bindingInProgress) return

        bindingInProgress = true
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                try {
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                    )

                    cameraProvider = provider
                    imageCapture = capture
                } catch (throwable: Throwable) {
                    foodAnalyzeViewModel.showMessage("无法启动相机预览")
                } finally {
                    bindingInProgress = false
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun requestCameraCapture() {
        if (!hasCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        val capture = imageCapture
        if (capture == null) {
            foodAnalyzeViewModel.showMessage("相机正在准备中")
            bindCameraPreviewIfReady()
            return
        }

        val output = runCatching {
            createCameraOutput()
        }.onFailure {
            foodAnalyzeViewModel.showMessage("无法创建拍照文件")
        }.getOrNull() ?: return

        val outputOptions = ImageCapture.OutputFileOptions.Builder(output.file).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    foodAnalyzeViewModel.onImageSelected(
                        imageUri = output.uri.toString(),
                        sourceLabel = "来自拍照",
                    )
                }

                override fun onError(exception: ImageCaptureException) {
                    foodAnalyzeViewModel.showMessage("拍照失败，请重试")
                }
            },
        )
    }

    private fun stopCameraPreview() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
        activePreviewView = null
        activeLifecycleOwner = null
        bindingInProgress = false
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    private fun createCameraOutput(): CameraOutput {
        val imageFile = createCameraImageFile()
        return CameraOutput(
            file = imageFile,
            uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                imageFile,
            ),
        )
    }

    private fun createCameraImageFile(): File {
        val imageDirectory = File(cacheDir, FOOD_IMAGE_DIRECTORY).apply {
            mkdirs()
        }
        return File.createTempFile(
            "food_${System.currentTimeMillis()}_",
            ".jpg",
            imageDirectory,
        )
    }

    private fun createTestFoodPhotoOutput(): CameraOutput {
        val imageDirectory = File(cacheDir, FOOD_IMAGE_DIRECTORY).apply {
            mkdirs()
        }
        val imageFile = File(imageDirectory, TEST_FOOD_PHOTO_FILE_NAME)
        assets.open(TEST_FOOD_PHOTO_FILE_NAME).use { input ->
            imageFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return CameraOutput(
            file = imageFile,
            uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                imageFile,
            ),
        )
    }

    private fun finishWithFoodResult() {
        val imageUri = foodAnalyzeViewModel.uiState.value.selectedImageUri
        if (imageUri.isNullOrBlank()) {
            foodAnalyzeViewModel.showMessage("请先拍照或选择食物图片")
            return
        }
        val recognitionState = foodAnalyzeViewModel.uiState.value.recognitionState
        if (recognitionState !is FoodRecognitionState.Success) {
            foodAnalyzeViewModel.showMessage("请先开始分析食物图片")
            return
        }
        val result = recognitionState.result

        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_IMAGE_URI, imageUri)
                .putExtra(EXTRA_FOOD_NAME, result.foodName)
                .putExtra(EXTRA_FOOD_LABEL, result.label)
                .putExtra(EXTRA_CONFIDENCE, result.confidence.toPercentLabel())
                .putExtra(EXTRA_CONFIDENCE_VALUE, result.confidence)
                .putExtra(EXTRA_WEIGHT, result.estimatedWeightLabel)
                .putExtra(EXTRA_SERVING_GRAMS, result.estimatedServingGrams)
                .putExtra(EXTRA_CALORIES, result.estimatedCaloriesLabel)
                .putExtra(EXTRA_CALORIES_KCAL, result.estimatedCaloriesKcal)
                .putExtra(EXTRA_PROTEIN, result.proteinLabel)
                .putExtra(EXTRA_PROTEIN_GRAMS, result.proteinGrams)
                .putExtra(EXTRA_DIETARY_FIBER, result.dietaryFiberLabel)
                .putExtra(EXTRA_DIETARY_FIBER_GRAMS, result.dietaryFiberGrams)
                .putExtra(EXTRA_CARBS, result.carbsLabel)
                .putExtra(EXTRA_CARBS_GRAMS, result.carbsGrams)
                .putExtra(EXTRA_FAT, result.fatLabel)
                .putExtra(EXTRA_FAT_GRAMS, result.fatGrams)
                .putExtra(EXTRA_ANALYSIS_SUMMARY, result.analysisSummary)
                .putExtra(EXTRA_DATA_SOURCE, result.dataSourceLabel)
                .putExtra(EXTRA_ITEM_SUMMARY, result.itemSummary)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        finish()
    }

    private fun analyzeSelectedFood() {
        val imageUri = foodAnalyzeViewModel.uiState.value.selectedImageUri
        if (imageUri.isNullOrBlank()) {
            foodAnalyzeViewModel.showMessage("请先拍照或选择食物图片")
            return
        }

        foodAnalyzeViewModel.markRecognitionLoading()
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bitmap = decodeBitmap(Uri.parse(imageUri))
                    getFoodClassifier().classify(bitmap)
                }
            }.onSuccess { result ->
                foodAnalyzeViewModel.onRecognitionSuccess(result)
            }.onFailure { throwable ->
                val friendlyMessage = when (throwable) {
                    is FoodClassifierException -> throwable.message ?: "食物识别失败"
                    else -> throwable.localizedMessage ?: "食物识别失败，请重试"
                }
                if (friendlyMessage.contains("未找到 food_detect.tflite")) {
                    val demoResult = FoodLabelMapper.toClassifyResult(
                        label = DEMO_FALLBACK_LABEL,
                        confidence = DEMO_FALLBACK_CONFIDENCE,
                    )
                    foodAnalyzeViewModel.onRecognitionSuccess(demoResult)
                    foodAnalyzeViewModel.showMessage("未检测到模型文件，已使用测试照片演示结果")
                } else {
                    foodAnalyzeViewModel.onRecognitionError(friendlyMessage)
                }
            }
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap {
        return contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
                ?: throw FoodClassifierException("图片解码失败，请重新拍摄或选择")
        } ?: throw FoodClassifierException("图片读取失败，请重新拍摄或选择")
    }

    private fun getFoodClassifier(): FoodClassifier {
        return foodClassifier ?: FoodClassifier(applicationContext).also {
            foodClassifier = it
        }
    }

    companion object {
        const val FOOD_IMAGE_DIRECTORY = "food_images"
        const val PENDING_CAMERA_IMAGE_URI_KEY = "pendingCameraImageUri"
        const val EXTRA_IMAGE_URI = "com.neurafit.app.extra.IMAGE_URI"
        const val EXTRA_FOOD_NAME = "com.neurafit.app.extra.FOOD_NAME"
        const val EXTRA_FOOD_LABEL = "com.neurafit.app.extra.FOOD_LABEL"
        const val EXTRA_CONFIDENCE = "com.neurafit.app.extra.CONFIDENCE"
        const val EXTRA_CONFIDENCE_VALUE = "com.neurafit.app.extra.CONFIDENCE_VALUE"
        const val EXTRA_WEIGHT = "com.neurafit.app.extra.WEIGHT"
        const val EXTRA_SERVING_GRAMS = "com.neurafit.app.extra.SERVING_GRAMS"
        const val EXTRA_CALORIES = "com.neurafit.app.extra.CALORIES"
        const val EXTRA_CALORIES_KCAL = "com.neurafit.app.extra.CALORIES_KCAL"
        const val EXTRA_PROTEIN = "com.neurafit.app.extra.PROTEIN"
        const val EXTRA_PROTEIN_GRAMS = "com.neurafit.app.extra.PROTEIN_GRAMS"
        const val EXTRA_DIETARY_FIBER = "com.neurafit.app.extra.DIETARY_FIBER"
        const val EXTRA_DIETARY_FIBER_GRAMS = "com.neurafit.app.extra.DIETARY_FIBER_GRAMS"
        const val EXTRA_CARBS = "com.neurafit.app.extra.CARBS"
        const val EXTRA_CARBS_GRAMS = "com.neurafit.app.extra.CARBS_GRAMS"
        const val EXTRA_FAT = "com.neurafit.app.extra.FAT"
        const val EXTRA_FAT_GRAMS = "com.neurafit.app.extra.FAT_GRAMS"
        const val EXTRA_ANALYSIS_SUMMARY = "com.neurafit.app.extra.ANALYSIS_SUMMARY"
        const val EXTRA_DATA_SOURCE = "com.neurafit.app.extra.DATA_SOURCE"
        const val EXTRA_ITEM_SUMMARY = "com.neurafit.app.extra.ITEM_SUMMARY"
        const val DEMO_FOOD_NAME = "鸡胸肉沙拉"
        const val DEMO_CONFIDENCE = "92%"
        const val DEMO_WEIGHT = "中份 300g"
        const val DEMO_CALORIES = "420 kcal"
        private const val TEST_FOOD_PHOTO_FILE_NAME = "test_food_hamburger.jpg"
        private const val DEMO_FALLBACK_LABEL = "Hamburger"
        private const val DEMO_FALLBACK_CONFIDENCE = 0.92f

        fun createIntent(context: Context): Intent =
            Intent(context, FoodAnalyzeActivity::class.java)
    }

    private data class CameraOutput(
        val file: File,
        val uri: Uri,
    )
}

private fun Float.toPercentLabel(): String =
    "${(this * 100).toInt()}%"
