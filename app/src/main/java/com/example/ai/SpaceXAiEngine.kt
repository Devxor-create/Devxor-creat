package com.example.ai

import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.data.model.AiMemory
import com.example.data.model.AiProject
import com.example.data.model.ChatMessage
import com.example.data.model.GeminiModelOption
import com.example.data.model.SpaceXCapabilityTool
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "SpaceXAiEngine"

data class StreamChunkUpdate(
    val accumulatedText: String,
    val thinkingSummary: String? = null,
    val groundingSources: List<String> = emptyList(),
    val isComplete: Boolean = false
)

data class WebOrGameBundle(
    val title: String,
    val summary: String,
    val htmlCode: String,
    val cssCode: String,
    val jsCode: String
)

enum class AspectRatioOption(val label: String, val sdkRatio: AspectRatio) {
    SQUARE_1_1("1:1", AspectRatio.SQUARE_1x1),
    PORTRAIT_2_3("2:3", AspectRatio.PORTRAIT_2x3),
    LANDSCAPE_3_2("3:2", AspectRatio.LANDSCAPE_3x2),
    PORTRAIT_3_4("3:4", AspectRatio.PORTRAIT_3x4),
    LANDSCAPE_4_3("4:3", AspectRatio.LANDSCAPE_4x3),
    PORTRAIT_9_16("9:16", AspectRatio.PORTRAIT_9x16),
    LANDSCAPE_16_9("16:9", AspectRatio.LANDSCAPE_16x9)
}

enum class ImageResolutionOption(val label: String, val sdkSize: ImageSize) {
    RES_1K("1K", ImageSize.SIZE_1K),
    RES_2K("2K", ImageSize.SIZE_2K),
    RES_4K("4K", ImageSize.SIZE_4K)
}

class SpaceXAiEngine {

    companion object {
        val supportedModels = listOf(
            GeminiModelOption(
                id = "gemini-3.8-flash",
                displayName = "SpaceX AI Default (Gemini 3.8 Flash)",
                badge = "DEFAULT",
                description = "Versatile frontier intelligence for reasoning, coding, search, maps, and multimodal tasks.",
                supportsThinking = true,
                supportsGrounding = true
            ),
            GeminiModelOption(
                id = "gemini-4-argon",
                displayName = "Gemini 4 Argon (Deep Reasoning)",
                badge = "REASONING",
                description = "Maximum analytical power for complex STEM, mathematics, architecture, and deep coding.",
                supportsThinking = true,
                supportsGrounding = true
            ),
            GeminiModelOption(
                id = "gemini-3.5-flash-lite",
                displayName = "Gemini 3.5 Flash-Lite (Ultra Fast)",
                badge = "LOW LATENCY",
                description = "Instant low-latency responses for rapid brainstorming, drafts, and quick lookups.",
                supportsThinking = false,
                supportsGrounding = true
            )
        )

        val builtInTools = listOf(
            SpaceXCapabilityTool(
                id = "google_search",
                name = "Google Search Grounding",
                category = "RESEARCH",
                description = "Real-time live web search with verifiable source links and citations.",
                statusLabel = "Connected via Firebase AI Logic",
                activeByDefault = false
            ),
            SpaceXCapabilityTool(
                id = "google_maps",
                name = "Google Maps Grounding",
                category = "RESEARCH",
                description = "Geospatial intelligence for places, coordinates, and location-aware queries.",
                statusLabel = "Connected via Firebase AI Logic",
                activeByDefault = false
            ),
            SpaceXCapabilityTool(
                id = "deep_thinking",
                name = "High Thinking Mode",
                category = "INTELLIGENCE",
                description = "Enables extended multi-step chain-of-thought reasoning with thought summaries.",
                statusLabel = "Ready",
                activeByDefault = false
            ),
            SpaceXCapabilityTool(
                id = "nano_banana_image",
                name = "Nano Banana 2.1 & 3 Pro Image",
                category = "CREATIVE",
                description = "Create and edit high-resolution images with custom aspect ratios and 1K-4K output.",
                statusLabel = "Connected via Firebase AI Logic",
                activeByDefault = true
            ),
            SpaceXCapabilityTool(
                id = "gemini_tts",
                name = "Gemini 3.1 Flash TTS",
                category = "VOICE",
                description = "Natural multi-voice neural speech synthesis with PCM audio playback.",
                statusLabel = "Connected via Firebase AI Logic",
                activeByDefault = true
            ),
            SpaceXCapabilityTool(
                id = "code_sandbox",
                name = "SpaceX Sandboxed Web Runtime",
                category = "DEVELOPMENT",
                description = "Live isolated WebView runtime for HTML5, CSS, JavaScript, WebGL, and Canvas apps.",
                statusLabel = "Active On-Device",
                activeByDefault = true
            )
        )

        val ttsVoices = listOf("Kore", "Puck", "Charon", "Fenrir", "Aoede")
    }

    private fun buildSystemInstruction(
        profile: UserProfile?,
        project: AiProject?,
        memories: List<AiMemory>,
        useWebSearch: Boolean,
        useMaps: Boolean
    ): Content {
        val sb = StringBuilder()
        sb.appendLine("You are SpaceX AI, a futuristic, hyper-capable, accurate AI assistant.")
        if (profile != null) {
            if (profile.displayName.isNotBlank()) {
                sb.appendLine("Address the user naturally as ${profile.displayName} when appropriate.")
            }
            sb.appendLine("Preferred response language: ${profile.language}.")
            sb.appendLine("Preferred response style: ${profile.responseStyle}.")
            if (!profile.bio.isNullOrBlank()) {
                sb.appendLine("User background: ${profile.bio}")
            }
            val customPersona = profile.customSystemPrompt?.trim().orEmpty()
            if (customPersona.isNotEmpty()) {
                sb.appendLine("Custom user system prompt (follow this persona and specialized behavior closely):")
                sb.appendLine(customPersona)
            }
        }
        if (profile?.memoryEnabled != false && memories.isNotEmpty()) {
            sb.appendLine("Approved user memories to honor:")
            memories.take(15).forEach { mem ->
                sb.appendLine("- [${mem.category}] ${mem.content}")
            }
        }
        if (project != null) {
            sb.appendLine("Active project workspace: ${project.name} (${project.category}).")
            if (project.instructions.isNotBlank()) {
                sb.appendLine("Project instructions: ${project.instructions}")
            }
        }
        if (useWebSearch) {
            sb.appendLine("Live Google Search Grounding is active. Distinguish clearly between live web findings and general knowledge, and cite findings clearly.")
        }
        if (useMaps) {
            sb.appendLine("Live Google Maps Grounding is active for places and geographic details.")
        }
        sb.appendLine("Format responses cleanly using Markdown, fenced code blocks with language tags, and clear structure.")
        return content { text(sb.toString()) }
    }

    fun streamChatReply(
        modelId: String,
        prompt: String,
        historyMessages: List<ChatMessage>,
        attachedImage: Bitmap?,
        attachedDocumentText: String?,
        attachedDocumentName: String?,
        profile: UserProfile?,
        project: AiProject?,
        memories: List<AiMemory>,
        useWebSearch: Boolean,
        useMaps: Boolean,
        useHighThinking: Boolean,
        attachedAudioBytes: ByteArray? = null,
        attachedAudioMimeType: String? = null
    ): Flow<StreamChunkUpdate> = flow {
        val tools = buildList {
            if (useWebSearch) add(Tool.googleSearch())
            if (useMaps) add(Tool.googleMaps())
        }

        val effectiveModelId = if (useHighThinking && modelId == "gemini-3.5-flash-lite") {
            "gemini-3.8-flash"
        } else {
            modelId
        }

        val config = generationConfig {
            if (useHighThinking) {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            }
        }

        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = effectiveModelId,
            generationConfig = config,
            tools = tools.ifEmpty { null },
            systemInstruction = buildSystemInstruction(profile, project, memories, useWebSearch, useMaps)
        )

        val historyContent = historyMessages
            .filter { it.text.isNotBlank() }
            .takeLast(20)
            .map { msg ->
                content(role = if (msg.role == "model") "model" else "user") {
                    text(msg.text)
                }
            }

        val chat = model.startChat(history = historyContent)
        val userContent = content("user") {
            if (attachedImage != null) {
                image(attachedImage)
            }
            if (attachedAudioBytes != null && !attachedAudioMimeType.isNullOrBlank()) {
                inlineData(attachedAudioBytes, attachedAudioMimeType)
            }
            if (!attachedDocumentText.isNullOrBlank()) {
                text("Attached document (${attachedDocumentName ?: "document"}):\n$attachedDocumentText\n\nUser request:\n$prompt")
            } else {
                text(prompt)
            }
        }

        val textAcc = StringBuilder()
        val thoughtAcc = StringBuilder()
        val sourcesSet = linkedSetOf<String>()

        withTimeout(60_000L) {
            chat.sendMessageStream(userContent).collect { chunk ->
                chunk.text?.let { textAcc.append(it) }
                val grounding = chunk.candidates.firstOrNull()?.groundingMetadata
                grounding?.groundingChunks?.forEach { gChunk ->
                    gChunk.web?.let { web ->
                        val title = web.title ?: "Web Source"
                        val uri = web.uri ?: ""
                        if (uri.isNotBlank()) sourcesSet.add("$title | $uri")
                    }
                    gChunk.maps?.let { maps ->
                        val title = maps.title ?: "Google Maps"
                        val uri = maps.uri ?: ""
                        if (uri.isNotBlank()) sourcesSet.add("$title (Google Maps) | $uri")
                    }
                }
                emit(
                    StreamChunkUpdate(
                        accumulatedText = textAcc.toString(),
                        thinkingSummary = thoughtAcc.toString().takeIf { it.isNotBlank() },
                        groundingSources = sourcesSet.toList(),
                        isComplete = false
                    )
                )
            }
        }

        emit(
            StreamChunkUpdate(
                accumulatedText = textAcc.toString(),
                thinkingSummary = thoughtAcc.toString().takeIf { it.isNotBlank() },
                groundingSources = sourcesSet.toList(),
                isComplete = true
            )
        )
    }

    suspend fun generateCodeAssistantReply(
        actionLabel: String,
        language: String,
        currentCode: String,
        userInstruction: String
    ): String {
        val model by lazy {
            Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = "gemini-3.8-flash",
                systemInstruction = content {
                    text("You are SpaceX Code Studio AI. Return clean, working, well-structured $language code and concise technical explanation.")
                }
            )
        }
        val prompt = """
            Action: $actionLabel
            Language: $language
            Instruction: $userInstruction
            
            Current Code:
            ```$language
            $currentCode
            ```
        """.trimIndent()
        val response = withTimeout(60_000L) {
            model.generateContent(prompt)
        }
        return response.text ?: throw IllegalStateException("AI Code Assistant returned an empty response.")
    }

    suspend fun generateWebOrGameBundle(
        studioType: String,
        promptText: String
    ): WebOrGameBundle {
        val schema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Short project title"),
                "summary" to Schema.string(description = "Brief 1-2 sentence description of what was built"),
                "htmlCode" to Schema.string(description = "Complete HTML body markup (or full HTML5 structure)"),
                "cssCode" to Schema.string(description = "Complete CSS styling"),
                "jsCode" to Schema.string(description = "Complete interactive JavaScript code")
            )
        )

        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = schema
        }

        val roleDesc = if (studioType == "game") {
            "You are SpaceX Game Studio AI. Generate a fun, playable, self-contained HTML5 Canvas or WebGL 2D/3D game with touch/tap AND keyboard controls, score counter, start/restart button, and responsive viewport sizing so it plays smoothly inside a mobile WebView."
        } else {
            "You are SpaceX Website Builder AI. Generate a stunning, responsive modern website with clean semantic HTML5, modern CSS (flexbox/grid, dark futuristic or requested palette), and interactive JavaScript."
        }

        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config,
            systemInstruction = content { text(roleDesc) }
        )

        val response = withTimeout(60_000L) {
            model.generateContent(promptText)
        }
        val rawJson = response.text ?: throw IllegalStateException("Generator returned an empty response.")
        val json = JSONObject(rawJson)
        return WebOrGameBundle(
            title = json.optString("title", if (studioType == "game") "Orbital Game" else "Orbital Site"),
            summary = json.optString("summary", "Generated by SpaceX AI."),
            htmlCode = json.optString("htmlCode", "<div>Empty HTML</div>"),
            cssCode = json.optString("cssCode", ""),
            jsCode = json.optString("jsCode", "")
        )
    }

    suspend fun generateOrEditImage(
        promptText: String,
        sourceBitmap: Bitmap?,
        modelId: String,
        aspectRatio: AspectRatioOption,
        imageSize: ImageResolutionOption
    ): Bitmap {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(aspectRatio.sdkRatio)
                .setImageSize(imageSize.sdkSize)
                .build()
        }

        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelId,
            generationConfig = config
        )

        val response = withTimeout(60_000L) {
            if (sourceBitmap != null) {
                model.generateContent(
                    content {
                        image(sourceBitmap)
                        text(promptText)
                    }
                )
            } else {
                model.generateContent(promptText)
            }
        }

        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        return imagePart?.image
            ?: throw IllegalStateException("No image was returned by the model.")
    }

    suspend fun analyzeVisualContent(
        bitmap: Bitmap,
        promptText: String
    ): String {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash"
        )
        val response = withTimeout(60_000L) {
            model.generateContent(
                content {
                    image(bitmap)
                    text(promptText.ifBlank { "Analyze this image in detail." })
                }
            )
        }
        return response.text ?: throw IllegalStateException("Model returned empty analysis.")
    }

    suspend fun analyzeDocumentOrVideoBytes(
        bytes: ByteArray,
        mimeType: String,
        promptText: String
    ): String {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash"
        )
        val response = withTimeout(60_000L) {
            model.generateContent(
                content {
                    inlineData(bytes, mimeType)
                    text(promptText.ifBlank { "Analyze this media and extract key insights." })
                }
            )
        }
        return response.text ?: throw IllegalStateException("Model returned empty analysis.")
    }

    suspend fun transcribeAudio(
        audioBytes: ByteArray,
        mimeType: String
    ): String {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash"
        )
        val response = withTimeout(60_000L) {
            model.generateContent(
                content {
                    inlineData(audioBytes, mimeType)
                    text("Transcribe the spoken words in this audio accurately into clean plain text with punctuation. Output only the transcript.")
                }
            )
        }
        return response.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("No speech detected in audio memo.")
    }

    @OptIn(com.google.firebase.ai.type.PublicPreviewAPI::class)
    suspend fun synthesizeSpeechPcm(
        textToSpeak: String,
        voiceName: String = "Kore"
    ): ByteArray {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.AUDIO)
            speechConfig = SpeechConfig(
                voice = Voice(voiceName),
                languageCode = "en-US"
            )
        }
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.1-flash-tts-preview",
            generationConfig = config
        )
        val response = withTimeout(60_000L) {
            model.generateContent(textToSpeak.take(1500))
        }
        val part = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()
        if (part is InlineDataPart) {
            return part.inlineData
        }
        throw IllegalStateException("TTS model did not return audio data.")
    }

    suspend fun playPcmAudio(pcmBytes: ByteArray) = withContext(Dispatchers.IO) {
        if (pcmBytes.isEmpty()) return@withContext
        val sampleRate = 24000
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuf, pcmBytes.size)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bufferSize)
            .build()

        try {
            track.write(pcmBytes, 0, pcmBytes.size)
            track.play()
            val durationMs = (pcmBytes.size.toLong() * 1000L) / (sampleRate * 2L)
            kotlinx.coroutines.delay(durationMs + 250L)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback error", e)
        } finally {
            try {
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }
    }
}
