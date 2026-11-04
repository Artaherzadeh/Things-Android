package com.things.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.things.app.ui.theme.ThingsColors
import kotlin.math.cos
import kotlin.math.sin

/** Small circular arrow shown after a recurring to-do. */
@Composable
fun RepeatIcon(modifier: Modifier = Modifier, color: Color = ThingsColors.TextSecondary) = Canvas(modifier) {
    val w = size.width
    val strokeWidth = w * 0.11f
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
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
        style = stroke,
    )
    // Arrow head at the end of the arc (clockwise tangent).
    val endRad = Math.toRadians(230.0)
    val tip = Offset(cx + r * cos(endRad).toFloat(), cy + r * sin(endRad).toFloat())
    val dirX = -sin(endRad).toFloat()
    val dirY = cos(endRad).toFloat()
    val len = w * 0.26f
    fun arm(deg: Double): Offset {
        val a = Math.toRadians(deg)
        val bx = -dirX
        val by = -dirY
        return Offset(
            tip.x + (bx * cos(a) - by * sin(a)).toFloat() * len,
            tip.y + (bx * sin(a) + by * cos(a)).toFloat() * len,
        )
    }
    drawLine(color, tip, arm(38.0), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    drawLine(color, tip, arm(-38.0), strokeWidth = strokeWidth, cap = StrokeCap.Round)
}

/** Small filled bell shown after a to-do that has a reminder. */
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

/** The plain "+" drawn on the Today page's floating button. */
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
