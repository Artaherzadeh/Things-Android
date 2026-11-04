package com.things.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.things.app.ui.home.FabShadowColor
import com.things.app.ui.home.ProgressIcon
import com.things.app.ui.home.StarIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType

private val ScreenPaddingStart = 18.dp
private val ScreenPaddingEnd = 18.dp
private val RowHeight = 44.dp
private val CheckboxSize = 20.dp
private val GroupGap = 32.dp

/**
 * Static Today page. Stateless: everything comes from [state] and every action is a callback
 * that defaults to a no-op.
 *
 * Order: to-dos with no project come first, flat and without a header; then one block per
 * project (header with progress ring, name and chevron, a divider, then that project's to-dos).
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onProjectClick: (String) -> Unit = {},
    onItemClick: (String) -> Unit = {},
    onToggleItem: (String) -> Unit = {},
    onAddClick: () -> Unit = {},
) {
    Box(
        modifier
            .fillMaxSize()
            .background(ThingsColors.Background)
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(onBack = onBack, onMoreClick = onMoreClick)
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 140.dp),
            ) {
                item(key = "title") { TitleRow() }

                items(state.looseItems, key = { "todo-${it.id}" }) { item ->
                    TodoRow(item, onItemClick, onToggleItem)
                }

                state.groups.forEachIndexed { index, group ->
                    val topGap = if (index == 0 && state.looseItems.isEmpty()) 0.dp else GroupGap
                    item(key = "group-${group.projectId}") {
                        GroupHeader(group, topGap) { onProjectClick(group.projectId) }
                    }
                    items(group.items, key = { "todo-${it.id}" }) { item ->
                        TodoRow(item, onItemClick, onToggleItem)
                    }
                }
            }
        }

        TodayFab(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, onMoreClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = ThingsColors.TextSecondary,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.semantics { contentDescription = "More" },
        ) {
            MoreCircleIcon(Modifier.size(30.dp))
        }
    }
}

@Composable
private fun TitleRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = ScreenPaddingStart, end = ScreenPaddingEnd, top = 12.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StarIcon(Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            "Today",
            style = ThingsType.PageTitle,
            modifier = Modifier
                .semantics { heading() }
                .testTag("today_title"),
        )
    }
}

@Composable
private fun GroupHeader(group: TodayGroup, topGap: Dp, onClick: () -> Unit) {
    Column {
        Spacer(Modifier.height(topGap))
        Row(
            Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .clickable(onClickLabel = "Open ${group.projectTitle}", role = Role.Button, onClick = onClick)
                .padding(start = ScreenPaddingStart, end = ScreenPaddingEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressIcon(
                progress = group.projectProgress,
                modifier = Modifier.size(24.dp),
                color = ThingsColors.ProjectBlue,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                group.projectTitle,
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
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = ScreenPaddingStart),
            thickness = 1.dp,
            color = ThingsColors.Divider,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TodoRow(
    item: TodayItem,
    onItemClick: (String) -> Unit,
    onToggle: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable(onClick = { onItemClick(item.id) })
            .padding(start = 6.dp, end = ScreenPaddingEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TodoCheckbox(checked = item.checked, title = item.title, onToggle = { onToggle(item.id) })
        Text(
            item.title,
            style = if (item.checked) {
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
        if (item.isRecurring) {
            Spacer(Modifier.width(8.dp))
            RepeatIcon(Modifier.size(16.dp))
        }
        if (item.hasReminder) {
            Spacer(Modifier.width(4.dp))
            BellIcon(Modifier.size(16.dp))
        }
    }
}

/** A 38dp-wide tap slot so the visible 20dp box lines up at 18dp from the start edge. */
@Composable
private fun TodoCheckbox(checked: Boolean, title: String, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        Modifier
            .width(38.dp)
            .fillMaxHeight()
            .clickable(onClickLabel = "Toggle $title", role = Role.Checkbox, onClick = onToggle)
            .semantics { stateDescription = if (checked) "Completed" else "Not completed" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = 12.dp)
                .size(CheckboxSize)
                .clip(shape)
                .then(if (checked) Modifier.background(ThingsColors.ProjectBlue) else Modifier)
                .border(1.5.dp, if (checked) ThingsColors.ProjectBlue else ThingsColors.CheckboxStroke, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) CheckMark(Modifier.size(CheckboxSize))
        }
    }
}

@Composable
private fun TodayFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(52.dp)
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = FabShadowColor,
                spotColor = FabShadowColor,
            )
            .clip(CircleShape)
            .background(ThingsColors.Fab)
            .clickable(onClickLabel = "Add", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PlusGlyph(Modifier.size(24.dp))
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Today - sample")
@Composable
private fun TodayScreenPreview() {
    ThingsTheme { TodayScreen(sampleTodayState()) }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Today - projects only")
@Composable
private fun TodayScreenProjectsOnlyPreview() {
    ThingsTheme { TodayScreen(sampleTodayState().copy(looseItems = emptyList())) }
}
