package com.things.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colors sampled from the Things home screen designs. */
object ThingsColors {
    val Background = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF2B2D31)
    val TextSecondary = Color(0xFF9A9BA1)
    val SearchBackground = Color(0xFFE8E9ED)
    val SearchContent = Color(0xFF9A9BA1)
    val Divider = Color(0xFFE6E8EC)

    val Inbox = Color(0xFF1FA6F7)
    val Today = Color(0xFFFFCC00)
    val Upcoming = Color(0xFFF5336F)
    val Anytime = Color(0xFF3DA394)
    val AnytimeLight = Color(0xFF6CC4B5)
    val Someday = Color(0xFFCBBB7A)
    val SomedayDark = Color(0xFFB9A862)
    val Logbook = Color(0xFF4DB562)
    val LogbookDark = Color(0xFF2F9A49)

    val Badge = Color(0xFFFF2D6E)
    val Fab = Color(0xFF4F8EEA)

    val AreaIcon = Color(0xFFB9BBC2)
    val ProgressRing = Color(0xFFC4C6CC)

    // Add / create menu
    val MenuSurface = Color(0xFF383B42)
    val MenuTitle = Color(0xFFF4F5F7)
    val MenuDescription = Color(0xFFA2A5AD)
    val MenuProject = Color(0xFF6EA0FF)
    val MenuArea = Color(0xFF4FD6A8)
    val Scrim = Color(0x66000000)
}

private val LightColors = lightColorScheme(
    primary = ThingsColors.Fab,
    background = ThingsColors.Background,
    surface = ThingsColors.Background,
    onBackground = ThingsColors.TextPrimary,
    onSurface = ThingsColors.TextPrimary,
)

/** Row title styles: lists and areas are semibold, projects are regular. */
object ThingsType {
    val RowTitleBold = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = ThingsColors.TextPrimary,
    )
    val RowTitle = RowTitleBold.copy(fontWeight = FontWeight.Normal)
    val Count = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        color = ThingsColors.TextSecondary,
    )
    val Badge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Color.White,
    )
    val Search = Count.copy(fontSize = 18.sp)
    val Settings = Count.copy(fontSize = 18.sp)
    val MenuTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        color = ThingsColors.MenuTitle,
    )
    val MenuDescription = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        color = ThingsColors.MenuDescription,
    )
}

@Composable
fun ThingsTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // The supplied designs are light only.
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content,
    )
}
