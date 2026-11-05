package com.things.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.SelectionAction
import com.things.app.ui.components.SwipeToDeleteRow
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsType

/** The pieces of the Home select mode: a row that can be ticked, the Done pill and the menu entries. */

/** Space the light blue background of a ticked row keeps from the screen edges. */
private val SelectInset = 8.dp

/**
 * A Home row (project or area). Normally swiping it to the left calls [onSwipeLeft], which starts the
 * select mode. In the select mode ([selecting]) the row has a ring at its end, a tap ticks or unticks
 * it ([onToggle]) instead of opening it, and a ticked row gets a light blue background.
 */
@Composable
internal fun HomeSwipeRow(
    selecting: Boolean,
    selected: Boolean,
    onSwipeLeft: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (selecting) {
        SelectableRow(selected, onToggle, modifier, content)
    } else {
        SwipeToDeleteRow(modifier = modifier, onSwipeLeft = onSwipeLeft, content = content)
    }
}

@Composable
private fun SelectableRow(
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val highlight = ThingsColors.ProjectBlue.copy(alpha = 0.14f)
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                if (selected) {
                    val inset = SelectInset.toPx()
                    drawRoundRect(
                        color = highlight,
                        topLeft = Offset(inset, 0f),
                        size = Size(size.width - 2 * inset, size.height),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                }
            }
    ) {
        content()
        // Catches the tap before the row, so a tap ticks instead of opening.
        Box(
            Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Checkbox,
                    onClick = onToggle,
                )
        )
        // A box as high as the first line, so the ring is exactly in the middle of it.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 20.dp)
                .height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            SelectRing(selected)
        }
    }
}

/** The same ring as the to-do rows: empty grey, or blue with a lighter halo when ticked. */
@Composable
private fun SelectRing(selected: Boolean) {
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
            ),
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

/** The blue Done pill that ends the select mode, like the one on the list pages. */
@Composable
internal fun DonePill(onDone: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ThingsColors.Fab)
            .clickable(onClickLabel = "Done", role = Role.Button, onClick = onDone)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Done", style = ThingsType.RowTitleBold.copy(color = Color.White))
    }
}

/**
 * The entries of the three-dots menu of the Home select bar. Only projects ticked: Mark as Completed,
 * Set Tags, Set Deadline and Repeat, then Duplicate, then Share. An area among the ticked rows: Set
 * Tags, then Share.
 */
internal fun homeMenuGroups(projectsOnly: Boolean): List<List<SelectionAction>> =
    if (projectsOnly) {
        listOf(
            listOf(
                SelectionAction.MarkCompleted,
                SelectionAction.SetTags,
                SelectionAction.SetDeadline,
                SelectionAction.Repeat,
            ),
            listOf(SelectionAction.Duplicate),
            listOf(SelectionAction.Share),
        )
    } else {
        listOf(
            listOf(SelectionAction.SetTags),
            listOf(SelectionAction.Share),
        )
    }
