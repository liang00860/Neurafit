package com.neurafit.app.camera

import android.Manifest
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.neurafit.app.pose.PoseOverlayResult
import com.neurafit.app.realtime.RealtimeVideoFrameSink
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var boundPreviewView: PreviewView? = null
    private var boundLifecycleOwner: LifecycleOwner? = null
    private var boundLensFacing: Int? = null
    private var bindingInProgress = false
    private var analysisExecutor: ExecutorService? = null
    private var frameAnalyzer: FrameAnalyzer? = null

    fun startCamera(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
        onPoseResult: (PoseOverlayResult?) -> Unit = {},
        realtimeVideoFrameSink: RealtimeVideoFrameSink? = null,
        onError: (Throwable) -> Unit = {},
    ) {
        val alreadyBound = cameraProvider != null &&
            boundPreviewView === previewView &&
            boundLifecycleOwner == lifecycleOwner &&
            boundLensFacing == lensFacing
        if (alreadyBound) {
            frameAnalyzer?.onPoseResult = onPoseResult
            frameAnalyzer?.updateRealtimeVideoFrameSink(realtimeVideoFrameSink)
            return
        }
        if (bindingInProgress) return

        bindingInProgress = true
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                try {
                    val provider = cameraProviderFuture.get()
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()
                    val preview = Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    releaseAnalysis()
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .also { analysis ->
                            val executor = Executors.newSingleThreadExecutor()
                            val analyzer = FrameAnalyzer(
                                context = context,
                                initialOnPoseResult = onPoseResult,
                                realtimeVideoFrameSink = realtimeVideoFrameSink,
                            )
                            analysisExecutor = executor
                            frameAnalyzer = analyzer
                            analysis.setAnalyzer(executor, analyzer)
                        }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis,
                    )

                    cameraProvider = provider
                    boundPreviewView = previewView
                    boundLifecycleOwner = lifecycleOwner
                    boundLensFacing = lensFacing
                } catch (throwable: Throwable) {
                    onError(throwable)
                } finally {
                    bindingInProgress = false
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        boundPreviewView = null
        boundLifecycleOwner = null
        boundLensFacing = null
        bindingInProgress = false
        releaseAnalysis()
    }

    private fun releaseAnalysis() {
        frameAnalyzer?.close()
        frameAnalyzer = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
    }

    companion object {
        const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    }
}
