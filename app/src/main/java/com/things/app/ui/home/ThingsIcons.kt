package com.things.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.things.app.ui.theme.ThingsColors
import kotlin.math.cos
import kotlin.math.sin

/*
 * Canvas drawn approximations of the Things icons so the screen runs with no assets.
 * To use the exact Figma artwork, export each icon as SVG, import it through
 * Android Studio (New > Vector Asset) and swap it in for the matching composable.
 */

private val IconSize = 24.dp

private fun DrawScope.p(x: Float, y: Float) = Offset(size.width * x, size.height * y)

@Composable
fun InboxIcon(modifier: Modifier = Modifier.size(IconSize)) = Canvas(modifier) {
    drawRoundRect(ThingsColors.Inbox, cornerRadius = CornerRadius(size.width * 0.24f))
    // White tray
    val tray = Path().apply {
        moveTo(size.width * 0.2f, size.height * 0.22f)
        lineTo(size.width * 0.8f, size.height * 0.22f)
        lineTo(size.width * 0.8f, size.height * 0.72f)
        quadraticTo(size.width * 0.8f, size.height * 0.8f, size.width * 0.72f, size.height * 0.8f)
        lineTo(size.width * 0.28f, size.height * 0.8f)
        quadraticTo(size.width * 0.2f, size.height * 0.8f, size.width * 0.2f, size.height * 0.72f)
        close()
    }
    drawPath(tray, Color.White)
    // Tray opening
    val opening = Path().apply {
        moveTo(size.width * 0.2f, size.height * 0.22f)
        lineTo(size.width * 0.34f, size.height * 0.22f)
        lineTo(size.width * 0.34f, size.height * 0.45f)
        lineTo(size.width * 0.66f, size.height * 0.45f)
        lineTo(size.width * 0.66f, size.height * 0.22f)
        lineTo(size.width * 0.8f, size.height * 0.22f)
    }
    drawPath(opening, ThingsColors.Inbox, style = Stroke(width = size.width * 0.001f))
    drawRect(
        ThingsColors.Inbox,
        topLeft = p(0.34f, 0.2f),
        size = Size(size.width * 0.32f, size.height * 0.25f),
    )
}

@Composable
fun StarIcon(modifier: Modifier = Modifier.size(IconSize)) = Canvas(modifier) {
    val cx = size.width / 2f
    val cy = size.height * 0.54f
    val outer = size.width * 0.5f
    val inner = outer * 0.5f
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = Math.toRadians((-90.0 + i * 36.0))
        val x = cx + (r * cos(a)).toFloat()
        val y = cy + (r * sin(a)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, ThingsColors.Today)
    drawPath(
        path,
        ThingsColors.Today,
        style = Stroke(width = size.width * 0.1f, join = StrokeJoin.Round),
    )
}

@Composable
fun CalendarIcon(modifier: Modifier = Modifier.size(IconSize)) = Canvas(modifier) {
    val r = size.width * 0.2f
    drawRoundRect(
        ThingsColors.Upcoming,
        topLeft = p(0.02f, 0.1f),
        size = Size(size.width * 0.96f, size.height * 0.84f),
        cornerRadius = CornerRadius(r),
    )
    // Binder pins
    listOf(0.3f, 0.7f).forEach {
        drawLine(
            Color.White,
            start = p(it, 0.02f),
            end = p(it, 0.2f),
            strokeWidth = size.width * 0.08f,
            cap = StrokeCap.Round,
        )
    }
    // Light body
    drawRoundRect(
        Color.White.copy(alpha = 0.88f),
        topLeft = p(0.12f, 0.34f),
        size = Size(size.width * 0.76f, size.height * 0.5f),
        cornerRadius = CornerRadius(size.width * 0.08f),
    )
    // Date grid
    for (row in 0 until 3) {
        for (col in 0 until 4) {
            drawRoundRect(
                ThingsColors.Upcoming.copy(alpha = 0.55f),
                topLeft = p(0.2f + col * 0.16f, 0.42f + row * 0.13f),
                size = Size(size.width * 0.1f, size.height * 0.07f),
                cornerRadius = CornerRadius(size.width * 0.02f),
            )
        }
    }
}

@Composable
fun LayersIcon(modifier: Modifier = Modifier.size(IconSize)) = Canvas(modifier) {
    fun diamond(yOffset: Float) = Path().apply {
        moveTo(size.width * 0.5f, size.height * (0.08f + yOffset))
        lineTo(size.width * 0.96f, size.height * (0.3f + yOffset))
        lineTo(size.width * 0.5f, size.height * (0.52f + yOffset))
        lineTo(size.width * 0.04f, size.height * (0.3f + yOffset))
        close()
    }
    // Bottom, middle (light) then top (dark) layers
    drawPath(diamond(0.36f), ThingsColors.AnytimeLight, style = Stroke(size.width * 0.1f, join = StrokeJoin.Round))
    drawPath(diamond(0.18f), ThingsColors.AnytimeLight, style = Stroke(size.width * 0.1f, join = StrokeJoin.Round))
    drawPath(diamond(0.18f), ThingsColors.Anytime)
    drawPath(diamond(0f), ThingsColors.Anytime)
    drawPath(diamond(0f), ThingsColors.Anytime, style = Stroke(size.width * 0.1f, join = StrokeJoin.Round))
}

@Composable
fun ArchiveIcon(modifier: Modifier = Modifier.size(IconSize)) = Canvas(modifier) {
    // Lid
    drawRoundRect(
        ThingsColors.Someday,
        topLeft = p(0.02f, 0.12f),
        size = Size(size.width * 0.96f, size.height * 0.26f),
        cornerRadius = CornerRadius(size.width * 0.04f),
    )
    // Body
    drawRoundRect(
        ThingsColors.SomedayDark,
        topLeft = p(0.1f, 0.34f),
        size = Size(size.width * 0.8f, size.height * 0.56f),
        cornerRadius = CornerRadius(size.width * 0.05f),
    )
    // Handle slot
    drawRoundRect(
        Color(0xFFF1E9C4),
        topLeft = p(0.34f, 0.48f),
        size = Size(size.width * 0.32f, size.height * 0.1f),
        cornerRadius = CornerRadius(size.width * 0.03f),
    )
}

@Composable
fun LogbookIcon(modifier: Modifier = Modifier.size(IconSize)) = Canvas(modifier) {
    drawRoundRect(
        ThingsColors.Logbook,
        topLeft = p(0.12f, 0.04f),
        size = Size(size.width * 0.76f, size.height * 0.92f),
        cornerRadius = CornerRadius(size.width * 0.1f),
    )
    drawRoundRect(
        ThingsColors.LogbookDark,
        topLeft = p(0.12f, 0.04f),
        size = Size(size.width * 0.14f, size.height * 0.92f),
        cornerRadius = CornerRadius(size.width * 0.06f),
    )
    val check = Path().apply {
        moveTo(size.width * 0.34f, size.height * 0.52f)
        lineTo(size.width * 0.5f, size.height * 0.68f)
        lineTo(size.width * 0.74f, size.height * 0.36f)
    }
    drawPath(
        check,
        Color.White,
        style = Stroke(width = size.width * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** Outlined box used for Areas. */
@Composable
fun AreaIcon(
    modifier: Modifier = Modifier.size(IconSize),
    color: Color = ThingsColors.AreaIcon,
) = Canvas(modifier) {
    val stroke = Stroke(width = size.width * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val outline = Path().apply {
        moveTo(size.width * 0.5f, size.height * 0.06f)
        lineTo(size.width * 0.92f, size.height * 0.28f)
        lineTo(size.width * 0.92f, size.height * 0.72f)
        lineTo(size.width * 0.5f, size.height * 0.94f)
        lineTo(size.width * 0.08f, size.height * 0.72f)
        lineTo(size.width * 0.08f, size.height * 0.28f)
        close()
    }
    drawPath(outline, color, style = stroke)
    val lid = Path().apply {
        moveTo(size.width * 0.08f, size.height * 0.28f)
        lineTo(size.width * 0.5f, size.height * 0.5f)
        lineTo(size.width * 0.92f, size.height * 0.28f)
    }
    drawPath(lid, color, style = stroke)
}

/** Project icon: a ring that fills clockwise as the project progresses (0f..1f). */
@Composable
fun ProgressIcon(
    progress: Float,
    modifier: Modifier = Modifier.size(IconSize),
    color: Color = ThingsColors.ProgressRing,
) = Canvas(modifier) {
    val ringWidth = size.width * 0.085f
    val inset = ringWidth / 2f
    drawCircle(
        color,
        radius = size.width / 2f - inset,
        style = Stroke(width = ringWidth),
    )
    if (progress > 0f) {
        val pieInset = size.width * 0.19f
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = true,
            topLeft = Offset(pieInset, pieInset),
            size = Size(size.width - pieInset * 2, size.height - pieInset * 2),
        )
    }
}

val IconDefaultSize: Dp = IconSize
