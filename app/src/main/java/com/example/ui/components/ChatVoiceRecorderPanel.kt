package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.RecordedAudioMemo
import com.example.core.formatAudioDuration

enum class ChatVoiceMode {
    DICTATION,
    AUDIO_MEMO
}

@Composable
fun ChatVoiceRecorderPanel(
    mode: ChatVoiceMode,
    isDictating: Boolean,
    isRecordingMemo: Boolean,
    elapsedMs: Long,
    amplitudes: List<Float>,
    liveTranscript: String,
    speechAvailable: Boolean,
    errorMessage: String?,
    onSelectMode: (ChatVoiceMode) -> Unit,
    onRestartDictation: () -> Unit,
    onCommitDictation: () -> Unit,
    onStartMemoRecording: () -> Unit,
    onStopMemoRecording: () -> Unit,
    onCancel: () -> Unit
) {
    val activeColor = if (mode == ChatVoiceMode.AUDIO_MEMO && isRecordingMemo) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .border(1.dp, activeColor.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .testTag("chat_voice_panel")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Mode Switcher & Timer Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == ChatVoiceMode.DICTATION,
                        onClick = { onSelectMode(ChatVoiceMode.DICTATION) },
                        label = { Text("Voice-to-Text") },
                        leadingIcon = { Icon(Icons.Outlined.Mic, null, Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("voice_mode_dictation")
                    )
                    FilterChip(
                        selected = mode == ChatVoiceMode.AUDIO_MEMO,
                        onClick = { onSelectMode(ChatVoiceMode.AUDIO_MEMO) },
                        label = { Text("Audio Memo") },
                        leadingIcon = { Icon(Icons.Outlined.GraphicEq, null, Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("voice_mode_memo")
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isLive = (mode == ChatVoiceMode.DICTATION && isDictating) ||
                        (mode == ChatVoiceMode.AUDIO_MEMO && isRecordingMemo)
                    if (isLive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(activeColor)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (mode == ChatVoiceMode.AUDIO_MEMO) {
                            formatAudioDuration(elapsedMs)
                        } else if (isDictating) {
                            "LIVE"
                        } else {
                            "READY"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold,
                        color = activeColor
                    )
                }
            }

            // Live Waveform Visualizer
            LiveWaveformBars(
                amplitudes = amplitudes,
                barColor = activeColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )

            // Live Transcription Preview or Memo Status
            if (mode == ChatVoiceMode.DICTATION) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            liveTranscript.isNotBlank() -> liveTranscript
                            !speechAvailable -> "On-device speech recognizer is unavailable here. Switch to Audio Memo to record and transcribe with Gemini AI."
                            isDictating -> "Listening... speak clearly into your microphone."
                            else -> "Tap Listen to dictate directly into your message."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (liveTranscript.isNotBlank()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                Text(
                    text = if (isRecordingMemo) {
                        "Recording AAC audio memo... tap Stop & Attach when finished."
                    } else {
                        "Record an audio memo to attach directly to Gemini or transcribe into text."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            errorMessage?.takeIf { it.isNotBlank() }?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("voice_cancel_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel")
                }

                Spacer(Modifier.weight(1f))

                if (mode == ChatVoiceMode.DICTATION) {
                    if (!isDictating && speechAvailable) {
                        OutlinedButton(
                            onClick = onRestartDictation,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Listen")
                        }
                    }
                    if (!speechAvailable) {
                        Button(
                            onClick = { onSelectMode(ChatVoiceMode.AUDIO_MEMO) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Use Audio Memo")
                        }
                    } else {
                        Button(
                            onClick = onCommitDictation,
                            enabled = liveTranscript.isNotBlank() || isDictating,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("voice_insert_text_button")
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (liveTranscript.isNotBlank()) "Insert Text" else "Stop")
                        }
                    }
                } else {
                    if (isRecordingMemo) {
                        Button(
                            onClick = onStopMemoRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("voice_record_toggle_button")
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop & Attach")
                        }
                    } else {
                        Button(
                            onClick = onStartMemoRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("voice_record_toggle_button")
                        ) {
                            Icon(Icons.Rounded.FiberManualRecord, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start Recording")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachedAudioMemoCard(
    memo: RecordedAudioMemo,
    isPlaying: Boolean,
    playbackProgress: Float,
    isTranscribing: Boolean,
    onTogglePlay: () -> Unit,
    onTranscribeToText: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .testTag("attached_audio_memo_card")
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .testTag("audio_memo_play_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause audio memo" else "Play audio memo",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Voice Memo (${memo.formattedDuration})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${(memo.bytes.size / 1024).coerceAtLeast(1)} KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    if (isPlaying) {
                        LinearProgressIndicator(
                            progress = { playbackProgress.coerceIn(0f, 1f) },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    } else {
                        LiveWaveformBars(
                            amplitudes = memo.waveformSamples,
                            barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                        )
                    }
                }

                TextButton(
                    onClick = onTranscribeToText,
                    enabled = !isTranscribing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("audio_memo_transcribe_button")
                ) {
                    if (isTranscribing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Transcribe")
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag("audio_memo_remove_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove audio memo")
                }
            }
        }
    }
}

@Composable
fun MicPermissionPromptCard(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .testTag("mic_permission_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Microphone Access Needed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = if (permanentlyDenied) {
                    "Microphone permission was blocked in Android settings. Open App Settings to enable real-time voice recording and dictation."
                } else {
                    "SpaceX AI needs microphone permission to record audio memos or transcribe your voice in real time."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Not now")
                }
                Spacer(Modifier.width(8.dp))
                if (permanentlyDenied) {
                    Button(
                        onClick = onOpenAppSettings,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Outlined.Settings, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("App Settings")
                    }
                } else {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Allow Microphone")
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveWaveformBars(
    amplitudes: List<Float>,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val samples = if (amplitudes.isEmpty()) List(20) { 0.15f } else amplitudes
    val animated = samples.map { target ->
        val smoothed by animateFloatAsState(
            targetValue = target.coerceIn(0.08f, 1f),
            animationSpec = tween(durationMillis = 80),
            label = "waveform_bar"
        )
        smoothed
    }
    Canvas(modifier = modifier) {
        val count = animated.size.coerceAtLeast(1)
        val slotWidth = size.width / count.toFloat()
        val barWidth = (slotWidth * 0.56f).coerceAtLeast(3f)
        val centerY = size.height / 2f

        animated.forEachIndexed { index, level ->
            val barHeight = (size.height * level).coerceIn(4f, size.height)
            val left = index * slotWidth + (slotWidth - barWidth) / 2f
            val top = centerY - barHeight / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
