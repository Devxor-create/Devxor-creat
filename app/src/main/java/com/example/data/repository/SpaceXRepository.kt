package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AiMemory
import com.example.data.model.AiProject
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.LibraryItem
import com.example.data.model.StudioProject
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class SpaceXRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun checkUserMatch(userId: String): String {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user operation." }
        return uid
    }

    private fun <T> Flow<T>.handleStreamErrors(
        userId: String,
        operationType: OperationType,
        path: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    // --- Profile ---

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        checkUserMatch(userId)
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .handleStreamErrors(userId, OperationType.GET, path)
        )
    }

    suspend fun saveOnboardingProfile(
        userId: String,
        displayName: String,
        username: String,
        bio: String?,
        email: String?,
        photoUrl: String?,
        language: String,
        responseStyle: String
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = checkUserMatch(userId)
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            val cleanUsername = username.trim().replace(Regex("[^a-zA-Z0-9_]"), "_").take(32)
                .let { if (it.length < 3) "${it}_ai".take(32) else it }
            if (!snapshot.exists()) {
                val profile = UserProfile(
                    userId = uid,
                    displayName = displayName.trim().take(60).ifEmpty { "Commander" },
                    username = cleanUsername,
                    bio = bio?.trim()?.take(300)?.takeIf { it.isNotBlank() },
                    email = email?.trim()?.take(120)?.takeIf { it.isNotBlank() },
                    photoUrl = photoUrl?.trim()?.take(500)?.takeIf { it.isNotBlank() },
                    language = language.trim().take(40).ifEmpty { "English" },
                    responseStyle = responseStyle.trim().take(40).ifEmpty { "Balanced" },
                    memoryEnabled = true,
                    darkMode = true,
                    selectedModel = "gemini-3.8-flash",
                    onboardingCompleted = true,
                    tier = "FREE"
                )
                docRef.set(profile.toCreateMap()).await()
            } else {
                val updates = buildMap<String, Any> {
                    put("displayName", displayName.trim().take(60).ifEmpty { "Commander" })
                    put("username", cleanUsername)
                    put("bio", (bio ?: "").trim().take(300))
                    photoUrl?.takeIf { it.isNotBlank() }?.let { put("photoUrl", it.take(500)) }
                    put("language", language.trim().take(40).ifEmpty { "English" })
                    put("responseStyle", responseStyle.trim().take(40).ifEmpty { "Balanced" })
                    put("onboardingCompleted", true)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfilePreferences(
        userId: String,
        displayName: String? = null,
        username: String? = null,
        bio: String? = null,
        language: String? = null,
        responseStyle: String? = null,
        customSystemPrompt: String? = null,
        memoryEnabled: Boolean? = null,
        darkMode: Boolean? = null,
        selectedModel: String? = null
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = checkUserMatch(userId)
            val updates = buildMap<String, Any> {
                displayName?.let { put("displayName", it.trim().take(60).ifEmpty { "Commander" }) }
                username?.let { raw ->
                    val clean = raw.trim().replace(Regex("[^a-zA-Z0-9_]"), "_").take(32)
                    put("username", if (clean.length < 3) "${clean}_ai" else clean)
                }
                bio?.let { put("bio", it.trim().take(300)) }
                language?.let { put("language", it.trim().take(40).ifEmpty { "English" }) }
                responseStyle?.let { put("responseStyle", it.trim().take(40).ifEmpty { "Balanced" }) }
                customSystemPrompt?.let { put("customSystemPrompt", it.trim().take(2000)) }
                memoryEnabled?.let { put("memoryEnabled", it) }
                darkMode?.let { put("darkMode", it) }
                selectedModel?.let { put("selectedModel", it.trim().take(80).ifEmpty { "gemini-3.8-flash" }) }
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Conversations ---

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        checkUserMatch(userId)
        val path = "users/$userId/conversations"
        emitAll(
            db.collection("users").document(userId).collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .handleStreamErrors(userId, OperationType.LIST, path)
        )
    }

    suspend fun createConversation(
        userId: String,
        title: String,
        modelId: String,
        projectId: String? = null
    ): Result<String> {
        val basePath = "users/$userId/conversations"
        return try {
            val uid = checkUserMatch(userId)
            val docRef = db.collection("users").document(uid).collection("conversations").document()
            val conv = Conversation(
                userId = uid,
                title = title.trim().take(120).ifEmpty { "New Chat" },
                projectId = projectId,
                modelId = modelId,
                pinned = false,
                archived = false,
                lastMessagePreview = "",
                messageCount = 0L
            )
            docRef.set(conv.toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, basePath)
            Result.failure(e)
        }
    }

    suspend fun updateConversationMeta(
        userId: String,
        conversationId: String,
        title: String? = null,
        pinned: Boolean? = null,
        archived: Boolean? = null,
        modelId: String? = null,
        lastMessagePreview: String? = null,
        messageCount: Long? = null
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId"
        return try {
            val uid = checkUserMatch(userId)
            require(conversationId.isNotBlank())
            val updates = buildMap<String, Any> {
                title?.let { put("title", it.trim().take(120).ifEmpty { "Untitled Chat" }) }
                pinned?.let { put("pinned", it) }
                archived?.let { put("archived", it) }
                modelId?.let { put("modelId", it.take(80)) }
                lastMessagePreview?.let { put("lastMessagePreview", it.take(300)) }
                messageCount?.let { put("messageCount", it.coerceAtLeast(0L)) }
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun duplicateConversation(
        userId: String,
        source: Conversation,
        messages: List<ChatMessage>
    ): Result<String> {
        val basePath = "users/$userId/conversations"
        return try {
            val uid = checkUserMatch(userId)
            val newDoc = db.collection("users").document(uid).collection("conversations").document()
            val copyConv = source.copy(
                id = "",
                userId = uid,
                title = "${source.title} (Copy)".take(120),
                pinned = false
            )
            newDoc.set(copyConv.toCreateMap()).await()
            messages.forEach { msg ->
                val msgRef = newDoc.collection("messages").document()
                msgRef.set(msg.copy(id = "", userId = uid).toCreateMap()).await()
            }
            Result.success(newDoc.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, basePath)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(userId: String, conversationId: String): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId"
        return try {
            val uid = checkUserMatch(userId)
            val convRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
            val msgs = convRef.collection("messages").get().await()
            if (!msgs.isEmpty) {
                val batch = db.batch()
                msgs.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            convRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Messages ---

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> = flow {
        checkUserMatch(userId)
        val path = "users/$userId/conversations/$conversationId/messages"
        emitAll(
            db.collection("users").document(userId)
                .collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .handleStreamErrors(userId, OperationType.LIST, path)
        )
    }

    suspend fun fetchConversationMessages(
        userId: String,
        conversationId: String
    ): Result<List<ChatMessage>> {
        val path = "users/$userId/conversations/$conversationId/messages"
        return try {
            val uid = checkUserMatch(userId)
            require(conversationId.isNotBlank()) { "Conversation ID cannot be blank." }
            val snapshot = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .await()
            val messages = snapshot.toObjects(
                ChatMessage::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(messages)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversationsBatch(
        userId: String,
        conversationIds: List<String>
    ): Result<Unit> {
        val path = "users/$userId/conversations"
        return try {
            checkUserMatch(userId)
            for (convId in conversationIds) {
                if (convId.isNotBlank()) {
                    deleteConversation(userId, convId).getOrThrow()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        userId: String,
        conversationId: String,
        message: ChatMessage
    ): Result<String> {
        val path = "users/$userId/conversations/$conversationId/messages"
        return try {
            val uid = checkUserMatch(userId)
            val msgRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages").document()
            msgRef.set(message.copy(userId = uid).toCreateMap()).await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMessage(
        userId: String,
        conversationId: String,
        messageId: String,
        text: String? = null,
        feedback: String? = null
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId/messages/$messageId"
        return try {
            val uid = checkUserMatch(userId)
            val updates = buildMap<String, Any> {
                text?.let { put("text", it.take(30000)) }
                feedback?.let {
                    put("feedback", if (it in listOf("none", "like", "dislike")) it else "none")
                }
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages").document(messageId)
                .update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Projects ---

    fun observeProjects(userId: String): Flow<List<AiProject>> = flow {
        checkUserMatch(userId)
        val path = "users/$userId/projects"
        emitAll(
            db.collection("users").document(userId).collection("projects")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AiProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .handleStreamErrors(userId, OperationType.LIST, path)
        )
    }

    suspend fun saveProject(
        userId: String,
        projectId: String? = null,
        name: String,
        category: String,
        instructions: String,
        description: String
    ): Result<String> {
        val basePath = "users/$userId/projects"
        return try {
            val uid = checkUserMatch(userId)
            val col = db.collection("users").document(uid).collection("projects")
            if (projectId.isNullOrBlank()) {
                val doc = col.document()
                val model = AiProject(
                    userId = uid,
                    name = name,
                    category = category,
                    instructions = instructions,
                    description = description
                )
                doc.set(model.toCreateMap()).await()
                Result.success(doc.id)
            } else {
                col.document(projectId).update(
                    mapOf(
                        "name" to name.trim().take(100).ifEmpty { "Untitled Project" },
                        "category" to category.trim().take(40).ifEmpty { "Programming" },
                        "instructions" to instructions.trim().take(4000),
                        "description" to description.trim().take(500),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(projectId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, basePath)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(userId: String, projectId: String): Result<Unit> {
        val path = "users/$userId/projects/$projectId"
        return try {
            val uid = checkUserMatch(userId)
            db.collection("users").document(uid).collection("projects").document(projectId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Memories ---

    fun observeMemories(userId: String): Flow<List<AiMemory>> = flow {
        checkUserMatch(userId)
        val path = "users/$userId/memories"
        emitAll(
            db.collection("users").document(userId).collection("memories")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AiMemory::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .handleStreamErrors(userId, OperationType.LIST, path)
        )
    }

    suspend fun saveMemory(
        userId: String,
        memoryId: String? = null,
        content: String,
        category: String
    ): Result<String> {
        val basePath = "users/$userId/memories"
        return try {
            val uid = checkUserMatch(userId)
            require(content.trim().isNotEmpty()) { "Memory content cannot be empty." }
            val col = db.collection("users").document(uid).collection("memories")
            if (memoryId.isNullOrBlank()) {
                val doc = col.document()
                doc.set(
                    AiMemory(userId = uid, content = content, category = category).toCreateMap()
                ).await()
                Result.success(doc.id)
            } else {
                col.document(memoryId).update(
                    mapOf(
                        "content" to content.trim().take(500),
                        "category" to category.trim().take(40).ifEmpty { "Preference" },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(memoryId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, basePath)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(userId: String, memoryId: String): Result<Unit> {
        val path = "users/$userId/memories/$memoryId"
        return try {
            val uid = checkUserMatch(userId)
            db.collection("users").document(uid).collection("memories").document(memoryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Library ---

    fun observeLibraryItems(userId: String): Flow<List<LibraryItem>> = flow {
        checkUserMatch(userId)
        val path = "users/$userId/library_items"
        emitAll(
            db.collection("users").document(userId).collection("library_items")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LibraryItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .handleStreamErrors(userId, OperationType.LIST, path)
        )
    }

    suspend fun saveLibraryItem(
        userId: String,
        itemId: String? = null,
        title: String,
        folder: String,
        itemType: String,
        content: String,
        language: String = "markdown"
    ): Result<String> {
        val basePath = "users/$userId/library_items"
        return try {
            val uid = checkUserMatch(userId)
            val col = db.collection("users").document(uid).collection("library_items")
            if (itemId.isNullOrBlank()) {
                val doc = col.document()
                doc.set(
                    LibraryItem(
                        userId = uid,
                        title = title,
                        folder = folder,
                        itemType = itemType,
                        content = content,
                        language = language
                    ).toCreateMap()
                ).await()
                Result.success(doc.id)
            } else {
                val safeType = if (itemType in listOf("note", "code", "website", "game", "conversation", "document", "image_prompt")) {
                    itemType
                } else "note"
                col.document(itemId).update(
                    mapOf(
                        "title" to title.trim().take(120).ifEmpty { "Untitled Item" },
                        "folder" to folder.trim().take(60).ifEmpty { "General" },
                        "itemType" to safeType,
                        "content" to content.take(100000),
                        "language" to language.trim().take(40),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(itemId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, basePath)
            Result.failure(e)
        }
    }

    suspend fun deleteLibraryItem(userId: String, itemId: String): Result<Unit> {
        val path = "users/$userId/library_items/$itemId"
        return try {
            val uid = checkUserMatch(userId)
            db.collection("users").document(uid).collection("library_items").document(itemId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Studio Projects (Code Studio / Website Builder / Game Studio) ---

    fun observeStudioProjects(userId: String): Flow<List<StudioProject>> = flow {
        checkUserMatch(userId)
        val path = "users/$userId/studio_projects"
        emitAll(
            db.collection("users").document(userId).collection("studio_projects")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudioProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .handleStreamErrors(userId, OperationType.LIST, path)
        )
    }

    suspend fun saveStudioProject(
        userId: String,
        studioId: String? = null,
        name: String,
        studioType: String,
        language: String,
        primaryCode: String,
        cssCode: String,
        jsCode: String,
        notes: String? = null
    ): Result<String> {
        val basePath = "users/$userId/studio_projects"
        return try {
            val uid = checkUserMatch(userId)
            val col = db.collection("users").document(uid).collection("studio_projects")
            if (studioId.isNullOrBlank()) {
                val doc = col.document()
                doc.set(
                    StudioProject(
                        userId = uid,
                        name = name,
                        studioType = studioType,
                        language = language,
                        primaryCode = primaryCode,
                        cssCode = cssCode,
                        jsCode = jsCode,
                        notes = notes
                    ).toCreateMap()
                ).await()
                Result.success(doc.id)
            } else {
                val safeStudioType = if (studioType in listOf("code", "website", "game")) studioType else "code"
                val updates = buildMap<String, Any> {
                    put("name", name.trim().take(100).ifEmpty { "Orbital Project" })
                    put("studioType", safeStudioType)
                    put("language", language.trim().take(40).ifEmpty { "HTML" })
                    put("primaryCode", primaryCode.take(150000))
                    put("cssCode", cssCode.take(100000))
                    put("jsCode", jsCode.take(150000))
                    notes?.takeIf { it.isNotBlank() }?.let { put("notes", it.take(2000)) }
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                col.document(studioId).update(updates).await()
                Result.success(studioId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, basePath)
            Result.failure(e)
        }
    }

    suspend fun deleteStudioProject(userId: String, studioId: String): Result<Unit> {
        val path = "users/$userId/studio_projects/$studioId"
        return try {
            val uid = checkUserMatch(userId)
            db.collection("users").document(uid).collection("studio_projects").document(studioId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Full Account Data Export ---

    suspend fun exportUserDataJson(userId: String): Result<String> {
        val path = "users/$userId"
        return try {
            val uid = checkUserMatch(userId)
            val userDoc = db.collection("users").document(uid).get().await()
            val conversations = db.collection("users").document(uid).collection("conversations").get().await()
            val projects = db.collection("users").document(uid).collection("projects").get().await()
            val memories = db.collection("users").document(uid).collection("memories").get().await()
            val library = db.collection("users").document(uid).collection("library_items").get().await()
            val studios = db.collection("users").document(uid).collection("studio_projects").get().await()

            val root = JSONObject().apply {
                put("exportedAt", Timestamp.now().toDate().toString())
                put("profile", JSONObject(userDoc.data ?: emptyMap<String, Any>()))
                put("conversations", JSONArray(conversations.documents.map { JSONObject(it.data ?: emptyMap<String, Any>()) }))
                put("projects", JSONArray(projects.documents.map { JSONObject(it.data ?: emptyMap<String, Any>()) }))
                put("memories", JSONArray(memories.documents.map { JSONObject(it.data ?: emptyMap<String, Any>()) }))
                put("library", JSONArray(library.documents.map { JSONObject(it.data ?: emptyMap<String, Any>()) }))
                put("studioProjects", JSONArray(studios.documents.map { JSONObject(it.data ?: emptyMap<String, Any>()) }))
            }
            Result.success(root.toString(2))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }
}
