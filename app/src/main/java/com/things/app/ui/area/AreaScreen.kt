package com.things.app.ui.area

import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.AddTarget
import com.things.app.ui.components.AreaMenuActions
import com.things.app.ui.components.DropGapHeight
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.LocalDropSlot
import com.things.app.ui.components.PageMenuAction
import com.things.app.ui.components.ProjectLinkRow
import com.things.app.ui.components.TagsSheet
import com.things.app.ui.components.TitleMenuButton
import com.things.app.ui.home.AreaIcon
import com.things.app.ui.home.ProgressIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType

private const val ProjectKeyPrefix = "area-project-"

/**
 * Area page: the area icon and title with its menu, then one line per project (ring, name,
 * chevron). Tapping a project reports its id. Every action is a callback.
 *
 * The three-dots menu of this page has Add Tags (opens the tag list), Delete Area ([onDeleteArea])
 * and Share (the area name and its projects, through the Android share sheet).
 *
 * Add button: a tap adds a project at the top; hold it and drag over the list and the rows make
 * room where the project will land; let go and [onCreateProjectAt] gets the index. The page that
 * owns the data adds the project and sets [editingId] to its id: that row is then a text field with
 * the keyboard open. [onTitleChange] reports each edit and [onEditDone] tells the page the cursor
 * left the row, so it can drop the project if it is still empty. There are no headings here, so
 * the left edge of the screen has no special meaning.
 */
@Composable
fun AreaScreen(
    state: AreaUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onTitleMenuClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onProjectClick: (String) -> Unit = {},
    onDeleteArea: () -> Unit = {},
    onCreateProjectAt: (index: Int) -> Unit = {},
    editingId: String? = null,
    onTitleChange: (id: String, title: String) -> Unit = { _, _ -> },
    onEditDone: (id: String) -> Unit = {},
) {
    val context = LocalContext.current
    var tagsOpen by remember { mutableStateOf(false) }
    var areaTags by remember(state.id) { mutableStateOf(emptyList<String>()) }

    ListPageScaffold(
        title = state.title,
        icon = { AreaIcon(Modifier.size(28.dp), ThingsColors.MenuArea) },
        modifier = modifier,
        titleTag = "area_title",
        titleTrailing = { TitleMenuButton("Area menu", onTitleMenuClick) },
        titleBottomPadding = 32.dp,
        onBack = onBack,
        onMoreClick = onMoreClick,
        menuActions = AreaMenuActions,
        enabledMenuActions = AreaMenuActions.toSet(),
        onMenuAction = { action ->
            when (action) {
                PageMenuAction.AddTags -> tagsOpen = true
                PageMenuAction.DeleteArea -> onDeleteArea()
                PageMenuAction.Share -> {
                    val text = (listOf(state.title) + state.projects.map { "- ${it.title}" }).joinToString("\n")
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                else -> Unit
            }
        },
        onAddClick = {
            onCreateProjectAt(0)
            onAddClick()
        },
        allowHeading = false,
        addLabel = "New Project",
        onAddAt = { target ->
            val index: Int? = when (target) {
                is AddTarget.Top -> 0
                is AddTarget.At -> {
                    val i = state.projects.indexOfFirst { it.id == target.anchorTaskId }
                    if (i < 0) state.projects.size else if (target.after) i + 1 else i
                }
                is AddTarget.Heading -> null
            }
            if (index != null) onCreateProjectAt(index)
        },
        taskIdForKey = { key ->
            (key as? String)?.takeIf { it.startsWith(ProjectKeyPrefix) }?.removePrefix(ProjectKeyPrefix)
        },
    ) {
        items(state.projects, key = { "$ProjectKeyPrefix${it.id}" }) { project ->
            val slot = LocalDropSlot.current
            Column {
                if (slot.beforeTaskId == project.id) Spacer(Modifier.height(DropGapHeight))
                if (project.id == editingId) {
                    NewProjectRow(
                        title = project.title,
                        onTitleChange = { onTitleChange(project.id, it) },
                        onEditDone = { onEditDone(project.id) },
                    )
                } else {
                    ProjectLinkRow(
                        title = project.title,
                        progress = project.progress,
                        onClick = { onProjectClick(project.id) },
                    )
                }
                if (slot.afterTaskId == project.id) Spacer(Modifier.height(DropGapHeight))
            }
        }
    }

    if (tagsOpen) {
        TagsSheet(
            selected = areaTags,
            onDismiss = { tagsOpen = false },
            onDone = { areaTags = it },
        )
    }
}

/**
 * A project that is being named: ring and a text field that takes the keyboard as soon as it
 * appears. [onEditDone] is called when the cursor leaves the field.
 */
@Composable
private fun NewProjectRow(
    title: String,
    onTitleChange: (String) -> Unit,
    onEditDone: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    val style = ThingsType.RowTitle

    LaunchedEffect(Unit) { requester.requestFocus() }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ThingsSpacing.RowHeight)
            .padding(horizontal = ThingsSpacing.PageMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressIcon(progress = 0f, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = title,
            onValueChange = onTitleChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(ThingsColors.ProjectBlue),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            decorationBox = { inner ->
                Box {
                    if (title.isEmpty()) {
                        Text("New Project", style = style.copy(color = ThingsColors.TextSecondary))
                    }
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(requester)
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        hadFocus = true
                    } else if (hadFocus) {
                        hadFocus = false
                        onEditDone()
                    }
                },
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Area")
@Composable
private fun AreaPreview() {
    ThingsTheme { AreaScreen(sampleAreaState()) }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Area - empty")
@Composable
private fun EmptyAreaPreview() {
    ThingsTheme { AreaScreen(AreaUiState(id = "empty", title = "New Area")) }
}
