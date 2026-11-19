package com.neurafit.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

val Zinc50 = Color(0xFFFAFAFA)
val Zinc100 = Color(0xFFF4F4F5)
val Zinc200 = Color(0xFFE4E4E7)
val Zinc300 = Color(0xFFD4D4D8)
val Zinc400 = Color(0xFFA1A1AA)
val Zinc500 = Color(0xFF71717A)
val Zinc800 = Color(0xFF27272A)
val Zinc900 = Color(0xFF18181B)
val Zinc950 = Color(0xFF09090B)
val Lime = Color(0xFFC6EA7E)
val Mint = Color(0xFF54D8C7)
val Purple = Color(0xFF6C3FF5)
val Orange = Color(0xFFFF9B6B)
val Sky = Color(0xFF0EA5E9)

// Dark Theme specific colors for the bottom nav
val DarkBg = Color(0xFF09090B)
val DarkCard = Color(0xFF18181B)

val CardShape = RoundedCornerShape(16.dp)
val SmallCardShape = RoundedCornerShape(12.dp)
val PillShape = RoundedCornerShape(999.dp)

enum class FitIcon {
    Bell,
    Home,
    Dashboard,
    Dumbbell,
    Utensils,
    Activity,
    ArrowRight,
    ArrowLeft,
    PieChart,
    Droplet,
    UtensilsCrossed,
    ArrowUpRight,
    Calendar,
    Camera,
    Image,
    Zap,
    Radar,
    Timer,
    Flame,
    Moon,
    Pause,
    Stop,
    Settings,
    Save,
    Crown,
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    border: Color = Zinc200,
    shape: RoundedCornerShape = CardShape,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape),
        content = content,
    )
}

@Composable
fun PageTitle(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        if (eyebrow != null) {
            MonoText(
                text = eyebrow,
                color = Zinc500,
                size = 13,
                weight = FontWeight.Normal,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = title,
            color = Zinc950,
            fontSize = 48.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = subtitle,
                color = Zinc500,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Zinc500,
    size: Int = 12,
    weight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign? = null,
    decoration: TextDecoration? = null,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = size.sp,
        lineHeight = (size + 5).sp,
        fontWeight = weight,
        letterSpacing = 0.sp,
        textAlign = textAlign,
        textDecoration = decoration,
    )
}

@Composable
fun PillTag(
    text: String,
    background: Color,
    color: Color,
    modifier: Modifier = Modifier,
    border: Color = Color.Transparent,
) {
    Box(
        modifier = modifier
            .clip(PillShape)
            .background(background)
            .border(1.dp, border, PillShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        MonoText(text = text, color = color, size = 10, weight = FontWeight.ExtraBold)
    }
}

@Composable
fun PrimaryPillButton(
    text: String,
    icon: FitIcon? = null,
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    color: Color = Zinc950,
    padding: PaddingValues = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(padding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
        )
        if (icon != null) {
            Spacer(Modifier.width(8.dp))
            LineIcon(icon = icon, tint = color, size = 20.dp)
        }
    }
}

@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    onClick: () -> Unit = {},
) {
    val foreground = if (dark) Color.White else Zinc950
    val background = if (dark) Zinc950 else Color.Transparent
    val border = if (dark) Zinc950 else Zinc950
    Box(
        modifier = modifier
            .clip(SmallCardShape)
            .background(background)
            .border(2.dp, border, SmallCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            color = foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
fun RemoteImage(
    url: String,
    description: String?,
    modifier: Modifier = Modifier,
    grayscale: Boolean = false,
    alpha: Float = 1f,
) {
    val colorFilter = remember(grayscale) {
        if (grayscale) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        } else {
            null
        }
    }
    AsyncImage(
        model = url,
        contentDescription = description,
        modifier = modifier.alpha(alpha),
        contentScale = ContentScale.Crop,
        colorFilter = colorFilter,
    )
}

@Composable
fun ScrimImageCard(
    imageUrl: String,
    description: String,
    modifier: Modifier = Modifier,
    grayscale: Boolean = false,
    imageAlpha: Float = 1f,
    contentPadding: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    AppCard(modifier = modifier, background = Zinc100) {
        RemoteImage(
            url = imageUrl,
            description = description,
            grayscale = grayscale,
            alpha = imageAlpha,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Zinc950.copy(alpha = 0.25f),
                        1f to Zinc950.copy(alpha = 0.92f),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun RowScope.NavText(label: String, selected: Boolean) {
    Text(
        text = label.uppercase(),
        color = if (selected) Zinc950 else Zinc500,
        fontSize = 10.sp,
        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
        letterSpacing = 0.sp,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

@Composable
fun IconCircleButton(
    icon: FitIcon,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    background: Color = Color.White.copy(alpha = 0.10f),
    border: Color = Color.White.copy(alpha = 0.20f),
    tint: Color = Color.White,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LineIcon(icon = icon, tint = tint, size = iconSize)
    }
}

@Composable
fun LineIcon(
    icon: FitIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        drawFitIcon(icon, tint)
    }
}

private fun DrawScope.drawFitIcon(icon: FitIcon, tint: Color) {
    val s = min(size.width, size.height)
    val base = s * 0.075f
    val stroke = Stroke(width = base, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    fun sz(w: Float, h: Float) = Size(w * s, h * s)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = base) {
        drawLine(tint, p(x1, y1), p(x2, y2), width, StrokeCap.Round)
    }
    fun rect(x: Float, y: Float, w: Float, h: Float, radius: Float = 0.08f, fill: Boolean = false) {
        drawRoundRect(
            color = tint,
            topLeft = p(x, y),
            size = sz(w, h),
            cornerRadius = CornerRadius(radius * s, radius * s),
            style = if (fill) Fill else stroke,
        )
    }
    fun circle(x: Float, y: Float, r: Float, fill: Boolean = false) {
        drawCircle(
            color = tint,
            radius = r * s,
            center = p(x, y),
            style = if (fill) Fill else stroke,
        )
    }

    when (icon) {
        FitIcon.Bell -> {
            drawArc(
                color = tint,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = p(0.23f, 0.22f),
                size = sz(0.54f, 0.58f),
                style = stroke,
            )
            line(0.25f, 0.70f, 0.75f, 0.70f)
            line(0.50f, 0.18f, 0.50f, 0.12f)
            circle(0.50f, 0.82f, 0.035f, fill = true)
        }
        FitIcon.Home -> {
            val path = Path().apply {
                moveTo(0.18f * s, 0.48f * s)
                lineTo(0.50f * s, 0.20f * s)
                lineTo(0.82f * s, 0.48f * s)
                lineTo(0.82f * s, 0.82f * s)
                lineTo(0.26f * s, 0.82f * s)
                lineTo(0.26f * s, 0.48f * s)
            }
            drawPath(path, tint, style = stroke)
            rect(0.43f, 0.60f, 0.14f, 0.22f, 0.03f)
        }
        FitIcon.Dashboard -> {
            rect(0.16f, 0.16f, 0.28f, 0.28f)
            rect(0.56f, 0.16f, 0.28f, 0.20f)
            rect(0.16f, 0.56f, 0.28f, 0.28f)
            rect(0.56f, 0.48f, 0.28f, 0.36f)
        }
        FitIcon.Dumbbell -> {
            line(0.26f, 0.50f, 0.74f, 0.50f, base * 0.85f)
            rect(0.10f, 0.37f, 0.10f, 0.26f, 0.03f)
            rect(0.21f, 0.33f, 0.09f, 0.34f, 0.03f)
            rect(0.70f, 0.33f, 0.09f, 0.34f, 0.03f)
            rect(0.80f, 0.37f, 0.10f, 0.26f, 0.03f)
        }
        FitIcon.Utensils -> {
            line(0.30f, 0.14f, 0.30f, 0.86f)
            line(0.21f, 0.14f, 0.21f, 0.38f)
            line(0.39f, 0.14f, 0.39f, 0.38f)
            line(0.21f, 0.38f, 0.39f, 0.38f)
            val path = Path().apply {
                moveTo(0.66f * s, 0.14f * s)
                cubicTo(0.85f * s, 0.20f * s, 0.86f * s, 0.45f * s, 0.67f * s, 0.52f * s)
                lineTo(0.67f * s, 0.86f * s)
            }
            drawPath(path, tint, style = stroke)
        }
        FitIcon.Activity -> {
            line(0.12f, 0.56f, 0.28f, 0.56f)
            line(0.28f, 0.56f, 0.39f, 0.32f)
            line(0.39f, 0.32f, 0.55f, 0.74f)
            line(0.55f, 0.74f, 0.68f, 0.48f)
            line(0.68f, 0.48f, 0.88f, 0.48f)
        }
        FitIcon.ArrowRight -> {
            line(0.16f, 0.50f, 0.82f, 0.50f)
            line(0.58f, 0.26f, 0.82f, 0.50f)
            line(0.58f, 0.74f, 0.82f, 0.50f)
        }
        FitIcon.ArrowLeft -> {
            line(0.84f, 0.50f, 0.18f, 0.50f)
            line(0.42f, 0.26f, 0.18f, 0.50f)
            line(0.42f, 0.74f, 0.18f, 0.50f)
        }
        FitIcon.PieChart -> {
            circle(0.50f, 0.50f, 0.34f)
            line(0.50f, 0.50f, 0.50f, 0.16f)
            line(0.50f, 0.50f, 0.82f, 0.50f)
        }
        FitIcon.Droplet -> {
            val path = Path().apply {
                moveTo(0.50f * s, 0.12f * s)
                cubicTo(0.72f * s, 0.38f * s, 0.82f * s, 0.55f * s, 0.82f * s, 0.68f * s)
                cubicTo(0.82f * s, 0.86f * s, 0.68f * s, 0.94f * s, 0.50f * s, 0.94f * s)
                cubicTo(0.32f * s, 0.94f * s, 0.18f * s, 0.86f * s, 0.18f * s, 0.68f * s)
                cubicTo(0.18f * s, 0.55f * s, 0.28f * s, 0.38f * s, 0.50f * s, 0.12f * s)
                close()
            }
            drawPath(path, tint, style = stroke)
        }
        FitIcon.UtensilsCrossed -> {
            line(0.20f, 0.18f, 0.78f, 0.86f)
            line(0.78f, 0.18f, 0.20f, 0.86f)
            line(0.22f, 0.18f, 0.22f, 0.34f, base * 0.6f)
            line(0.31f, 0.18f, 0.31f, 0.42f, base * 0.6f)
        }
        FitIcon.ArrowUpRight -> {
            line(0.23f, 0.77f, 0.77f, 0.23f)
            line(0.46f, 0.23f, 0.77f, 0.23f)
            line(0.77f, 0.23f, 0.77f, 0.54f)
        }
        FitIcon.Calendar -> {
            rect(0.16f, 0.22f, 0.68f, 0.60f, 0.06f)
            line(0.16f, 0.40f, 0.84f, 0.40f)
            line(0.34f, 0.14f, 0.34f, 0.28f)
            line(0.66f, 0.14f, 0.66f, 0.28f)
        }
        FitIcon.Camera -> {
            rect(0.14f, 0.28f, 0.72f, 0.52f, 0.08f)
            rect(0.28f, 0.18f, 0.24f, 0.14f, 0.04f)
            circle(0.50f, 0.54f, 0.16f)
        }
        FitIcon.Image -> {
            rect(0.14f, 0.18f, 0.72f, 0.64f, 0.07f)
            circle(0.34f, 0.36f, 0.06f)
            val path = Path().apply {
                moveTo(0.20f * s, 0.72f * s)
                lineTo(0.40f * s, 0.52f * s)
                lineTo(0.52f * s, 0.64f * s)
                lineTo(0.64f * s, 0.48f * s)
                lineTo(0.82f * s, 0.72f * s)
            }
            drawPath(path, tint, style = stroke)
        }
        FitIcon.Zap -> {
            val path = Path().apply {
                moveTo(0.56f * s, 0.08f * s)
                lineTo(0.22f * s, 0.56f * s)
                lineTo(0.48f * s, 0.56f * s)
                lineTo(0.42f * s, 0.92f * s)
                lineTo(0.78f * s, 0.42f * s)
                lineTo(0.52f * s, 0.42f * s)
                close()
            }
            drawPath(path, tint, style = Fill)
        }
        FitIcon.Radar -> {
            circle(0.50f, 0.50f, 0.34f)
            circle(0.50f, 0.50f, 0.20f)
            line(0.50f, 0.50f, 0.78f, 0.32f)
            circle(0.50f, 0.50f, 0.035f, fill = true)
        }
        FitIcon.Timer -> {
            circle(0.50f, 0.56f, 0.34f)
            line(0.40f, 0.12f, 0.60f, 0.12f)
            line(0.50f, 0.12f, 0.50f, 0.22f)
            line(0.50f, 0.56f, 0.50f, 0.36f)
            line(0.50f, 0.56f, 0.64f, 0.64f)
        }
        FitIcon.Flame -> {
            val path = Path().apply {
                moveTo(0.50f * s, 0.08f * s)
                cubicTo(0.76f * s, 0.30f * s, 0.84f * s, 0.50f * s, 0.74f * s, 0.72f * s)
                cubicTo(0.62f * s, 0.96f * s, 0.28f * s, 0.92f * s, 0.22f * s, 0.66f * s)
                cubicTo(0.16f * s, 0.44f * s, 0.34f * s, 0.32f * s, 0.38f * s, 0.16f * s)
                cubicTo(0.44f * s, 0.32f * s, 0.56f * s, 0.34f * s, 0.50f * s, 0.08f * s)
                close()
            }
            drawPath(path, tint, style = stroke)
        }
        FitIcon.Moon -> {
            val path = Path().apply {
                moveTo(0.72f * s, 0.20f * s)
                cubicTo(0.38f * s, 0.28f * s, 0.30f * s, 0.70f * s, 0.58f * s, 0.84f * s)
                cubicTo(0.40f * s, 0.88f * s, 0.18f * s, 0.74f * s, 0.16f * s, 0.52f * s)
                cubicTo(0.14f * s, 0.28f * s, 0.42f * s, 0.08f * s, 0.72f * s, 0.20f * s)
                close()
            }
            drawPath(path, tint, style = stroke)
        }
        FitIcon.Pause -> {
            rect(0.28f, 0.18f, 0.14f, 0.64f, 0.04f, fill = true)
            rect(0.58f, 0.18f, 0.14f, 0.64f, 0.04f, fill = true)
        }
        FitIcon.Stop -> {
            rect(0.26f, 0.26f, 0.48f, 0.48f, 0.06f, fill = true)
        }
        FitIcon.Settings -> {
            circle(0.50f, 0.50f, 0.18f)
            for (i in 0 until 8) {
                val angle = (PI / 4.0 * i).toFloat()
                val x1 = 0.50f + cos(angle) * 0.30f
                val y1 = 0.50f + sin(angle) * 0.30f
                val x2 = 0.50f + cos(angle) * 0.42f
                val y2 = 0.50f + sin(angle) * 0.42f
                line(x1, y1, x2, y2, base * 0.75f)
            }
        }
        FitIcon.Save -> {
            rect(0.18f, 0.14f, 0.64f, 0.72f, 0.06f)
            rect(0.30f, 0.14f, 0.36f, 0.22f, 0.03f)
            rect(0.30f, 0.58f, 0.40f, 0.28f, 0.03f)
            line(0.62f, 0.20f, 0.62f, 0.30f, base * 0.65f)
        }
        FitIcon.Crown -> {
            val path = Path().apply {
                moveTo(0.18f * s, 0.34f * s)
                lineTo(0.34f * s, 0.56f * s)
                lineTo(0.50f * s, 0.28f * s)
                lineTo(0.66f * s, 0.56f * s)
                lineTo(0.82f * s, 0.34f * s)
                lineTo(0.72f * s, 0.76f * s)
                lineTo(0.28f * s, 0.76f * s)
                close()
            }
            drawPath(path, tint, style = stroke)
            line(0.30f, 0.86f, 0.70f, 0.86f)
        }
    }
}

fun heroTextStyle(size: Int = 48): TextStyle =
    TextStyle(
        fontSize = size.sp,
        lineHeight = (size - 2).sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.sp,
    )
