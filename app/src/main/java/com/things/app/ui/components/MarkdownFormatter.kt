package com.things.app.ui.components

/** The styles the notes editor can apply, named after the Things Markdown guide. */
enum class MarkdownStyle(val label: String, val marker: String? = null) {
    Bold("Bold", "**"),
    Italic("Italic", "*"),
    Highlight("Highlight", "::"),
    Strikethrough("Strikethrough", "~~"),
    Code("Code", "`"),
    Heading("Heading"),
    Quote("Quote"),
    BulletList("List"),
    NumberedList("Numbered list"),
    TaskList("Task list");

    /** Inline styles wrap the selection in markers; the others work on whole lines. */
    val isInline: Boolean get() = marker != null
}

/** The text after a formatting action and where the selection should be. */
data class MarkdownEdit(val text: String, val selectionStart: Int, val selectionEnd: Int)

/**
 * Pure text editing for Markdown notes: no Compose, no Android. Applying a style a second time
 * removes it, so every action is a toggle.
 */
object MarkdownFormatter {

    fun apply(style: MarkdownStyle, text: String, selectionStart: Int, selectionEnd: Int): MarkdownEdit {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        return if (style.isInline) applyInline(style, text, start, end) else applyBlock(style, text, start, end)
    }

    /** The styles that already apply to the selection, so the toolbar can show them as pressed. */
    fun activeStyles(text: String, selectionStart: Int, selectionEnd: Int): Set<MarkdownStyle> {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val (s, e) = trimmed(text, start, end)
        return MarkdownStyle.entries.filter { style ->
            if (style.isInline) {
                s < e && planInline(style, text, s, e).active
            } else {
                isBlockActive(style, text, start, end)
            }
        }.toSet()
    }

    // Inline styles

    private class InlinePlan(val active: Boolean, val contentStart: Int, val contentEnd: Int, val markerLength: Int)

    private fun applyInline(style: MarkdownStyle, text: String, start: Int, end: Int): MarkdownEdit {
        val marker = style.marker!!
        val (s, e) = trimmed(text, start, end)
        if (s >= e) {
            val caret = start + marker.length
            return MarkdownEdit(text.substring(0, start) + marker + marker + text.substring(start), caret, caret)
        }
        val plan = planInline(style, text, s, e)
        val m = plan.markerLength
        return if (plan.active) {
            val out = text.substring(0, plan.contentStart - m) +
                text.substring(plan.contentStart, plan.contentEnd) +
                text.substring(plan.contentEnd + m)
            MarkdownEdit(out, plan.contentStart - m, plan.contentEnd - m)
        } else {
            val out = text.substring(0, plan.contentStart) + marker +
                text.substring(plan.contentStart, plan.contentEnd) + marker +
                text.substring(plan.contentEnd)
            MarkdownEdit(out, plan.contentStart + m, plan.contentEnd + m)
        }
    }

    private fun planInline(style: MarkdownStyle, text: String, s: Int, e: Int): InlinePlan {
        val marker = style.marker!!
        return if (marker[0] == '*') planStars(style, marker.length, text, s, e) else planMarker(marker, text, s, e)
    }

    /** Bold (two stars) and italic (one star) share the star character, so count the stars. */
    private fun planStars(style: MarkdownStyle, markerLength: Int, text: String, s: Int, e: Int): InlinePlan {
        var lead = 0
        while (s + lead < e && text[s + lead] == '*') lead++
        var trail = 0
        while (e - 1 - trail >= s && text[e - 1 - trail] == '*') trail++
        val inner = minOf(lead, trail).coerceAtMost((e - s - 1) / 2)

        var before = inner
        var i = s - 1
        while (i >= 0 && text[i] == '*') {
            before++
            i--
        }
        var after = inner
        var j = e
        while (j < text.length && text[j] == '*') {
            after++
            j++
        }
        val stars = minOf(before, after)
        val active = if (style == MarkdownStyle.Italic) stars % 2 == 1 else stars >= 2
        return InlinePlan(active, s + inner, e - inner, markerLength)
    }

    private fun planMarker(marker: String, text: String, s: Int, e: Int): InlinePlan {
        val m = marker.length
        if (e - s > 2 * m && text.startsWith(marker, s) && text.startsWith(marker, e - m)) {
            return InlinePlan(true, s + m, e - m, m)
        }
        val outside = s >= m && text.startsWith(marker, s - m) && text.startsWith(marker, e)
        return InlinePlan(outside, s, e, m)
    }

    private fun trimmed(text: String, start: Int, end: Int): Pair<Int, Int> {
        var s = start
        var e = end
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        return s to e
    }

    // Block styles

    private val headingPrefix = Regex("^#{1,6} ")
    private val quotePrefix = Regex("^> ")
    private val bulletPrefix = Regex("^[-*+\u2022] (?!\\[[ xX~]\\] )")
    private val numberedPrefix = Regex("^\\d+\\. ")
    private val taskPrefix = Regex("^- \\[[ xX~]\\] ")
    private val anyListPrefix = Regex("^(?:- \\[[ xX~]\\] |[-*+\\u2022] |\\d+\\. )")

    private fun prefixOf(style: MarkdownStyle): Regex = when (style) {
        MarkdownStyle.Heading -> headingPrefix
        MarkdownStyle.Quote -> quotePrefix
        MarkdownStyle.BulletList -> bulletPrefix
        MarkdownStyle.NumberedList -> numberedPrefix
        MarkdownStyle.TaskList -> taskPrefix
        else -> error("$style is not a block style")
    }

    private class LineRange(val start: Int, val end: Int)

    private fun lineRange(text: String, start: Int, end: Int): LineRange {
        val lineStart = text.lastIndexOf('\n', start - 1) + 1
        val last = if (end > start) end - 1 else end
        val newline = text.indexOf('\n', last)
        return LineRange(lineStart, if (newline == -1) text.length else newline)
    }

    private fun applyBlock(style: MarkdownStyle, text: String, start: Int, end: Int): MarkdownEdit {
        val range = lineRange(text, start, end)
        val lines = text.substring(range.start, range.end).split('\n')
        val prefix = prefixOf(style)
        val nonBlank = lines.filter { it.isNotBlank() }
        val remove = nonBlank.isNotEmpty() && nonBlank.all { prefix.containsMatchIn(it) }

        var number = 1
        val edited = lines.map { line ->
            when {
                remove -> line.replaceFirst(prefix, "")
                line.isBlank() && lines.size > 1 -> line
                else -> addPrefix(style, line, number++)
            }
        }
        val block = edited.joinToString("\n")
        val out = text.substring(0, range.start) + block + text.substring(range.end)
        return if (start == end) {
            val caret = range.start + block.length
            MarkdownEdit(out, caret, caret)
        } else {
            MarkdownEdit(out, range.start, range.start + block.length)
        }
    }

    private fun addPrefix(style: MarkdownStyle, line: String, number: Int): String {
        val base = when (style) {
            MarkdownStyle.Heading -> line.replaceFirst(headingPrefix, "")
            MarkdownStyle.BulletList, MarkdownStyle.NumberedList, MarkdownStyle.TaskList ->
                line.replaceFirst(anyListPrefix, "")
            else -> line
        }
        val prefix = when (style) {
            MarkdownStyle.Heading -> "# "
            MarkdownStyle.Quote -> "> "
            MarkdownStyle.BulletList -> "- "
            MarkdownStyle.NumberedList -> "$number. "
            MarkdownStyle.TaskList -> "- [ ] "
            else -> ""
        }
        return prefix + base
    }

    private fun isBlockActive(style: MarkdownStyle, text: String, start: Int, end: Int): Boolean {
        if (style.isInline) return false
        val range = lineRange(text, start, end)
        val first = text.substring(range.start, range.end).split('\n').firstOrNull { it.isNotBlank() } ?: return false
        return prefixOf(style).containsMatchIn(first)
    }
}
