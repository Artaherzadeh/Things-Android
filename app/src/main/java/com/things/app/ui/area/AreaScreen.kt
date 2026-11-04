package com.things.app.ui.area

import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.AreaMenuActions
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.PageMenuAction
import com.things.app.ui.components.ProjectLinkRow
import com.things.app.ui.components.TagsSheet
import com.things.app.ui.components.TitleMenuButton
import com.things.app.ui.home.AreaIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsTheme

/**
 * Area page: the area icon and title with its menu, then one line per project (ring, name,
 * chevron). Tapping a project reports its id. Every action is a callback.
 *
 * The three-dots menu of this page has Add Tags (opens the tag list), Delete Area ([onDeleteArea])
 * and Share (the area name and its projects, through the Android share sheet).
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
        onAddClick = onAddClick,
    ) {
        items(state.projects, key = { "area-project-${it.id}" }) { project ->
            ProjectLinkRow(
                title = project.title,
                progress = project.progress,
                onClick = { onProjectClick(project.id) },
            )
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
