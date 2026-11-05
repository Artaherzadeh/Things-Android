package com.things.app.ui.quickfind

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.things.app.ui.home.AreaIcon
import com.things.app.ui.home.ArchiveIcon
import com.things.app.ui.home.CalendarIcon
import com.things.app.ui.home.InboxIcon
import com.things.app.ui.home.LayersIcon
import com.things.app.ui.home.LogbookIcon
import com.things.app.ui.home.ProgressIcon
import com.things.app.ui.home.StarIcon
import com.things.app.ui.home.SystemList
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType

private const val ShowMillis = 200
private const val HideMillis = 140
private val PanelShape = RoundedCornerShape(28.dp)
private val PanelOrigin = TransformOrigin(0.5f, 0f)

/** The popover is 16dp from the left, right and top edges, and 16dp inside its own edges. */
private val PanelMargin = 16.dp

/**
 * Static Quick Find popover: a dimmed scrim and a panel at the top with the search field, a close
 * button, the recent lists and a hint. Typing does not filter anything yet. Draw it above the
 * screen (for example in a Box over the NavHost). Tapping the scrim, the close button or going
 * back calls [onDismiss].
 *
 * Inside the panel the search icon, the "Recent" label, the divider and the list icons all start at
 * the same x, and the list names start where the search text starts.
 */
@Composable
fun QuickFindOverlay(
    visible: Boolean,
    state: QuickFindUiState,
    onDismiss: () -> Unit,
    onItemClick: (QuickFindItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible, onBack = onDismiss)

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(ShowMillis)),
            exit = fadeOut(tween(HideMillis)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(ThingsColors.Scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Close Quick Find",
                        onClick = onDismiss,
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(ShowMillis)) +
                scaleIn(tween(ShowMillis), initialScale = 0.94f, transformOrigin = PanelOrigin),
            exit = fadeOut(tween(HideMillis)) +
                scaleOut(tween(HideMillis), targetScale = 0.94f, transformOrigin = PanelOrigin),
        ) {
            QuickFindPanel(state = state, onDismiss = onDismiss, onItemClick = onItemClick)
        }
    }
}

@Composable
private fun QuickFindPanel(
    state: QuickFindUiState,
    onDismiss: () -> Unit,
    onItemClick: (QuickFindItem) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .statusBarsPadding()
            .padding(PanelMargin)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .shadow(24.dp, PanelShape)
            .clip(PanelShape)
            .background(ThingsColors.Background)
            // Taps on the panel itself must not reach the scrim and dismiss it.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { paneTitle = "Quick Find" }
            .padding(PanelMargin)
    ) {
        SearchRow(query = query, onQueryChange = { query = it }, onClose = onDismiss)

        Spacer(Modifier.height(24.dp))
        Text(
            "Recent",
            style = ThingsType.Count.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = PanelMargin),
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(Modifier.padding(horizontal = PanelMargin), color = ThingsColors.Divider)

        state.recent.forEach { item ->
            QuickFindRow(item = item, onClick = { onItemClick(item) })
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Quickly switch lists, find to-dos, search for tags...",
            style = ThingsType.Count.copy(fontSize = 15.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SearchRow(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .height(ThingsSpacing.RowHeight)
                .clip(CircleShape)
                .background(ThingsColors.SearchBackground)
                .padding(horizontal = PanelMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = ThingsColors.SearchContent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(4.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Quick Find", style = ThingsType.Search)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = ThingsType.Search.copy(color = ThingsColors.TextPrimary),
                    cursorBrush = SolidColor(ThingsColors.Fab),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .size(ThingsSpacing.RowHeight)
                .clip(CircleShape)
                .background(ThingsColors.SearchBackground)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = "Close Quick Find" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = null,
                tint = ThingsColors.TextSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun QuickFindRow(item: QuickFindItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(ThingsSpacing.RowHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = PanelMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { TargetIcon(item) }
        Spacer(Modifier.width(4.dp))
        Text(
            item.title,
            style = ThingsType.RowTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.isCurrent) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Current list",
                tint = ThingsColors.Fab,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun TargetIcon(item: QuickFindItem) {
    when (val target = item.target) {
        is QuickFindTarget.System -> when (target.list) {
            SystemList.Inbox -> InboxIcon(Modifier.size(24.dp))
            SystemList.Today -> StarIcon(Modifier.size(24.dp))
            SystemList.Upcoming -> CalendarIcon(Modifier.size(24.dp))
            SystemList.Anytime -> LayersIcon(Modifier.size(24.dp))
            SystemList.Someday -> ArchiveIcon(Modifier.size(24.dp))
            SystemList.Logbook -> LogbookIcon(Modifier.size(24.dp))
        }
        is QuickFindTarget.Area -> AreaIcon(Modifier.size(24.dp), ThingsColors.MenuArea)
        is QuickFindTarget.Project ->
            ProgressIcon(item.progress, Modifier.size(24.dp), ThingsColors.ProjectBlue)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Quick Find")
@Composable
private fun QuickFindPreview() {
    ThingsTheme {
        QuickFindOverlay(
            visible = true,
            state = sampleQuickFindState(),
            onDismiss = {},
            onItemClick = {},
        )
    }
}
