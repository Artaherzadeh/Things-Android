package com.things.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.things.app.ui.theme.ThingsColors
import kotlin.math.cos
import kotlin.math.sin

/** Circular arrow: marks a repeating to-do. */
@Composable
fun RepeatIcon(modifier: Modifier = Modifier, color: Color = ThingsColors.TextSecondary) = Canvas(modifier) {
    val w = size.width
    val strokeWidth = w * 0.11f
    val cx = w * 0.5f
    val cy = size.height * 0.54f
    val r = w * 0.38f
    drawArc(
        color = color,
        startAngle = -60f,
        sweepAngle = 290f,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    // Arrow head at the end of the arc (clockwise tangent).
    val endRad = Math.toRadians(230.0)
    val tip = Offset(cx + r * cos(endRad).toFloat(), cy + r * sin(endRad).toFloat())
    val backX = sin(endRad).toFloat()
    val backY = -cos(endRad).toFloat()
    val len = w * 0.26f
    fun arm(deg: Double): Offset {
        val a = Math.toRadians(deg)
        return Offset(
            tip.x + (backX * cos(a) - backY * sin(a)).toFloat() * len,
            tip.y + (backX * sin(a) + backY * cos(a)).toFloat() * len,
        )
    }
    drawLine(color, tip, arm(38.0), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    drawLine(color, tip, arm(-38.0), strokeWidth = strokeWidth, cap = StrokeCap.Round)
}

/** Blue round badge with a repeat arrow, shown before the title of a repeating to-do. */
@Composable
fun RepeatBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(ThingsColors.ProjectBlue.copy(alpha = 0.2f))
            .semantics { contentDescription = "Repeats" },
        contentAlignment = Alignment.Center,
    ) {
        RepeatIcon(Modifier.size(16.dp), ThingsColors.ProjectBlue)
    }
}

/** Small filled bell: the to-do has a reminder. */
@Composable
fun BellIcon(modifier: Modifier = Modifier, color: Color = ThingsColors.TextSecondary) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val bell = Path().apply {
        moveTo(w * 0.5f, h * 0.1f)
        cubicTo(w * 0.74f, h * 0.1f, w * 0.8f, h * 0.3f, w * 0.8f, h * 0.5f)
        lineTo(w * 0.8f, h * 0.62f)
        lineTo(w * 0.9f, h * 0.74f)
        lineTo(w * 0.1f, h * 0.74f)
        lineTo(w * 0.2f, h * 0.62f)
        lineTo(w * 0.2f, h * 0.5f)
        cubicTo(w * 0.2f, h * 0.3f, w * 0.26f, h * 0.1f, w * 0.5f, h * 0.1f)
        close()
    }
    drawPath(bell, color)
    drawCircle(color, radius = w * 0.1f, center = Offset(w * 0.5f, h * 0.88f))
}

/** Outlined tag: the to-do has tags. */
@Composable
fun TagIcon(modifier: Modifier = Modifier, color: Color = ThingsColors.TextSecondary) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val tag = Path().apply {
        moveTo(w * 0.14f, h * 0.14f)
        lineTo(w * 0.52f, h * 0.14f)
        lineTo(w * 0.9f, h * 0.52f)
        lineTo(w * 0.52f, h * 0.9f)
        lineTo(w * 0.14f, h * 0.52f)
        close()
    }
    drawPath(tag, color, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.32f, h * 0.32f))
}

/** Page with a folded corner: the to-do has a note. */
@Composable
fun NoteIcon(modifier: Modifier = Modifier, color: Color = ThingsColors.TextSecondary) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val page = Path().apply {
        moveTo(w * 0.22f, h * 0.1f)
        lineTo(w * 0.58f, h * 0.1f)
        lineTo(w * 0.8f, h * 0.32f)
        lineTo(w * 0.8f, h * 0.9f)
        lineTo(w * 0.22f, h * 0.9f)
        close()
    }
    val fold = Path().apply {
        moveTo(w * 0.58f, h * 0.1f)
        lineTo(w * 0.58f, h * 0.32f)
        lineTo(w * 0.8f, h * 0.32f)
    }
    drawPath(page, color, style = stroke)
    drawPath(fold, color, style = stroke)
}

/** Three rows of circle and line: the to-do has a checklist. */
@Composable
fun ChecklistIcon(modifier: Modifier = Modifier, color: Color = ThingsColors.TextSecondary) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    listOf(0.2f, 0.5f, 0.8f).forEach { row ->
        drawCircle(
            color,
            radius = w * 0.07f,
            center = Offset(w * 0.18f, h * row),
            style = Stroke(width = w * 0.08f),
        )
        drawLine(
            color,
            Offset(w * 0.4f, h * row),
            Offset(w * 0.9f, h * row),
            strokeWidth = w * 0.08f,
            cap = StrokeCap.Round,
        )
    }
}

/** Circle with three dots: the "more" button in the top bar. */
@Composable
fun MoreCircleIcon(modifier: Modifier = Modifier, color: Color = ThingsColors.TextSecondary) = Canvas(modifier) {
    val w = size.width
    val strokeWidth = w * 0.07f
    drawCircle(color, radius = w / 2f - strokeWidth / 2f, style = Stroke(width = strokeWidth))
    listOf(0.3f, 0.5f, 0.7f).forEach {
        drawCircle(color, radius = w * 0.055f, center = Offset(w * it, size.height * 0.5f))
    }
}

/** The plain "+" on the floating add button. */
@Composable
fun PlusGlyph(modifier: Modifier = Modifier, color: Color = Color.White) = Canvas(modifier) {
    val strokeWidth = size.width * 0.09f
    drawLine(
        color,
        Offset(size.width * 0.1f, size.height * 0.5f),
        Offset(size.width * 0.9f, size.height * 0.5f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawLine(
        color,
        Offset(size.width * 0.5f, size.height * 0.1f),
        Offset(size.width * 0.5f, size.height * 0.9f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
}

/** White check mark used inside a checked checkbox. */
@Composable
fun CheckMark(modifier: Modifier = Modifier, color: Color = Color.White) = Canvas(modifier) {
    val check = Path().apply {
        moveTo(size.width * 0.24f, size.height * 0.52f)
        lineTo(size.width * 0.43f, size.height * 0.7f)
        lineTo(size.width * 0.76f, size.height * 0.32f)
    }
    drawPath(
        check,
        color,
        style = Stroke(width = size.width * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
