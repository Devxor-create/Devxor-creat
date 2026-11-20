package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ai.SpaceXAiEngine
import com.example.core.AppThemeMode
import com.example.core.ConversationExporter
import com.example.core.ThemePreferencesManager
import com.example.core.attemptAutoSignIn
import com.example.core.authStateFlow
import com.example.core.configureAppCheck
import com.example.core.signOutUser
import com.example.data.repository.SpaceXRepository
import com.example.ui.components.SpaceXDrawerContent
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CodeStudioScreen
import com.example.ui.screens.ConversationHistoryDetailScreen
import com.example.ui.screens.ConversationHistoryScreen
import com.example.ui.screens.CreativeStudioScreen
import com.example.ui.screens.HelpSupportScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolsAndModelsScreen
import com.example.ui.screens.VoiceAiScreen
import com.example.ui.screens.WebsiteOrGameStudioScreen
import com.example.ui.state.SpaceXRoute
import com.example.ui.state.SpaceXViewModel
import com.example.ui.theme.SpaceXAiTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingDeepLinkConversationId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)
        pendingDeepLinkConversationId = ConversationExporter.parseDeepLinkConversationId(intent)

        enableEdgeToEdge()
        setContent {
            SpaceXAiRoot(
                deepLinkConversationId = pendingDeepLinkConversationId,
                onConsumeDeepLink = { pendingDeepLinkConversationId = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ConversationExporter.parseDeepLinkConversationId(intent)?.let { convId ->
            pendingDeepLinkConversationId = convId
        }
    }
}

@Composable
fun SpaceXAiRoot(
    deepLinkConversationId: String? = null,
    onConsumeDeepLink: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val themePreferences = remember(context) { ThemePreferencesManager(context.applicationContext) }
    val themeMode by themePreferences.themeModeFlow
        .collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)
    val systemDarkTheme = isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> systemDarkTheme
    }

    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        SpaceXAiTheme(darkTheme = isDarkTheme) {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedSpaceXShell(
                user = user,
                themeMode = themeMode,
                isDarkTheme = isDarkTheme,
                onSelectThemeMode = { selectedMode ->
                    scope.launch { themePreferences.setThemeMode(selectedMode) }
                },
                deepLinkConversationId = deepLinkConversationId,
                onConsumeDeepLink = onConsumeDeepLink,
                onSignOut = { beforeSignOut ->
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope,
                        onBeforeAuthSignOut = beforeSignOut
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedSpaceXShell(
    user: FirebaseUser,
    themeMode: AppThemeMode,
    isDarkTheme: Boolean,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    deepLinkConversationId: String?,
    onConsumeDeepLink: () -> Unit,
    onSignOut: (onBeforeSignOut: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(appContext) { SpaceXRepository(appContext) }
    val aiEngine = remember { SpaceXAiEngine() }
    val viewModel: SpaceXViewModel = viewModel(
        key = user.uid,
        factory = SpaceXViewModel.provideFactory(repository, aiEngine, user.uid)
    )

    val snapshot by viewModel.workspace.collectAsStateWithLifecycle()
    val codeStudioState by viewModel.codeStudio.collectAsStateWithLifecycle()
    val websiteStudioState by viewModel.websiteStudio.collectAsStateWithLifecycle()
    val gameStudioState by viewModel.gameStudio.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val historyInspectionState by viewModel.historyInspectionState.collectAsStateWithLifecycle()
    val historyCachedMessages by viewModel.historyCachedMessages.collectAsStateWithLifecycle()
    val isIndexingHistorySearch by viewModel.isIndexingHistorySearch.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    fun shareTextContent(title: String, content: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "Share from SpaceX AI"))
    }

    SpaceXAiTheme(darkTheme = isDarkTheme) {
        if (!snapshot.profileLoaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@SpaceXAiTheme
        }

        val profile = snapshot.profile
        if (profile == null || !profile.onboardingCompleted) {
            OnboardingScreen(
                initialDisplayName = user.displayName ?: "Commander",
                initialEmail = user.email,
                initialPhotoUrl = user.photoUrl?.toString(),
                onCompleteOnboarding = { displayName, username, bio, language, style ->
                    viewModel.completeOnboarding(
                        displayName = displayName,
                        username = username,
                        bio = bio,
                        email = user.email,
                        photoUrl = user.photoUrl?.toString(),
                        language = language,
                        responseStyle = style,
                        onDone = {}
                    )
                }
            )
            return@SpaceXAiTheme
        }

        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestinationRoute = navBackStackEntry?.destination?.route
        val isHistoryDetail = currentDestinationRoute == SpaceXRoute.ROUTE_HISTORY_DETAIL
        val currentRoute = SpaceXRoute.fromRoute(currentDestinationRoute)

        fun navigateTopLevel(targetRoute: String) {
            navController.navigate(targetRoute) {
                popUpTo(SpaceXRoute.Chat.route) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        LaunchedEffect(navController) {
            viewModel.navigationEvents.collect { targetRoute ->
                if (targetRoute.startsWith("history/") && targetRoute != SpaceXRoute.History.route) {
                    navController.navigate(targetRoute) {
                        launchSingleTop = true
                    }
                } else {
                    navigateTopLevel(targetRoute)
                }
            }
        }

        LaunchedEffect(deepLinkConversationId, profile.onboardingCompleted) {
            val targetId = deepLinkConversationId
            if (!targetId.isNullOrBlank() && profile.onboardingCompleted) {
                viewModel.selectConversation(targetId)
                onConsumeDeepLink()
            }
        }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        val drawerComposable: @Composable () -> Unit = {
            SpaceXDrawerContent(
                currentRoute = currentRoute,
                profile = profile,
                conversations = snapshot.conversations,
                activeConversationId = snapshot.activeConversationId,
                onNewChat = {
                    viewModel.startNewChat()
                    scope.launch { drawerState.close() }
                },
                onSelectConversation = { id ->
                    viewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onRenameConversation = { conv, newTitle ->
                    viewModel.renameConversation(conv.id, newTitle)
                },
                onTogglePin = { viewModel.togglePinConversation(it) },
                onToggleArchive = { viewModel.toggleArchiveConversation(it) },
                onDuplicate = { viewModel.duplicateConversation(it) },
                onShareConversation = { conv ->
                    viewModel.prepareConversationTranscript(conv) { title, body ->
                        shareTextContent(title, body)
                    }
                },
                onDeleteConversation = { viewModel.deleteConversation(it.id) },
                onNavigate = { route ->
                    navigateTopLevel(route.route)
                    scope.launch { drawerState.close() }
                }
            )
        }

        val mainScaffold: @Composable (showMenuButton: Boolean) -> Unit = { showMenuButton ->
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        navigationIcon = {
                            if (isHistoryDetail) {
                                IconButton(
                                    onClick = { navController.popBackStack() },
                                    modifier = Modifier.testTag("history_detail_back_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to history")
                                }
                            } else if (showMenuButton) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("open_drawer_button")
                                ) {
                                    Icon(Icons.Rounded.Menu, contentDescription = "Open sidebar")
                                }
                            }
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isHistoryDetail) "Conversation Transcript" else currentRoute.label,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { navigateTopLevel(SpaceXRoute.History.route) },
                                modifier = Modifier.testTag("topbar_history_button")
                            ) {
                                Icon(
                                    Icons.Outlined.History,
                                    contentDescription = "Conversation history",
                                    tint = if (currentRoute == SpaceXRoute.History) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            IconButton(
                                onClick = { viewModel.startNewChat() },
                                modifier = Modifier.testTag("topbar_new_chat_button")
                            ) {
                                Icon(
                                    Icons.Rounded.AddComment,
                                    contentDescription = "New chat",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = SpaceXRoute.Chat.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    composable(SpaceXRoute.Chat.route) {
                        ChatScreen(
                            snapshot = snapshot,
                            onSendMessage = { p, img, docT, docN, audioBytes, audioMime, audioLabel ->
                                viewModel.sendChatMessage(
                                    prompt = p,
                                    attachedImage = img,
                                    attachedDocumentText = docT,
                                    attachedDocumentName = docN,
                                    attachedAudioBytes = audioBytes,
                                    attachedAudioMimeType = audioMime,
                                    attachedAudioLabel = audioLabel
                                )
                            },
                            onStopGeneration = { viewModel.stopGeneration() },
                            onSelectModel = { viewModel.selectModel(it) },
                            onToggleWebSearch = { viewModel.toggleWebSearch() },
                            onToggleMaps = { viewModel.toggleMapsGrounding() },
                            onToggleHighThinking = { viewModel.toggleHighThinking() },
                            onRegenerate = { viewModel.regenerateLastResponse() },
                            onEditMessage = { msg, txt -> viewModel.editUserMessageAndResend(msg, txt) },
                            onFeedback = { msg, fb -> viewModel.setMessageFeedback(msg, fb) },
                            onSpeakText = { viewModel.speakTextWithGeminiTts(it) },
                            onSaveToLibrary = { title, content ->
                                viewModel.saveLibraryItem(null, title, "Saved Chats", "conversation", content)
                            },
                            onOpenInCodeStudio = { lang, code ->
                                viewModel.openCodeInStudio("Chat Snippet", lang, code)
                            },
                            onQuickNavigate = { navigateTopLevel(it.route) },
                            onTranscribeAudio = { audioBytes, mimeType, onDone ->
                                viewModel.transcribeRecordedAudio(audioBytes, mimeType, onDone)
                            },
                            onSaveSystemPrompt = { prompt ->
                                viewModel.updateProfileSettings(customSystemPrompt = prompt)
                                viewModel.showBanner(
                                    if (prompt.isBlank()) "Custom system prompt cleared."
                                    else "Custom system prompt saved."
                                )
                            },
                            onShareConversation = {
                                val transcript = snapshot.messages.joinToString("\n\n") {
                                    "${it.role.uppercase()}: ${it.text}"
                                }
                                shareTextContent("SpaceX AI Conversation", transcript)
                            },
                            onNotify = { viewModel.showBanner(it) }
                        )
                    }
                    composable(SpaceXRoute.History.route) {
                        ConversationHistoryScreen(
                            conversations = snapshot.conversations,
                            activeConversationId = snapshot.activeConversationId,
                            cachedMessagesByConvId = historyCachedMessages,
                            isDeepSearching = isIndexingHistorySearch,
                            onSearchQueryChanged = { viewModel.onHistorySearchQueryChanged(it) },
                            onStartNewChat = { viewModel.startNewChat() },
                            onOpenConversation = { convId -> viewModel.selectConversation(convId) },
                            onInspectConversation = { convId ->
                                navController.navigate(SpaceXRoute.historyDetailRoute(Uri.encode(convId))) {
                                    launchSingleTop = true
                                }
                            },
                            onRenameConversation = { conv, newTitle ->
                                viewModel.renameConversation(conv.id, newTitle)
                            },
                            onTogglePin = { viewModel.togglePinConversation(it) },
                            onToggleArchive = { viewModel.toggleArchiveConversation(it) },
                            onDuplicate = { viewModel.duplicateConversation(it) },
                            onShareConversation = { conv ->
                                viewModel.prepareConversationTranscript(conv) { title, body ->
                                    shareTextContent(title, body)
                                }
                            },
                            onDeleteConversation = { viewModel.deleteConversation(it.id) },
                            onBatchPin = { ids, pinned -> viewModel.batchSetPinned(ids, pinned) },
                            onBatchArchive = { ids, archived -> viewModel.batchSetArchived(ids, archived) },
                            onBatchDelete = { ids -> viewModel.deleteConversationsBatch(ids) }
                        )
                    }
                    composable(
                        route = SpaceXRoute.ROUTE_HISTORY_DETAIL,
                        arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val rawId = backStackEntry.arguments?.getString("conversationId").orEmpty()
                        val convId = Uri.decode(rawId)
                        ConversationHistoryDetailScreen(
                            conversationId = convId,
                            inspectionState = historyInspectionState,
                            onLoadConversation = { viewModel.loadConversationHistoryDetail(it) },
                            onResumeInChat = { viewModel.selectConversation(it) },
                            onShareTranscript = { conv, msgs ->
                                val body = buildString {
                                    appendLine("# ${conv.title}")
                                    appendLine("Model: ${conv.modelId}")
                                    appendLine()
                                    msgs.forEach { msg ->
                                        appendLine("${msg.role.uppercase()}:")
                                        appendLine(msg.text)
                                        appendLine()
                                    }
                                }
                                shareTextContent(conv.title, body)
                            },
                            onSaveToLibrary = { title, content ->
                                viewModel.saveLibraryItem(null, title, "Saved Chats", "conversation", content)
                            },
                            onDeleteConversation = { viewModel.deleteConversation(it) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(SpaceXRoute.CodeStudio.route) {
                        CodeStudioScreen(
                            state = codeStudioState,
                            savedProjects = snapshot.studioProjects,
                            onUpdateState = { viewModel.updateCodeStudio(it) },
                            onRunAiAssistant = { action, instr -> viewModel.runCodeAssistant(action, instr) },
                            onSaveProject = { viewModel.saveCodeStudioProject() },
                            onLoadProject = { viewModel.loadStudioProject(it) },
                            onExportCode = { title, content -> shareTextContent(title, content) }
                        )
                    }
                    composable(SpaceXRoute.WebsiteBuilder.route) {
                        WebsiteOrGameStudioScreen(
                            isGameStudio = false,
                            state = websiteStudioState,
                            savedProjects = snapshot.studioProjects,
                            onGenerate = { viewModel.generateWebsite(it) },
                            onUpdateCode = { h, c, j, d -> viewModel.updateWebsiteCode(h, c, j, d) },
                            onSave = { viewModel.saveBuilderProject(isGame = false) },
                            onLoadProject = { viewModel.loadStudioProject(it) },
                            onExport = { title, content -> shareTextContent(title, content) }
                        )
                    }
                    composable(SpaceXRoute.GameStudio.route) {
                        WebsiteOrGameStudioScreen(
                            isGameStudio = true,
                            state = gameStudioState,
                            savedProjects = snapshot.studioProjects,
                            onGenerate = { viewModel.generateGame(it) },
                            onUpdateCode = { h, c, j, _ -> viewModel.updateGameCode(h, c, j) },
                            onSave = { viewModel.saveBuilderProject(isGame = true) },
                            onLoadProject = { viewModel.loadStudioProject(it) },
                            onExport = { title, content -> shareTextContent(title, content) }
                        )
                    }
                    composable(SpaceXRoute.CreativeStudio.route) {
                        CreativeStudioScreen(
                            snapshot = snapshot,
                            onSelectImageModel = { viewModel.setImageModel(it) },
                            onSelectAspectRatio = { viewModel.setAspectRatio(it) },
                            onSelectResolution = { viewModel.setImageResolution(it) },
                            onGenerateOrEditImage = { p, bmp -> viewModel.generateOrEditImage(p, bmp) },
                            onAnalyzeImage = { bmp, p -> viewModel.analyzeImage(bmp, p) },
                            onAnalyzeVideoBytes = { b, m, p, t -> viewModel.analyzeMediaBytes(b, m, p, t) },
                            onSpeakText = { viewModel.speakTextWithGeminiTts(it) },
                            onSavePromptToLibrary = { title, content ->
                                viewModel.saveLibraryItem(null, title, "Prompts", "image_prompt", content)
                            }
                        )
                    }
                    composable(SpaceXRoute.VoiceAi.route) {
                        VoiceAiScreen(
                            state = voiceState,
                            onSetListening = { viewModel.setVoiceListening(it) },
                            onSelectVoice = { viewModel.selectVoicePersona(it) },
                            onSendVoiceTurn = { viewModel.sendVoiceTurn(it) },
                            onStopSession = { viewModel.stopVoiceSession() }
                        )
                    }
                    composable(SpaceXRoute.Projects.route) {
                        ProjectsScreen(
                            projects = snapshot.projects,
                            activeProjectId = snapshot.activeProjectId,
                            onSaveProject = { id, n, cat, instr, desc ->
                                viewModel.saveProject(id, n, cat, instr, desc)
                            },
                            onDeleteProject = { viewModel.deleteProject(it) },
                            onStartProjectChat = { projId -> viewModel.startNewChat(projId) }
                        )
                    }
                    composable(SpaceXRoute.Library.route) {
                        LibraryScreen(
                            items = snapshot.libraryItems,
                            onSaveItem = { id, title, folder, type, content, lang ->
                                viewModel.saveLibraryItem(id, title, folder, type, content, lang)
                            },
                            onDeleteItem = { viewModel.deleteLibraryItem(it) },
                            onShareItem = { item -> shareTextContent(item.title, item.content) }
                        )
                    }
                    composable(SpaceXRoute.Memory.route) {
                        MemoryScreen(
                            memoryEnabled = profile.memoryEnabled,
                            memories = snapshot.memories,
                            onToggleMemory = { viewModel.updateProfileSettings(memoryEnabled = it) },
                            onSaveMemory = { id, content, cat -> viewModel.saveMemory(id, content, cat) },
                            onDeleteMemory = { viewModel.deleteMemory(it) }
                        )
                    }
                    composable(SpaceXRoute.ExploreTools.route) {
                        ToolsAndModelsScreen(
                            selectedModelId = snapshot.selectedModelId,
                            onSelectModel = { viewModel.selectModel(it) },
                            onNavigate = { navigateTopLevel(it.route) }
                        )
                    }
                    composable(SpaceXRoute.Settings.route) {
                        SettingsScreen(
                            profile = profile,
                            userEmail = user.email,
                            themeMode = themeMode,
                            onSelectThemeMode = { selected ->
                                onSelectThemeMode(selected)
                                val resolvedDark = when (selected) {
                                    AppThemeMode.LIGHT -> false
                                    AppThemeMode.DARK -> true
                                    AppThemeMode.SYSTEM -> isDarkTheme
                                }
                                viewModel.updateProfileSettings(darkMode = resolvedDark)
                                viewModel.showBanner("Theme set to ${selected.label}.")
                            },
                            onSaveProfile = { dn, un, bio, lang, style ->
                                viewModel.updateProfileSettings(
                                    displayName = dn,
                                    username = un,
                                    bio = bio,
                                    language = lang,
                                    responseStyle = style
                                )
                                viewModel.showBanner("Profile settings saved.")
                            },
                            onSaveSystemPrompt = { prompt ->
                                viewModel.updateProfileSettings(customSystemPrompt = prompt)
                                viewModel.showBanner(
                                    if (prompt.isBlank()) "Custom system prompt cleared."
                                    else "Custom system prompt saved."
                                )
                            },
                            onToggleMemory = { viewModel.updateProfileSettings(memoryEnabled = it) },
                            onExportAllData = {
                                viewModel.exportAllUserData { json ->
                                    shareTextContent("SpaceX AI Account Export", json)
                                }
                            },
                            onSignOut = {
                                onSignOut {
                                    viewModel.stopGeneration()
                                    viewModel.stopVoiceSession()
                                }
                            }
                        )
                    }
                    composable(SpaceXRoute.Help.route) {
                        HelpSupportScreen()
                    }
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth >= 840.dp) {
                PermanentNavigationDrawer(
                    drawerContent = drawerComposable
                ) {
                    mainScaffold(false)
                }
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = drawerComposable
                ) {
                    mainScaffold(true)
                }
            }
        }
    }
}
