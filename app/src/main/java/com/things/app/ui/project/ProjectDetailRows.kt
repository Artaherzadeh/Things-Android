package com.things.app.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.things.app.ui.components.BellIcon
import com.things.app.ui.components.DeadlineModel
import com.things.app.ui.components.RepeatIcon
import com.things.app.ui.components.RepeatInfoModel
import com.things.app.ui.components.ScheduleKind
import com.things.app.ui.components.ScheduleModel
import com.things.app.ui.home.ArchiveIcon
import com.things.app.ui.home.CalendarIcon
import com.things.app.ui.home.StarIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsType

private val DetailPrimary = ThingsType.RowTitle.copy(fontSize = 16.sp, lineHeight = 24.sp)
private val DetailSecondary = ThingsType.Count.copy(fontSize = 15.sp)

/**
 * What a project holds itself, under its title and above its notes: the tags, the When line with its
 * reminder, the deadline with how long is left, and the repeat. Each line shows only while it is
 * set, and a thin line runs above every line and under the last one. A tap on the tags, the When
 * line or the deadline line reports through [onTags], [onSchedule] or [onDeadline] so the page can
 * open the sheet of that value; the repeat line only shows what is set. Nothing is drawn when the
 * project holds none of them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectDetailRows(
    tags: List<String>,
    schedule: ScheduleModel?,
    deadline: DeadlineModel?,
    repeat: RepeatInfoModel?,
    onTags: () -> Unit,
    onSchedule: () -> Unit,
    onDeadline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty() && schedule == null && deadline == null && repeat == null) return

    Column(modifier.fillMaxWidth().padding(horizontal = ThingsSpacing.PageMargin)) {
        if (tags.isNotEmpty()) {
            Separator()
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onTags)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { TagChip(it) }
            }
        }
        schedule?.let {
            Separator()
            DetailLine(onClick = onSchedule, icon = { ScheduleKindIcon(it.kind) }) {
                Text(it.label, style = DetailPrimary)
                it.reminder?.let { time ->
                    Spacer(Modifier.width(8.dp))
                    BellIcon(Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(time, style = DetailSecondary)
                }
            }
        }
        deadline?.let {
            Separator()
            DetailLine(
                onClick = onDeadline,
                icon = {
                    Icon(
                        Icons.Outlined.Flag,
                        contentDescription = "Deadline",
                        tint = ThingsColors.TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                },
            ) {
                Text(it.dateLabel, style = DetailPrimary)
                it.remainingLabel?.let { left ->
                    Spacer(Modifier.width(8.dp))
                    Text(left, style = DetailSecondary)
                }
            }
        }
        repeat?.let {
            Separator()
            DetailLine(onClick = null, icon = { RepeatIcon(Modifier.size(20.dp), ThingsColors.Fab) }) {
                Text(
                    buildAnnotatedString {
                        append("Repeat " + it.title.replaceFirstChar { c -> c.lowercase() })
                        withStyle(SpanStyle(color = ThingsColors.TextSecondary, fontSize = 15.sp)) {
                            append(" \u2013 " + it.summary)
                        }
                    },
                    style = DetailPrimary,
                )
            }
        }
        Separator()
    }
}

@Composable
private fun Separator() {
    HorizontalDivider(thickness = 1.dp, color = ThingsColors.Divider)
}

/** One line: an icon in a 24dp slot, 8dp of space and the content, at least 48dp high. */
@Composable
private fun DetailLine(
    onClick: (() -> Unit)?,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(8.dp))
        content()
    }
}

@Composable
private fun ScheduleKindIcon(kind: ScheduleKind) {
    when (kind) {
        ScheduleKind.Today -> StarIcon(Modifier.size(20.dp))
        ScheduleKind.Evening -> Icon(
            Icons.Outlined.NightsStay,
            contentDescription = null,
            tint = ThingsColors.ProjectBlue,
            modifier = Modifier.size(20.dp),
        )
        ScheduleKind.Someday -> ArchiveIcon(Modifier.size(20.dp))
        ScheduleKind.Date -> CalendarIcon(Modifier.size(20.dp))
    }
}

@Composable
private fun TagChip(name: String) {
    Text(
        name,
        style = ThingsType.Count.copy(fontSize = 14.sp, color = ThingsColors.Anytime),
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(ThingsColors.AnytimeLight.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
