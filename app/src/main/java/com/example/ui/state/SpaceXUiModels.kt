package com.example.ui.state

import android.graphics.Bitmap
import com.example.ai.AspectRatioOption
import java.util.Calendar
import com.example.ai.ImageResolutionOption
import com.example.data.model.AiMemory
import com.example.data.model.AiProject
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.LibraryItem
import com.example.data.model.StudioProject
import com.example.data.model.UserProfile

enum class SpaceXRoute(val label: String, val route: String) {
    Chat("AI Chat", "chat"),
    History("Conversation History", "history"),
    CodeStudio("Code Studio", "code_studio"),
    WebsiteBuilder("Website Builder", "website_builder"),
    GameStudio("Game Studio", "game_studio"),
    CreativeStudio("Media & Image AI", "creative_studio"),
    VoiceAi("Voice AI", "voice_ai"),
    Projects("Projects", "projects"),
    Library("Library", "library"),
    Memory("Memory", "memory"),
    ExploreTools("Tools & Models", "explore_tools"),
    Settings("Settings", "settings"),
    Help("Help & Support", "help");

    companion object {
        const val ROUTE_HISTORY_DETAIL = "history/{conversationId}"
        fun historyDetailRoute(conversationId: String): String = "history/$conversationId"

        fun fromRoute(route: String?): SpaceXRoute {
            if (route != null && route.startsWith("history/")) return History
            return entries.firstOrNull { it.route == route } ?: Chat
        }
    }
}

sealed interface HistoryInspectionState {
    data object Idle : HistoryInspectionState
    data object Loading : HistoryInspectionState
    data class Success(
        val conversation: Conversation,
        val messages: List<ChatMessage>
    ) : HistoryInspectionState
    data class Error(val message: String) : HistoryInspectionState
}

sealed interface GenerationUiState {
    data object Idle : GenerationUiState
    data object Loading : GenerationUiState
    data class Streaming(
        val text: String,
        val thinkingSummary: String? = null,
        val groundingSources: List<String> = emptyList()
    ) : GenerationUiState
    data class Error(val message: String) : GenerationUiState
}

sealed interface MediaStudioState {
    data object Idle : MediaStudioState
    data object Loading : MediaStudioState
    data class ImageSuccess(val bitmap: Bitmap, val prompt: String) : MediaStudioState
    data class AnalysisSuccess(val text: String, val title: String) : MediaStudioState
    data class Error(val message: String) : MediaStudioState
}

data class CodeStudioState(
    val activeProjectId: String? = null,
    val projectName: String = "OrbitalDashboard",
    val language: String = "HTML",
    val activeTab: Int = 0, // 0: HTML/Main, 1: CSS, 2: JS, 3: Preview, 4: Console
    val htmlCode: String = """
        <div class="cockpit">
          <div class="badge">SPACEX AI // TELEMETRY</div>
          <h1>Orbital Flight Deck</h1>
          <p id="status">Systems nominal. Ready for ignition.</p>
          <button onclick="boostTelemetry()">Engage Thrusters</button>
        </div>
    """.trimIndent(),
    val cssCode: String = """
        body {
          margin: 0;
          min-height: 100vh;
          display: grid;
          place-items: center;
          background: radial-gradient(circle at top, #0B1F35, #071426);
          color: #F5F8FC;
          font-family: system-ui, sans-serif;
        }
        .cockpit {
          background: rgba(18, 44, 73, 0.75);
          border: 1px solid #1E3E63;
          border-radius: 20px;
          padding: 28px;
          max-width: 320px;
          text-align: center;
        }
        .badge {
          color: #B7FF35;
          font-size: 11px;
          letter-spacing: 2px;
          margin-bottom: 10px;
        }
        button {
          margin-top: 14px;
          background: #B7FF35;
          color: #071426;
          border: none;
          padding: 12px 20px;
          border-radius: 12px;
          font-weight: 700;
          cursor: pointer;
        }
    """.trimIndent(),
    val jsCode: String = """
        let velocity = 7.66;
        function boostTelemetry() {
          velocity = (velocity + 0.42).toFixed(2);
          document.getElementById('status').textContent =
            'Orbital velocity: ' + velocity + ' km/s — Nominal';
          console.log('Thruster burn -> velocity ' + velocity + ' km/s');
        }
    """.trimIndent(),
    val editorThemeDark: Boolean = true,
    val consoleLogs: List<String> = listOf("SpaceX Code Studio sandbox ready."),
    val aiAssistantOutput: String = "",
    val isBusy: Boolean = false,
    val error: String? = null
)

data class BuilderStudioState(
    val activeProjectId: String? = null,
    val title: String = "",
    val summary: String = "",
    val htmlCode: String = "",
    val cssCode: String = "",
    val jsCode: String = "",
    val isDesktopPreview: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null
)

data class VoiceSessionState(
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val selectedVoice: String = "Kore",
    val transcript: String = "",
    val lastResponse: String = "",
    val error: String? = null
)

data class SpaceXWorkspaceSnapshot(
    val profile: UserProfile? = null,
    val profileLoaded: Boolean = false,
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val projects: List<AiProject> = emptyList(),
    val activeProjectId: String? = null,
    val memories: List<AiMemory> = emptyList(),
    val libraryItems: List<LibraryItem> = emptyList(),
    val studioProjects: List<StudioProject> = emptyList(),
    val selectedModelId: String = "gemini-3.8-flash",
    val webSearchEnabled: Boolean = false,
    val mapsGroundingEnabled: Boolean = false,
    val highThinkingEnabled: Boolean = false,
    val generationState: GenerationUiState = GenerationUiState.Idle,
    val mediaState: MediaStudioState = MediaStudioState.Idle,
    val selectedImageModel: String = "gemini-nano-banana-2.1",
    val selectedAspectRatio: AspectRatioOption = AspectRatioOption.SQUARE_1_1,
    val selectedResolution: ImageResolutionOption = ImageResolutionOption.RES_1K
)

data class ConversationDateSection(
    val label: String,
    val items: List<Conversation>
)

fun groupConversationsByDate(
    conversations: List<Conversation>,
    oldestFirst: Boolean = false
): List<ConversationDateSection> {
    if (conversations.isEmpty()) return emptyList()

    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfTodayMs = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val startOfYesterdayMs = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, -6)
    val startOfLast7DaysMs = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, -23)
    val startOfLast30DaysMs = cal.timeInMillis

    val bucketOrder = if (oldestFirst) {
        listOf("Older", "Last 30 Days", "Last 7 Days", "Yesterday", "Today")
    } else {
        listOf("Today", "Yesterday", "Last 7 Days", "Last 30 Days", "Older")
    }

    val grouped = linkedMapOf<String, MutableList<Conversation>>()
    bucketOrder.forEach { grouped[it] = mutableListOf() }

    conversations.forEach { conv ->
        val ts = conv.updatedAt?.toDate()?.time
        val bucket = when {
            ts == null || ts >= startOfTodayMs -> "Today"
            ts >= startOfYesterdayMs -> "Yesterday"
            ts >= startOfLast7DaysMs -> "Last 7 Days"
            ts >= startOfLast30DaysMs -> "Last 30 Days"
            else -> "Older"
        }
        grouped[bucket]?.add(conv)
    }

    return bucketOrder.mapNotNull { label ->
        val list = grouped[label].orEmpty()
        if (list.isEmpty()) null else ConversationDateSection(label = label, items = list)
    }
}
