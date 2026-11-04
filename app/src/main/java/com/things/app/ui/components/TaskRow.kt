package com.things.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.things.app.ui.home.StarIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsType

/** Everything a list row needs to draw one to-do. Pure UI data; no business rules. */
data class TaskRowModel(
    val id: String,
    val title: String,
    /** Project or area name shown under the title. Null hides the line. */
    val subtitle: String? = null,
    val checked: Boolean = false,
    /** The to-do is a copy created by a repeating template: draws the grey repeat icon. */
    val isRecurring: Boolean = false,
    val hasReminder: Boolean = false,
    val hasTag: Boolean = false,
    val hasNote: Boolean = false,
    val hasChecklist: Boolean = false,
    /** True when the to-do is scheduled for Today: draws the yellow star before the title. */
    val isToday: Boolean = false,
    /** Short grey label before the title, such as the next day (Fri) or a status (Waiting). */
    val chip: String? = null,
    /** The row is a repeating template: it gets the blue round repeat badge before the title. */
    val isRepeatingTemplate: Boolean = false,
    /** What the row shows when it is opened: notes, checklist, dates, tags. */
    val details: TaskDetailsModel = TaskDetailsModel(),
)

/** Where the repeat marker of a recurring to-do is drawn. */
enum class RecurringStyle { TrailingIcon, LeadingBadge }

private val CheckboxSize = 20.dp
private val SelectShape = RoundedCornerShape(12.dp)

/**
 * One to-do row. The checkbox box sits at x = 20dp and the title starts at x = 52dp, the same x as
 * the names on Home. Next to the checkbox is a column with two parts:
 *  - the title line: chip, repeat badge and star before the title, then the reminder, note, tag
 *    and checklist icons right after the title, all on the same line;
 *  - the subtitle (project or area name), drawn under the first element of the title line, so it
 *    lines up with the repeat badge when there is one.
 *
 * In select mode ([selecting]) a ring appears at the end of the row, filled blue when [selected],
 * and a selected row gets a light blue rounded background. The row sits 8dp in from both edges.
 */
@Composable
fun TaskRow(
    task: TaskRowModel,
    modifier: Modifier = Modifier,
    recurringStyle: RecurringStyle = RecurringStyle.TrailingIcon,
    selecting: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onToggle: () -> Unit = {},
) {
    val leadingBadge = task.isRepeatingTemplate ||
        (task.isRecurring && recurringStyle == RecurringStyle.LeadingBadge)

    val selectModifier = if (selecting) {
        Modifier
            .padding(horizontal = 8.dp)
            .clip(SelectShape)
            .then(if (selected) Modifier.background(ThingsColors.ProjectBlue.copy(alpha = 0.14f)) else Modifier)
    } else {
        Modifier
    }

    Row(
        modifier
            .fillMaxWidth()
            .then(selectModifier)
            .heightIn(min = if (task.subtitle == null) ThingsSpacing.RowHeight else ThingsSpacing.TwoLineRowHeight)
            .clickable(onClick = onClick)
            .padding(
                start = if (selecting) 0.dp else 8.dp,
                end = if (selecting) 12.dp else ThingsSpacing.PageMargin,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheckbox(
            checked = task.checked,
            title = task.title,
            onToggle = onToggle,
            modifier = Modifier.heightIn(min = ThingsSpacing.RowHeight),
        )

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                task.chip?.let {
                    TaskChip(it)
                    Spacer(Modifier.width(8.dp))
                }
                if (leadingBadge) {
                    RepeatBadge()
                    Spacer(Modifier.width(8.dp))
                }
                if (task.isToday) {
                    StarIcon(Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }

                Text(
                    task.title,
                    style = if (task.checked) {
                        ThingsType.RowTitle.copy(
                            color = ThingsColors.TextSecondary,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    } else {
                        ThingsType.RowTitle
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (task.hasTag) TrailingGlyph("Tags") { TagIcon(Modifier.size(16.dp)) }
                if (task.isRecurring && recurringStyle == RecurringStyle.TrailingIcon) {
                    TrailingGlyph("Repeats") { RepeatIcon(Modifier.size(16.dp)) }
                }
                if (task.hasReminder) TrailingGlyph("Reminder") { BellIcon(Modifier.size(16.dp)) }
                if (task.hasNote) TrailingGlyph("Notes") { NoteIcon(Modifier.size(16.dp)) }
                if (task.hasChecklist) TrailingGlyph("Checklist") { ChecklistIcon(Modifier.size(16.dp)) }
            }
            task.subtitle?.let {
                Text(it, style = ThingsType.RowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (selecting) {
            Spacer(Modifier.width(8.dp))
            SelectRing(selected, task.title)
        }
    }
}

/** The ring at the end of a row in select mode: empty grey, or blue with a lighter halo when ticked. */
@Composable
private fun SelectRing(selected: Boolean, title: String) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(ThingsColors.ProjectBlue.copy(alpha = 0.3f))
                } else {
                    Modifier.border(2.dp, ThingsColors.CheckboxStroke, CircleShape)
                }
            )
            .semantics {
                contentDescription = "Select $title"
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(ThingsColors.ProjectBlue)
            )
        }
    }
}

@Composable
private fun TaskChip(text: String) {
    Text(
        text,
        style = ThingsType.Count,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ThingsColors.SearchBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun TrailingGlyph(description: String, glyph: @Composable () -> Unit) {
    Spacer(Modifier.width(8.dp))
    Box(Modifier.semantics { contentDescription = description }) { glyph() }
}

/**
 * The checkbox and its tap area. With the defaults the tap area is 44dp wide, from x = 8dp to
 * x = 52dp, the 20dp box sits at x = 20dp and 12dp of space separates it from the title. Give the
 * modifier a height to make the tap area taller.
 *
 * The whole tap area takes the touch, but only the 20dp box reacts to it: it dims while pressed.
 * There is no ripple spread over the area around the box.
 */
@Composable
internal fun TaskCheckbox(
    checked: Boolean,
    title: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    startPadding: Dp = 12.dp,
    slotWidth: Dp = 44.dp,
) {
    val shape = RoundedCornerShape(4.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .width(slotWidth)
            .fillMaxHeight()
            .clickable(
                interactionSource = source,
                indication = null,
                onClickLabel = "Toggle $title",
                role = Role.Checkbox,
                onClick = onToggle,
            )
            .semantics { stateDescription = if (checked) "Completed" else "Not completed" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = startPadding)
                .size(CheckboxSize)
                .clip(shape)
                .background(
                    when {
                        checked && pressed -> ThingsColors.ProjectBlue.copy(alpha = 0.7f)
                        checked -> ThingsColors.ProjectBlue
                        pressed -> ThingsColors.CheckboxStroke.copy(alpha = 0.25f)
                        else -> Color.Transparent
                    }
                )
                .border(2.dp, if (checked) ThingsColors.ProjectBlue else ThingsColors.CheckboxStroke, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) CheckMark(Modifier.size(CheckboxSize))
        }
    }
}
