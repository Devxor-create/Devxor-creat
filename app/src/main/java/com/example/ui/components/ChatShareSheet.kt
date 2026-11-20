package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.ConversationExporter
import com.example.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatShareBottomSheet(
    conversationTitle: String,
    conversationId: String?,
    modelId: String,
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val safeTitle = conversationTitle.trim().ifEmpty { "SpaceX AI Conversation" }
    val shareLink = remember(conversationId) {
        ConversationExporter.buildShareableConversationLink(conversationId)
    }

    var isBusy by remember { mutableStateOf(false) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var linkCopied by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri: Uri? ->
        if (targetUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            busyMessage = "Rendering PDF document..."
            localError = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(targetUri)
                        ?: error("Unable to open destination file for writing.")
                    output.use { stream ->
                        ConversationExporter.writeConversationPdfToStream(
                            outputStream = stream,
                            title = safeTitle,
                            modelId = modelId,
                            messages = messages
                        ).getOrThrow()
                    }
                }
            }
            isBusy = false
            busyMessage = null
            result.onSuccess {
                onNotify("PDF saved to device.")
                onDismiss()
            }.onFailure { err ->
                localError = err.localizedMessage ?: "Failed to save PDF."
            }
        }
    }

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { targetUri: Uri? ->
        if (targetUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            busyMessage = "Exporting JSON history..."
            localError = null
            val jsonContent = ConversationExporter.buildConversationJson(
                title = safeTitle,
                conversationId = conversationId,
                modelId = modelId,
                shareLink = shareLink,
                messages = messages
            )
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(targetUri)
                        ?: error("Unable to open destination file for writing.")
                    output.use { stream ->
                        ConversationExporter.writeTextToStream(stream, jsonContent).getOrThrow()
                    }
                }
            }
            isBusy = false
            busyMessage = null
            result.onSuccess {
                onNotify("Chat history exported as JSON.")
                onDismiss()
            }.onFailure { err ->
                localError = err.localizedMessage ?: "Failed to save JSON file."
            }
        }
    }

    val saveTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { targetUri: Uri? ->
        if (targetUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            busyMessage = "Exporting text transcript..."
            localError = null
            val textContent = ConversationExporter.buildConversationTranscriptMarkdown(
                title = safeTitle,
                modelId = modelId,
                shareLink = shareLink,
                messages = messages
            )
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(targetUri)
                        ?: error("Unable to open destination file for writing.")
                    output.use { stream ->
                        ConversationExporter.writeTextToStream(stream, textContent).getOrThrow()
                    }
                }
            }
            isBusy = false
            busyMessage = null
            result.onSuccess {
                onNotify("Chat history exported as TXT.")
                onDismiss()
            }.onFailure { err ->
                localError = err.localizedMessage ?: "Failed to save text file."
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isBusy) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("chat_share_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Share & Export Chat",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$safeTitle • ${messages.size} messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isBusy) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    busyMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            localError?.let { errText ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Section 1: Shareable Deep Link
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Shareable App Link",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (shareLink != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = shareLink,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(shareLink))
                                    linkCopied = true
                                    onNotify("Shareable link copied to clipboard.")
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("share_copy_link_button")
                            ) {
                                Icon(
                                    imageVector = if (linkCopied) Icons.Rounded.Check else Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (linkCopied) "Copied" else "Copy Link")
                            }

                            Button(
                                onClick = {
                                    ConversationExporter.launchTextShareIntent(
                                        context = context,
                                        subject = "$safeTitle — SpaceX AI",
                                        content = "Open \"$safeTitle\" in SpaceX AI:\n$shareLink"
                                    )
                                },
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("share_send_link_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Share Link")
                            }
                        }
                    } else {
                        Text(
                            text = "Send at least one message to generate a direct SpaceX AI conversation link.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 2: PDF Export
            Text(
                text = "Export Document",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ShareOptionCard(
                icon = Icons.Outlined.Download,
                title = "Save PDF to Device",
                subtitle = "Choose a folder on your device or cloud drive",
                enabled = !isBusy && messages.isNotEmpty(),
                testTag = "share_save_pdf_button",
                onClick = {
                    localError = null
                    savePdfLauncher.launch(ConversationExporter.suggestedPdfFileName(safeTitle))
                }
            )

            ShareOptionCard(
                icon = Icons.Outlined.PictureAsPdf,
                title = "Share PDF File",
                subtitle = "Generate a formatted A4 PDF and send via any app",
                enabled = !isBusy && messages.isNotEmpty(),
                testTag = "share_send_pdf_button",
                onClick = {
                    scope.launch {
                        isBusy = true
                        busyMessage = "Preparing PDF for sharing..."
                        localError = null
                        val result = ConversationExporter.createShareablePdfUri(
                            context = context,
                            title = safeTitle,
                            modelId = modelId,
                            messages = messages
                        )
                        isBusy = false
                        busyMessage = null
                        result.onSuccess { pdfUri ->
                            ConversationExporter.launchPdfShareIntent(
                                context = context,
                                pdfUri = pdfUri,
                                title = safeTitle
                            )
                            onDismiss()
                        }.onFailure { err ->
                            localError = err.localizedMessage ?: "Unable to export PDF."
                        }
                    }
                }
            )

            ShareOptionCard(
                icon = Icons.Outlined.DataObject,
                title = "Save as JSON (.json)",
                subtitle = "Export structured messages & metadata to device storage",
                enabled = !isBusy && messages.isNotEmpty(),
                testTag = "share_save_json_button",
                onClick = {
                    localError = null
                    saveJsonLauncher.launch(ConversationExporter.suggestedJsonFileName(safeTitle))
                }
            )

            ShareOptionCard(
                icon = Icons.Outlined.Description,
                title = "Save as Text (.txt)",
                subtitle = "Export a clean readable transcript file to device storage",
                enabled = !isBusy && messages.isNotEmpty(),
                testTag = "share_save_txt_button",
                onClick = {
                    localError = null
                    saveTextLauncher.launch(ConversationExporter.suggestedTextFileName(safeTitle))
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            // Section 3: Text & Markdown Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val markdown = ConversationExporter.buildConversationTranscriptMarkdown(
                            title = safeTitle,
                            modelId = modelId,
                            shareLink = shareLink,
                            messages = messages
                        )
                        clipboard.setText(AnnotatedString(markdown))
                        onNotify("Conversation transcript copied.")
                        onDismiss()
                    },
                    enabled = !isBusy && messages.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("share_copy_text_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Text")
                }

                OutlinedButton(
                    onClick = {
                        val markdown = ConversationExporter.buildConversationTranscriptMarkdown(
                            title = safeTitle,
                            modelId = modelId,
                            shareLink = shareLink,
                            messages = messages
                        )
                        ConversationExporter.launchTextShareIntent(
                            context = context,
                            subject = safeTitle,
                            content = markdown
                        )
                        onDismiss()
                    },
                    enabled = !isBusy && messages.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("share_send_text_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Share Text")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ShareOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.14f else 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
