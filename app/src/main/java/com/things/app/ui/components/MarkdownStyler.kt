package com.things.app.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.things.app.ui.theme.ThingsColors

/** The colors the note styler uses. The defaults come from the app theme. */
@Immutable
class MarkdownPalette(
    val syntax: Color,
    val highlight: Color,
    val codeBackground: Color,
    val quote: Color,
    val link: Color,
    val muted: Color,
) {
    companion object {
        val Default = MarkdownPalette(
            syntax = ThingsColors.TextSecondary,
            highlight = ThingsColors.Today.copy(alpha = 0.4f),
            codeBackground = ThingsColors.SearchBackground,
            quote = ThingsColors.TextSecondary,
            link = ThingsColors.ProjectBlue,
            muted = ThingsColors.TextSecondary,
        )
    }
}

/**
 * The notes text as the editor shows it: [transformed] has the inline Markdown markers (the stars
 * of **bold**, the tildes of ~~strike~~, ...) removed and the styles applied to what is left.
 * The stored text still holds the markers.
 */
class MarkdownRendering internal constructor(
    val transformed: TransformedText,
    private val toTransformed: IntArray,
    private val visible: IntArray,
) {
    /**
     * The selection narrowed to the characters that can be seen, so hidden markers at its edges are
     * left out. Null when no visible character is selected.
     */
    fun contentRange(start: Int, end: Int): TextRange? {
        val s = toTransformed[start.coerceIn(0, toTransformed.lastIndex)]
        val e = toTransformed[end.coerceIn(0, toTransformed.lastIndex)]
        if (e <= s) return null
        return TextRange(visible[s], visible[e - 1] + 1)
    }
}

/**
 * Turns Markdown text into styled text for the notes editor.
 *
 * [style] keeps the Markdown characters in the text (dimmed), so its result has exactly the same
 * length as the input. [rendering] hides the inline markers instead, so the person sees Bold in
 * bold and not the stars around it; it also gives the offset mapping the text field needs.
 * Block markers (#, >, - and numbers) stay visible.
 *
 * While the cursor sits at the end of a styled word it is placed before the hidden closing
 * markers, so Backspace deletes the letter and typing continues in the same style.
 */
object MarkdownStyler {
    private val headingLine = Regex("^(#{1,6}) (.*)$")
    private val ruleLine = Regex("^\\s*(?:-{3,}|\\*{3,}|_{3,})\\s*$")
    private val listMarker = Regex("^(\\s*)(- \\[[ xX~]\\] |[-*+\u2022] |\\d+\\. )")
    private val codeSpan = Regex("`[^`\\n]+`")
    private val boldItalicStars = Regex("\\*\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*\\*")
    private val boldStars = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*")
    private val boldUnderscores = Regex("(?<!\\w)__(?=\\S)(.+?)(?<=\\S)__(?!\\w)")
    private val italicStar = Regex("(?<!\\*)\\*(?=[^\\s*])(.+?)(?<=[^\\s*])\\*(?!\\*)")
    private val italicUnderscore = Regex("(?<!\\w)_(?=[^\\s_])(.+?)(?<=[^\\s_])_(?!\\w)")
    private val highlight = Regex("::(?=\\S)(.+?)(?<=\\S)::")
    private val strike = Regex("~~(?=\\S)(.+?)(?<=\\S)~~")
    private val url = Regex("https?://[^\\s<>]+")

    private class Built(val text: AnnotatedString, val markers: List<IntRange>)

    /** Styled text with the Markdown characters still in it (dimmed). Same length as [text]. */
    fun style(text: String, palette: MarkdownPalette = MarkdownPalette.Default): AnnotatedString =
        build(text, palette).text

    /** Styled text with the inline markers removed, plus the offset mapping for the text field. */
    fun rendering(text: String, palette: MarkdownPalette = MarkdownPalette.Default): MarkdownRendering {
        val built = build(text, palette)
        val hidden = BooleanArray(text.length)
        built.markers.forEach { range -> for (i in range) hidden[i] = true }

        val toTransformed = IntArray(text.length + 1)
        val visibleList = ArrayList<Int>(text.length)
        for (i in text.indices) {
            toTransformed[i] = visibleList.size
            if (!hidden[i]) visibleList.add(i)
        }
        toTransformed[text.length] = visibleList.size
        val visible = visibleList.toIntArray()

        if (built.markers.isEmpty()) {
            return MarkdownRendering(TransformedText(built.text, OffsetMapping.Identity), toTransformed, visible)
        }

        // A cursor between two visible characters sits right after the first one, before any
        // hidden marker that follows it.
        val toOriginal = IntArray(visible.size + 1) { t -> if (t == 0) 0 else visible[t - 1] + 1 }

        val out = AnnotatedString.Builder()
        visible.forEach { out.append(text[it]) }
        built.text.spanStyles.forEach { span ->
            val s = toTransformed[span.start]
            val e = toTransformed[span.end]
            if (e > s) out.addStyle(span.item, s, e)
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                toTransformed[offset.coerceIn(0, text.length)]

            override fun transformedToOriginal(offset: Int): Int =
                toOriginal[offset.coerceIn(0, visible.size)]
        }
        return MarkdownRendering(TransformedText(out.toAnnotatedString(), mapping), toTransformed, visible)
    }

    private fun build(text: String, palette: MarkdownPalette): Built {
        val builder = AnnotatedString.Builder(text)
        val markers = mutableListOf<IntRange>()
        var offset = 0
        var inFence = false
        text.split('\n').forEach { line ->
            val isFence = line.trimStart().startsWith("```")
            when {
                isFence -> {
                    builder.addStyle(
                        SpanStyle(color = palette.syntax, fontFamily = FontFamily.Monospace),
                        offset,
                        offset + line.length,
                    )
                    inFence = !inFence
                }
                inFence -> builder.addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = palette.codeBackground),
                    offset,
                    offset + line.length,
                )
                else -> styleLine(builder, markers, line, offset, palette)
            }
            offset += line.length + 1
        }
        return Built(builder.toAnnotatedString(), markers)
    }

    private fun styleLine(
        b: AnnotatedString.Builder,
        markers: MutableList<IntRange>,
        line: String,
        offset: Int,
        p: MarkdownPalette,
    ) {
        val heading = headingLine.matchEntire(line)
        if (heading != null) {
            val level = heading.groupValues[1].length
            b.addStyle(
                SpanStyle(fontWeight = FontWeight.Bold, fontSize = headingSize(level)),
                offset,
                offset + line.length,
            )
            b.addStyle(SpanStyle(color = p.syntax), offset, offset + level + 1)
            styleInline(b, markers, line, offset, p)
            return
        }
        if (ruleLine.matches(line)) {
            b.addStyle(SpanStyle(color = p.syntax), offset, offset + line.length)
            return
        }
        if (line.startsWith("> ")) {
            b.addStyle(SpanStyle(color = p.syntax), offset, offset + 2)
            b.addStyle(SpanStyle(color = p.quote), offset + 2, offset + line.length)
        } else {
            val marker = listMarker.find(line)
            if (marker != null) {
                val group = marker.groups[2]!!
                val from = offset + group.range.first
                val to = offset + group.range.last + 1
                val numbered = group.value.firstOrNull()?.isDigit() == true
                b.addStyle(
                    SpanStyle(color = p.syntax, fontFamily = if (numbered) FontFamily.Monospace else null),
                    from,
                    to,
                )
                val done = group.value.contains('x') || group.value.contains('X') || group.value.contains('~')
                if (done) {
                    b.addStyle(
                        SpanStyle(color = p.muted, textDecoration = TextDecoration.LineThrough),
                        to,
                        offset + line.length,
                    )
                }
            }
        }
        styleInline(b, markers, line, offset, p)
    }

    private fun styleInline(
        b: AnnotatedString.Builder,
        markers: MutableList<IntRange>,
        line: String,
        offset: Int,
        p: MarkdownPalette,
    ) {
        val codeRanges = codeSpan.findAll(line).map { it.range }.toList()
        fun inCode(index: Int) = codeRanges.any { index in it }

        // Every marker character belongs to one style only, so overlapping matches such as the
        // bold match found inside ***both*** are skipped.
        val claimed = HashSet<Int>()

        codeRanges.forEach { r ->
            val from = offset + r.first
            val to = offset + r.last + 1
            b.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = p.codeBackground), from, to)
            claimed.addAll(from until from + 1)
            claimed.addAll(to - 1 until to)
            dimEnds(b, p, markers, from, to, 1)
        }

        fun styleMatches(regex: Regex, markerLength: Int, style: SpanStyle) {
            regex.findAll(line).forEach { match ->
                if (inCode(match.range.first)) return@forEach
                val from = offset + match.range.first
                val to = offset + match.range.last + 1
                val open = from until from + markerLength
                val close = to - markerLength until to
                if (open.any { it in claimed } || close.any { it in claimed }) return@forEach
                claimed.addAll(open)
                claimed.addAll(close)
                b.addStyle(style, from, to)
                dimEnds(b, p, markers, from, to, markerLength)
            }
        }

        val bold = SpanStyle(fontWeight = FontWeight.Bold)
        val italic = SpanStyle(fontStyle = FontStyle.Italic)
        styleMatches(boldItalicStars, 3, SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
        styleMatches(boldStars, 2, bold)
        styleMatches(boldUnderscores, 2, bold)
        styleMatches(italicStar, 1, italic)
        styleMatches(italicUnderscore, 1, italic)
        styleMatches(highlight, 2, SpanStyle(background = p.highlight))
        styleMatches(strike, 2, SpanStyle(textDecoration = TextDecoration.LineThrough))

        url.findAll(line).forEach { match ->
            if (inCode(match.range.first)) return@forEach
            b.addStyle(
                SpanStyle(color = p.link, textDecoration = TextDecoration.Underline),
                offset + match.range.first,
                offset + match.range.last + 1,
            )
        }
    }

    private fun dimEnds(
        b: AnnotatedString.Builder,
        p: MarkdownPalette,
        markers: MutableList<IntRange>,
        from: Int,
        to: Int,
        length: Int,
    ) {
        b.addStyle(SpanStyle(color = p.syntax), from, from + length)
        b.addStyle(SpanStyle(color = p.syntax), to - length, to)
        markers += from until from + length
        markers += to - length until to
    }

    private fun headingSize(level: Int): TextUnit = when (level) {
        1 -> 24.sp
        2 -> 21.sp
        3 -> 19.sp
        else -> 18.sp
    }
}

/** Plugs [MarkdownStyler] into a text field: inline markers are hidden and the offsets are mapped. */
class MarkdownVisualTransformation(
    private val palette: MarkdownPalette = MarkdownPalette.Default,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        MarkdownStyler.rendering(text.text, palette).transformed
}
