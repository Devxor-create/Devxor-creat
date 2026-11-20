package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class SystemPromptPreset(
    val title: String,
    val prompt: String
)

object SystemPromptPresets {
    const val MAX_PROMPT_LENGTH = 2000

    val items: List<SystemPromptPreset> = listOf(
        SystemPromptPreset(
            title = "Orbital Engineer",
            prompt = "You are a principal aerospace propulsion and orbital mechanics engineer. Answer with rigorous first-principles physics, explicit units, trade-off tables, and safety margins."
        ),
        SystemPromptPreset(
            title = "Senior Code Architect",
            prompt = "You are a staff software architect. Prioritize idiomatic, production-grade code, clean architecture boundaries, edge-case handling, and concise rationale without filler."
        ),
        SystemPromptPreset(
            title = "Socratic Tutor",
            prompt = "Act as a patient Socratic tutor. Break complex ideas into intuitive steps, ask guiding questions to test understanding, and offer clear worked examples."
        ),
        SystemPromptPreset(
            title = "Concise Executive",
            prompt = "Respond with executive brevity: lead with a one-sentence bottom-line takeaway, followed by tight bullet points with actionable recommendations."
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SystemPromptBottomSheet(
    currentPrompt: String,
    onDismiss: () -> Unit,
    onSavePrompt: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftPrompt by remember(currentPrompt) {
        mutableStateOf(currentPrompt.take(SystemPromptPresets.MAX_PROMPT_LENGTH))
    }
    val hasActivePrompt = draftPrompt.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("system_prompt_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Custom System Prompt",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Define the AI's persona, tone, and rules across replies.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (currentPrompt.isNotBlank()) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = if (currentPrompt.isNotBlank()) "ACTIVE" else "DEFAULT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (currentPrompt.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "QUICK PERSONA PRESETS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SystemPromptPresets.items.forEach { preset ->
                    AssistChip(
                        onClick = {
                            draftPrompt = preset.prompt.take(SystemPromptPresets.MAX_PROMPT_LENGTH)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(preset.title) },
                        modifier = Modifier.testTag(
                            "system_prompt_preset_${preset.title.lowercase().replace(' ', '_')}"
                        )
                    )
                }
            }

            OutlinedTextField(
                value = draftPrompt,
                onValueChange = { updated ->
                    draftPrompt = updated.take(SystemPromptPresets.MAX_PROMPT_LENGTH)
                },
                label = { Text("System Prompt Instructions") },
                placeholder = {
                    Text("e.g., Act as a senior Kotlin & Jetpack Compose mentor. Give concise code-first answers with edge-case notes.")
                },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(16.dp),
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Applied automatically to every chat response.")
                        Text("${draftPrompt.length}/${SystemPromptPresets.MAX_PROMPT_LENGTH}")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("system_prompt_input")
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        RoundedCornerShape(14.dp)
                    )
            ) {
                Text(
                    text = if (hasActivePrompt) {
                        "Tip: Saved to your profile in Cloud Firestore so your custom persona stays active across sessions."
                    } else {
                        "Leave blank and save (or tap Clear) to return to the default SpaceX AI assistant persona."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onSavePrompt("")
                        onDismiss()
                    },
                    enabled = currentPrompt.isNotBlank() || draftPrompt.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("system_prompt_clear_button")
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear")
                }

                Button(
                    onClick = {
                        onSavePrompt(draftPrompt.trim())
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .testTag("system_prompt_save_button")
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Prompt", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}
