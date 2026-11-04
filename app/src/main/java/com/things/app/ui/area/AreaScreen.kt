package com.things.app.ui.area

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.ProjectLinkRow
import com.things.app.ui.components.TitleMenuButton
import com.things.app.ui.home.AreaIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsTheme

/**
 * Static area page: the area icon and title with its menu, then one line per project (ring, name,
 * chevron). Tapping a project reports its id. Every action is a callback.
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
) {
    ListPageScaffold(
        title = state.title,
        icon = { AreaIcon(Modifier.size(28.dp), ThingsColors.MenuArea) },
        modifier = modifier,
        titleTag = "area_title",
        titleTrailing = { TitleMenuButton("Area menu", onTitleMenuClick) },
        titleBottomPadding = 32.dp,
        onBack = onBack,
        onMoreClick = onMoreClick,
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
