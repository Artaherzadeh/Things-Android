package com.things.app.ui.logbook

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.ChecklistIcon
import com.things.app.ui.components.ExpandableTaskRow
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.RepeatIcon
import com.things.app.ui.components.TaskCheckbox
import com.things.app.ui.components.TaskRowModel
import com.things.app.ui.components.rememberTaskExpansion
import com.things.app.ui.home.LogbookIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType

/**
 * Logbook: the completed to-dos, a month at a time.
 *
 * - Tap a to-do: it opens in place as a card, like on the other pages.
 * - Untick its checkbox (on the row or on the card): the to-do folds away and goes back to where it
 *   was. [onRestore] gets the entry so the page that owns it can take it back. The entry is only
 *   removed from this list; nothing else is stored here.
 */
@Composable
fun LogbookScreen(
    state: LogbookUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onEntryClick: (String) -> Unit = {},
    onRestore: (LogbookEntry) -> Unit = {},
) {
    val expansion = rememberTaskExpansion()
    var removed by remember { mutableStateOf(emptySet<String>()) }

    // Months without any entry left are not drawn.
    val months = state.months
        .map { it to it.entries.filter { entry -> entry.id !in removed } }
        .filter { it.second.isNotEmpty() }

    ListPageScaffold(
        title = "Logbook",
        icon = { LogbookIcon(Modifier.size(28.dp)) },
        modifier = modifier,
        titleTag = "logbook_title",
        onBack = onBack,
        onMoreClick = onMoreClick,
        expansion = expansion,
        onAddClick = onAddClick,
    ) {
        months.forEachIndexed { index, (month, entries) ->
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
            items(entries, key = { "entry-${month.id}-${it.id}" }) { entry ->
                ExpandableTaskRow(
                    task = entry.toTask(),
                    expansion = expansion,
                    onOpen = { onEntryClick(entry.id) },
                    onToggle = {
                        expansion.delete(entry.id)
                        onRestore(entry)
                    },
                    onDelete = { removed = removed + entry.id },
                    closedRow = { _, open, toggle -> LogbookRow(entry, open, toggle) },
                )
            }
        }
    }
}

/** The entry as a to-do, completed, so the card and the sheets can work on it. */
private fun LogbookEntry.toTask() = TaskRowModel(
    id = id,
    title = title,
    subtitle = subtitle,
    checked = true,
    isRecurring = isRecurring,
    hasChecklist = hasChecklist,
)

/**
 * One completed to-do: the checked box at x = 20dp, in line with the checkboxes of the other pages,
 * then the date in blue and the title. Tapping the box takes the to-do out of the Logbook; tapping
 * anywhere else opens it.
 */
@Composable
private fun LogbookRow(entry: LogbookEntry, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ThingsSpacing.RowHeight)
            .clickable(onClick = onOpen)
            .padding(start = 8.dp, end = ThingsSpacing.PageMargin)
            .testTag("logbook_entry_${entry.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheckbox(
            checked = true,
            title = entry.title,
            onToggle = onToggle,
            modifier = Modifier.heightIn(min = ThingsSpacing.RowHeight),
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
                    entry.title,
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
