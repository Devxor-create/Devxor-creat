package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay

private sealed interface MarkdownNode {
    data class Heading(val level: Int, val text: String) : MarkdownNode
    data class Paragraph(val text: String) : MarkdownNode
    data class ListItem(
        val text: String,
        val indentLevel: Int,
        val orderedMarker: String? = null,
        val isChecked: Boolean? = null
    ) : MarkdownNode
    data class BlockQuote(val lines: List<String>) : MarkdownNode
    data class CodeBlock(val language: String, val code: String) : MarkdownNode
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownNode
    data object Divider : MarkdownNode
}

private val ORDERED_LIST_REGEX = Regex("^(\\d+[.)])\\s+(.*)$")
private val CHECKBOX_REGEX = Regex("^\\[( |x|X)]\\s+(.*)$")
private val HR_REGEX = Regex("^([-*_])(\\s*\\1){2,}\\s*$")
private val TABLE_SEP_REGEX = Regex("^\\s*\\|?(\\s*:?-{3,}:?\\s*\\|)+\\s*:?-{3,}:?\\s*\\|?\\s*$")

private fun parseMarkdownDocument(raw: String): List<MarkdownNode> {
    val nodes = mutableListOf<MarkdownNode>()
    val lines = raw.replace("\r\n", "\n").lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()

        // 1. Fenced code blocks (``` or ~~~)
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            val fence = if (trimmed.startsWith("```")) "```" else "~~~"
            val lang = trimmed.removePrefix(fence).trim().ifEmpty { "code" }
            val codeBuf = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith(fence)) {
                codeBuf.appendLine(lines[i])
                i++
            }
            if (i < lines.size) i++ // consume closing fence
            nodes.add(MarkdownNode.CodeBlock(lang, codeBuf.toString().trimEnd('\n')))
            continue
        }

        // Blank line
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 2. Horizontal rule
        if (HR_REGEX.matches(trimmed)) {
            nodes.add(MarkdownNode.Divider)
            i++
            continue
        }

        // 3. Headings (# to ####)
        val headingLevel = trimmed.takeWhile { it == '#' }.length
        if (headingLevel in 1..4 && trimmed.length > headingLevel && trimmed[headingLevel] == ' ') {
            nodes.add(MarkdownNode.Heading(headingLevel, trimmed.substring(headingLevel + 1).trim()))
            i++
            continue
        }

        // 4. Blockquote (> ...)
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                quoteLines.add(lines[i].trimStart().removePrefix(">").trimStart())
                i++
            }
            nodes.add(MarkdownNode.BlockQuote(quoteLines))
            continue
        }

        // 5. Markdown Table (header row + separator row)
        if (trimmed.contains("|") && i + 1 < lines.size && TABLE_SEP_REGEX.matches(lines[i + 1])) {
            val headers = splitTableRow(trimmed)
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size) {
                val rowTrimmed = lines[i].trim()
                if (rowTrimmed.isEmpty() || !rowTrimmed.contains("|")) break
                rows.add(splitTableRow(rowTrimmed))
                i++
            }
            nodes.add(MarkdownNode.Table(headers, rows))
            continue
        }

        // 6. Ordered or Unordered list item
        val leadingSpaces = line.takeWhile { it == ' ' || it == '\t' }
            .sumOf { if (it == '\t') 4 else 1 }
        val indentLevel = (leadingSpaces / 2).coerceIn(0, 4)

        val orderedMatch = ORDERED_LIST_REGEX.matchEntire(trimmed)
        if (orderedMatch != null) {
            nodes.add(
                MarkdownNode.ListItem(
                    text = orderedMatch.groupValues[2],
                    indentLevel = indentLevel,
                    orderedMarker = orderedMatch.groupValues[1]
                )
            )
            i++
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            val contentAfterBullet = trimmed.drop(2)
            val checkMatch = CHECKBOX_REGEX.matchEntire(contentAfterBullet)
            if (checkMatch != null) {
                val checked = checkMatch.groupValues[1].equals("x", ignoreCase = true)
                nodes.add(
                    MarkdownNode.ListItem(
                        text = checkMatch.groupValues[2],
                        indentLevel = indentLevel,
                        isChecked = checked
                    )
                )
            } else {
                nodes.add(
                    MarkdownNode.ListItem(
                        text = contentAfterBullet,
                        indentLevel = indentLevel
                    )
                )
            }
            i++
            continue
        }

        // 7. Paragraph (coalesce contiguous non-special text lines)
        val paraBuf = mutableListOf<String>()
        while (i < lines.size) {
            val cur = lines[i]
            val curTrim = cur.trimStart()
            if (curTrim.isEmpty() ||
                curTrim.startsWith("```") ||
                curTrim.startsWith("~~~") ||
                curTrim.startsWith(">") ||
                HR_REGEX.matches(curTrim) ||
                ORDERED_LIST_REGEX.matches(curTrim) ||
                curTrim.startsWith("- ") ||
                curTrim.startsWith("* ") ||
                curTrim.startsWith("+ ") ||
                (curTrim.takeWhile { it == '#' }.length in 1..4 &&
                    curTrim.length > curTrim.takeWhile { it == '#' }.length &&
                    curTrim[curTrim.takeWhile { it == '#' }.length] == ' ') ||
                (curTrim.contains("|") && i + 1 < lines.size && TABLE_SEP_REGEX.matches(lines[i + 1]))
            ) {
                break
            }
            paraBuf.add(cur.trim())
            i++
        }
        if (paraBuf.isNotEmpty()) {
            nodes.add(MarkdownNode.Paragraph(paraBuf.joinToString(" ")))
        }
    }

    return nodes
}

private fun splitTableRow(row: String): List<String> {
    val clean = row.trim().removePrefix("|").removeSuffix("|")
    return clean.split("|").map { it.trim() }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onOpenInCodeStudio: ((language: String, code: String) -> Unit)? = null
) {
    val nodes = remember(markdown) { parseMarkdownDocument(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        nodes.forEach { node ->
            when (node) {
                is MarkdownNode.Heading -> {
                    val style = when (node.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    val headingColor = if (node.level <= 2) MaterialTheme.colorScheme.primary else textColor
                    SelectionContainer {
                        Text(
                            text = formatInlineMarkdown(node.text, headingColor),
                            style = style,
                            fontWeight = FontWeight.Bold,
                            color = headingColor,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                is MarkdownNode.Paragraph -> {
                    SelectionContainer {
                        Text(
                            text = formatInlineMarkdown(node.text, textColor),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }
                }

                is MarkdownNode.ListItem -> {
                    val startPadding = (6 + node.indentLevel * 16).dp
                    SelectionContainer {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = startPadding),
                            verticalAlignment = Alignment.Top
                        ) {
                            when {
                                node.isChecked != null -> {
                                    Icon(
                                        imageVector = if (node.isChecked) {
                                            Icons.Rounded.CheckBox
                                        } else {
                                            Icons.Rounded.CheckBoxOutlineBlank
                                        },
                                        contentDescription = if (node.isChecked) "Checked" else "Unchecked",
                                        tint = if (node.isChecked) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier
                                            .padding(top = 2.dp, end = 8.dp)
                                            .size(18.dp)
                                    )
                                }
                                node.orderedMarker != null -> {
                                    Text(
                                        text = node.orderedMarker,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.widthIn(min = 24.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                else -> {
                                    val bulletChar = when (node.indentLevel % 3) {
                                        0 -> "•"
                                        1 -> "◦"
                                        else -> "▪"
                                    }
                                    Text(
                                        text = bulletChar,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = formatInlineMarkdown(node.text, textColor),
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                is MarkdownNode.BlockQuote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        SelectionContainer {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                node.lines.forEach { quoteLine ->
                                    Text(
                                        text = formatInlineMarkdown(quoteLine, textColor.copy(alpha = 0.9f)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontStyle = FontStyle.Italic,
                                        color = textColor.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                }

                is MarkdownNode.CodeBlock -> {
                    MarkdownCodeBlockCard(
                        language = node.language,
                        code = node.code,
                        onOpenInCodeStudio = onOpenInCodeStudio
                    )
                }

                is MarkdownNode.Table -> {
                    MarkdownTableCard(
                        headers = node.headers,
                        rows = node.rows,
                        textColor = textColor
                    )
                }

                MarkdownNode.Divider -> {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlockCard(
    language: String,
    code: String,
    onOpenInCodeStudio: ((language: String, code: String) -> Unit)?
) {
    val clipboard = LocalClipboardManager.current
    var copiedCode by remember(code) { mutableStateOf(false) }

    LaunchedEffect(copiedCode) {
        if (copiedCode) {
            delay(1800L)
            copiedCode = false
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val baseCodeColor = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    val highlightedCode = remember(code, language, primaryColor, secondaryColor, baseCodeColor, mutedColor) {
        highlightCodeSyntax(
            code = code,
            baseColor = baseCodeColor,
            keywordColor = primaryColor,
            stringColor = Color(0xFF7DD3FC),
            numberColor = Color(0xFFFBBF24),
            commentColor = mutedColor,
            annotationColor = secondaryColor
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = language.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onOpenInCodeStudio != null) {
                    TextButton(
                        onClick = { onOpenInCodeStudio(language, code) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Code,
                            contentDescription = "Open in Code Studio",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Studio", fontSize = 12.sp)
                    }
                }
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copiedCode = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        if (copiedCode) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (copiedCode) "Copied" else "Copy", fontSize = 12.sp)
                }
            }
        }
        SelectionContainer {
            Text(
                text = highlightedCode,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun MarkdownTableCard(
    headers: List<String>,
    rows: List<List<String>>,
    textColor: Color
) {
    val columnCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .horizontalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (c in 0 until columnCount) {
                Text(
                    text = formatInlineMarkdown(headers.getOrElse(c) { "" }, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(min = 90.dp, max = 220.dp)
                )
            }
        }
        rows.forEachIndexed { rowIdx, row ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .background(
                        if (rowIdx % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        else Color.Transparent
                    )
                    .padding(vertical = 7.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (c in 0 until columnCount) {
                    Text(
                        text = formatInlineMarkdown(row.getOrElse(c) { "" }, textColor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        modifier = Modifier.widthIn(min = 90.dp, max = 220.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun formatInlineMarkdown(text: String, defaultColor: Color): AnnotatedString {
    val primary = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    return remember(text, defaultColor, primary, codeBg) {
        buildAnnotatedString {
            appendInlineSegment(
                source = text,
                start = 0,
                end = text.length,
                defaultColor = defaultColor,
                primaryColor = primary,
                codeBgColor = codeBg
            )
        }
    }
}

private fun AnnotatedString.Builder.appendInlineSegment(
    source: String,
    start: Int,
    end: Int,
    defaultColor: Color,
    primaryColor: Color,
    codeBgColor: Color
) {
    var i = start
    while (i < end) {
        val ch = source[i]

        // Escaped markdown punctuation
        if (ch == '\\' && i + 1 < end && source[i + 1] in "\\`*_~[]()") {
            append(source[i + 1])
            i += 2
            continue
        }

        // Inline code span `...`
        if (ch == '`') {
            val close = source.indexOf('`', i + 1)
            if (close != -1 && close < end) {
                withStyle(
                    SpanStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        background = codeBgColor,
                        color = primaryColor,
                        fontSize = 13.sp
                    )
                ) {
                    append(" ${source.substring(i + 1, close)} ")
                }
                i = close + 1
                continue
            }
        }

        // Bold + Italic ***...***
        if (source.startsWith("***", i) && i + 3 < end) {
            val close = findClosingDelimiter(source, "***", i + 3, end)
            if (close != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = defaultColor)) {
                    appendInlineSegment(source, i + 3, close, defaultColor, primaryColor, codeBgColor)
                }
                i = close + 3
                continue
            }
        }

        // Bold **...** or __...__
        if ((source.startsWith("**", i) || source.startsWith("__", i)) && i + 2 < end) {
            val delim = source.substring(i, i + 2)
            val close = findClosingDelimiter(source, delim, i + 2, end)
            if (close != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
                    appendInlineSegment(source, i + 2, close, defaultColor, primaryColor, codeBgColor)
                }
                i = close + 2
                continue
            }
        }

        // Strikethrough ~~...~~
        if (source.startsWith("~~", i) && i + 2 < end) {
            val close = findClosingDelimiter(source, "~~", i + 2, end)
            if (close != -1) {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = defaultColor.copy(alpha = 0.7f))) {
                    appendInlineSegment(source, i + 2, close, defaultColor, primaryColor, codeBgColor)
                }
                i = close + 2
                continue
            }
        }

        // Italic *...* or boundary-safe _..._
        if (ch == '*' && i + 1 < end && source[i + 1] != ' ') {
            val close = findClosingSingleChar(source, '*', i + 1, end)
            if (close != -1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInlineSegment(source, i + 1, close, defaultColor, primaryColor, codeBgColor)
                }
                i = close + 1
                continue
            }
        }

        if (ch == '_' && i + 1 < end && source[i + 1] != ' ' &&
            (i == start || !source[i - 1].isLetterOrDigit())
        ) {
            val close = findClosingUnderscore(source, i + 1, end)
            if (close != -1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInlineSegment(source, i + 1, close, defaultColor, primaryColor, codeBgColor)
                }
                i = close + 1
                continue
            }
        }

        // Link [label](url)
        if (ch == '[') {
            val closeBracket = source.indexOf(']', i + 1)
            if (closeBracket != -1 && closeBracket + 1 < end && source[closeBracket + 1] == '(') {
                val closeParen = source.indexOf(')', closeBracket + 2)
                if (closeParen != -1 && closeParen < end) {
                    val label = source.substring(i + 1, closeBracket)
                    val url = source.substring(closeBracket + 2, closeParen).trim()
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = primaryColor,
                                        textDecoration = TextDecoration.Underline,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            )
                        ) {
                            append(label)
                        }
                        i = closeParen + 1
                        continue
                    }
                }
            }
        }

        append(ch)
        i++
    }
}

private fun findClosingDelimiter(source: String, delim: String, fromIndex: Int, end: Int): Int {
    var idx = source.indexOf(delim, fromIndex)
    while (idx != -1 && idx + delim.length <= end) {
        if (idx > fromIndex && source[idx - 1] != '\\') return idx
        idx = source.indexOf(delim, idx + 1)
    }
    return -1
}

private fun findClosingSingleChar(source: String, ch: Char, fromIndex: Int, end: Int): Int {
    for (idx in fromIndex until end) {
        if (source[idx] == ch && source[idx - 1] != '\\' && source[idx - 1] != ' ') {
            if (idx + 1 >= end || source[idx + 1] != ch) {
                return idx
            }
        }
    }
    return -1
}

private fun findClosingUnderscore(source: String, fromIndex: Int, end: Int): Int {
    for (idx in fromIndex until end) {
        if (source[idx] == '_' && source[idx - 1] != '\\' && source[idx - 1] != ' ') {
            val nextOk = idx + 1 >= end || !source[idx + 1].isLetterOrDigit()
            if (nextOk) return idx
        }
    }
    return -1
}

private val CODE_KEYWORDS = setOf(
    "fun", "val", "var", "class", "object", "interface", "enum", "sealed", "data",
    "if", "else", "when", "for", "while", "do", "return", "break", "continue",
    "import", "package", "try", "catch", "finally", "throw", "suspend", "async",
    "await", "function", "const", "let", "def", "from", "as", "in", "is", "true",
    "false", "null", "None", "True", "False", "public", "private", "override",
    "export", "default", "new", "this", "super", "struct", "impl", "fn", "pub"
)

private fun highlightCodeSyntax(
    code: String,
    baseColor: Color,
    keywordColor: Color,
    stringColor: Color,
    numberColor: Color,
    commentColor: Color,
    annotationColor: Color
): AnnotatedString = buildAnnotatedString {
    val len = code.length
    var i = 0
    while (i < len) {
        val ch = code[i]
        when {
            // Line comments // or #
            (ch == '/' && i + 1 < len && code[i + 1] == '/') ||
                (ch == '#' && (i == 0 || code[i - 1] == '\n' || code[i - 1] == ' ')) -> {
                val eol = code.indexOf('\n', i).let { if (it == -1) len else it }
                withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                    append(code.substring(i, eol))
                }
                i = eol
            }
            // Block comments /* ... */
            ch == '/' && i + 1 < len && code[i + 1] == '*' -> {
                val close = code.indexOf("*/", i + 2).let { if (it == -1) len else it + 2 }
                withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                    append(code.substring(i, close))
                }
                i = close
            }
            // Strings "..." or '...'
            ch == '"' || ch == '\'' -> {
                val quote = ch
                var j = i + 1
                while (j < len && code[j] != '\n') {
                    if (code[j] == '\\' && j + 1 < len) {
                        j += 2
                        continue
                    }
                    if (code[j] == quote) {
                        j++
                        break
                    }
                    j++
                }
                withStyle(SpanStyle(color = stringColor)) {
                    append(code.substring(i, j))
                }
                i = j
            }
            // Annotations @Composable
            ch == '@' && i + 1 < len && code[i + 1].isLetter() -> {
                var j = i + 1
                while (j < len && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                withStyle(SpanStyle(color = annotationColor, fontWeight = FontWeight.Medium)) {
                    append(code.substring(i, j))
                }
                i = j
            }
            // Numbers
            ch.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit()) -> {
                var j = i + 1
                while (j < len && (code[j].isDigit() || code[j] == '.' || code[j] == '_' || code[j] == 'f' || code[j] == 'L' || code[j] == 'x')) {
                    j++
                }
                withStyle(SpanStyle(color = numberColor)) {
                    append(code.substring(i, j))
                }
                i = j
            }
            // Identifiers & Keywords
            ch.isLetter() || ch == '_' -> {
                var j = i + 1
                while (j < len && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j)
                if (word in CODE_KEYWORDS) {
                    withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold)) {
                        append(word)
                    }
                } else {
                    withStyle(SpanStyle(color = baseColor)) {
                        append(word)
                    }
                }
                i = j
            }
            else -> {
                withStyle(SpanStyle(color = baseColor)) {
                    append(ch)
                }
                i++
            }
        }
    }
}
