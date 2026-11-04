package com.things.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.things.app.ui.theme.ThingsColors

/** Three dots in a row, 24dp wide and 8dp high. Grey next to a page title, blue at the end of a heading. */
@Composable
fun MenuDots(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 24.dp, height = 8.dp)) {
        val radius = size.height / 2f
        repeat(3) { index ->
            drawCircle(
                color,
                radius = radius,
                center = Offset(radius + index * (size.width - size.height) / 2f, radius),
            )
        }
    }
}

/** The grey dots drawn right after a project or area title: opens that page's menu. */
@Composable
fun TitleMenuButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        MenuDots(ThingsColors.TextSecondary)
    }
}
