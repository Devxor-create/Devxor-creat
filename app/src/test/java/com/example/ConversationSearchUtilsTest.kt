package com.example

import androidx.compose.ui.graphics.Color
import com.example.data.model.Conversation
import com.example.ui.state.ConversationSearchUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSearchUtilsTest {

    private val conversations = listOf(
        Conversation(
            id = "c1",
            title = "Starship Orbital Trajectory",
            modelId = "gemini-3.8-flash",
            lastMessagePreview = "Use Hohmann transfer delta-v equations."
        ),
        Conversation(
            id = "c2",
            title = "Jetpack Compose Animation",
            modelId = "gemini-3.5-flash-lite",
            lastMessagePreview = "Try animateFloatAsState with spring."
        ),
        Conversation(
            id = "c3",
            title = "Weekly Meal Prep",
            modelId = "gemini-3.8-flash",
            lastMessagePreview = "High-protein vegetarian bowl ideas."
        )
    )

    @Test
    fun `empty or whitespace query returns all conversations`() {
        val result = ConversationSearchUtils.filterConversationsByKeyword(conversations, "   ")
        assertEquals(3, result.size)
    }

    @Test
    fun `multi-keyword query matches across title and message cache`() {
        val messageCache = mapOf(
            "c1" to listOf("Remember to check propellant mass fraction during ascent."),
            "c2" to listOf("Use LaunchedEffect for one-shot animations.")
        )

        val matches = ConversationSearchUtils.filterConversationsByKeyword(
            conversations = conversations,
            query = "orbital propellant",
            cachedMessagesByConvId = messageCache
        )

        assertEquals(1, matches.size)
        assertEquals("c1", matches.first().conversation.id)
        assertNotNull(matches.first().matchedMessageSnippet)
        assertTrue(matches.first().matchedMessageSnippet!!.contains("propellant", ignoreCase = true))
    }

    @Test
    fun `highlightKeywords applies span annotations to all occurrences`() {
        val annotated = ConversationSearchUtils.highlightKeywords(
            text = "Orbital flight and orbital mechanics",
            query = "orbital",
            highlightBackground = Color.Yellow,
            highlightForeground = Color.Black
        )
        assertEquals(2, annotated.spanStyles.size)
    }
}
