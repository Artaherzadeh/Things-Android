package com.things.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsType
import kotlin.math.roundToInt

private val NotesTextStyle = ThingsType.RowTitle.copy(fontSize = 17.sp)
private val ToolbarShape = RoundedCornerShape(16.dp)
private val ToolbarWidthEstimate = 240.dp
private val ToolbarGap = 8.dp

private val InlineStyles = listOf(
    MarkdownStyle.Bold,
    MarkdownStyle.Italic,
    MarkdownStyle.Strikethrough,
    MarkdownStyle.Highlight,
    MarkdownStyle.Code,
)
private val BlockStyles = listOf(
    MarkdownStyle.Heading,
    MarkdownStyle.Quote,
    MarkdownStyle.BulletList,
    MarkdownStyle.NumberedList,
    MarkdownStyle.TaskList,
)

/**
 * The notes field of a to-do or project. The text is Markdown: it is styled while typing, and when
 * some text is selected a small toolbar appears under the selection with the styles that can be
 * applied to it. The toolbar is drawn under the selection so it does not cover the system menu
 * (cut, copy, paste) that Android shows above it.
 */
@Composable
fun NotesEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Notes",
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var focused by remember { mutableStateOf(false) }
    var showBlockStyles by remember { mutableStateOf(false) }
    val transformation = remember { MarkdownVisualTransformation() }

    val selection = value.selection
    val showToolbar = focused && !selection.collapsed && value.text.isNotEmpty()

    LaunchedEffect(showToolbar) {
        if (!showToolbar) showBlockStyles = false
    }

    Box(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = NotesTextStyle,
            cursorBrush = SolidColor(ThingsColors.Fab),
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            onTextLayout = { layout = it },
            decorationBox = { inner ->
                Box {
                    if (value.text.isEmpty()) {
                        Text(placeholder, style = NotesTextStyle.copy(color = ThingsColors.TextSecondary))
                    }
                    inner()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .testTag("notes_editor"),
        )

        val currentLayout = layout
        if (showToolbar && currentLayout != null) {
            val density = LocalDensity.current
            val anchor = selectionAnchor(
                layout = currentLayout,
                selection = selection,
                toolbarWidth = with(density) { ToolbarWidthEstimate.roundToPx() },
                gap = with(density) { ToolbarGap.roundToPx() },
            )
            Popup(
                alignment = Alignment.TopStart,
                offset = anchor,
                properties = PopupProperties(focusable = false),
            ) {
                FormatToolbar(
                    active = MarkdownFormatter.activeStyles(value.text, selection.start, selection.end),
                    showBlockStyles = showBlockStyles,
                    onSwitchLevel = { showBlockStyles = !showBlockStyles },
                    onStyle = { style ->
                        val edit = MarkdownFormatter.apply(style, value.text, selection.start, selection.end)
                        onValueChange(
                            value.copy(
                                text = edit.text,
                                selection = TextRange(edit.selectionStart, edit.selectionEnd),
                                composition = null,
                            )
                        )
                    },
                )
            }
        }
    }
}

/** Where the toolbar goes: under the last selected line, kept inside the width of the field. */
private fun selectionAnchor(
    layout: TextLayoutResult,
    selection: TextRange,
    toolbarWidth: Int,
    gap: Int,
): IntOffset {
    val lastChar = (layout.layoutInput.text.length - 1).coerceAtLeast(0)
    val startBox = layout.getBoundingBox(selection.min.coerceIn(0, lastChar))
    val endBox = layout.getBoundingBox((selection.max - 1).coerceIn(0, lastChar))
    val maxX = (layout.size.width - toolbarWidth).coerceAtLeast(0)
    return IntOffset(
        x = startBox.left.roundToInt().coerceIn(0, maxX),
        y = endBox.bottom.roundToInt() + gap,
    )
}

@Composable
private fun FormatToolbar(
    active: Set<MarkdownStyle>,
    showBlockStyles: Boolean,
    onSwitchLevel: () -> Unit,
    onStyle: (MarkdownStyle) -> Unit,
) {
    Row(
        Modifier
            .clip(ToolbarShape)
            .background(ThingsColors.MenuSurface)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val styles = if (showBlockStyles) BlockStyles else InlineStyles
        styles.forEach { style ->
            ToolbarButton(description = style.label, active = style in active, onClick = { onStyle(style) }) {
                StyleGlyph(style)
            }
        }
        ToolbarButton(
            description = if (showBlockStyles) "Text styles" else "Paragraph styles",
            active = false,
            onClick = onSwitchLevel,
        ) {
            Text(if (showBlockStyles) "\u2039" else "\u203A", style = GlyphStyle.copy(fontSize = 24.sp))
        }
    }
}

private val GlyphStyle = TextStyle(color = ThingsColors.MenuTitle, fontSize = 16.sp)

@Composable
private fun ToolbarButton(
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(width = 40.dp, height = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) ThingsColors.MenuTitle.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                selected = active
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun StyleGlyph(style: MarkdownStyle) {
    when (style) {
        MarkdownStyle.Bold -> Text("B", style = GlyphStyle.copy(fontWeight = FontWeight.Bold))
        MarkdownStyle.Italic -> Text("I", style = GlyphStyle.copy(fontStyle = FontStyle.Italic))
        MarkdownStyle.Strikethrough ->
            Text("S", style = GlyphStyle.copy(textDecoration = TextDecoration.LineThrough))
        MarkdownStyle.Highlight -> Text(
            "A",
            style = GlyphStyle.copy(
                color = ThingsColors.TextPrimary,
                background = ThingsColors.Today.copy(alpha = 0.75f),
            ),
        )
        MarkdownStyle.Code -> Text("</>", style = GlyphStyle.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp))
        MarkdownStyle.Heading -> Text("H", style = GlyphStyle.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp))
        MarkdownStyle.Quote -> Text("\u201C", style = GlyphStyle.copy(fontSize = 24.sp))
        MarkdownStyle.BulletList -> Text("\u2022", style = GlyphStyle.copy(fontSize = 24.sp))
        MarkdownStyle.NumberedList -> Text("1.", style = GlyphStyle.copy(fontFamily = FontFamily.Monospace))
        MarkdownStyle.TaskList -> Text("[ ]", style = GlyphStyle.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp))
    }
}
