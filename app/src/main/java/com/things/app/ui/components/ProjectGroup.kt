package com.things.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.things.app.ui.home.ProgressIcon
import com.things.app.ui.theme.ThingsColors
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

/** A tappable project line: blue progress ring, bold name and a small chevron. */
@Composable
fun ProjectLinkRow(
    title: String,
    progress: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClickLabel = "Open $title", role = Role.Button, onClick = onClick)
            .padding(start = 18.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressIcon(
            progress = progress,
            modifier = Modifier.size(24.dp),
            color = ThingsColors.ProjectBlue,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = ThingsType.RowTitleBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = ThingsColors.TextSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Header of a project block: the project line and a divider under it.
 * [topGap] is the space above the header (use 0.dp for the first block under the title).
 */
@Composable
fun ProjectGroupHeader(
    title: String,
    progress: Float,
    modifier: Modifier = Modifier,
    topGap: Dp = GroupGap,
    onClick: () -> Unit = {},
) {
    Column(modifier) {
        Spacer(Modifier.height(topGap))
        ProjectLinkRow(title = title, progress = progress, onClick = onClick)
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 18.dp),
            thickness = 1.dp,
            color = ThingsColors.Divider,
        )
        Spacer(Modifier.height(12.dp))
    }
}
