package com.things.app.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
 * Turns Markdown text into styled text for the notes editor. The Markdown characters stay in the
 * text (dimmed), so the result has exactly the same length as the input and every cursor offset
 * stays valid.
 */
object MarkdownStyler {
    private val headingLine = Regex("^(#{1,6}) (.*)$")
    private val ruleLine = Regex("^\\s*(?:-{3,}|\\*{3,}|_{3,})\\s*$")
    private val listMarker = Regex("^(\\s*)(- \\[[ xX~]\\] |[-*+\u2022] |\\d+\\. )")
    private val codeSpan = Regex("`[^`\\n]+`")
    private val boldStars = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*")
    private val boldUnderscores = Regex("(?<!\\w)__(?=\\S)(.+?)(?<=\\S)__(?!\\w)")
    private val italicStar = Regex("(?<!\\*)\\*(?=[^\\s*])(.+?)(?<=[^\\s*])\\*(?!\\*)")
    private val italicUnderscore = Regex("(?<!\\w)_(?=[^\\s_])(.+?)(?<=[^\\s_])_(?!\\w)")
    private val highlight = Regex("::(?=\\S)(.+?)(?<=\\S)::")
    private val strike = Regex("~~(?=\\S)(.+?)(?<=\\S)~~")
    private val url = Regex("https?://[^\\s<>]+")

    fun style(text: String, palette: MarkdownPalette = MarkdownPalette.Default): AnnotatedString {
        val builder = AnnotatedString.Builder(text)
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
                else -> styleLine(builder, line, offset, palette)
            }
            offset += line.length + 1
        }
        return builder.toAnnotatedString()
    }

    private fun styleLine(b: AnnotatedString.Builder, line: String, offset: Int, p: MarkdownPalette) {
        val heading = headingLine.matchEntire(line)
        if (heading != null) {
            val level = heading.groupValues[1].length
            b.addStyle(
                SpanStyle(fontWeight = FontWeight.Bold, fontSize = headingSize(level)),
                offset,
                offset + line.length,
            )
            b.addStyle(SpanStyle(color = p.syntax), offset, offset + level + 1)
            styleInline(b, line, offset, p)
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
        styleInline(b, line, offset, p)
    }

    private fun styleInline(b: AnnotatedString.Builder, line: String, offset: Int, p: MarkdownPalette) {
        val codeRanges = codeSpan.findAll(line).map { it.range }.toList()
        fun inCode(index: Int) = codeRanges.any { index in it }

        codeRanges.forEach { r ->
            val from = offset + r.first
            val to = offset + r.last + 1
            b.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = p.codeBackground), from, to)
            dimEnds(b, p, from, to, 1)
        }

        fun styleMatches(regex: Regex, markerLength: Int, style: SpanStyle) {
            regex.findAll(line).forEach { match ->
                if (inCode(match.range.first)) return@forEach
                val from = offset + match.range.first
                val to = offset + match.range.last + 1
                b.addStyle(style, from, to)
                dimEnds(b, p, from, to, markerLength)
            }
        }

        val bold = SpanStyle(fontWeight = FontWeight.Bold)
        val italic = SpanStyle(fontStyle = FontStyle.Italic)
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

    private fun dimEnds(b: AnnotatedString.Builder, p: MarkdownPalette, from: Int, to: Int, length: Int) {
        b.addStyle(SpanStyle(color = p.syntax), from, from + length)
        b.addStyle(SpanStyle(color = p.syntax), to - length, to)
    }

    private fun headingSize(level: Int): TextUnit = when (level) {
        1 -> 24.sp
        2 -> 21.sp
        3 -> 19.sp
        else -> 18.sp
    }
}

/** Plugs [MarkdownStyler] into a text field. The offsets are not changed. */
class MarkdownVisualTransformation(
    private val palette: MarkdownPalette = MarkdownPalette.Default,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(MarkdownStyler.style(text.text, palette), OffsetMapping.Identity)
}
