package com.things.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The layout grid of the app. Every padding, gap, size and offset is a multiple of 4dp. The only
 * exceptions are hairlines (1dp dividers and borders) and the corner radius of round shapes.
 *
 * Rules that follow from these values, so things line up on every page:
 *  - Page content starts [PageMargin] from the screen edge and ends [PageMargin] from the other.
 *  - A checkbox or a leading icon sits at x = [PageMargin]; the text after it starts at
 *    x = [TextStart], on Home, in lists, in project headers and inside open to-do cards.
 *  - Floating buttons sit [FabMargin] from the right edge and [FabMargin] above the bottom edge.
 *  - A card or a popover keeps the same distance from the sides as from the top or bottom.
 */
object ThingsSpacing {
    val PageMargin = 20.dp
    val TextStart = 52.dp
    val RowHeight = 44.dp
    val TwoLineRowHeight = 56.dp
    val FabSize = 52.dp
    val FabMargin = 20.dp
}
