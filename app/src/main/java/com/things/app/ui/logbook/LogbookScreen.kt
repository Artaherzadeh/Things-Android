package com.things.app.ui.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.CheckMark
import com.things.app.ui.components.ChecklistIcon
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.RepeatIcon
import com.things.app.ui.home.LogbookIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType

/** Static presentation only: does not complete, restore or delete tasks. */
@Composable
fun LogbookScreen(
    state: LogbookUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onEntryClick: (String) -> Unit = {},
) {
    ListPageScaffold(
        title = "Logbook",
        icon = { LogbookIcon(Modifier.size(28.dp)) },
        modifier = modifier,
        titleTag = "logbook_title",
        onBack = onBack,
        onMoreClick = onMoreClick,
        onAddClick = onAddClick,
    ) {
        state.months.forEachIndexed { index, month ->
            item(key = "month-${month.id}") {
                Column(Modifier.padding(start = 18.dp, end = 18.dp)) {
                    Spacer(Modifier.height(if (index == 0) 12.dp else 40.dp))
                    Text(
                        month.title,
                        style = ThingsType.RowTitleBold,
                        modifier = Modifier.padding(vertical = 12.dp).semantics { heading() },
                    )
                    HorizontalDivider(color = ThingsColors.Divider)
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(month.entries, key = { "entry-${month.id}-${it.id}" }) { entry ->
                LogbookRow(entry, onClick = { onEntryClick(entry.id) })
            }
        }
    }
}

@Composable
private fun LogbookRow(entry: LogbookEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .testTag("logbook_entry_${entry.id}")
            .semantics { stateDescription = "Completed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))
                .background(ThingsColors.ProjectBlue),
            contentAlignment = Alignment.Center,
        ) {
            CheckMark(Modifier.size(18.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            entry.dateLabel,
            style = ThingsType.RowSubtitle.copy(color = ThingsColors.ProjectBlue),
            modifier = Modifier.widthIn(min = 42.dp),
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
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
                    Spacer(Modifier.width(6.dp))
                    RepeatIcon(Modifier.size(16.dp))
                }
                if (entry.hasChecklist) {
                    Spacer(Modifier.width(6.dp))
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
