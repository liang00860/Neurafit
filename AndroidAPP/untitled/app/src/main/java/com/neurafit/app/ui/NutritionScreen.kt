package com.neurafit.app.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.neurafit.app.FoodAnalyzeActivity
import com.neurafit.app.nutrition.NutritionSaveUiState
import com.neurafit.app.nutrition.NutritionScanRecord
import com.neurafit.app.nutrition.NutritionViewModel
import com.neurafit.app.nutrition.RecipeSelectionUiState
import com.neurafit.app.nutrition.SelectedRecipeRecord

// Custom Colors for this screen based on reference (Dark Theme Update)
private val BgWhite = Color(0xFF000000) // Changed to Black
private val TextBlack = Color(0xFFFFFFFF) // Changed to White
private val TextGray = Color(0xFFA1A1AA) // Slightly lighter gray for dark theme
private val BannerGreen = Color(0xFF5AB190) // Soft green/teal background
private val BannerTextLight = Color(0xFFE8F5F0)
private val TabActiveBg = Color(0xFFFFFFFF) // Changed to White
private val TabInactiveBg = Color(0xFF18181B) // Changed to Dark Gray
private val TabInactiveText = Color(0xFFA1A1AA)
private val TabActiveText = Color(0xFF000000) // New color for active tab text in dark theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(onAnalyzeFood: (() -> Unit)? = null) {
    val context = LocalContext.current
    val nutritionViewModel: NutritionViewModel = viewModel()
    val saveState by nutritionViewModel.saveState.collectAsState()
    val recipeSaveState by nutritionViewModel.recipeSaveState.collectAsState()
    var latestScanRecord by remember { mutableStateOf<NutritionScanRecord?>(null) }
    var showAnalysisSheet by remember { mutableStateOf(false) }
    var selectedRecipe by remember { mutableStateOf<RecipeData?>(null) }
    var showRecipeSheet by remember { mutableStateOf(false) }
    val analysisSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val recipeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val foodAnalyzeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                latestScanRecord = data.toNutritionScanRecord()
                nutritionViewModel.resetSaveState()
                showAnalysisSheet = true
            }
        }
    }
    val openFoodAnalyze = onAnalyzeFood ?: {
        foodAnalyzeLauncher.launch(FoodAnalyzeActivity.createIntent(context))
    }

    LaunchedEffect(latestScanRecord?.scanId) {
        latestScanRecord?.let { record ->
            nutritionViewModel.saveScan(record)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgWhite)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp) // Leave space for bottom nav
        ) {
            // 1. Status bar spacing & 2. Greeting Area
            GreetingHeader()

            // 3. Main health banner card
            MainHealthBanner(openFoodAnalyze)

            latestScanRecord?.let { record ->
                Spacer(modifier = Modifier.height(24.dp))
                FoodScanResultCard(
                    imageUri = record.imageUri,
                    foodName = record.foodName,
                    weight = record.weightLabel,
                    calories = record.caloriesLabel,
                )
            }

            // 4. Category section
            CategorySection()

            // 5. Popular Recipes section
            PopularRecipesSection(
                onRecipeClick = { recipe ->
                    nutritionViewModel.resetRecipeSaveState()
                    selectedRecipe = recipe
                    showRecipeSheet = true
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            // 6. Featured Recipe Banner
            FeaturedRecipeBanner(
                onClick = {
                    nutritionViewModel.resetRecipeSaveState()
                    selectedRecipe = RecipeData(
                        recipeId = "featured_lemon_salmon",
                        title = "清柠香煎三文鱼",
                        calories = "450",
                        time = "25 分钟",
                        imageUrl = "https://images.unsplash.com/photo-1467003909585-2f8a72700288?q=80&w=800&auto=format&fit=crop",
                        carbs = "12g",
                        protein = "38g",
                        fiber = "6g",
                        ingredients = "三文鱼排、新鲜芦笋、柠檬、黄油、海盐",
                        steps = "1. 三文鱼撒海盐黑胡椒腌制10分钟\n2. 平底锅加少许橄榄油，中火煎至两面金黄\n3. 挤上新鲜柠檬汁即可"
                    )
                    showRecipeSheet = true
                }
            )
        }

        val scanRecord = latestScanRecord
        if (showAnalysisSheet && scanRecord != null) {
            ModalBottomSheet(
                onDismissRequest = { showAnalysisSheet = false },
                sheetState = analysisSheetState,
                containerColor = Color(0xFF0A0A0A),
                contentColor = Color.White,
                dragHandle = null,
            ) {
                NutritionAnalysisSheet(
                    record = scanRecord,
                    saveState = saveState,
                    onRetrySave = {
                        nutritionViewModel.saveScan(scanRecord, force = true)
                    },
                    onRescan = {
                        showAnalysisSheet = false
                        openFoodAnalyze()
                    },
                )
            }
        }

        val currentRecipe = selectedRecipe
        if (showRecipeSheet && currentRecipe != null) {
            ModalBottomSheet(
                onDismissRequest = { showRecipeSheet = false },
                sheetState = recipeSheetState,
                containerColor = Color(0xFF0A0A0A),
                contentColor = Color.White,
                dragHandle = null,
            ) {
                RecipeDetailSheet(
                    recipe = currentRecipe,
                    saveState = recipeSaveState,
                    onSelectRecipe = {
                        currentRecipe.toSelectedRecipeRecord()?.let { record ->
                            nutritionViewModel.saveSelectedRecipe(record)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RecipeDetailSheet(
    recipe: RecipeData,
    saveState: RecipeSelectionUiState,
    onSelectRecipe: () -> Unit,
) {
    val saveActionText = when (saveState) {
        RecipeSelectionUiState.Idle -> "选定食谱"
        RecipeSelectionUiState.Saving -> "上传中"
        is RecipeSelectionUiState.Saved -> "已选定"
        RecipeSelectionUiState.NeedsLogin -> "请先登录"
        is RecipeSelectionUiState.Error -> "重新上传"
    }
    val saveActionEnabled =
        saveState is RecipeSelectionUiState.Idle || saveState is RecipeSelectionUiState.Error

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 14.dp, bottom = 32.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(44.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.White.copy(alpha = 0.24f)),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = recipe.imageUrl,
                contentDescription = recipe.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.title,
                    color = Color.White,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutritionPill(text = "热门")
                    NutritionPill(text = recipe.time)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "包含：${recipe.ingredients}",
                    color = TextGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BannerGreen)
                .padding(22.dp),
        ) {
            Column {
                Text(
                    text = "${recipe.calories} 千卡",
                    color = Color.White,
                    fontSize = 38.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "预估热量总计",
                    color = BannerTextLight,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        
        recipe.steps?.let { steps ->
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "制作步骤",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(20.dp)
            ) {
                Text(
                    text = steps,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "营养成分",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MacroTile(label = "蛋白质", value = recipe.protein, modifier = Modifier.weight(1f))
            MacroTile(label = "膳食纤维", value = recipe.fiber, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MacroTile(label = "碳水", value = recipe.carbs, modifier = Modifier.weight(1f))
            MacroTile(label = "脂肪估算", value = "适量", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(16.dp))
        RecipeSaveStatusNotice(saveState = saveState)
        Spacer(modifier = Modifier.height(20.dp))
        SheetActionButton(
            text = saveActionText,
            icon = FitIcon.Save,
            highlighted = true,
            enabled = saveActionEnabled,
            onClick = onSelectRecipe,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GreetingHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "Nutrition",
            color = Color.White,
            fontSize = 68.sp,
            lineHeight = 74.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .padding(3.dp),
            ) {
                RemoteImage(
                    url = ImageUrls.Avatar,
                    description = "个人头像",
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape),
                )
            }
            IconCircleButton(
                icon = FitIcon.Bell,
                size = 44.dp,
                iconSize = 18.dp,
                background = Color.White.copy(alpha = 0.12f),
                border = Color.White.copy(alpha = 0.08f),
                tint = Color.White.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun MainHealthBanner(onAnalyzeFood: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(BannerGreen)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "状态更好了！", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "你今天的饮食非常健康，\n继续保持。",
                    color = BannerTextLight,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable(enabled = onAnalyzeFood != null) { onAnalyzeFood?.invoke() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "开始识别菜品",
                        color = BannerGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxHeight()
            ) {
                AsyncImage(
                    model = "https://images.unsplash.com/photo-1490645935967-10de6ba17061?q=80&w=800&auto=format&fit=crop",
                    contentDescription = "Healthy food",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(BannerGreen, Color.Transparent),
                                startX = 0f,
                                endX = 200f
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun FeaturedRecipeBanner(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "今日推荐",
                color = TextBlack,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onClick)
        ) {
            AsyncImage(
                model = "https://images.unsplash.com/photo-1467003909585-2f8a72700288?q=80&w=800&auto=format&fit=crop",
                contentDescription = "清柠香煎三文鱼",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 200f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                Text(
                    text = "清柠香煎三文鱼",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LineIcon(
                            icon = FitIcon.Zap,
                            tint = BannerGreen,
                            size = 16.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "450 千卡",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LineIcon(
                            icon = FitIcon.Timer,
                            tint = Color.White.copy(alpha = 0.7f),
                            size = 16.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "25 分钟",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodScanResultCard(
    imageUri: String,
    foodName: String,
    weight: String,
    calories: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TabInactiveBg)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = imageUri,
                contentDescription = "已扫描食物",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "最近扫描：$foodName",
                    color = TextBlack,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${calories.toChineseNutritionUnits()} · ${weight.toChineseNutritionUnits()}",
                    color = BannerGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun NutritionAnalysisSheet(
    record: NutritionScanRecord,
    saveState: NutritionSaveUiState,
    onRetrySave: () -> Unit,
    onRescan: () -> Unit,
) {
    val saveActionText = when (saveState) {
        NutritionSaveUiState.Idle -> "等待保存"
        NutritionSaveUiState.Saving -> "保存中"
        is NutritionSaveUiState.Saved -> "已保存"
        NutritionSaveUiState.NeedsLogin -> "请先登录"
        is NutritionSaveUiState.Error -> "重试保存"
    }
    val saveActionEnabled = saveState is NutritionSaveUiState.Error

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 14.dp, bottom = 32.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(44.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.White.copy(alpha = 0.24f)),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = record.imageUri,
                contentDescription = "已扫描食物",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.foodName,
                    color = Color.White,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutritionPill(text = record.confidenceLabel)
                    NutritionPill(text = record.weightLabel.toChineseNutritionUnits())
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${record.dataSource} · 估算",
                    color = TextGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (record.itemSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = record.itemSummary,
                        color = BannerGreen,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BannerGreen)
                .padding(22.dp),
        ) {
            Column {
                Text(
                    text = record.caloriesLabel.toChineseNutritionUnits(),
                    color = Color.White,
                    fontSize = 38.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = record.analysisSummary,
                    color = BannerTextLight,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MacroTile(label = "蛋白质估算", value = record.proteinLabel.toChineseNutritionUnits(), modifier = Modifier.weight(1f))
            MacroTile(label = "膳食纤维估算", value = record.dietaryFiberLabel.toChineseNutritionUnits(), modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MacroTile(label = "碳水估算", value = record.carbsLabel.toChineseNutritionUnits(), modifier = Modifier.weight(1f))
            MacroTile(label = "脂肪估算", value = record.fatLabel.toChineseNutritionUnits(), modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(16.dp))
        SaveStatusNotice(saveState = saveState)
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetActionButton(
                text = saveActionText,
                icon = FitIcon.Save,
                highlighted = true,
                enabled = saveActionEnabled,
                onClick = onRetrySave,
                modifier = Modifier.weight(1f),
            )
            SheetActionButton(
                text = "重新扫描",
                icon = FitIcon.Camera,
                highlighted = false,
                onClick = onRescan,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SaveStatusNotice(saveState: NutritionSaveUiState) {
    val (message, color) = when (saveState) {
        NutritionSaveUiState.Idle -> "识别完成后会自动保存到云端。" to TextGray
        NutritionSaveUiState.Saving -> "正在上传照片并保存营养记录..." to BannerGreen
        is NutritionSaveUiState.Saved -> "已保存到云端营养记录。" to BannerGreen
        NutritionSaveUiState.NeedsLogin -> "请先登录账号，登录后重新扫描即可保存。" to Color(0xFFFFD166)
        is NutritionSaveUiState.Error -> saveState.message to Color(0xFFFF9B9B)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = message,
            color = color,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RecipeSaveStatusNotice(saveState: RecipeSelectionUiState) {
    val (message, color) = when (saveState) {
        RecipeSelectionUiState.Idle -> "点击按钮后会将该食谱保存到你的云端食谱库。" to TextGray
        RecipeSelectionUiState.Saving -> "正在上传选定食谱..." to BannerGreen
        is RecipeSelectionUiState.Saved -> "已保存到云端食谱库。" to BannerGreen
        RecipeSelectionUiState.NeedsLogin -> "请先登录账号，登录后即可保存食谱。" to Color(0xFFFFD166)
        is RecipeSelectionUiState.Error -> saveState.message to Color(0xFFFF9B9B)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = message,
            color = color,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NutritionPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun MacroTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(TabInactiveBg)
            .padding(14.dp),
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = TextGray,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SheetActionButton(
    text: String,
    icon: FitIcon,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val background = if (highlighted) Color.White else TabInactiveBg
    val content = if (highlighted) Color.Black else Color.White
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.64f)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(icon = icon, tint = content, size = 18.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = content,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CategorySection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "分类",
                color = TextBlack,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "查看全部",
                color = TextGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CategoryTab("热门", true)
            CategoryTab("甜点", false)
            CategoryTab("面食", false)
            CategoryTab("沙拉", false)
        }
    }
}

@Composable
private fun CategoryTab(text: String, isActive: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (isActive) TabActiveBg else TabInactiveBg)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            color = if (isActive) TabActiveText else TabInactiveText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

data class RecipeData(
    val recipeId: String,
    val title: String,
    val calories: String,
    val time: String,
    val imageUrl: String,
    val carbs: String,
    val protein: String,
    val fiber: String,
    val ingredients: String,
    val steps: String? = null
)

val popularRecipes = listOf(
    RecipeData(
        recipeId = "popular_spicy_seafood_ramen",
        title = "海鲜辣味拉面",
        calories = "421",
        time = "20 分钟",
        imageUrl = "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?q=80&w=800&auto=format&fit=crop",
        carbs = "52g",
        protein = "18g",
        fiber = "4g",
        ingredients = "拉面、虾、鱿鱼、辣椒酱、葱、蒜",
        steps = "1. 将海鲜洗净备用\n2. 锅中烧水，加入辣椒酱煮沸\n3. 放入拉面和海鲜煮熟即可"
    ),
    RecipeData(
        recipeId = "popular_avocado_toast",
        title = "健康牛油果吐司",
        calories = "310",
        time = "10 分钟",
        imageUrl = "https://images.unsplash.com/photo-1588137378633-dea1336ce1e2?q=80&w=800&auto=format&fit=crop",
        carbs = "28g",
        protein = "8g",
        fiber = "10g",
        ingredients = "全麦吐司、牛油果、鸡蛋、黑胡椒、橄榄油",
        steps = "1. 将牛油果捣碎，加入少许盐和黑胡椒\n2. 全麦吐司烤至微脆\n3. 涂上牛油果泥，放上煎好的鸡蛋即可"
    ),
    RecipeData(
        recipeId = "popular_salmon_asparagus",
        title = "香煎三文鱼配芦笋",
        calories = "450",
        time = "25 分钟",
        imageUrl = "https://images.unsplash.com/photo-1467003909585-2f8a72700288?q=80&w=800&auto=format&fit=crop",
        carbs = "12g",
        protein = "38g",
        fiber = "6g",
        ingredients = "三文鱼排、新鲜芦笋、柠檬、黄油、海盐",
        steps = "1. 三文鱼撒海盐黑胡椒腌制10分钟\n2. 平底锅加少许橄榄油，中火煎至两面金黄\n3. 挤上新鲜柠檬汁即可"
    ),
    RecipeData(
        recipeId = "popular_chicken_quinoa_salad",
        title = "鸡胸肉藜麦沙拉",
        calories = "380",
        time = "15 分钟",
        imageUrl = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?q=80&w=800&auto=format&fit=crop",
        carbs = "35g",
        protein = "32g",
        fiber = "8g",
        ingredients = "鸡胸肉、藜麦、樱桃番茄、黄瓜、生菜、油醋汁",
        steps = "1. 鸡胸肉煮熟撕成丝，藜麦煮熟备用\n2. 蔬菜洗净切块\n3. 将所有材料混合，淋上油醋汁拌匀"
    ),
    RecipeData(
        recipeId = "popular_blueberry_yogurt_bowl",
        title = "蓝莓燕麦酸奶碗",
        calories = "280",
        time = "5 分钟",
        imageUrl = "https://images.unsplash.com/photo-1511690656952-34342bb7c2f2?q=80&w=800&auto=format&fit=crop",
        carbs = "45g",
        protein = "12g",
        fiber = "7g",
        ingredients = "希腊酸奶、燕麦片、新鲜蓝莓、蜂蜜、奇亚籽",
        steps = "1. 将酸奶倒入碗中\n2. 铺上燕麦片和新鲜蓝莓\n3. 撒上奇亚籽，淋上少许蜂蜜即可"
    )
)

@Composable
private fun PopularRecipesSection(onRecipeClick: (RecipeData) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "热门食谱",
                color = TextBlack,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "查看全部",
                color = TextGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            popularRecipes.forEach { recipe ->
                RecipeCard(
                    title = recipe.title,
                    calories = recipe.calories,
                    time = recipe.time,
                    imageUrl = recipe.imageUrl,
                    onClick = { onRecipeClick(recipe) }
                )
            }
        }
    }
}

@Composable
private fun RecipeCard(title: String, calories: String, time: String, imageUrl: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(240.dp)
            .height(280.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Dark gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        startY = 300f
                    )
                )
        )

        // Bookmark Icon Top Right (as in many iOS apps) or bottom right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            // Using a simple icon for bookmark
            LineIcon(icon = FitIcon.Camera, tint = Color.White, size = 18.dp) // Replace with actual bookmark icon
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 24.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$calories 千卡 · $time",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun Intent.toNutritionScanRecord(): NutritionScanRecord {
    val caloriesLabel = getStringExtra(FoodAnalyzeActivity.EXTRA_CALORIES)
        ?: FoodAnalyzeActivity.DEMO_CALORIES
    val confidenceLabel = getStringExtra(FoodAnalyzeActivity.EXTRA_CONFIDENCE)
        ?: FoodAnalyzeActivity.DEMO_CONFIDENCE
    val weightLabel = getStringExtra(FoodAnalyzeActivity.EXTRA_WEIGHT)
        ?: FoodAnalyzeActivity.DEMO_WEIGHT
    val foodName = getStringExtra(FoodAnalyzeActivity.EXTRA_FOOD_NAME)
        ?: FoodAnalyzeActivity.DEMO_FOOD_NAME

    return NutritionScanRecord(
        scanId = "scan_${System.currentTimeMillis()}",
        imageUri = getStringExtra(FoodAnalyzeActivity.EXTRA_IMAGE_URI).orEmpty(),
        foodName = foodName,
        foodLabel = getStringExtra(FoodAnalyzeActivity.EXTRA_FOOD_LABEL)
            ?: foodName.lowercase().replace(Regex("\\s+"), " "),
        confidence = getFloatExtra(FoodAnalyzeActivity.EXTRA_CONFIDENCE_VALUE, confidenceLabel.firstNumberAsFloat(0f)),
        confidenceLabel = confidenceLabel,
        servingGrams = getIntExtra(FoodAnalyzeActivity.EXTRA_SERVING_GRAMS, weightLabel.firstNumberAsInt(0)),
        weightLabel = weightLabel,
        caloriesKcal = getIntExtra(FoodAnalyzeActivity.EXTRA_CALORIES_KCAL, caloriesLabel.firstNumberAsInt(0)),
        caloriesLabel = caloriesLabel,
        proteinGrams = getFloatExtra(FoodAnalyzeActivity.EXTRA_PROTEIN_GRAMS, 0f),
        proteinLabel = getStringExtra(FoodAnalyzeActivity.EXTRA_PROTEIN) ?: "--",
        dietaryFiberGrams = getFloatExtra(FoodAnalyzeActivity.EXTRA_DIETARY_FIBER_GRAMS, 0f),
        dietaryFiberLabel = getStringExtra(FoodAnalyzeActivity.EXTRA_DIETARY_FIBER) ?: "--",
        carbsGrams = getFloatExtra(FoodAnalyzeActivity.EXTRA_CARBS_GRAMS, 0f),
        carbsLabel = getStringExtra(FoodAnalyzeActivity.EXTRA_CARBS) ?: "--",
        fatGrams = getFloatExtra(FoodAnalyzeActivity.EXTRA_FAT_GRAMS, 0f),
        fatLabel = getStringExtra(FoodAnalyzeActivity.EXTRA_FAT) ?: "--",
        analysisSummary = getStringExtra(FoodAnalyzeActivity.EXTRA_ANALYSIS_SUMMARY)
            ?: "已生成本地营养分析。",
        dataSource = getStringExtra(FoodAnalyzeActivity.EXTRA_DATA_SOURCE)
            ?: "Food.AI",
        itemSummary = getStringExtra(FoodAnalyzeActivity.EXTRA_ITEM_SUMMARY).orEmpty(),
    )
}

private fun RecipeData.toSelectedRecipeRecord(): SelectedRecipeRecord? {
    val prepTimeMinutes = time.firstNumberAsInt(-1)
    val caloriesKcal = calories.firstNumberAsInt(-1)
    val carbsGrams = carbs.firstNumberAsFloat(-1f)
    val proteinGrams = protein.firstNumberAsFloat(-1f)
    val fiberGrams = fiber.firstNumberAsFloat(-1f)
    if (
        prepTimeMinutes < 0 ||
        caloriesKcal < 0 ||
        carbsGrams < 0f ||
        proteinGrams < 0f ||
        fiberGrams < 0f
    ) {
        return null
    }

    return SelectedRecipeRecord(
        recipeId = recipeId,
        title = title,
        imageUrl = imageUrl,
        prepTimeMinutes = prepTimeMinutes,
        caloriesKcal = caloriesKcal,
        carbsGrams = carbsGrams,
        proteinGrams = proteinGrams,
        fiberGrams = fiberGrams,
        ingredients = ingredients,
        steps = steps.orEmpty(),
    )
}

private fun String.firstNumberAsInt(fallback: Int): Int =
    Regex("""\d+""").find(this)?.value?.toIntOrNull() ?: fallback

private fun String.firstNumberAsFloat(fallback: Float): Float =
    Regex("""\d+(?:\.\d+)?""").find(this)?.value?.toFloatOrNull()?.let { value ->
        if (contains("%")) value / 100f else value
    } ?: fallback

private fun String.toChineseNutritionUnits(): String =
    replace("kcal", "千卡", ignoreCase = true)
        .replace(Regex("""(?<=\d)\s*g\b""", RegexOption.IGNORE_CASE), "克")
