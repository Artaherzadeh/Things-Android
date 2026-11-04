package com.things.app

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.things.app.ui.components.MarkdownEdit
import com.things.app.ui.components.MarkdownFormatter
import com.things.app.ui.components.MarkdownStyle
import com.things.app.ui.components.MarkdownStyler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownFormatterTest {

    private fun assertEdit(text: String, start: Int, end: Int, edit: MarkdownEdit) {
        assertEquals(text, edit.text)
        assertEquals(start, edit.selectionStart)
        assertEquals(end, edit.selectionEnd)
    }

    @Test
    fun boldWrapsTheSelectionAndTogglesOff() {
        val on = MarkdownFormatter.apply(MarkdownStyle.Bold, "hello world", 0, 5)
        assertEdit("**hello** world", 2, 7, on)
        val off = MarkdownFormatter.apply(MarkdownStyle.Bold, on.text, on.selectionStart, on.selectionEnd)
        assertEdit("hello world", 0, 5, off)
    }

    @Test
    fun italicAndBoldShareStarsWithoutBreakingEachOther() {
        val both = MarkdownFormatter.apply(MarkdownStyle.Italic, "**a**", 2, 3)
        assertEdit("***a***", 3, 4, both)
        val boldOnly = MarkdownFormatter.apply(MarkdownStyle.Italic, both.text, 3, 4)
        assertEdit("**a**", 2, 3, boldOnly)

        val italicThenBold = MarkdownFormatter.apply(MarkdownStyle.Bold, "*a*", 1, 2)
        assertEdit("***a***", 3, 4, italicThenBold)
        val italicOnly = MarkdownFormatter.apply(MarkdownStyle.Bold, italicThenBold.text, 3, 4)
        assertEdit("*a*", 1, 2, italicOnly)
    }

    @Test
    fun highlightStrikethroughAndCodeUseTheirOwnMarkers() {
        assertEdit("::hi:: there", 2, 4, MarkdownFormatter.apply(MarkdownStyle.Highlight, "hi there", 0, 2))
        assertEdit("hi", 0, 2, MarkdownFormatter.apply(MarkdownStyle.Strikethrough, "~~hi~~", 2, 4))
        assertEdit("`x` = 1", 1, 2, MarkdownFormatter.apply(MarkdownStyle.Code, "x = 1", 0, 1))
    }

    @Test
    fun surroundingWhitespaceStaysOutsideTheMarkers() {
        assertEdit(" **hi** ", 3, 5, MarkdownFormatter.apply(MarkdownStyle.Bold, " hi ", 0, 4))
    }

    @Test
    fun aCaretWithoutSelectionInsertsAnEmptyPair() {
        assertEdit("a****b", 3, 3, MarkdownFormatter.apply(MarkdownStyle.Bold, "ab", 1, 1))
        assertEdit("a**b", 2, 2, MarkdownFormatter.apply(MarkdownStyle.Italic, "ab", 1, 1))
    }

    @Test
    fun headingAndQuoteToggleTheLinePrefix() {
        assertEdit("# Title", 7, 7, MarkdownFormatter.apply(MarkdownStyle.Heading, "Title", 2, 2))
        assertEdit("Title", 5, 5, MarkdownFormatter.apply(MarkdownStyle.Heading, "# Title", 3, 3))
        assertEdit("> a", 3, 3, MarkdownFormatter.apply(MarkdownStyle.Quote, "a", 0, 0))
    }

    @Test
    fun listsApplyToEverySelectedLine() {
        assertEdit("- a\n- b", 0, 7, MarkdownFormatter.apply(MarkdownStyle.BulletList, "a\nb", 0, 3))
        assertEdit("1. a\n\n2. b", 0, 10, MarkdownFormatter.apply(MarkdownStyle.NumberedList, "a\n\nb", 0, 4))
        assertEdit("- [ ] a", 7, 7, MarkdownFormatter.apply(MarkdownStyle.TaskList, "a", 0, 0))
    }

    @Test
    fun switchingListTypeReplacesThePreviousMarker() {
        assertEdit("1. a\n2. b", 0, 9, MarkdownFormatter.apply(MarkdownStyle.NumberedList, "- a\n- b", 0, 7))
        assertEdit("a", 1, 1, MarkdownFormatter.apply(MarkdownStyle.TaskList, "- [ ] a", 7, 7))
    }

    @Test
    fun activeStylesReportWhatTheSelectionAlreadyHas() {
        val active = MarkdownFormatter.activeStyles("**hi**", 2, 4)
        assertTrue(MarkdownStyle.Bold in active)
        assertFalse(MarkdownStyle.Italic in active)
        assertTrue(MarkdownStyle.Heading in MarkdownFormatter.activeStyles("# Title", 3, 3))
    }
}

@RunWith(AndroidJUnit4::class)
class MarkdownStylerTest {

    @Test
    fun styledTextKeepsTheSameLengthAndCharacters() {
        val text = "# Title\n**bold** and *italic*\n- item"
        assertEquals(text, MarkdownStyler.style(text).text)
    }

    @Test
    fun boldItalicStrikethroughAndHighlightGetTheirSpans() {
        val styled = MarkdownStyler.style("a **b** *c* ~~d~~ ::e::")
        val spans = styled.spanStyles
        assertTrue(spans.any { it.item.fontWeight == FontWeight.Bold && it.start == 2 && it.end == 7 })
        assertTrue(spans.any { it.item.fontStyle == FontStyle.Italic && it.start == 8 && it.end == 11 })
        assertTrue(spans.any { it.item.textDecoration == TextDecoration.LineThrough && it.start == 12 && it.end == 17 })
        assertTrue(spans.any { it.item.background.alpha > 0f && it.start == 18 && it.end == 23 })
    }

    @Test
    fun boldMarkersAreNotMistakenForItalic() {
        val styled = MarkdownStyler.style("**bold**")
        assertFalse(styled.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun headingsAreBoldAndCodeBlocksAreMonospaced() {
        val heading = MarkdownStyler.style("# T")
        assertTrue(heading.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 0 && it.end == 3 })

        val fenced = MarkdownStyler.style("```\ncode\n```")
        assertTrue(fenced.spanStyles.any { it.item.fontFamily == FontFamily.Monospace && it.start == 4 && it.end == 8 })
    }
}
