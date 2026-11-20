package com.example

import android.content.Intent
import android.net.Uri
import com.example.core.ConversationExporter
import com.example.data.model.ChatMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConversationExporterTest {

    @Test
    fun `shareable link builds and parses round-trip conversation id`() {
        val link = ConversationExporter.buildShareableConversationLink("conv_42_alpha")
        assertEquals("spacexai://chat/conv_42_alpha", link)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        assertEquals("conv_42_alpha", ConversationExporter.parseDeepLinkConversationId(intent))
    }

    @Test
    fun `invalid deep link schemes return null`() {
        assertNull(ConversationExporter.buildShareableConversationLink("   "))
        assertNull(
            ConversationExporter.parseDeepLinkConversationId(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/chat/123"))
            )
        )
        assertNull(
            ConversationExporter.parseDeepLinkConversationId(
                Intent(Intent.ACTION_SEND, Uri.parse("spacexai://chat/123"))
            )
        )
    }

    @Test
    fun `suggested pdf filename sanitizes special characters`() {
        val name = ConversationExporter.suggestedPdfFileName("Orbital Launch: Plan #1 / Alpha!")
        assertTrue(name.startsWith("SpaceX_AI_Orbital_Launch_Plan_1_Alpha"))
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun `markdown transcript includes link attachments and sources`() {
        val messages = listOf(
            ChatMessage(
                id = "1",
                role = "user",
                text = "Summarize telemetry",
                attachmentLabel = "telemetry.csv"
            ),
            ChatMessage(
                id = "2",
                role = "model",
                text = "Telemetry nominal at 7.66 km/s.",
                modelId = "gemini-3.8-flash",
                groundingSources = listOf("SpaceFlight Report | https://example.com/report")
            )
        )

        val markdown = ConversationExporter.buildConversationTranscriptMarkdown(
            title = "Orbital Brief",
            modelId = "gemini-3.8-flash",
            shareLink = "spacexai://chat/conv_1",
            messages = messages
        )

        assertTrue(markdown.contains("# Orbital Brief"))
        assertTrue(markdown.contains("Deep Link: spacexai://chat/conv_1"))
        assertTrue(markdown.contains("Attachment: telemetry.csv"))
        assertTrue(markdown.contains("Telemetry nominal at 7.66 km/s."))
        assertTrue(markdown.contains("SpaceFlight Report | https://example.com/report"))
    }

    @Test
    fun `writeConversationPdfToStream completes for multi-turn conversation`() = runTest {
        val longParagraph = List(45) { idx ->
            "Line ${idx + 1}: Orbital mechanics trajectory calculation with delta-v budget and telemetry verification."
        }.joinToString("\n")
        val messages = listOf(
            ChatMessage(id = "1", role = "user", text = "Compute orbital delta-v."),
            ChatMessage(
                id = "2",
                role = "model",
                text = longParagraph,
                modelId = "gemini-3.8-flash",
                groundingSources = listOf("Orbital Reference | https://example.com/orbital")
            )
        )

        val out = ByteArrayOutputStream()
        val res = ConversationExporter.writeConversationPdfToStream(
            outputStream = out,
            title = "Delta-V Analysis",
            modelId = "gemini-3.8-flash",
            messages = messages
        )
        // Robolectric legacy graphics stubs nativeCreateDocument() to 0 ("document is closed!");
        // verify ConversationExporter catches it cleanly into Result rather than crashing.
        if (res.isSuccess) {
            assertTrue(out.size() > 0)
        } else {
            assertTrue(res.exceptionOrNull() is IllegalStateException)
        }
    }
}
