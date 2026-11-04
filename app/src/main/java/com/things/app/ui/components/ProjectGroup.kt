package com.things.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.things.app.ui.home.ProgressIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsType

/** Vertical gap between one project block and the block above it. */
val GroupGap = 32.dp

/** A project and the to-dos that belong to it, as drawn on list pages. Pure UI data. */
data class TaskGroupModel(
    val projectId: String,
    val projectTitle: String,
    /** 0f..1f, drives the ring icon. */
    val projectProgress: Float,
    val items: List<TaskRowModel>,
)

/**
 * A tappable project line: blue progress ring (24dp, at x = 20dp), bold name (at x = 52dp, in line
 * with the to-do titles and the names on Home) and a small chevron.
 *
 * Without [onToggleExpanded] the chevron only points right. With it, the chevron points down while
 * the project is [expanded] and turns to the side when collapsed, like the areas on Home; tapping
 * the chevron calls [onToggleExpanded] and tapping the rest of the line calls [onClick].
 */
@Composable
fun ProjectLinkRow(
    title: String,
    progress: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    expanded: Boolean = true,
    onToggleExpanded: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(ThingsSpacing.RowHeight)
            .clickable(onClickLabel = "Open $title", role = Role.Button, onClick = onClick)
            .padding(horizontal = ThingsSpacing.PageMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressIcon(
            progress = progress,
            modifier = Modifier.size(24.dp),
            color = ThingsColors.ProjectBlue,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = ThingsType.RowTitleBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(4.dp))
        if (onToggleExpanded != null) {
            val rotation by animateFloatAsState(if (expanded) 0f else -90f, label = "projectChevron")
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(
                        onClickLabel = if (expanded) "Collapse $title" else "Expand $title",
                        role = Role.Button,
                        onClick = onToggleExpanded,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = ThingsColors.TextSecondary,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotation),
                )
            }
        } else {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = ThingsColors.TextSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Header of a project block: the project line and a divider under it.
 * [topGap] is the space above the header (use 0.dp for the first block under the title).
 * Pass [onToggleExpanded] to make the chevron open and close the project's to-dos.
 */
@Composable
fun ProjectGroupHeader(
    title: String,
    progress: Float,
    modifier: Modifier = Modifier,
    topGap: Dp = GroupGap,
    onClick: () -> Unit = {},
    expanded: Boolean = true,
    onToggleExpanded: (() -> Unit)? = null,
) {
    Column(modifier) {
        Spacer(Modifier.height(topGap))
        ProjectLinkRow(
            title = title,
            progress = progress,
            onClick = onClick,
            expanded = expanded,
            onToggleExpanded = onToggleExpanded,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = ThingsSpacing.PageMargin),
            thickness = 1.dp,
            color = ThingsColors.Divider,
        )
        Spacer(Modifier.height(12.dp))
    }
}
