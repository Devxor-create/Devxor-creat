package com.example.core

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConversationExporter {

    private const val DEEP_LINK_SCHEME = "spacexai"
    private const val DEEP_LINK_HOST_CHAT = "chat"

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_HORIZONTAL = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_HORIZONTAL * 2f)
    private const val TOP_MARGIN = 44f
    private const val BOTTOM_MARGIN = 48f
    private const val CONTENT_BOTTOM = PAGE_HEIGHT - BOTTOM_MARGIN

    fun buildShareableConversationLink(conversationId: String?): String? {
        val cleanId = conversationId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST_CHAT/${Uri.encode(cleanId)}"
    }

    fun parseDeepLinkConversationId(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (!uri.scheme.equals(DEEP_LINK_SCHEME, ignoreCase = true)) return null
        if (!uri.host.equals(DEEP_LINK_HOST_CHAT, ignoreCase = true)) return null
        val segment = uri.pathSegments.firstOrNull()?.trim()
        return segment?.takeIf { it.isNotEmpty() }?.let { Uri.decode(it) }
    }

    fun suggestedPdfFileName(title: String): String = suggestedExportFileName(title, "pdf")

    fun suggestedJsonFileName(title: String): String = suggestedExportFileName(title, "json")

    fun suggestedTextFileName(title: String): String = suggestedExportFileName(title, "txt")

    private fun suggestedExportFileName(title: String, extension: String): String {
        val slug = title
            .trim()
            .replace(Regex("[^A-Za-z0-9\\-_ ]"), "")
            .replace(Regex("\\s+"), "_")
            .take(36)
            .ifEmpty { "Conversation" }
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "SpaceX_AI_${slug}_$dateStamp.$extension"
    }

    fun buildConversationJson(
        title: String,
        conversationId: String?,
        modelId: String,
        shareLink: String?,
        messages: List<ChatMessage>
    ): String {
        val root = JSONObject().apply {
            put("app", "SpaceX AI")
            put("title", title.trim().ifEmpty { "SpaceX AI Conversation" })
            conversationId?.takeIf { it.isNotBlank() }?.let { put("conversationId", it) }
            put("modelId", modelId)
            shareLink?.takeIf { it.isNotBlank() }?.let { put("shareLink", it) }
            put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()))
            put("messageCount", messages.size)

            val msgsArray = JSONArray()
            messages.forEach { msg ->
                val msgObj = JSONObject().apply {
                    if (msg.id.isNotBlank()) put("id", msg.id)
                    put("role", msg.role)
                    put("modelId", msg.modelId)
                    put("text", msg.text)
                    put("feedback", msg.feedback)
                    msg.attachmentLabel?.takeIf { it.isNotBlank() }?.let { put("attachmentLabel", it) }
                    msg.thinkingSummary?.takeIf { it.isNotBlank() }?.let { put("thinkingSummary", it) }
                    if (msg.groundingSources.isNotEmpty()) {
                        put("groundingSources", JSONArray(msg.groundingSources))
                    }
                    msg.createdAt?.toDate()?.let { created ->
                        put("createdAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(created))
                    }
                }
                msgsArray.put(msgObj)
            }
            put("messages", msgsArray)
        }
        return root.toString(2)
    }

    suspend fun writeTextToStream(
        outputStream: OutputStream,
        content: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(content)
            }
        }
    }

    fun buildConversationTranscriptMarkdown(
        title: String,
        modelId: String,
        shareLink: String?,
        messages: List<ChatMessage>
    ): String = buildString {
        appendLine("# ${title.ifBlank { "SpaceX AI Conversation" }}")
        appendLine("Model: $modelId")
        appendLine("Exported: ${formatTimestamp(Date())}")
        if (!shareLink.isNullOrBlank()) {
            appendLine("Deep Link: $shareLink")
        }
        appendLine()
        messages.forEach { msg ->
            val speaker = if (msg.role == "user") "YOU" else "SPACEX AI (${msg.modelId})"
            appendLine("### $speaker")
            if (!msg.attachmentLabel.isNullOrBlank()) {
                appendLine("_Attachment: ${msg.attachmentLabel}_")
            }
            appendLine(msg.text.trim())
            if (msg.groundingSources.isNotEmpty()) {
                appendLine()
                appendLine("Sources:")
                msg.groundingSources.forEach { src ->
                    appendLine("- $src")
                }
            }
            appendLine()
        }
    }.trimEnd()

    suspend fun createShareablePdfUri(
        context: Context,
        title: String,
        modelId: String,
        messages: List<ChatMessage>
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = File(context.cacheDir, "exports").apply {
                if (!exists()) mkdirs()
            }
            // Keep export cache tidy
            exportDir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(4)
                ?.forEach { it.delete() }

            val outFile = File(exportDir, suggestedPdfFileName(title))
            FileOutputStream(outFile).use { stream ->
                renderPdf(
                    outputStream = stream,
                    title = title,
                    modelId = modelId,
                    messages = messages
                )
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
        }
    }

    suspend fun writeConversationPdfToStream(
        outputStream: OutputStream,
        title: String,
        modelId: String,
        messages: List<ChatMessage>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            renderPdf(
                outputStream = outputStream,
                title = title,
                modelId = modelId,
                messages = messages
            )
        }
    }

    fun launchPdfShareIntent(context: Context, pdfUri: Uri, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            putExtra(Intent.EXTRA_SUBJECT, "${title.ifBlank { "SpaceX AI Conversation" }} (PDF)")
            putExtra(Intent.EXTRA_TEXT, "Exported conversation from SpaceX AI")
            clipData = ClipData.newRawUri(title.ifBlank { "SpaceX AI PDF" }, pdfUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Conversation PDF").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }

    fun launchTextShareIntent(context: Context, subject: String, content: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share from SpaceX AI"))
    }

    private fun renderPdf(
        outputStream: OutputStream,
        title: String,
        modelId: String,
        messages: List<ChatMessage>
    ) {
        val safeTitle = title.trim().ifEmpty { "SpaceX AI Conversation" }
        val exportedAt = formatTimestamp(Date())

        val pdf = PdfDocument()
        try {
            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#071426")
                style = Paint.Style.FILL
            }
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#B7FF35")
                style = Paint.Style.FILL
            }
            val userBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E8F5C8")
                style = Paint.Style.FILL
            }
            val aiBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E6EFF8")
                style = Paint.Style.FILL
            }
            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D6E0EC")
                strokeWidth = 0.8f
            }
            val brandTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#B7FF35")
                textSize = 9.5f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val heroTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 16f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val heroMetaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                textSize = 9.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val rolePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#071426")
                textSize = 9.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val msgTimePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 8.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val sourcePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#334155")
                textSize = 8.8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            }
            val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 8.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }

            var pageNumber = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            var canvas = page.canvas

            fun drawFooter(targetCanvas: Canvas, number: Int) {
                targetCanvas.drawLine(
                    MARGIN_HORIZONTAL,
                    PAGE_HEIGHT - 32f,
                    PAGE_WIDTH - MARGIN_HORIZONTAL,
                    PAGE_HEIGHT - 32f,
                    dividerPaint
                )
                targetCanvas.drawText(
                    "SpaceX AI • Exported Conversation",
                    MARGIN_HORIZONTAL,
                    PAGE_HEIGHT - 18f,
                    footerPaint
                )
                val pageLabel = "Page $number"
                val labelWidth = footerPaint.measureText(pageLabel)
                targetCanvas.drawText(
                    pageLabel,
                    PAGE_WIDTH - MARGIN_HORIZONTAL - labelWidth,
                    PAGE_HEIGHT - 18f,
                    footerPaint
                )
            }

            fun startNewPage(): Float {
                drawFooter(canvas, pageNumber)
                pdf.finishPage(page)
                pageNumber += 1
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas

                // Compact running header on subsequent pages
                canvas.drawText("SPACEX AI // $safeTitle", MARGIN_HORIZONTAL, 28f, msgTimePaint)
                canvas.drawLine(MARGIN_HORIZONTAL, 34f, PAGE_WIDTH - MARGIN_HORIZONTAL, 34f, dividerPaint)
                return TOP_MARGIN + 6f
            }

            // Cover banner on Page 1
            val bannerRect = RectF(MARGIN_HORIZONTAL, 34f, PAGE_WIDTH - MARGIN_HORIZONTAL, 128f)
            canvas.drawRoundRect(bannerRect, 12f, 12f, headerBgPaint)
            canvas.drawRoundRect(
                RectF(MARGIN_HORIZONTAL, 34f, MARGIN_HORIZONTAL + 5f, 128f),
                4f,
                4f,
                accentPaint
            )
            canvas.drawText("SPACEX AI // TRANSCRIPT EXPORT", MARGIN_HORIZONTAL + 16f, 54f, brandTextPaint)

            val titleLayout = StaticLayout.Builder
                .obtain(safeTitle, 0, safeTitle.length, heroTitlePaint, (CONTENT_WIDTH - 28f).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(2)
                .build()
            canvas.save()
            canvas.translate(MARGIN_HORIZONTAL + 16f, 60f)
            titleLayout.draw(canvas)
            canvas.restore()

            val metaLine = "Model: $modelId  •  ${messages.size} messages  •  $exportedAt"
            canvas.drawText(metaLine, MARGIN_HORIZONTAL + 16f, 115f, heroMetaPaint)

            var cursorY = 148f

            if (messages.isEmpty()) {
                canvas.drawText("No messages in this conversation.", MARGIN_HORIZONTAL, cursorY, bodyPaint)
            } else {
                messages.forEachIndexed { index, message ->
                    if (cursorY + 46f > CONTENT_BOTTOM) {
                        cursorY = startNewPage()
                    }

                    val isUser = message.role == "user"
                    val roleLabel = if (isUser) "YOU" else "SPACEX AI"
                    val badgeWidth = rolePaint.measureText(roleLabel) + 18f
                    val badgeRect = RectF(MARGIN_HORIZONTAL, cursorY, MARGIN_HORIZONTAL + badgeWidth, cursorY + 18f)
                    canvas.drawRoundRect(badgeRect, 6f, 6f, if (isUser) userBadgePaint else aiBadgePaint)
                    canvas.drawText(roleLabel, MARGIN_HORIZONTAL + 9f, cursorY + 12.5f, rolePaint)

                    val msgDate = message.createdAt?.toDate()?.let { formatTimestamp(it) }.orEmpty()
                    val subMeta = buildString {
                        if (!isUser) append(message.modelId)
                        if (!isUser && msgDate.isNotEmpty()) append(" • ")
                        append(msgDate)
                    }
                    if (subMeta.isNotEmpty()) {
                        canvas.drawText(subMeta, MARGIN_HORIZONTAL + badgeWidth + 8f, cursorY + 12.5f, msgTimePaint)
                    }
                    cursorY += 24f

                    if (!message.attachmentLabel.isNullOrBlank()) {
                        val attachText = "Attachment: ${message.attachmentLabel}"
                        if (cursorY + 14f > CONTENT_BOTTOM) cursorY = startNewPage()
                        canvas.drawText(attachText, MARGIN_HORIZONTAL, cursorY + 10f, sourcePaint)
                        cursorY += 16f
                    }

                    val cleanBody = message.text.trim().ifEmpty { "(Empty message)" }
                    val bodyLayout = StaticLayout.Builder
                        .obtain(cleanBody, 0, cleanBody.length, bodyPaint, CONTENT_WIDTH.toInt())
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(2.5f, 1.0f)
                        .build()

                    for (lineIdx in 0 until bodyLayout.lineCount) {
                        val lineTop = bodyLayout.getLineTop(lineIdx)
                        val lineBottom = bodyLayout.getLineBottom(lineIdx)
                        val lineHeight = (lineBottom - lineTop).toFloat()
                        if (cursorY + lineHeight > CONTENT_BOTTOM) {
                            cursorY = startNewPage()
                        }
                        val lineStart = bodyLayout.getLineStart(lineIdx)
                        val lineEnd = bodyLayout.getLineEnd(lineIdx)
                        val baselineOffset = (bodyLayout.getLineBaseline(lineIdx) - lineTop).toFloat()
                        val lineString = cleanBody.substring(lineStart, lineEnd).trimEnd('\n', '\r')
                        canvas.drawText(lineString, MARGIN_HORIZONTAL, cursorY + baselineOffset, bodyPaint)
                        cursorY += lineHeight
                    }

                    if (message.groundingSources.isNotEmpty()) {
                        cursorY += 6f
                        val sourcesText = "Sources: " + message.groundingSources.joinToString(" | ")
                        val srcLayout = StaticLayout.Builder
                            .obtain(sourcesText, 0, sourcesText.length, sourcePaint, CONTENT_WIDTH.toInt())
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .build()
                        for (lineIdx in 0 until srcLayout.lineCount) {
                            val lineTop = srcLayout.getLineTop(lineIdx)
                            val lineBottom = srcLayout.getLineBottom(lineIdx)
                            val lineHeight = (lineBottom - lineTop).toFloat()
                            if (cursorY + lineHeight > CONTENT_BOTTOM) {
                                cursorY = startNewPage()
                            }
                            val lineString = sourcesText.substring(
                                srcLayout.getLineStart(lineIdx),
                                srcLayout.getLineEnd(lineIdx)
                            ).trimEnd('\n', '\r')
                            val baselineOffset = (srcLayout.getLineBaseline(lineIdx) - lineTop).toFloat()
                            canvas.drawText(lineString, MARGIN_HORIZONTAL, cursorY + baselineOffset, sourcePaint)
                            cursorY += lineHeight
                        }
                    }

                    cursorY += 12f
                    if (index < messages.lastIndex) {
                        if (cursorY + 8f > CONTENT_BOTTOM) {
                            cursorY = startNewPage()
                        } else {
                            canvas.drawLine(
                                MARGIN_HORIZONTAL,
                                cursorY,
                                PAGE_WIDTH - MARGIN_HORIZONTAL,
                                cursorY,
                                dividerPaint
                            )
                            cursorY += 12f
                        }
                    }
                }
            }

            drawFooter(canvas, pageNumber)
            pdf.finishPage(page)
            pdf.writeTo(outputStream)
        } finally {
            pdf.close()
        }
    }

    private fun formatTimestamp(date: Date): String {
        return SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(date)
    }
}
