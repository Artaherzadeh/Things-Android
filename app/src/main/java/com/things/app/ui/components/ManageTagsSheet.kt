package com.things.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsType

private val DeleteRed = Color(0xFFFF2D55)

/**
 * The Manage Tags sheet, opened from the Tags sheet: every tag on its own line with a red trash
 * button at the start, the number of tags nested under it in grey and a blue pencil at the end.
 * Tap a tag that has nested tags to show or hide them. Trash removes the tag (the tags nested under
 * it become ordinary tags); the pencil opens Edit Tag; New Tag adds a tag. The round blue button at
 * the top right closes the sheet. Changes are made at once in [SampleTagStore].
 */
@Composable
fun ManageTagsSheet(onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val listHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp

    SheetFrame(title = "Manage Tags", onClose = onDismiss, confirm = true, onConfirm = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(listHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            SampleTagStore.tags.toList().forEach { tag ->
                ManageTagRow(
                    name = tag.name,
                    depth = 0,
                    childCount = tag.children.size,
                    onClick = {
                        expanded = if (tag.name in expanded) expanded - tag.name else expanded + tag.name
                    },
                    onDelete = { SampleTagStore.remove(tag.name) },
                    onEdit = { editing = tag.name },
                )
                if (tag.name in expanded) {
                    tag.children.forEach { child ->
                        ManageTagRow(
                            name = child,
                            depth = 1,
                            childCount = 0,
                            onClick = {},
                            onDelete = { SampleTagStore.remove(child) },
                            onEdit = { editing = child },
                        )
                    }
                }
            }
        }
        SheetButton(
            "New Tag",
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .fillMaxWidth(),
        ) { creating = true }
    }

    editing?.let { tag -> EditTagSheet(tag = tag, onDismiss = { editing = null }) }

    if (creating) {
        NewTagDialog(
            onCancel = { creating = false },
            onAdd = { name ->
                creating = false
                SampleTagStore.add(name)
            },
        )
    }
}

@Composable
private fun ManageTagRow(
    name: String,
    depth: Int,
    childCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = childCount > 0, onClick = onClick)
            .padding(start = 12.dp + 28.dp * depth, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(Icons.Rounded.Delete, "Delete $name", DeleteRed, onDelete)
        Spacer(Modifier.width(12.dp))
        Text(name, style = ThingsType.RowTitle.copy(color = ThingsColors.MenuTitle), modifier = Modifier.weight(1f))
        if (childCount > 0) {
            Text(childCount.toString(), style = ThingsType.RowTitle.copy(color = ThingsColors.TextSecondary))
            Spacer(Modifier.width(12.dp))
        }
        RoundIconButton(Icons.Rounded.Edit, "Edit $name", ThingsColors.Fab.copy(alpha = 0.55f), onEdit)
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, background: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/**
 * The Edit Tag sheet: a cross at the start closes it without saving, the blue check at the end saves
 * the new name and Group (the tag this one is nested under, or none). The check works only while
 * the name is not empty and not used by another tag. A tag that has tags nested under it cannot be
 * put under another tag, so its Group line is grey.
 */
@Composable
private fun EditTagSheet(tag: String, onDismiss: () -> Unit) {
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf(tag) }
    var parent by remember { mutableStateOf(SampleTagStore.parentOf(tag)) }
    var picking by remember { mutableStateOf(false) }
    val hasChildren = SampleTagStore.childrenOf(tag).isNotEmpty()
    val clean = name.trim()
    val valid = clean.isNotEmpty() &&
        (clean.equals(tag, ignoreCase = true) || SampleTagStore.names().none { it.equals(clean, ignoreCase = true) })
    val bodyHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp

    SheetFrame(
        title = "Edit Tag",
        onClose = onDismiss,
        confirm = valid,
        onConfirm = {
            SampleTagStore.update(tag, clean, parent)
            onDismiss()
        },
        leadingClose = true,
    ) {
        BasicTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            textStyle = ThingsType.RowTitle.copy(color = ThingsColors.MenuTitle),
            cursorBrush = SolidColor(ThingsColors.Fab),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ThingsColors.MenuTitle.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (name.isEmpty()) {
                        Text("Tag name", style = ThingsType.RowTitle.copy(color = ThingsColors.TextSecondary))
                    }
                    inner()
                }
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = !hasChildren, role = Role.Button) { picking = true }
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val valueColor = if (hasChildren) ThingsColors.TextSecondary else ThingsColors.Fab
            Text("Group", style = ThingsType.RowTitle.copy(color = ThingsColors.MenuTitle), modifier = Modifier.weight(1f))
            Text(parent ?: "No Tag", style = ThingsType.RowTitle.copy(color = valueColor))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = valueColor)
        }
        Spacer(Modifier.height(bodyHeight))
    }

    if (picking) {
        GroupPickerSheet(
            self = tag,
            selected = parent,
            onPick = { parent = it },
            onDismiss = { picking = false },
        )
    }
}

/** The tags a tag can be put under: none, or any other tag that is not itself nested. */
@Composable
private fun GroupPickerSheet(self: String, selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    SheetFrame(title = "Group", onClose = onDismiss, confirm = false, onConfirm = {}) {
        Column(
            Modifier
                .heightIn(min = 200.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            SheetRow(
                selected = selected == null,
                onClick = {
                    onPick(null)
                    onDismiss()
                },
                icon = { TagIcon(Modifier.size(28.dp), color = ThingsColors.TextSecondary) },
                label = "No Tag",
            )
            SampleTagStore.tags.filter { it.name != self }.forEach { candidate ->
                SheetRow(
                    selected = selected == candidate.name,
                    onClick = {
                        onPick(candidate.name)
                        onDismiss()
                    },
                    icon = { TagIcon(Modifier.size(28.dp), color = ThingsColors.MenuTitle) },
                    label = candidate.name,
                )
            }
        }
    }
}
