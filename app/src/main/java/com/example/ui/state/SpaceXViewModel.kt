package com.example.ui.state

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.AspectRatioOption
import com.example.ai.ImageResolutionOption
import com.example.ai.SpaceXAiEngine
import com.example.data.model.AiMemory
import com.example.data.model.AiProject
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.LibraryItem
import com.example.data.model.StudioProject
import com.example.data.model.UserProfile
import com.example.data.repository.SpaceXRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SpaceXViewModel"

class SpaceXViewModel(
    private val repository: SpaceXRepository,
    private val aiEngine: SpaceXAiEngine,
    val currentUserId: String
) : ViewModel() {

    private val _navigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val navigationEvents = _navigationEvents.asSharedFlow()

    private val _historyInspectionState = MutableStateFlow<HistoryInspectionState>(HistoryInspectionState.Idle)
    val historyInspectionState: StateFlow<HistoryInspectionState> = _historyInspectionState.asStateFlow()

    private val _historyCachedMessages = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val historyCachedMessages: StateFlow<Map<String, List<String>>> = _historyCachedMessages.asStateFlow()

    private val _isIndexingHistorySearch = MutableStateFlow(false)
    val isIndexingHistorySearch: StateFlow<Boolean> = _isIndexingHistorySearch.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    private val _activeProjectId = MutableStateFlow<String?>(null)
    private val _selectedModelId = MutableStateFlow("gemini-3.8-flash")
    private val _webSearchEnabled = MutableStateFlow(false)
    private val _mapsEnabled = MutableStateFlow(false)
    private val _highThinkingEnabled = MutableStateFlow(false)

    private val _generationState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    private val _mediaState = MutableStateFlow<MediaStudioState>(MediaStudioState.Idle)
    private val _selectedImageModel = MutableStateFlow("gemini-nano-banana-2.1")
    private val _selectedAspectRatio = MutableStateFlow(AspectRatioOption.SQUARE_1_1)
    private val _selectedResolution = MutableStateFlow(ImageResolutionOption.RES_1K)

    private val _codeStudio = MutableStateFlow(CodeStudioState())
    val codeStudio: StateFlow<CodeStudioState> = _codeStudio.asStateFlow()

    private val _websiteStudio = MutableStateFlow(
        BuilderStudioState(
            title = "SpaceX AI Site Preview",
            summary = "Describe a website above to generate a complete responsive web app."
        )
    )
    val websiteStudio: StateFlow<BuilderStudioState> = _websiteStudio.asStateFlow()

    private val _gameStudio = MutableStateFlow(
        BuilderStudioState(
            title = "Orbital Asteroids",
            summary = "Describe any 2D/WebGL game above or run the starter orbital game!",
            htmlCode = "<div class='hud'>Score: <span id='score'>0</span></div><canvas id='game'></canvas>",
            cssCode = "body{margin:0;overflow:hidden;background:#071426;color:#F5F8FC;font-family:sans-serif;}.hud{position:fixed;top:12px;left:16px;font-weight:700;color:#B7FF35;z-index:2;}canvas{display:block;width:100vw;height:100vh;}",
            jsCode = """
                const c=document.getElementById('game'),ctx=c.getContext('2d');
                function resize(){c.width=innerWidth;c.height=innerHeight;} resize(); addEventListener('resize',resize);
                let px=c.width/2,score=0,stars=[],obs=[];
                for(let i=0;i<40;i++)stars.push({x:Math.random()*c.width,y:Math.random()*c.height,s:Math.random()*2+1});
                c.addEventListener('pointermove',e=>{px=e.clientX;});
                c.addEventListener('pointerdown',e=>{px=e.clientX;});
                setInterval(()=>{obs.push({x:Math.random()*(c.width-30)+15,y:-20,r:14});},850);
                function loop(){
                  ctx.fillStyle='#071426';ctx.fillRect(0,0,c.width,c.height);
                  ctx.fillStyle='#96A8BE';stars.forEach(s=>{s.y+=s.s;if(s.y>c.height){s.y=0;s.x=Math.random()*c.width;}ctx.fillRect(s.x,s.y,2,2);});
                  ctx.fillStyle='#B7FF35';ctx.beginPath();ctx.moveTo(px,c.height-70);ctx.lineTo(px-16,c.height-40);ctx.lineTo(px+16,c.height-40);ctx.closePath();ctx.fill();
                  obs.forEach((o,i)=>{o.y+=3.2;ctx.fillStyle='#38BDF8';ctx.beginPath();ctx.arc(o.x,o.y,o.r,0,Math.PI*2);ctx.fill();
                    if(Math.hypot(o.x-px,o.y-(c.height-52))<26){score+=10;document.getElementById('score').textContent=score;obs.splice(i,1);}
                    else if(o.y>c.height+20){obs.splice(i,1);}
                  });
                  requestAnimationFrame(loop);
                }
                loop();
            """.trimIndent()
        )
    )
    val gameStudio: StateFlow<BuilderStudioState> = _gameStudio.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceSessionState())
    val voiceState: StateFlow<VoiceSessionState> = _voiceState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _profileLoaded = MutableStateFlow(false)

    private var activeChatJob: Job? = null
    private var activeVoiceJob: Job? = null
    private var historySearchIndexJob: Job? = null

    private val profileFlow = repository.observeProfile(currentUserId)
        .catch { err ->
            Log.e(TAG, "Profile stream error", err)
            _statusBanner.value = err.localizedMessage ?: "Failed to load profile"
            emit(null)
        }

    private val conversationsFlow = repository.observeConversations(currentUserId)
        .catch { err ->
            Log.e(TAG, "Conversations stream error", err)
            emit(emptyList())
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val messagesFlow = _activeConversationId
        .flatMapLatest { convId ->
            if (convId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeMessages(currentUserId, convId).catch { err ->
                Log.e(TAG, "Messages stream error", err)
                emit(emptyList())
            }
        }

    private val projectsFlow = repository.observeProjects(currentUserId)
        .catch { emit(emptyList()) }

    private val memoriesFlow = repository.observeMemories(currentUserId)
        .catch { emit(emptyList()) }

    private val libraryFlow = repository.observeLibraryItems(currentUserId)
        .catch { emit(emptyList()) }

    private val studioProjectsFlow = repository.observeStudioProjects(currentUserId)
        .catch { emit(emptyList()) }

    val workspace: StateFlow<SpaceXWorkspaceSnapshot> = combine(
        profileFlow,
        conversationsFlow,
        _activeConversationId,
        messagesFlow,
        projectsFlow,
        _activeProjectId,
        memoriesFlow,
        libraryFlow,
        studioProjectsFlow,
        _selectedModelId,
        _webSearchEnabled,
        _mapsEnabled,
        _highThinkingEnabled,
        _generationState,
        _mediaState,
        _selectedImageModel,
        _selectedAspectRatio,
        _selectedResolution,
        _profileLoaded
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val profile = values[0] as UserProfile?
        @Suppress("UNCHECKED_CAST")
        val conversations = values[1] as List<Conversation>
        val activeConvId = values[2] as String?
        @Suppress("UNCHECKED_CAST")
        val messages = values[3] as List<ChatMessage>
        @Suppress("UNCHECKED_CAST")
        val projects = values[4] as List<AiProject>
        val activeProjId = values[5] as String?
        @Suppress("UNCHECKED_CAST")
        val memories = values[6] as List<AiMemory>
        @Suppress("UNCHECKED_CAST")
        val library = values[7] as List<LibraryItem>
        @Suppress("UNCHECKED_CAST")
        val studioProjects = values[8] as List<StudioProject>
        val modelId = values[9] as String
        val webSearch = values[10] as Boolean
        val mapsEnabled = values[11] as Boolean
        val highThinking = values[12] as Boolean
        val genState = values[13] as GenerationUiState
        val mediaState = values[14] as MediaStudioState
        val imgModel = values[15] as String
        val ratio = values[16] as AspectRatioOption
        val res = values[17] as ImageResolutionOption
        val profileLoaded = values[18] as Boolean

        SpaceXWorkspaceSnapshot(
            profile = profile,
            profileLoaded = profileLoaded,
            conversations = conversations,
            activeConversationId = activeConvId,
            messages = messages,
            projects = projects,
            activeProjectId = activeProjId,
            memories = memories,
            libraryItems = library,
            studioProjects = studioProjects,
            selectedModelId = modelId,
            webSearchEnabled = webSearch,
            mapsGroundingEnabled = mapsEnabled,
            highThinkingEnabled = highThinking,
            generationState = genState,
            mediaState = mediaState,
            selectedImageModel = imgModel,
            selectedAspectRatio = ratio,
            selectedResolution = res
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = SpaceXWorkspaceSnapshot()
    )

    init {
        viewModelScope.launch {
            profileFlow.collect { prof ->
                _profileLoaded.value = true
                if (prof != null) {
                    _selectedModelId.value = prof.selectedModel
                }
            }
        }
    }

    fun navigateTo(route: SpaceXRoute) {
        _navigationEvents.tryEmit(route.route)
    }

    fun navigateToRoute(route: String) {
        _navigationEvents.tryEmit(route)
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(msg: String) {
        _statusBanner.value = msg
    }

    // --- Onboarding & Profile ---

    fun completeOnboarding(
        displayName: String,
        username: String,
        bio: String?,
        email: String?,
        photoUrl: String?,
        language: String,
        responseStyle: String,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.saveOnboardingProfile(
                userId = currentUserId,
                displayName = displayName,
                username = username,
                bio = bio,
                email = email,
                photoUrl = photoUrl,
                language = language,
                responseStyle = responseStyle
            )
            res.onSuccess {
                onDone()
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to save onboarding profile."
            }
        }
    }

    fun updateProfileSettings(
        displayName: String? = null,
        username: String? = null,
        bio: String? = null,
        language: String? = null,
        responseStyle: String? = null,
        customSystemPrompt: String? = null,
        memoryEnabled: Boolean? = null,
        darkMode: Boolean? = null,
        selectedModel: String? = null
    ) {
        selectedModel?.let { _selectedModelId.value = it }
        viewModelScope.launch {
            repository.updateProfilePreferences(
                userId = currentUserId,
                displayName = displayName,
                username = username,
                bio = bio,
                language = language,
                responseStyle = responseStyle,
                customSystemPrompt = customSystemPrompt,
                memoryEnabled = memoryEnabled,
                darkMode = darkMode,
                selectedModel = selectedModel
            ).onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to update settings."
            }
        }
    }

    // --- Chat & Conversations ---

    fun selectModel(modelId: String) {
        _selectedModelId.value = modelId
        updateProfileSettings(selectedModel = modelId)
    }

    fun toggleWebSearch() {
        _webSearchEnabled.value = !_webSearchEnabled.value
    }

    fun toggleMapsGrounding() {
        _mapsEnabled.value = !_mapsEnabled.value
    }

    fun toggleHighThinking() {
        _highThinkingEnabled.value = !_highThinkingEnabled.value
    }

    fun startNewChat(projectId: String? = null) {
        activeChatJob?.cancel()
        _generationState.value = GenerationUiState.Idle
        _activeConversationId.value = null
        if (projectId != null) {
            _activeProjectId.value = projectId
        }
        _navigationEvents.tryEmit(SpaceXRoute.Chat.route)
    }

    fun selectConversation(conversationId: String) {
        activeChatJob?.cancel()
        _generationState.value = GenerationUiState.Idle
        _activeConversationId.value = conversationId
        _navigationEvents.tryEmit(SpaceXRoute.Chat.route)
    }

    fun selectProject(projectId: String?) {
        _activeProjectId.value = projectId
    }

    fun sendChatMessage(
        prompt: String,
        attachedImage: Bitmap? = null,
        attachedDocumentText: String? = null,
        attachedDocumentName: String? = null,
        attachedAudioBytes: ByteArray? = null,
        attachedAudioMimeType: String? = null,
        attachedAudioLabel: String? = null
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty() && attachedImage == null && attachedDocumentText.isNullOrBlank() && attachedAudioBytes == null) return

        val snap = workspace.value
        val modelId = snap.selectedModelId
        val activeProject = snap.projects.firstOrNull { it.id == snap.activeProjectId }
        val existingHistory = snap.messages

        activeChatJob?.cancel()
        activeChatJob = viewModelScope.launch {
            _generationState.value = GenerationUiState.Loading
            try {
                var convId = _activeConversationId.value
                if (convId.isNullOrBlank()) {
                    val title = cleanPrompt.take(48).ifEmpty {
                        attachedAudioLabel ?: "Media Analysis"
                    }
                    val createRes = repository.createConversation(
                        userId = currentUserId,
                        title = title,
                        modelId = modelId,
                        projectId = snap.activeProjectId
                    )
                    convId = createRes.getOrThrow()
                    _activeConversationId.value = convId
                }

                val attachmentParts = buildList {
                    if (attachedImage != null) add("Image")
                    if (!attachedDocumentName.isNullOrBlank()) add(attachedDocumentName)
                    if (!attachedAudioLabel.isNullOrBlank()) add(attachedAudioLabel)
                }
                val attachmentLabel = attachmentParts.joinToString(" + ").takeIf { it.isNotEmpty() }

                val userMsg = ChatMessage(
                    userId = currentUserId,
                    role = "user",
                    text = cleanPrompt.ifEmpty {
                        if (attachedAudioBytes != null) "Listen to the attached voice memo and respond."
                        else "Analyze attached item."
                    },
                    modelId = modelId,
                    attachmentLabel = attachmentLabel
                )
                repository.addMessage(currentUserId, convId, userMsg).getOrThrow()

                var finalReply = ""
                var finalThoughts: String? = null
                var finalSources: List<String> = emptyList()

                val effectivePrompt = cleanPrompt.ifEmpty {
                    if (attachedAudioBytes != null) {
                        "Listen carefully to this voice memo. Respond to the user's request or summarize and reply helpfully."
                    } else {
                        "Analyze the attached content in detail."
                    }
                }

                aiEngine.streamChatReply(
                    modelId = modelId,
                    prompt = effectivePrompt,
                    historyMessages = existingHistory,
                    attachedImage = attachedImage,
                    attachedDocumentText = attachedDocumentText,
                    attachedDocumentName = attachedDocumentName,
                    profile = snap.profile,
                    project = activeProject,
                    memories = snap.memories,
                    useWebSearch = snap.webSearchEnabled,
                    useMaps = snap.mapsGroundingEnabled,
                    useHighThinking = snap.highThinkingEnabled,
                    attachedAudioBytes = attachedAudioBytes,
                    attachedAudioMimeType = attachedAudioMimeType
                ).collect { chunk ->
                    finalReply = chunk.accumulatedText
                    finalThoughts = chunk.thinkingSummary
                    finalSources = chunk.groundingSources
                    _generationState.value = GenerationUiState.Streaming(
                        text = finalReply,
                        thinkingSummary = finalThoughts,
                        groundingSources = finalSources
                    )
                }

                if (finalReply.isBlank()) {
                    _generationState.value = GenerationUiState.Error("Model returned an empty response.")
                    return@launch
                }

                val modelMsg = ChatMessage(
                    userId = currentUserId,
                    role = "model",
                    text = finalReply,
                    modelId = modelId,
                    thinkingSummary = finalThoughts,
                    groundingSources = finalSources
                )
                repository.addMessage(currentUserId, convId, modelMsg)
                repository.updateConversationMeta(
                    userId = currentUserId,
                    conversationId = convId,
                    lastMessagePreview = finalReply.take(140),
                    messageCount = existingHistory.size.toLong() + 2L
                )
                _generationState.value = GenerationUiState.Idle
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat generation timed out", e)
                _generationState.value = GenerationUiState.Error("Generation timed out after 60 seconds. Please retry.")
            } catch (e: CancellationException) {
                _generationState.value = GenerationUiState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat generation failed", e)
                _generationState.value = GenerationUiState.Error(e.localizedMessage ?: "AI generation failed.")
            }
        }
    }

    fun stopGeneration() {
        activeChatJob?.cancel()
        activeChatJob = null
        _generationState.value = GenerationUiState.Idle
    }

    fun regenerateLastResponse() {
        val lastUserMsg = workspace.value.messages.lastOrNull { it.role == "user" } ?: return
        sendChatMessage(lastUserMsg.text)
    }

    fun editUserMessageAndResend(message: ChatMessage, newText: String) {
        val convId = _activeConversationId.value ?: return
        viewModelScope.launch {
            repository.updateMessage(currentUserId, convId, message.id, text = newText)
            sendChatMessage(newText)
        }
    }

    fun setMessageFeedback(message: ChatMessage, feedback: String) {
        val convId = _activeConversationId.value ?: return
        viewModelScope.launch {
            repository.updateMessage(currentUserId, convId, message.id, feedback = feedback)
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateConversationMeta(currentUserId, conversationId, title = newTitle)
        }
    }

    fun togglePinConversation(conv: Conversation) {
        viewModelScope.launch {
            repository.updateConversationMeta(currentUserId, conv.id, pinned = !conv.pinned)
        }
    }

    fun toggleArchiveConversation(conv: Conversation) {
        viewModelScope.launch {
            repository.updateConversationMeta(currentUserId, conv.id, archived = !conv.archived)
        }
    }

    fun duplicateConversation(conv: Conversation) {
        viewModelScope.launch {
            val msgs = if (_activeConversationId.value == conv.id) {
                workspace.value.messages
            } else {
                repository.fetchConversationMessages(currentUserId, conv.id).getOrDefault(emptyList())
            }
            repository.duplicateConversation(currentUserId, conv, msgs)
                .onSuccess { newId ->
                    _activeConversationId.value = newId
                    _statusBanner.value = "Conversation duplicated."
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Failed to duplicate conversation."
                }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
            }
            repository.deleteConversation(currentUserId, conversationId)
                .onSuccess { _statusBanner.value = "Conversation deleted." }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Failed to delete conversation."
                }
        }
    }

    fun deleteConversationsBatch(conversationIds: List<String>) {
        if (conversationIds.isEmpty()) return
        viewModelScope.launch {
            if (_activeConversationId.value in conversationIds) {
                _activeConversationId.value = null
            }
            repository.deleteConversationsBatch(currentUserId, conversationIds)
                .onSuccess {
                    _statusBanner.value = "Deleted ${conversationIds.size} conversation(s)."
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Failed to delete selected conversations."
                }
        }
    }

    fun batchSetArchived(conversationIds: List<String>, archived: Boolean) {
        if (conversationIds.isEmpty()) return
        viewModelScope.launch {
            var failures = 0
            for (id in conversationIds) {
                repository.updateConversationMeta(currentUserId, id, archived = archived)
                    .onFailure { failures++ }
            }
            _statusBanner.value = if (failures == 0) {
                if (archived) "Archived ${conversationIds.size} conversation(s)."
                else "Restored ${conversationIds.size} conversation(s)."
            } else {
                "Updated with $failures error(s)."
            }
        }
    }

    fun batchSetPinned(conversationIds: List<String>, pinned: Boolean) {
        if (conversationIds.isEmpty()) return
        viewModelScope.launch {
            var failures = 0
            for (id in conversationIds) {
                repository.updateConversationMeta(currentUserId, id, pinned = pinned)
                    .onFailure { failures++ }
            }
            _statusBanner.value = if (failures == 0) {
                if (pinned) "Pinned ${conversationIds.size} conversation(s)."
                else "Unpinned ${conversationIds.size} conversation(s)."
            } else {
                "Updated with $failures error(s)."
            }
        }
    }

    fun loadConversationHistoryDetail(conversationId: String) {
        viewModelScope.launch {
            _historyInspectionState.value = HistoryInspectionState.Loading
            val conv = workspace.value.conversations.firstOrNull { it.id == conversationId }
                ?: Conversation(id = conversationId, userId = currentUserId, title = "Conversation")
            repository.fetchConversationMessages(currentUserId, conversationId)
                .onSuccess { msgs ->
                    _historyCachedMessages.update { it + (conversationId to msgs.map { m -> m.text }) }
                    _historyInspectionState.value = HistoryInspectionState.Success(conv, msgs)
                }
                .onFailure { err ->
                    _historyInspectionState.value = HistoryInspectionState.Error(
                        err.localizedMessage ?: "Failed to load conversation messages from Firestore."
                    )
                }
        }
    }

    fun onHistorySearchQueryChanged(query: String) {
        historySearchIndexJob?.cancel()
        if (query.trim().length < 2) {
            _isIndexingHistorySearch.value = false
            return
        }
        historySearchIndexJob = viewModelScope.launch {
            delay(220L)
            val conversationsToIndex = workspace.value.conversations
                .asSequence()
                .filter { conv -> !_historyCachedMessages.value.containsKey(conv.id) }
                .take(25)
                .toList()
            if (conversationsToIndex.isEmpty()) {
                _isIndexingHistorySearch.value = false
                return@launch
            }
            _isIndexingHistorySearch.value = true
            try {
                conversationsToIndex.forEach { conv ->
                    repository.fetchConversationMessages(currentUserId, conv.id)
                        .onSuccess { msgs ->
                            _historyCachedMessages.update { current ->
                                current + (conv.id to msgs.map { it.text }.filter { it.isNotBlank() })
                            }
                        }
                }
            } finally {
                _isIndexingHistorySearch.value = false
            }
        }
    }

    fun prepareConversationTranscript(conv: Conversation, onReady: (String, String) -> Unit) {
        viewModelScope.launch {
            val msgs = if (_activeConversationId.value == conv.id && workspace.value.messages.isNotEmpty()) {
                workspace.value.messages
            } else {
                repository.fetchConversationMessages(currentUserId, conv.id).getOrDefault(emptyList())
            }
            val body = if (msgs.isNotEmpty()) {
                buildString {
                    appendLine("# ${conv.title}")
                    appendLine("Model: ${conv.modelId}")
                    appendLine()
                    msgs.forEach { msg ->
                        appendLine("${msg.role.uppercase()}:")
                        appendLine(msg.text)
                        appendLine()
                    }
                }
            } else {
                "${conv.title}\n\n${conv.lastMessagePreview}"
            }
            onReady(conv.title, body)
        }
    }

    fun speakTextWithGeminiTts(text: String, voiceName: String = "Kore") {
        viewModelScope.launch {
            try {
                _statusBanner.value = "Synthesizing Gemini TTS ($voiceName)..."
                val pcm = aiEngine.synthesizeSpeechPcm(text, voiceName)
                _statusBanner.value = null
                aiEngine.playPcmAudio(pcm)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "TTS failed", e)
                _statusBanner.value = "TTS failed: ${e.localizedMessage}"
            }
        }
    }

    fun transcribeRecordedAudio(
        audioBytes: ByteArray,
        mimeType: String,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val transcript = aiEngine.transcribeAudio(audioBytes, mimeType)
                onResult(transcript)
                _statusBanner.value = "Audio memo transcribed."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio transcription failed", e)
                _statusBanner.value = e.localizedMessage ?: "Audio transcription failed."
                onResult(null)
            }
        }
    }

    // --- Code Studio ---

    fun updateCodeStudio(transform: (CodeStudioState) -> CodeStudioState) {
        _codeStudio.update(transform)
    }

    fun openCodeInStudio(title: String, language: String, code: String) {
        _codeStudio.update { current ->
            when (language.lowercase()) {
                "css" -> current.copy(projectName = title, cssCode = code, activeTab = 1)
                "javascript", "js" -> current.copy(projectName = title, jsCode = code, activeTab = 2)
                else -> current.copy(projectName = title, language = language, htmlCode = code, activeTab = 0)
            }
        }
        _navigationEvents.tryEmit(SpaceXRoute.CodeStudio.route)
    }

    fun runCodeAssistant(action: String, instruction: String) {
        val state = _codeStudio.value
        val combinedCode = buildString {
            appendLine("// HTML / Main")
            appendLine(state.htmlCode)
            appendLine("// CSS")
            appendLine(state.cssCode)
            appendLine("// JavaScript")
            appendLine(state.jsCode)
        }
        viewModelScope.launch {
            _codeStudio.update { it.copy(isBusy = true, error = null) }
            try {
                val reply = aiEngine.generateCodeAssistantReply(
                    actionLabel = action,
                    language = state.language,
                    currentCode = combinedCode,
                    userInstruction = instruction
                )
                _codeStudio.update {
                    it.copy(
                        isBusy = false,
                        aiAssistantOutput = reply,
                        consoleLogs = it.consoleLogs + "AI $action completed."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _codeStudio.update { it.copy(isBusy = false, error = e.localizedMessage ?: "AI Assistant failed.") }
            }
        }
    }

    fun saveCodeStudioProject() {
        val s = _codeStudio.value
        viewModelScope.launch {
            repository.saveStudioProject(
                userId = currentUserId,
                studioId = s.activeProjectId,
                name = s.projectName,
                studioType = "code",
                language = s.language,
                primaryCode = s.htmlCode,
                cssCode = s.cssCode,
                jsCode = s.jsCode
            ).onSuccess { id ->
                _codeStudio.update { it.copy(activeProjectId = id) }
                _statusBanner.value = "Code project saved to cloud."
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Save failed."
            }
        }
    }

    fun loadStudioProject(project: StudioProject) {
        when (project.studioType) {
            "website" -> {
                _websiteStudio.value = BuilderStudioState(
                    activeProjectId = project.id,
                    title = project.name,
                    summary = project.notes ?: "Loaded from cloud",
                    htmlCode = project.primaryCode,
                    cssCode = project.cssCode,
                    jsCode = project.jsCode
                )
                _navigationEvents.tryEmit(SpaceXRoute.WebsiteBuilder.route)
            }
            "game" -> {
                _gameStudio.value = BuilderStudioState(
                    activeProjectId = project.id,
                    title = project.name,
                    summary = project.notes ?: "Loaded from cloud",
                    htmlCode = project.primaryCode,
                    cssCode = project.cssCode,
                    jsCode = project.jsCode
                )
                _navigationEvents.tryEmit(SpaceXRoute.GameStudio.route)
            }
            else -> {
                _codeStudio.update {
                    it.copy(
                        activeProjectId = project.id,
                        projectName = project.name,
                        language = project.language,
                        htmlCode = project.primaryCode,
                        cssCode = project.cssCode,
                        jsCode = project.jsCode
                    )
                }
                _navigationEvents.tryEmit(SpaceXRoute.CodeStudio.route)
            }
        }
    }

    // --- Website & Game Builders ---

    fun generateWebsite(prompt: String) {
        viewModelScope.launch {
            _websiteStudio.update { it.copy(isBusy = true, error = null) }
            try {
                val bundle = aiEngine.generateWebOrGameBundle("website", prompt)
                _websiteStudio.update {
                    it.copy(
                        title = bundle.title,
                        summary = bundle.summary,
                        htmlCode = bundle.htmlCode,
                        cssCode = bundle.cssCode,
                        jsCode = bundle.jsCode,
                        isBusy = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _websiteStudio.update {
                    it.copy(isBusy = false, error = e.localizedMessage ?: "Website generation failed.")
                }
            }
        }
    }

    fun generateGame(prompt: String) {
        viewModelScope.launch {
            _gameStudio.update { it.copy(isBusy = true, error = null) }
            try {
                val bundle = aiEngine.generateWebOrGameBundle("game", prompt)
                _gameStudio.update {
                    it.copy(
                        title = bundle.title,
                        summary = bundle.summary,
                        htmlCode = bundle.htmlCode,
                        cssCode = bundle.cssCode,
                        jsCode = bundle.jsCode,
                        isBusy = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _gameStudio.update {
                    it.copy(isBusy = false, error = e.localizedMessage ?: "Game generation failed.")
                }
            }
        }
    }

    fun updateWebsiteCode(html: String? = null, css: String? = null, js: String? = null, desktop: Boolean? = null) {
        _websiteStudio.update {
            it.copy(
                htmlCode = html ?: it.htmlCode,
                cssCode = css ?: it.cssCode,
                jsCode = js ?: it.jsCode,
                isDesktopPreview = desktop ?: it.isDesktopPreview
            )
        }
    }

    fun updateGameCode(html: String? = null, css: String? = null, js: String? = null) {
        _gameStudio.update {
            it.copy(
                htmlCode = html ?: it.htmlCode,
                cssCode = css ?: it.cssCode,
                jsCode = js ?: it.jsCode
            )
        }
    }

    fun saveBuilderProject(isGame: Boolean) {
        val state = if (isGame) _gameStudio.value else _websiteStudio.value
        val type = if (isGame) "game" else "website"
        viewModelScope.launch {
            repository.saveStudioProject(
                userId = currentUserId,
                studioId = state.activeProjectId,
                name = state.title.ifBlank { if (isGame) "Orbital Game" else "Orbital Site" },
                studioType = type,
                language = "HTML5/CSS/JS",
                primaryCode = state.htmlCode,
                cssCode = state.cssCode,
                jsCode = state.jsCode,
                notes = state.summary
            ).onSuccess { id ->
                if (isGame) _gameStudio.update { it.copy(activeProjectId = id) }
                else _websiteStudio.update { it.copy(activeProjectId = id) }
                _statusBanner.value = "${if (isGame) "Game" else "Website"} saved to your cloud workspace."
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to save project."
            }
        }
    }

    // --- Creative Media Studio ---

    fun setImageModel(modelId: String) {
        _selectedImageModel.value = modelId
    }

    fun setAspectRatio(ratio: AspectRatioOption) {
        _selectedAspectRatio.value = ratio
    }

    fun setImageResolution(res: ImageResolutionOption) {
        _selectedResolution.value = res
    }

    fun generateOrEditImage(prompt: String, sourceBitmap: Bitmap?) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _mediaState.value = MediaStudioState.Loading
            try {
                val bmp = aiEngine.generateOrEditImage(
                    promptText = prompt,
                    sourceBitmap = sourceBitmap,
                    modelId = _selectedImageModel.value,
                    aspectRatio = _selectedAspectRatio.value,
                    imageSize = _selectedResolution.value
                )
                _mediaState.value = MediaStudioState.ImageSuccess(bmp, prompt)
            } catch (e: TimeoutCancellationException) {
                _mediaState.value = MediaStudioState.Error("Image generation timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation error", e)
                _mediaState.value = MediaStudioState.Error(e.localizedMessage ?: "Image generation failed.")
            }
        }
    }

    fun analyzeImage(bitmap: Bitmap, prompt: String) {
        viewModelScope.launch {
            _mediaState.value = MediaStudioState.Loading
            try {
                val text = aiEngine.analyzeVisualContent(bitmap, prompt)
                _mediaState.value = MediaStudioState.AnalysisSuccess(text, "Visual Intelligence Analysis")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mediaState.value = MediaStudioState.Error(e.localizedMessage ?: "Image analysis failed.")
            }
        }
    }

    fun analyzeMediaBytes(bytes: ByteArray, mimeType: String, prompt: String, title: String) {
        viewModelScope.launch {
            _mediaState.value = MediaStudioState.Loading
            try {
                val text = aiEngine.analyzeDocumentOrVideoBytes(bytes, mimeType, prompt)
                _mediaState.value = MediaStudioState.AnalysisSuccess(text, title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mediaState.value = MediaStudioState.Error(e.localizedMessage ?: "Media analysis failed.")
            }
        }
    }

    // --- Voice AI ---

    fun setVoiceListening(listening: Boolean) {
        _voiceState.update { it.copy(isListening = listening, error = null) }
    }

    fun selectVoicePersona(voice: String) {
        _voiceState.update { it.copy(selectedVoice = voice) }
    }

    fun sendVoiceTurn(transcriptText: String) {
        if (transcriptText.isBlank()) return
        val snap = workspace.value
        val voice = _voiceState.value.selectedVoice
        activeVoiceJob?.cancel()
        activeVoiceJob = viewModelScope.launch {
            _voiceState.update {
                it.copy(isListening = false, isThinking = true, transcript = transcriptText, error = null)
            }
            try {
                val replyAcc = StringBuilder()
                aiEngine.streamChatReply(
                    modelId = "gemini-3.8-flash",
                    prompt = transcriptText,
                    historyMessages = emptyList(),
                    attachedImage = null,
                    attachedDocumentText = null,
                    attachedDocumentName = null,
                    profile = snap.profile,
                    project = null,
                    memories = snap.memories,
                    useWebSearch = false,
                    useMaps = false,
                    useHighThinking = false
                ).collect { chunk ->
                    replyAcc.clear()
                    replyAcc.append(chunk.accumulatedText)
                    _voiceState.update { it.copy(lastResponse = chunk.accumulatedText) }
                }

                val finalReply = replyAcc.toString()
                _voiceState.update { it.copy(isThinking = false, isSpeaking = true, lastResponse = finalReply) }
                val pcm = aiEngine.synthesizeSpeechPcm(finalReply, voice)
                aiEngine.playPcmAudio(pcm)
                _voiceState.update { it.copy(isSpeaking = false) }
            } catch (e: CancellationException) {
                _voiceState.update { it.copy(isThinking = false, isSpeaking = false) }
                throw e
            } catch (e: Exception) {
                _voiceState.update {
                    it.copy(
                        isThinking = false,
                        isSpeaking = false,
                        error = e.localizedMessage ?: "Voice AI failed."
                    )
                }
            }
        }
    }

    fun stopVoiceSession() {
        activeVoiceJob?.cancel()
        _voiceState.update { it.copy(isListening = false, isThinking = false, isSpeaking = false) }
    }

    // --- Projects, Memory, Library, Export ---

    fun saveProject(projectId: String? = null, name: String, category: String, instructions: String, description: String) {
        viewModelScope.launch {
            repository.saveProject(currentUserId, projectId, name, category, instructions, description)
                .onSuccess { _statusBanner.value = "Project saved." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to save project." }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            if (_activeProjectId.value == projectId) _activeProjectId.value = null
            repository.deleteProject(currentUserId, projectId)
        }
    }

    fun saveMemory(memoryId: String? = null, content: String, category: String) {
        viewModelScope.launch {
            repository.saveMemory(currentUserId, memoryId, content, category)
                .onSuccess { _statusBanner.value = "Memory saved." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to save memory." }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            repository.deleteMemory(currentUserId, memoryId)
        }
    }

    fun saveLibraryItem(
        itemId: String? = null,
        title: String,
        folder: String,
        itemType: String,
        content: String,
        language: String = "markdown"
    ) {
        viewModelScope.launch {
            repository.saveLibraryItem(currentUserId, itemId, title, folder, itemType, content, language)
                .onSuccess { _statusBanner.value = "Saved to Library." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to save library item." }
        }
    }

    fun deleteLibraryItem(itemId: String) {
        viewModelScope.launch {
            repository.deleteLibraryItem(currentUserId, itemId)
        }
    }

    fun exportAllUserData(onJsonReady: (String) -> Unit) {
        viewModelScope.launch {
            repository.exportUserDataJson(currentUserId)
                .onSuccess { json -> onJsonReady(json) }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Export failed." }
        }
    }

    companion object {
        fun provideFactory(
            repository: SpaceXRepository,
            aiEngine: SpaceXAiEngine,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SpaceXViewModel(repository, aiEngine, currentUserId)
            }
        }
    }
}
