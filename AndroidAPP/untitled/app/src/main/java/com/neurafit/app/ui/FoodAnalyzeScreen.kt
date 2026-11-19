package com.neurafit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neurafit.app.food.FoodAnalyzeUiState
import com.neurafit.app.food.FoodAnalyzeViewModel
import com.neurafit.app.food.FoodRecognitionState

@Composable
fun FoodAnalyzeScreen(
    onBack: () -> Unit,
    onPreviewReady: (PreviewView, LifecycleOwner) -> Unit,
    onTakePhoto: () -> Unit,
    onChooseGallery: () -> Unit,
    onUseTestPhoto: () -> Unit,
    onAnalyze: () -> Unit,
    onComplete: () -> Unit,
    viewModel: FoodAnalyzeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveMessage) {
        uiState.saveMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSaveMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950),
    ) {
        FoodCapturePreview(
            uiState = uiState,
            onPreviewReady = onPreviewReady,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Zinc950.copy(alpha = 0.10f),
                        0.56f to Color.Transparent,
                        1f to Zinc950.copy(alpha = 0.88f),
                    ),
                ),
        )
        FoodRecognitionPanel(
            uiState = uiState,
            onAnalyze = onAnalyze,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 150.dp),
        )
        FoodCaptureDock(
            onBack = onBack,
            onChooseGallery = onChooseGallery,
            onUseTestPhoto = onUseTestPhoto,
            onTakePhoto = onTakePhoto,
            onComplete = onComplete,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(24.dp),
        )
    }
}

@Composable
private fun FoodRecognitionPanel(
    uiState: FoodAnalyzeUiState,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageSelected = uiState.selectedImageUri != null
    if (!imageSelected) return

    val recognitionState = uiState.recognitionState
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Zinc950.copy(alpha = 0.64f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CardShape)
            .padding(16.dp),
    ) {
        when (recognitionState) {
            FoodRecognitionState.Idle -> AnalyzeReadyContent(onAnalyze = onAnalyze)
            FoodRecognitionState.Loading -> AnalyzeLoadingContent()
            is FoodRecognitionState.Success -> AnalyzeSuccessContent(
                foodName = recognitionState.result.foodName,
                confidence = recognitionState.result.confidence,
                weightLabel = recognitionState.result.estimatedWeightLabel,
                caloriesLabel = recognitionState.result.estimatedCaloriesLabel,
                itemSummary = recognitionState.result.itemSummary,
                onAnalyze = onAnalyze,
            )
            is FoodRecognitionState.Error -> AnalyzeErrorContent(
                message = recognitionState.message,
                onAnalyze = onAnalyze,
            )
        }
    }
}

@Composable
private fun AnalyzeReadyContent(onAnalyze: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            MonoText(text = "Food Recognition", color = Lime, size = 11, weight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "图片已就绪",
                color = Color.White,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
            )
        }
        Spacer(Modifier.width(16.dp))
        AnalyzeActionButton(text = "开始分析", onClick = onAnalyze)
    }
}

@Composable
private fun AnalyzeLoadingContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MonoText(text = "Food Recognition", color = Lime, size = 11, weight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "识别中...",
            color = Color.White,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AnalyzeSuccessContent(
    foodName: String,
    confidence: Float,
    weightLabel: String,
    caloriesLabel: String,
    itemSummary: String,
    onAnalyze: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            MonoText(text = "Recognition Result", color = Lime, size = 11, weight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = foodName,
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "置信度 ${(confidence * 100).toInt()}%",
                color = Zinc300,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$caloriesLabel · $weightLabel",
                color = Lime,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
            )
            if (itemSummary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = itemSummary,
                    color = Zinc300,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        AnalyzeActionButton(text = "重新分析", onClick = onAnalyze)
    }
}

@Composable
private fun AnalyzeErrorContent(
    message: String,
    onAnalyze: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            MonoText(text = "Recognition Error", color = Orange, size = 11, weight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        AnalyzeActionButton(text = "重试", onClick = onAnalyze)
    }
}

@Composable
private fun AnalyzeActionButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(Lime)
            .border(1.dp, Lime, PillShape)
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Zinc950,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun FoodCapturePreview(
    uiState: FoodAnalyzeUiState,
    onPreviewReady: (PreviewView, LifecycleOwner) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedImageUri = uiState.selectedImageUri
    if (selectedImageUri != null) {
        RemoteImage(
            url = selectedImageUri,
            description = "Food preview",
            modifier = modifier,
            alpha = 0.96f,
        )
    } else {
        FoodLiveCameraPreview(
            onPreviewReady = onPreviewReady,
            modifier = modifier,
        )
    }
}

@Composable
private fun FoodLiveCameraPreview(
    onPreviewReady: (PreviewView, LifecycleOwner) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier.background(Zinc950),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            onPreviewReady(previewView, lifecycleOwner)
        },
    )
}

@Composable
private fun FoodCaptureDock(
    onBack: () -> Unit,
    onChooseGallery: () -> Unit,
    onUseTestPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Zinc950.copy(alpha = 0.92f)),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 34.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(PillShape)
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.20f), PillShape)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(
                icon = FitIcon.ArrowLeft,
                size = 48.dp,
                iconSize = 21.dp,
                onClick = onBack,
            )
            IconCircleButton(
                icon = FitIcon.Image,
                size = 48.dp,
                iconSize = 21.dp,
                onClick = onChooseGallery,
            )
            IconCircleButton(
                icon = FitIcon.UtensilsCrossed,
                size = 48.dp,
                iconSize = 21.dp,
                background = Lime,
                border = Lime,
                tint = Zinc950,
                onClick = onUseTestPhoto,
            )
            IconCircleButton(
                icon = FitIcon.Camera,
                size = 62.dp,
                iconSize = 27.dp,
                background = Color.White,
                border = Color.White,
                tint = Zinc950,
                onClick = onTakePhoto,
            )
            IconCircleButton(
                icon = FitIcon.Stop,
                size = 62.dp,
                iconSize = 25.dp,
                background = Color(0xFFDC2626),
                border = Color(0xFFDC2626),
                onClick = onComplete,
            )
        }
    }
}
