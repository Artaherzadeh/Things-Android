package com.things.app.ui.logbook

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.ChecklistIcon
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.RepeatIcon
import com.things.app.ui.components.TaskCard
import com.things.app.ui.components.TaskCheckbox
import com.things.app.ui.components.TaskExpansion
import com.things.app.ui.components.TaskRowModel
import com.things.app.ui.components.rememberTaskExpansion
import com.things.app.ui.home.LogbookIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType
import kotlinx.coroutines.delay

/**
 * Logbook: the completed to-dos, a block per month.
 *
 * - Tap a to-do: it opens in place as a card, like on the other pages.
 * - Untick its checkbox: the to-do is not completed any more, so it leaves the Logbook (the row folds
 *   up) and [onRestoreEntry] is told its id, so the page that owns the to-do can show it again
 *   where it was. This page only holds sample data, so nothing is put back anywhere yet.
 */
@Composable
fun LogbookScreen(
    state: LogbookUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onEntryClick: (String) -> Unit = {},
    onRestoreEntry: (String) -> Unit = {},
) {
    val expansion = rememberTaskExpansion()
    var restored by remember { mutableStateOf(emptySet<String>()) }

    ListPageScaffold(
        title = "Logbook",
        icon = { LogbookIcon(Modifier.size(28.dp)) },
        modifier = modifier,
        titleTag = "logbook_title",
        onBack = onBack,
        onMoreClick = onMoreClick,
        onAddClick = onAddClick,
        expansion = expansion,
    ) {
        state.months.forEachIndexed { index, month ->
            item(key = "month-${month.id}") {
                Column(Modifier.padding(horizontal = ThingsSpacing.PageMargin)) {
                    Spacer(Modifier.height(if (index == 0) 12.dp else 40.dp))
                    Text(
                        month.title,
                        style = ThingsType.RowTitleBold,
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .semantics { heading() },
                    )
                    HorizontalDivider(color = ThingsColors.Divider)
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(month.entries.filter { it.id !in restored }, key = { "entry-${month.id}-${it.id}" }) { entry ->
                LogbookEntryRow(
                    entry = entry,
                    expansion = expansion,
                    onOpen = { onEntryClick(entry.id) },
                    onRestore = { id ->
                        restored = restored + id
                        onRestoreEntry(id)
                    },
                )
            }
        }
    }
}

/** One entry: the row, or the open card; unticking it folds it away and then restores it. */
@Composable
private fun LogbookEntryRow(
    entry: LogbookEntry,
    expansion: TaskExpansion,
    onOpen: () -> Unit,
    onRestore: (String) -> Unit,
) {
    var restoring by remember { mutableStateOf(false) }
    val model = remember(entry) {
        TaskRowModel(
            id = entry.id,
            title = entry.title,
            checked = true,
            isRecurring = entry.isRecurring,
            hasChecklist = entry.hasChecklist,
        )
    }
    val current = expansion.resolve(model)
    val expanded = expansion.isExpanded(entry.id)

    // The row folds up first; the page drops it from its list when the animation is over.
    LaunchedEffect(restoring) {
        if (restoring) {
            delay(300)
            onRestore(entry.id)
        }
    }

    AnimatedVisibility(
        visible = !restoring,
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        Box(Modifier.animateContentSize()) {
            if (expanded) {
                BackHandler { expansion.collapse() }
                Box(Modifier.onGloballyPositioned { expansion.cardBounds = it.boundsInRoot() }) {
                    TaskCard(
                        task = current,
                        onEdit = { change -> expansion.edit(model, change) },
                        onToggle = {
                            restoring = true
                            expansion.collapse()
                        },
                    )
                }
            } else {
                LogbookRow(
                    entry = entry,
                    title = current.title,
                    onClick = {
                        expansion.expand(entry.id)
                        onOpen()
                    },
                    onRestore = { restoring = true },
                )
            }
        }
    }
}

/** The checked box is at x = 20dp, in line with the checkboxes of the other pages. */
@Composable
private fun LogbookRow(entry: LogbookEntry, title: String, onClick: () -> Unit, onRestore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ThingsSpacing.RowHeight)
            .clickable(onClick = onClick)
            .padding(start = ThingsSpacing.PageMargin, end = ThingsSpacing.PageMargin, top = 4.dp, bottom = 4.dp)
            .testTag("logbook_entry_${entry.id}")
            .semantics { stateDescription = "Completed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The 28dp slot is the 20dp box and the 8dp after it; only the box reacts to a press.
        TaskCheckbox(
            checked = true,
            title = title,
            onToggle = onRestore,
            modifier = Modifier.heightIn(min = 36.dp),
            startPadding = 0.dp,
            slotWidth = 28.dp,
        )
        Text(
            entry.dateLabel,
            style = ThingsType.RowSubtitle.copy(color = ThingsColors.ProjectBlue),
            modifier = Modifier.widthIn(min = 48.dp),
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = ThingsType.RowTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.isRecurring) {
                    Spacer(Modifier.width(8.dp))
                    RepeatIcon(Modifier.size(16.dp))
                }
                if (entry.hasChecklist) {
                    Spacer(Modifier.width(8.dp))
                    ChecklistIcon(Modifier.size(16.dp))
                }
            }
            entry.subtitle?.let { subtitle ->
                Text(
                    subtitle, style = ThingsType.RowSubtitle,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Logbook")
@Composable
private fun LogbookPreview() {
    ThingsTheme { LogbookScreen(sampleLogbookState()) }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Logbook - empty")
@Composable
private fun EmptyLogbookPreview() {
    ThingsTheme { LogbookScreen(LogbookUiState()) }
}
