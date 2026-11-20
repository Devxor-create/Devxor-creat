package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.SpaceXAiEngine
import com.example.core.ConversationExporter
import com.example.core.RecordedAudioMemo
import com.example.core.VoiceAudioRecorder
import com.example.data.model.ChatMessage
import com.example.ui.components.AttachedAudioMemoCard
import com.example.ui.components.ChatShareBottomSheet
import com.example.ui.components.ChatVoiceMode
import com.example.ui.components.ChatVoiceRecorderPanel
import com.example.ui.components.MarkdownText
import com.example.ui.components.MicPermissionPromptCard
import com.example.ui.components.SystemPromptBottomSheet
import com.example.ui.state.GenerationUiState
import com.example.ui.state.SpaceXRoute
import com.example.ui.state.SpaceXWorkspaceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    snapshot: SpaceXWorkspaceSnapshot,
    onSendMessage: (
        prompt: String,
        image: Bitmap?,
        docText: String?,
        docName: String?,
        audioBytes: ByteArray?,
        audioMimeType: String?,
        audioLabel: String?
    ) -> Unit,
    onStopGeneration: () -> Unit,
    onSelectModel: (String) -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleMaps: () -> Unit,
    onToggleHighThinking: () -> Unit,
    onRegenerate: () -> Unit,
    onEditMessage: (ChatMessage, String) -> Unit,
    onFeedback: (ChatMessage, String) -> Unit,
    onSpeakText: (String) -> Unit,
    onSaveToLibrary: (title: String, content: String) -> Unit,
    onOpenInCodeStudio: (language: String, code: String) -> Unit,
    onQuickNavigate: (SpaceXRoute) -> Unit,
    onTranscribeAudio: (audioBytes: ByteArray, mimeType: String, onDone: (String?) -> Unit) -> Unit = { _, _, onDone -> onDone(null) },
    onSaveSystemPrompt: (String) -> Unit = {},
    onShareConversation: () -> Unit = {},
    onNotify: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    var promptText by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var attachedDocText by remember { mutableStateOf<String?>(null) }
    var attachedDocName by remember { mutableStateOf<String?>(null) }
    var attachedAudioMemo by remember { mutableStateOf<RecordedAudioMemo?>(null) }
    var isTranscribingMemo by remember { mutableStateOf(false) }

    var showModelSheet by remember { mutableStateOf(false) }
    var showToolsSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showSystemPromptSheet by remember { mutableStateOf(false) }
    var showChatOptionsMenu by remember { mutableStateOf(false) }
    var showSearchInChat by remember { mutableStateOf(false) }
    var inChatSearch by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editInput by remember { mutableStateOf("") }

    var showVoicePanel by remember { mutableStateOf(false) }
    var voiceMode by remember { mutableStateOf(ChatVoiceMode.DICTATION) }
    var isDictating by remember { mutableStateOf(false) }
    var liveTranscript by remember { mutableStateOf("") }
    var dictationAmplitudes by remember { mutableStateOf(List(28) { 0.08f }) }
    var speechErrorMessage by remember { mutableStateOf<String?>(null) }
    var showMicPermissionCard by remember { mutableStateOf(false) }
    var micPermanentlyDenied by remember { mutableStateOf(false) }

    val audioRecorder = remember(context) { VoiceAudioRecorder(context, coroutineScope) }
    val recorderState by audioRecorder.state.collectAsStateWithLifecycle()

    val speechAvailable = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }

    val listState = rememberLazyListState()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                attachedBitmap = bmp.copy(Bitmap.Config.ARGB_8888, false)
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                val name = context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else "Document"
                } ?: "Document"
                val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    reader.readText().take(18000)
                }.orEmpty()
                attachedDocName = name
                attachedDocText = content.ifBlank { "[Binary document attached: $name]" }
            }
        }
    }

    val activeConversationTitle = remember(snapshot.conversations, snapshot.activeConversationId, snapshot.messages) {
        snapshot.conversations.firstOrNull { it.id == snapshot.activeConversationId }?.title
            ?: snapshot.messages.firstOrNull { it.role == "user" }?.text?.take(48)
            ?: "SpaceX AI Conversation"
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { targetUri: Uri? ->
        if (targetUri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val shareLink = ConversationExporter.buildShareableConversationLink(snapshot.activeConversationId)
            val jsonContent = ConversationExporter.buildConversationJson(
                title = activeConversationTitle,
                conversationId = snapshot.activeConversationId,
                modelId = snapshot.selectedModelId,
                shareLink = shareLink,
                messages = snapshot.messages
            )
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val out = context.contentResolver.openOutputStream(targetUri)
                        ?: error("Unable to open destination file.")
                    out.use { stream ->
                        ConversationExporter.writeTextToStream(stream, jsonContent).getOrThrow()
                    }
                }
            }
            result.onSuccess { onNotify("Chat history exported as JSON.") }
                .onFailure { err -> onNotify(err.localizedMessage ?: "Failed to save JSON file.") }
        }
    }

    val exportTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { targetUri: Uri? ->
        if (targetUri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val shareLink = ConversationExporter.buildShareableConversationLink(snapshot.activeConversationId)
            val textContent = ConversationExporter.buildConversationTranscriptMarkdown(
                title = activeConversationTitle,
                modelId = snapshot.selectedModelId,
                shareLink = shareLink,
                messages = snapshot.messages
            )
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val out = context.contentResolver.openOutputStream(targetUri)
                        ?: error("Unable to open destination file.")
                    out.use { stream ->
                        ConversationExporter.writeTextToStream(stream, textContent).getOrThrow()
                    }
                }
            }
            result.onSuccess { onNotify("Chat history exported as TXT.") }
                .onFailure { err -> onNotify(err.localizedMessage ?: "Failed to save text file.") }
        }
    }

    val speechRecognizer = remember(context, speechAvailable) {
        if (speechAvailable) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } else null
    }

    DisposableEffect(speechRecognizer, audioRecorder) {
        onDispose {
            runCatching { speechRecognizer?.destroy() }
            audioRecorder.release()
        }
    }

    fun appendSpokenText(spoken: String) {
        val clean = spoken.trim()
        if (clean.isNotEmpty()) {
            promptText = if (promptText.isBlank()) clean else "${promptText.trimEnd()} $clean"
        }
    }

    fun stopDictation(commitText: Boolean) {
        runCatching { speechRecognizer?.stopListening() }
        isDictating = false
        if (commitText && liveTranscript.isNotBlank()) {
            appendSpokenText(liveTranscript)
        }
        liveTranscript = ""
        dictationAmplitudes = List(28) { 0.08f }
    }

    fun startDictation() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            speechErrorMessage = "On-device speech recognizer unavailable. Switched to Audio Memo."
            voiceMode = ChatVoiceMode.AUDIO_MEMO
            return
        }
        speechErrorMessage = null
        liveTranscript = ""
        isDictating = true
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                val level = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1f)
                dictationAmplitudes = dictationAmplitudes.drop(1) + level
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isDictating = false
            }
            override fun onError(error: Int) {
                isDictating = false
                if (liveTranscript.isNotBlank()) {
                    appendSpokenText(liveTranscript)
                    liveTranscript = ""
                    showVoicePanel = false
                } else {
                    speechErrorMessage = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap Listen or switch to Audio Memo."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        else -> "Speech recognition unavailable (code $error). Try Audio Memo mode."
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                isDictating = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()
                val textToInsert = if (!spoken.isNullOrBlank()) spoken else liveTranscript
                if (textToInsert.isNotBlank()) {
                    appendSpokenText(textToInsert)
                    liveTranscript = ""
                    showVoicePanel = false
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    liveTranscript = partial
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { recognizer.startListening(intent) }.onFailure { err ->
            isDictating = false
            speechErrorMessage = err.localizedMessage ?: "Unable to start speech recognition."
        }
    }

    fun openVoiceRecorderUi(preferredMode: ChatVoiceMode) {
        showMicPermissionCard = false
        micPermanentlyDenied = false
        speechErrorMessage = null
        audioRecorder.clearError()
        val effectiveMode = if (preferredMode == ChatVoiceMode.DICTATION && !speechAvailable) {
            ChatVoiceMode.AUDIO_MEMO
        } else {
            preferredMode
        }
        voiceMode = effectiveMode
        showVoicePanel = true
        if (effectiveMode == ChatVoiceMode.DICTATION) {
            audioRecorder.cancelRecording()
            startDictation()
        } else {
            stopDictation(commitText = false)
            audioRecorder.startRecording()
        }
    }

    var pendingVoiceModeAfterPermission by remember { mutableStateOf(ChatVoiceMode.DICTATION) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openVoiceRecorderUi(pendingVoiceModeAfterPermission)
        } else {
            val activity = context.findActivity()
            micPermanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            showMicPermissionCard = true
        }
    }

    fun requestOrLaunchVoice(mode: ChatVoiceMode) {
        pendingVoiceModeAfterPermission = mode
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            openVoiceRecorderUi(mode)
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val filteredMessages = remember(snapshot.messages, inChatSearch) {
        if (inChatSearch.isBlank()) snapshot.messages
        else snapshot.messages.filter { it.text.contains(inChatSearch, ignoreCase = true) }
    }

    LaunchedEffect(filteredMessages.size, snapshot.generationState) {
        val targetIndex = when (snapshot.generationState) {
            is GenerationUiState.Loading,
            is GenerationUiState.Streaming,
            is GenerationUiState.Error -> filteredMessages.size
            GenerationUiState.Idle -> (filteredMessages.size - 1).coerceAtLeast(0)
        }
        if (filteredMessages.isNotEmpty() || snapshot.generationState !is GenerationUiState.Idle) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Optional In-Chat Search & Context Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Model Selector Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    .clickable { showModelSheet = true }
                    .testTag("model_selector_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    val activeModel = SpaceXAiEngine.supportedModels
                        .firstOrNull { it.id == snapshot.selectedModelId }
                    Text(
                        text = activeModel?.displayName?.substringBefore(" (") ?: snapshot.selectedModelId,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = "Select model", modifier = Modifier.size(18.dp))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (snapshot.messages.isNotEmpty()) {
                    IconButton(onClick = { showSearchInChat = !showSearchInChat }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search within conversation")
                    }
                    FilledTonalButton(
                        onClick = { showShareSheet = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("chat_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share or export conversation",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
                Box {
                    IconButton(
                        onClick = { showChatOptionsMenu = true },
                        modifier = Modifier.testTag("chat_options_menu_button")
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More chat options")
                    }
                    DropdownMenu(
                        expanded = showChatOptionsMenu,
                        onDismissRequest = { showChatOptionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("System Prompt / Persona...") },
                            leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                            onClick = {
                                showChatOptionsMenu = false
                                showSystemPromptSheet = true
                            },
                            modifier = Modifier.testTag("menu_system_prompt")
                        )
                        if (snapshot.messages.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Export as JSON (.json)") },
                                leadingIcon = { Icon(Icons.Outlined.DataObject, contentDescription = null) },
                                onClick = {
                                    showChatOptionsMenu = false
                                    exportJsonLauncher.launch(
                                        ConversationExporter.suggestedJsonFileName(activeConversationTitle)
                                    )
                                },
                                modifier = Modifier.testTag("menu_export_json")
                            )
                            DropdownMenuItem(
                                text = { Text("Export as Text (.txt)") },
                                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                                onClick = {
                                    showChatOptionsMenu = false
                                    exportTextLauncher.launch(
                                        ConversationExporter.suggestedTextFileName(activeConversationTitle)
                                    )
                                },
                                modifier = Modifier.testTag("menu_export_txt")
                            )
                            DropdownMenuItem(
                                text = { Text("Export PDF & Share Link...") },
                                leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null) },
                                onClick = {
                                    showChatOptionsMenu = false
                                    showShareSheet = true
                                },
                                modifier = Modifier.testTag("menu_export_pdf")
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showSearchInChat) {
            OutlinedTextField(
                value = inChatSearch,
                onValueChange = { inChatSearch = it },
                placeholder = { Text("Search messages in this thread...") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { inChatSearch = ""; showSearchInChat = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close search")
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Main Chat Content or Empty Homepage
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (filteredMessages.isEmpty() && snapshot.generationState is GenerationUiState.Idle) {
                ChatHomepageHero(
                    displayName = snapshot.profile?.displayName,
                    onPickPrompt = { promptText = it },
                    onQuickNavigate = onQuickNavigate
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 820.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredMessages, key = { it.id.ifBlank { it.hashCode().toString() } }) { msg ->
                        ChatMessageBubble(
                            message = msg,
                            onCopy = {
                                clipboard.setText(AnnotatedString(msg.text))
                                onNotify("Copied to clipboard.")
                            },
                            onEdit = {
                                editingMessage = msg
                                editInput = msg.text
                            },
                            onRegenerate = onRegenerate,
                            onLike = {
                                onFeedback(msg, if (msg.feedback == "like") "none" else "like")
                            },
                            onDislike = {
                                onFeedback(msg, if (msg.feedback == "dislike") "none" else "dislike")
                            },
                            onSpeak = { onSpeakText(msg.text) },
                            onSaveToLibrary = {
                                onSaveToLibrary(
                                    msg.text.lineSequence().firstOrNull()?.take(48) ?: "Saved AI Insight",
                                    msg.text
                                )
                            },
                            onOpenInCodeStudio = onOpenInCodeStudio,
                            onOpenSourceUri = { uri -> runCatching { uriHandler.openUri(uri) } }
                        )
                    }

                    when (val gen = snapshot.generationState) {
                        is GenerationUiState.Loading -> {
                            item(key = "ai_typing_loading") {
                                AiTypingBubble()
                            }
                        }
                        is GenerationUiState.Streaming -> {
                            if (gen.text.isBlank()) {
                                item(key = "ai_typing_waiting") {
                                    AiTypingBubble()
                                }
                            } else {
                                item(key = "ai_streaming_bubble") {
                                    StreamingStatusBubble(text = gen.text)
                                }
                            }
                        }
                        is GenerationUiState.Error -> {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp))
                                ) {
                                    Text(
                                        text = "Error: ${gen.message}",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                        GenerationUiState.Idle -> Unit
                    }
                }
            }
        }

        // Composer Area
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Microphone Permission Card
                AnimatedVisibility(visible = showMicPermissionCard) {
                    MicPermissionPromptCard(
                        permanentlyDenied = micPermanentlyDenied,
                        onRequestPermission = {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onOpenAppSettings = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                            showMicPermissionCard = false
                        },
                        onDismiss = { showMicPermissionCard = false }
                    )
                }

                // Live Real-Time Voice Recorder & Dictation Panel
                AnimatedVisibility(visible = showVoicePanel) {
                    ChatVoiceRecorderPanel(
                        mode = voiceMode,
                        isDictating = isDictating,
                        isRecordingMemo = recorderState.isRecording,
                        elapsedMs = recorderState.elapsedMs,
                        amplitudes = if (voiceMode == ChatVoiceMode.AUDIO_MEMO) recorderState.amplitudes else dictationAmplitudes,
                        liveTranscript = liveTranscript,
                        speechAvailable = speechAvailable,
                        errorMessage = if (voiceMode == ChatVoiceMode.AUDIO_MEMO) recorderState.error else speechErrorMessage,
                        onSelectMode = { selectedMode ->
                            voiceMode = selectedMode
                            speechErrorMessage = null
                            audioRecorder.clearError()
                            if (selectedMode == ChatVoiceMode.AUDIO_MEMO) {
                                stopDictation(commitText = true)
                                if (!recorderState.isRecording) {
                                    audioRecorder.startRecording()
                                }
                            } else {
                                audioRecorder.cancelRecording()
                                if (speechAvailable) startDictation()
                            }
                        },
                        onRestartDictation = { startDictation() },
                        onCommitDictation = {
                            stopDictation(commitText = true)
                            showVoicePanel = false
                        },
                        onStartMemoRecording = { audioRecorder.startRecording() },
                        onStopMemoRecording = {
                            coroutineScope.launch {
                                audioRecorder.stopRecordingAndCollect()
                                    .onSuccess { memo ->
                                        attachedAudioMemo = memo
                                        showVoicePanel = false
                                    }
                            }
                        },
                        onCancel = {
                            stopDictation(commitText = false)
                            audioRecorder.cancelRecording()
                            showVoicePanel = false
                        }
                    )
                }

                // Attached Recorded Audio Memo Preview Bar
                attachedAudioMemo?.let { memo ->
                    AttachedAudioMemoCard(
                        memo = memo,
                        isPlaying = recorderState.isPlayingPreview,
                        playbackProgress = recorderState.playbackProgress,
                        isTranscribing = isTranscribingMemo,
                        onTogglePlay = { audioRecorder.togglePlayback(memo.file) },
                        onTranscribeToText = {
                            isTranscribingMemo = true
                            onTranscribeAudio(memo.bytes, memo.mimeType) { transcript ->
                                isTranscribingMemo = false
                                if (!transcript.isNullOrBlank()) {
                                    appendSpokenText(transcript)
                                }
                            }
                        },
                        onRemove = {
                            audioRecorder.stopPlayback()
                            attachedAudioMemo = null
                        }
                    )
                }

                // Attached preview strip
                if (attachedBitmap != null || attachedDocName != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        attachedBitmap?.let { bmp ->
                            Box(modifier = Modifier.size(56.dp)) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Attached image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                IconButton(
                                    onClick = { attachedBitmap = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .background(MaterialTheme.colorScheme.background, CircleShape)
                                ) {
                                    Icon(Icons.Rounded.Close, "Remove image", modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                        attachedDocName?.let { doc ->
                            AssistChip(
                                onClick = { attachedDocName = null; attachedDocText = null },
                                label = { Text(doc, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = { Icon(Icons.Rounded.Close, "Remove", Modifier.size(16.dp)) }
                            )
                        }
                    }
                }

                // Grounding & Thinking Quick Toggles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = snapshot.webSearchEnabled,
                        onClick = onToggleWebSearch,
                        label = { Text("Web Search") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    FilterChip(
                        selected = snapshot.mapsGroundingEnabled,
                        onClick = onToggleMaps,
                        label = { Text("Google Maps") },
                        leadingIcon = { Icon(Icons.Outlined.Map, null, Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    FilterChip(
                        selected = snapshot.highThinkingEnabled,
                        onClick = onToggleHighThinking,
                        label = { Text("High Thinking") },
                        leadingIcon = { Icon(Icons.Outlined.Psychology, null, Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    val hasSystemPrompt = !snapshot.profile?.customSystemPrompt.isNullOrBlank()
                    FilterChip(
                        selected = hasSystemPrompt,
                        onClick = { showSystemPromptSheet = true },
                        label = {
                            Text(if (hasSystemPrompt) "System Prompt • On" else "System Prompt")
                        },
                        leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("chat_system_prompt_chip")
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Main Question Input Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    IconButton(
                        onClick = { showToolsSheet = true },
                        modifier = Modifier.testTag("chat_plus_tools_button")
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "Attach and tools",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        placeholder = { Text("Ask SpaceX AI anything...") },
                        maxLines = 5,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_prompt_input")
                    )

                    Spacer(Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            if (showVoicePanel) {
                                stopDictation(commitText = true)
                                audioRecorder.cancelRecording()
                                showVoicePanel = false
                            } else {
                                requestOrLaunchVoice(
                                    if (speechAvailable) ChatVoiceMode.DICTATION else ChatVoiceMode.AUDIO_MEMO
                                )
                            }
                        },
                        modifier = Modifier.testTag("chat_mic_button")
                    ) {
                        Icon(
                            if (showVoicePanel && (isDictating || recorderState.isRecording)) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = if (showVoicePanel) "Stop voice capture" else "Voice input or audio memo",
                            tint = if (showVoicePanel || recorderState.isRecording || isDictating) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    val isGenerating = snapshot.generationState is GenerationUiState.Loading ||
                        snapshot.generationState is GenerationUiState.Streaming
                    val canSend = promptText.isNotBlank() || attachedBitmap != null ||
                        attachedDocText != null || attachedAudioMemo != null

                    if (isGenerating) {
                        IconButton(
                            onClick = onStopGeneration,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .testTag("chat_stop_button")
                        ) {
                            Icon(
                                Icons.Rounded.Stop,
                                contentDescription = "Stop generating",
                                tint = MaterialTheme.colorScheme.onError
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val text = promptText
                                val img = attachedBitmap
                                val docT = attachedDocText
                                val docN = attachedDocName
                                val memo = attachedAudioMemo
                                audioRecorder.stopPlayback()
                                promptText = ""
                                attachedBitmap = null
                                attachedDocText = null
                                attachedDocName = null
                                attachedAudioMemo = null
                                onSendMessage(
                                    text,
                                    img,
                                    docT,
                                    docN,
                                    memo?.bytes,
                                    memo?.mimeType,
                                    memo?.let { "Voice Memo (${it.formattedDuration})" }
                                )
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Model Selector Modal
    if (showModelSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select AI Model",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Powered live via Firebase AI Logic",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SpaceXAiEngine.supportedModels.forEach { model ->
                    val selected = snapshot.selectedModelId == model.id
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onSelectModel(model.id)
                                showModelSheet = false
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = model.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = model.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Plus Tools & Attachments Modal
    if (showToolsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showToolsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Attachments & SpaceX Studios",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AssistChip(
                        onClick = {
                            showToolsSheet = false
                            requestOrLaunchVoice(ChatVoiceMode.AUDIO_MEMO)
                        },
                        label = { Text("Record Audio Memo") },
                        leadingIcon = { Icon(Icons.Outlined.GraphicEq, null) }
                    )
                    AssistChip(
                        onClick = {
                            showToolsSheet = false
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        label = { Text("Attach Image") },
                        leadingIcon = { Icon(Icons.Outlined.Image, null) }
                    )
                    AssistChip(
                        onClick = {
                            showToolsSheet = false
                            documentPicker.launch(arrayOf("text/*", "application/json", "application/xml"))
                        },
                        label = { Text("Attach Document / Code") },
                        leadingIcon = { Icon(Icons.Outlined.AttachFile, null) }
                    )
                    AssistChip(
                        onClick = { showToolsSheet = false; onQuickNavigate(SpaceXRoute.CodeStudio) },
                        label = { Text("Code Studio") },
                        leadingIcon = { Icon(Icons.Outlined.Code, null) }
                    )
                    AssistChip(
                        onClick = { showToolsSheet = false; onQuickNavigate(SpaceXRoute.WebsiteBuilder) },
                        label = { Text("Website Builder") },
                        leadingIcon = { Icon(Icons.Outlined.Language, null) }
                    )
                    AssistChip(
                        onClick = { showToolsSheet = false; onQuickNavigate(SpaceXRoute.CreativeStudio) },
                        label = { Text("Generate Image") },
                        leadingIcon = { Icon(Icons.Outlined.Image, null) }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Edit Message Dialog
    editingMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit & Resend Prompt") },
            text = {
                OutlinedTextField(
                    value = editInput,
                    onValueChange = { editInput = it },
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editInput.isNotBlank()) {
                            onEditMessage(target, editInput.trim())
                        }
                        editingMessage = null
                    }
                ) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Cancel") }
            }
        )
    }

    if (showShareSheet) {
        val activeTitle = remember(snapshot.conversations, snapshot.activeConversationId, snapshot.messages) {
            snapshot.conversations.firstOrNull { it.id == snapshot.activeConversationId }?.title
                ?: snapshot.messages.firstOrNull { it.role == "user" }?.text?.take(48)
                ?: "SpaceX AI Conversation"
        }
        ChatShareBottomSheet(
            conversationTitle = activeTitle,
            conversationId = snapshot.activeConversationId,
            modelId = snapshot.selectedModelId,
            messages = snapshot.messages,
            onDismiss = { showShareSheet = false },
            onNotify = { msg ->
                onNotify(msg)
                if (msg.isBlank()) onShareConversation()
            }
        )
    }

    if (showSystemPromptSheet) {
        SystemPromptBottomSheet(
            currentPrompt = snapshot.profile?.customSystemPrompt.orEmpty(),
            onDismiss = { showSystemPromptSheet = false },
            onSavePrompt = { prompt ->
                onSaveSystemPrompt(prompt)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatHomepageHero(
    displayName: String?,
    onPickPrompt: (String) -> Unit,
    onQuickNavigate: (SpaceXRoute) -> Unit
) {
    val suggestions = listOf(
        "Explain orbital mechanics with math equations",
        "Write a clean Python async web scraper",
        "Research the latest Mars exploration updates",
        "Brainstorm 5 futuristic SaaS startup ideas"
    )
    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.RocketLaunch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "What's on your mind today?",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (!displayName.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Ready for command, $displayName.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(22.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { suggestion ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        .clickable { onPickPrompt(suggestion) }
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { onQuickNavigate(SpaceXRoute.WebsiteBuilder) },
                label = { Text("Website Builder") }
            )
            AssistChip(
                onClick = { onQuickNavigate(SpaceXRoute.GameStudio) },
                label = { Text("Game Studio") }
            )
            AssistChip(
                onClick = { onQuickNavigate(SpaceXRoute.CreativeStudio) },
                label = { Text("Image AI") }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onSpeak: () -> Unit,
    onSaveToLibrary: () -> Unit,
    onOpenInCodeStudio: (String, String) -> Unit,
    onOpenSourceUri: (String) -> Unit
) {
    val isUser = message.role == "user"
    var copiedRecently by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(2000L)
            copiedRecently = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .widthIn(max = 680.dp)
                .border(
                    1.dp,
                    if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!message.attachmentLabel.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "Attachment: ${message.attachmentLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (isUser) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    MarkdownText(
                        markdown = message.text,
                        onOpenInCodeStudio = onOpenInCodeStudio
                    )
                }

                if (message.groundingSources.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "LIVE SOURCES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        message.groundingSources.forEach { src ->
                            val parts = src.split(" | ")
                            val label = parts.firstOrNull() ?: src
                            val url = parts.getOrNull(1)
                            AssistChip(
                                onClick = { if (!url.isNullOrBlank()) onOpenSourceUri(url) },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }
        }

        // Action Bar below message
        Row(
            modifier = Modifier
                .padding(top = 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (isUser) {
                IconButton(
                    onClick = {
                        onCopy()
                        copiedRecently = true
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("copy_user_message_button")
                ) {
                    Icon(
                        if (copiedRecently) Icons.Rounded.Check else Icons.Outlined.ContentCopy,
                        contentDescription = "Copy prompt",
                        modifier = Modifier.size(16.dp),
                        tint = if (copiedRecently) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Outlined.Edit, "Edit prompt", Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        onCopy()
                        copiedRecently = true
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("copy_ai_response_button")
                ) {
                    Icon(
                        imageVector = if (copiedRecently) Icons.Rounded.Check else Icons.Outlined.ContentCopy,
                        contentDescription = "Copy AI response to clipboard",
                        modifier = Modifier.size(15.dp),
                        tint = if (copiedRecently) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (copiedRecently) "Copied" else "Copy",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                IconButton(
                    onClick = onRegenerate,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Outlined.Refresh, "Regenerate", Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = onSpeak,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Read aloud", Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = onSaveToLibrary,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Outlined.BookmarkAdd, "Save to Library", Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = onLike,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        if (message.feedback == "like") Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                        "Like",
                        Modifier.size(16.dp),
                        if (message.feedback == "like") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDislike,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        if (message.feedback == "dislike") Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                        "Dislike",
                        Modifier.size(16.dp),
                        if (message.feedback == "dislike") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AiTypingBubble() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_typing_indicator"),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 4.dp,
                bottomEnd = 18.dp
            ),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 18.dp
                    )
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RocketLaunch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "AI is typing...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TypingDotsIndicator()
            }
        }
    }
}

@Composable
private fun TypingDotsIndicator(
    dotSizeDp: Int = 7
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_typing_dots")
    val dotOffsets = listOf(0, 140, 280)

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dotOffsets.forEachIndexed { index, delayMs ->
            val progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(delayMs)
                ),
                label = "typing_dot_$index"
            )
            Box(
                modifier = Modifier
                    .size(dotSizeDp.dp)
                    .scale(0.78f + (0.32f * progress))
                    .alpha(0.35f + (0.65f * progress))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun StreamingStatusBubble(text: String) {
    Surface(
        shape = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = 4.dp,
            bottomEnd = 18.dp
        ),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 18.dp
                )
            )
            .testTag("ai_streaming_bubble")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SPACEX AI • TYPING",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TypingDotsIndicator(dotSizeDp = 5)
            }
            MarkdownText(markdown = text)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

