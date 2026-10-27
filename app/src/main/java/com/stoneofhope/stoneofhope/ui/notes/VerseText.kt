package com.stoneofhope.stoneofhope.ui.notes

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stoneofhope.stoneofhope.data.model.MarkStyle
import com.stoneofhope.stoneofhope.data.notes.MarkedVerse

/**
 * iOS `IndexedTextColorHelper`: the Web's preset mark colors (SwiftUI's system colors). An unknown
 * key — a color the Web adds later — shows as yellow instead of failing.
 */
object MarkColors {
    private val palette = mapOf(
        "yellow" to Color(0xFFFFCC00),
        "green" to Color(0xFF34C759),
        "blue" to Color(0xFF007AFF),
        "pink" to Color(0xFFFF2D55),
        "orange" to Color(0xFFFF9500),
    )

    fun of(colorKey: String): Color = palette[colorKey.lowercase()] ?: palette.getValue("yellow")
}

/** Underlined spans travel as annotations; [MarkedText] draws them in the mark's color. */
private const val UNDERLINE_TAG = "verseUnderline"

/** iOS: a highlight is the color at 35% behind the text; an underline is a line in the color. */
fun AnnotatedString.Builder.appendMarked(verse: MarkedVerse) {
    val base = length
    append(verse.text)
    verse.marks.forEach { m ->
        when (m.style) {
            MarkStyle.Highlight -> addStyle(SpanStyle(background = MarkColors.of(m.colorKey).copy(alpha = 0.35f)), base + m.start, base + m.end)
            MarkStyle.Underline -> addStringAnnotation(UNDERLINE_TAG, m.colorKey, base + m.start, base + m.end)
        }
    }
}

fun MarkedVerse.annotated(): AnnotatedString = buildAnnotatedString { appendMarked(this@annotated) }

/**
 * Text that draws its verse underlines in each mark's own color (Compose underlines take the text
 * color), one stroke per line the span covers. Plain [Text] when there are none.
 */
@Composable
fun MarkedText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val underlines = remember(text) { text.getStringAnnotations(UNDERLINE_TAG, 0, text.length) }
    if (underlines.isEmpty()) {
        Text(text, style = style, color = color, modifier = modifier, maxLines = maxLines, overflow = overflow)
        return
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val l = layout ?: return@drawBehind
            val stroke = 1.5.dp.toPx()
            val below = 2.dp.toPx()
            underlines.forEach { a ->
                val lastLine = minOf(l.getLineForOffset(a.end - 1), l.lineCount - 1)
                for (line in l.getLineForOffset(a.start)..lastLine) {
                    val s = maxOf(a.start, l.getLineStart(line))
                    val e = minOf(a.end, l.getLineEnd(line, visibleEnd = true))
                    if (s >= e) continue
                    val y = l.getLineBaseline(line) + below
                    drawLine(MarkColors.of(a.item), Offset(l.getBoundingBox(s).left, y), Offset(l.getBoundingBox(e - 1).right, y), stroke)
                }
            }
        },
    )
}
