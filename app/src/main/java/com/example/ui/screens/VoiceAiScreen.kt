package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.SpaceXAiEngine
import com.example.ui.components.MarkdownText
import com.example.ui.state.VoiceSessionState

@Composable
fun VoiceAiScreen(
    state: VoiceSessionState,
    onSetListening: (Boolean) -> Unit,
    onSelectVoice: (String) -> Unit,
    onSendVoiceTurn: (String) -> Unit,
    onStopSession: () -> Unit
) {
    val context = LocalContext.current
    var manualPrompt by remember { mutableStateOf("") }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(speechRecognizer) {
        onDispose { speechRecognizer?.destroy() }
    }

    fun startVoiceCapture() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            onSetListening(false)
            return
        }
        onSetListening(true)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { onSetListening(false) }
            override fun onError(error: Int) { onSetListening(false) }
            override fun onResults(results: Bundle?) {
                onSetListening(false)
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    onSendVoiceTurn(spoken)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        recognizer.startListening(intent)
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceCapture()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SPACEX VOICE AI",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = when {
                state.isListening -> "Listening to your voice..."
                state.isThinking -> "Synthesizing response..."
                state.isSpeaking -> "Speaking (${state.selectedVoice})..."
                else -> "Tap microphone to talk with SpaceX AI"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // Voice Persona Chips
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpaceXAiEngine.ttsVoices.forEach { voice ->
                FilterChip(
                    selected = state.selectedVoice == voice,
                    onClick = { onSelectVoice(voice) },
                    label = { Text(voice) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        // Animated Orbital Pulse Ring
        val primaryColor = MaterialTheme.colorScheme.primary
        val active = state.isListening || state.isThinking || state.isSpeaking
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseRadius = size.minDimension * 0.34f
                val r1 = if (active) baseRadius * pulse else baseRadius
                drawCircle(
                    color = primaryColor.copy(alpha = if (active) 0.35f else 0.15f),
                    radius = r1 * 1.25f,
                    style = Stroke(width = 4f)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = if (active) 0.7f else 0.3f),
                    radius = r1,
                    style = Stroke(width = 6f)
                )
            }
            if (active) {
                Button(
                    onClick = onStopSession,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .size(88.dp)
                        .testTag("voice_stop_button")
                ) {
                    Icon(Icons.Rounded.Stop, "Stop voice session", Modifier.size(34.dp))
                }
            } else {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            startVoiceCapture()
                        } else {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .size(88.dp)
                        .testTag("voice_start_button")
                ) {
                    Icon(Icons.Rounded.Mic, "Start voice session", Modifier.size(34.dp))
                }
            }
        }

        // Fallback text-to-voice turn for environments without hardware mic
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = manualPrompt,
                onValueChange = { manualPrompt = it },
                placeholder = { Text("Or type a spoken prompt...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (manualPrompt.isNotBlank()) {
                        val t = manualPrompt
                        manualPrompt = ""
                        onSendVoiceTurn(t)
                    }
                },
                enabled = manualPrompt.isNotBlank() && !state.isThinking
            ) {
                Text("Speak")
            }
        }

        if (state.transcript.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("YOU SAID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(state.transcript, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (state.lastResponse.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("SPACEX AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    MarkdownText(markdown = state.lastResponse)
                }
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
