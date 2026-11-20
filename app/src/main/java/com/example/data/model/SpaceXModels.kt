package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val language: String = "English",
    val responseStyle: String = "Balanced",
    val customSystemPrompt: String? = null,
    val memoryEnabled: Boolean = true,
    val darkMode: Boolean = true,
    val selectedModel: String = "gemini-3.8-flash",
    val onboardingCompleted: Boolean = false,
    val tier: String = "FREE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("displayName", displayName.trim().take(60).ifEmpty { "Explorer" })
        put("username", username.trim().take(32).ifEmpty { "explorer" })
        bio?.takeIf { it.isNotBlank() }?.let { put("bio", it.trim().take(300)) }
        email?.takeIf { it.isNotBlank() }?.let { put("email", it.trim().take(120)) }
        photoUrl?.takeIf { it.isNotBlank() }?.let { put("photoUrl", it.trim().take(500)) }
        put("language", language.trim().take(40).ifEmpty { "English" })
        put("responseStyle", responseStyle.trim().take(40).ifEmpty { "Balanced" })
        customSystemPrompt?.takeIf { it.isNotBlank() }?.let { put("customSystemPrompt", it.trim().take(2000)) }
        put("memoryEnabled", memoryEnabled)
        put("darkMode", darkMode)
        put("selectedModel", selectedModel.trim().take(80).ifEmpty { "gemini-3.8-flash" })
        put("onboardingCompleted", onboardingCompleted)
        put("tier", "FREE")
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Chat",
    val projectId: String? = null,
    val modelId: String = "gemini-3.8-flash",
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("title", title.trim().take(120).ifEmpty { "New Chat" })
        projectId?.takeIf { it.isNotBlank() }?.let { put("projectId", it.take(128)) }
        put("modelId", modelId.take(80).ifEmpty { "gemini-3.8-flash" })
        put("pinned", pinned)
        put("archived", archived)
        put("lastMessagePreview", lastMessagePreview.take(300))
        put("messageCount", messageCount.coerceAtLeast(0L))
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val text: String = "",
    val modelId: String = "gemini-3.8-flash",
    val feedback: String = "none",
    val attachmentLabel: String? = null,
    val thinkingSummary: String? = null,
    val groundingSources: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("role", if (role == "model") "model" else "user")
        put("text", text.take(30000))
        put("modelId", modelId.take(80))
        put("feedback", if (feedback in listOf("none", "like", "dislike")) feedback else "none")
        attachmentLabel?.takeIf { it.isNotBlank() }?.let { put("attachmentLabel", it.take(120)) }
        thinkingSummary?.takeIf { it.isNotBlank() }?.let { put("thinkingSummary", it.take(5000)) }
        if (groundingSources.isNotEmpty()) {
            put("groundingSources", groundingSources.take(15))
        }
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class AiProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = "Programming",
    val instructions: String = "",
    val description: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("name", name.trim().take(100).ifEmpty { "Untitled Project" })
        put("category", category.trim().take(40).ifEmpty { "Programming" })
        put("instructions", instructions.trim().take(4000))
        put("description", description.trim().take(500))
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class AiMemory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val content: String = "",
    val category: String = "Preference",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("content", content.trim().take(500))
        put("category", category.trim().take(40).ifEmpty { "Preference" })
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class LibraryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val folder: String = "General",
    val itemType: String = "note",
    val content: String = "",
    val language: String = "markdown",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("title", title.trim().take(120).ifEmpty { "Untitled Item" })
        put("folder", folder.trim().take(60).ifEmpty { "General" })
        put(
            "itemType",
            if (itemType in listOf("note", "code", "website", "game", "conversation", "document", "image_prompt")) {
                itemType
            } else "note"
        )
        put("content", content.take(100000))
        put("language", language.trim().take(40))
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class StudioProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val studioType: String = "code",
    val language: String = "HTML",
    val primaryCode: String = "",
    val cssCode: String = "",
    val jsCode: String = "",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("name", name.trim().take(100).ifEmpty { "Orbital Project" })
        put("studioType", if (studioType in listOf("code", "website", "game")) studioType else "code")
        put("language", language.trim().take(40).ifEmpty { "HTML" })
        put("primaryCode", primaryCode.take(150000))
        put("cssCode", cssCode.take(100000))
        put("jsCode", jsCode.take(150000))
        notes?.takeIf { it.isNotBlank() }?.let { put("notes", it.take(2000)) }
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

data class GeminiModelOption(
    val id: String,
    val displayName: String,
    val badge: String,
    val description: String,
    val supportsThinking: Boolean = false,
    val supportsGrounding: Boolean = true
)

data class SpaceXCapabilityTool(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val statusLabel: String,
    val activeByDefault: Boolean
)
