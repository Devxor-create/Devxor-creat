package com.example.ui.state

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.example.data.model.Conversation

data class ConversationSearchMatch(
    val conversation: Conversation,
    val matchedMessageSnippet: String? = null,
    val matchScore: Int = 0
)

object ConversationSearchUtils {

    fun tokenizeQuery(query: String): List<String> =
        query.trim()
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    fun filterConversationsByKeyword(
        conversations: List<Conversation>,
        query: String,
        cachedMessagesByConvId: Map<String, List<String>> = emptyMap()
    ): List<ConversationSearchMatch> {
        val tokens = tokenizeQuery(query)
        if (tokens.isEmpty()) {
            return conversations.map { ConversationSearchMatch(conversation = it) }
        }

        return conversations.mapNotNull { conv ->
            val cachedMsgs = cachedMessagesByConvId[conv.id].orEmpty()
            val combinedHaystack = buildString {
                append(conv.title).append('\n')
                append(conv.lastMessagePreview).append('\n')
                append(conv.modelId).append('\n')
                cachedMsgs.forEach { append(it).append('\n') }
            }

            val allTokensMatch = tokens.all { token ->
                combinedHaystack.contains(token, ignoreCase = true)
            }
            if (!allTokensMatch) return@mapNotNull null

            val titleMatches = tokens.count { conv.title.contains(it, ignoreCase = true) }
            val previewMatches = tokens.count { conv.lastMessagePreview.contains(it, ignoreCase = true) }

            val matchedMsg = cachedMsgs.firstOrNull { msg ->
                tokens.any { token ->
                    msg.contains(token, ignoreCase = true) &&
                        !conv.title.contains(token, ignoreCase = true) &&
                        !conv.lastMessagePreview.contains(token, ignoreCase = true)
                }
            } ?: cachedMsgs.firstOrNull { msg ->
                tokens.any { token -> msg.contains(token, ignoreCase = true) }
            }

            val snippet = matchedMsg?.let { extractSnippet(it, tokens) }
            val score = (titleMatches * 3) + (previewMatches * 2) + if (snippet != null) 1 else 0
            ConversationSearchMatch(
                conversation = conv,
                matchedMessageSnippet = snippet,
                matchScore = score
            )
        }
    }

    fun extractSnippet(text: String, tokens: List<String>, radiusChars: Int = 46): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return ""
        val firstIndex = tokens
            .mapNotNull { token ->
                val idx = normalized.indexOf(token, ignoreCase = true)
                if (idx >= 0) idx else null
            }
            .minOrNull() ?: 0

        val start = (firstIndex - radiusChars).coerceAtLeast(0)
        val end = (firstIndex + radiusChars * 2).coerceAtMost(normalized.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < normalized.length) "…" else ""
        return "$prefix${normalized.substring(start, end).trim()}$suffix"
    }

    fun highlightKeywords(
        text: String,
        query: String,
        highlightBackground: Color,
        highlightForeground: Color
    ): AnnotatedString {
        val tokens = tokenizeQuery(query)
        if (tokens.isEmpty() || text.isEmpty()) return AnnotatedString(text)

        return buildAnnotatedString {
            append(text)
            val lowerText = text.lowercase()
            tokens.forEach { token ->
                val lowerToken = token.lowercase()
                if (lowerToken.isEmpty()) return@forEach
                var searchFrom = 0
                while (searchFrom < lowerText.length) {
                    val found = lowerText.indexOf(lowerToken, startIndex = searchFrom)
                    if (found < 0) break
                    addStyle(
                        style = SpanStyle(
                            background = highlightBackground,
                            color = highlightForeground,
                            fontWeight = FontWeight.Bold
                        ),
                        start = found,
                        end = (found + lowerToken.length).coerceAtMost(text.length)
                    )
                    searchFrom = found + lowerToken.length
                }
            }
        }
    }
}
