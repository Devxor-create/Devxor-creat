package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StudioProject
import com.example.ui.components.SandboxedWebPreview
import com.example.ui.state.BuilderStudioState
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun WebsiteOrGameStudioScreen(
    isGameStudio: Boolean,
    state: BuilderStudioState,
    savedProjects: List<StudioProject>,
    onGenerate: (String) -> Unit,
    onUpdateCode: (html: String?, css: String?, js: String?, desktop: Boolean?) -> Unit,
    onSave: () -> Unit,
    onLoadProject: (StudioProject) -> Unit,
    onExport: (title: String, content: String) -> Unit
) {
    var promptInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Preview, 1: HTML, 2: CSS, 3: JS

    val templates = if (isGameStudio) {
        listOf(
            "Create a neon space shooter game with enemy waves and score",
            "Create a retro snake arcade game with touch controls",
            "Create a brick breaker neon laser game"
        )
    } else {
        listOf(
            "Create a modern gaming website",
            "Create a futuristic aerospace startup landing page",
            "Create a minimalist developer portfolio with dark glass cards"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Generator Prompt Header
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isGameStudio) "SPACEX GAME STUDIO" else "SPACEX WEBSITE BUILDER",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = state.title.ifBlank { if (isGameStudio) "AI Game Generator" else "AI Website Generator" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row {
                        if (!isGameStudio) {
                            IconButton(
                                onClick = { onUpdateCode(null, null, null, !state.isDesktopPreview) }
                            ) {
                                Icon(
                                    imageVector = if (state.isDesktopPreview) Icons.Outlined.DesktopWindows
                                    else Icons.Outlined.PhoneAndroid,
                                    contentDescription = "Toggle desktop or mobile preview",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = onSave,
                            enabled = state.htmlCode.isNotBlank(),
                            modifier = Modifier.testTag("builder_save_button")
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = "Save project", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = {
                                val full = "<!DOCTYPE html><html><head><style>${state.cssCode}</style></head><body>${state.htmlCode}<script>${state.jsCode}</script></body></html>"
                                onExport(state.title.ifBlank { "SpaceX_Export" }, full)
                            },
                            enabled = state.htmlCode.isNotBlank()
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Export project")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        placeholder = {
                            Text(
                                if (isGameStudio) "Describe the HTML5/WebGL game to create..."
                                else "e.g., Create a modern gaming website..."
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("builder_prompt_input")
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (promptInput.isNotBlank()) {
                                onGenerate(promptInput)
                                selectedTab = 0
                            }
                        },
                        enabled = !state.isBusy && promptInput.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("builder_generate_button")
                    ) {
                        if (state.isBusy) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Build", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick Ideas & Saved Projects
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    templates.forEach { idea ->
                        AssistChip(
                            onClick = { promptInput = idea },
                            label = { Text(idea.take(36) + if (idea.length > 36) "..." else "") }
                        )
                    }
                    savedProjects.filter {
                        it.studioType == if (isGameStudio) "game" else "website"
                    }.forEach { proj ->
                        AssistChip(
                            onClick = { onLoadProject(proj); selectedTab = 0 },
                            label = { Text("Load: ${proj.name}") }
                        )
                    }
                }

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Tabs: Preview / HTML / CSS / JS
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            listOf("Live Preview", "HTML", "CSS", "JS").forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(label, fontSize = 12.sp) }
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> {
                    if (state.htmlCode.isBlank()) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(24.dp)) {
                                Text(
                                    text = state.summary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        SandboxedWebPreview(
                            htmlBody = state.htmlCode,
                            cssCode = state.cssCode,
                            jsCode = state.jsCode,
                            desktopMode = !isGameStudio && state.isDesktopPreview,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                1 -> CodeTextEditorBox(state.htmlCode) { onUpdateCode(it, null, null, null) }
                2 -> CodeTextEditorBox(state.cssCode) { onUpdateCode(null, it, null, null) }
                3 -> CodeTextEditorBox(state.jsCode) { onUpdateCode(null, null, it, null) }
            }
        }
    }
}

@Composable
private fun CodeTextEditorBox(code: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = code,
        onValueChange = onValueChange,
        textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, lineHeight = 18.sp),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxSize()
    )
}
