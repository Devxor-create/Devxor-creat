package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StudioProject
import com.example.ui.components.MarkdownText
import com.example.ui.components.SandboxedWebPreview
import com.example.ui.state.CodeStudioState
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun CodeStudioScreen(
    state: CodeStudioState,
    savedProjects: List<StudioProject>,
    onUpdateState: ((CodeStudioState) -> CodeStudioState) -> Unit,
    onRunAiAssistant: (action: String, instruction: String) -> Unit,
    onSaveProject: () -> Unit,
    onLoadProject: (StudioProject) -> Unit,
    onExportCode: (title: String, content: String) -> Unit
) {
    val context = LocalContext.current
    var aiPrompt by remember { mutableStateOf("") }
    var reloadNonce by remember { mutableIntStateOf(0) }
    val languages = listOf("HTML", "JavaScript", "TypeScript", "Python", "Kotlin", "C++", "Java")
    val tabs = listOf(
        if (state.language in listOf("HTML", "JavaScript", "TypeScript")) "index.html" else "main.${state.language.lowercase()}",
        "styles.css",
        "script.js",
        "Preview",
        "Console"
    )

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r ->
                    r.readText().take(80000)
                }.orEmpty()
                if (text.isNotBlank()) {
                    onUpdateState { s -> s.copy(htmlCode = text, activeTab = 0) }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedTextField(
                value = state.projectName,
                onValueChange = { v -> onUpdateState { it.copy(projectName = v) } },
                label = { Text("Project") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { onUpdateState { it.copy(editorThemeDark = !it.editorThemeDark) } }
            ) {
                Icon(Icons.Outlined.Brightness6, contentDescription = "Toggle editor theme")
            }
            IconButton(onClick = { importPicker.launch(arrayOf("text/*", "application/*")) }) {
                Icon(Icons.Outlined.FileOpen, contentDescription = "Import file")
            }
            IconButton(onClick = onSaveProject, modifier = Modifier.testTag("code_save_button")) {
                Icon(Icons.Outlined.Save, contentDescription = "Save project", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(
                onClick = {
                    val bundle = "<!-- ${state.projectName} -->\n${state.htmlCode}\n<style>\n${state.cssCode}\n</style>\n<script>\n${state.jsCode}\n</script>"
                    onExportCode(state.projectName, bundle)
                }
            ) {
                Icon(Icons.Outlined.Share, contentDescription = "Export code")
            }
            Button(
                onClick = {
                    reloadNonce++
                    onUpdateState {
                        it.copy(
                            activeTab = 3,
                            consoleLogs = it.consoleLogs + "Executed sandbox build #${reloadNonce}"
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("code_run_button")
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Run", fontWeight = FontWeight.Bold)
            }
        }

        // Language & Saved Projects Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            languages.forEach { lang ->
                FilterChip(
                    selected = state.language == lang,
                    onClick = { onUpdateState { it.copy(language = lang) } },
                    label = { Text(lang) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
            savedProjects.filter { it.studioType == "code" }.forEach { proj ->
                AssistChip(
                    onClick = { onLoadProject(proj) },
                    label = { Text("Load: ${proj.name}") }
                )
            }
        }

        // Code Tabs
        TabRow(
            selectedTabIndex = state.activeTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = state.activeTab == index,
                    onClick = { onUpdateState { it.copy(activeTab = index) } },
                    text = { Text(title, maxLines = 1, fontSize = 12.sp) }
                )
            }
        }

        // Active Editor / Preview / Console Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val editorBg = if (state.editorThemeDark) Color(0xFF06101E) else Color(0xFFF8FAFC)
            val editorFg = if (state.editorThemeDark) Color(0xFFE6F1FF) else Color(0xFF0F172A)

            when (state.activeTab) {
                0, 1, 2 -> {
                    val codeValue = when (state.activeTab) {
                        0 -> state.htmlCode
                        1 -> state.cssCode
                        else -> state.jsCode
                    }
                    OutlinedTextField(
                        value = codeValue,
                        onValueChange = { updated ->
                            onUpdateState {
                                when (state.activeTab) {
                                    0 -> it.copy(htmlCode = updated)
                                    1 -> it.copy(cssCode = updated)
                                    else -> it.copy(jsCode = updated)
                                }
                            }
                        },
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = editorFg
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = editorBg,
                            unfocusedContainerColor = editorBg,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("code_editor_field")
                    )
                }
                3 -> {
                    SandboxedWebPreview(
                        htmlBody = state.htmlCode,
                        cssCode = state.cssCode,
                        jsCode = state.jsCode,
                        reloadNonce = reloadNonce,
                        onConsoleLog = { line ->
                            onUpdateState { it.copy(consoleLogs = (it.consoleLogs + line).takeLast(40)) }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                4 -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF06101E),
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "SANDBOX TERMINAL",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            state.consoleLogs.forEach { logLine ->
                                Text(
                                    text = "> $logLine",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    color = Color(0xFFD7E6F7)
                                )
                            }
                            if (state.aiAssistantOutput.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "AI CODING ASSISTANT OUTPUT",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                MarkdownText(markdown = state.aiAssistantOutput)
                            }
                        }
                    }
                }
            }
        }

        // AI Coding Assistant Bar
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Write Code", "Explain", "Find & Fix Bugs", "Refactor").forEach { action ->
                        AssistChip(
                            onClick = {
                                onRunAiAssistant(action, aiPrompt.ifBlank { action })
                                onUpdateState { it.copy(activeTab = 4) }
                            },
                            label = { Text(action) },
                            leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(15.dp)) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        placeholder = { Text("Ask AI assistant to generate, debug, or refactor...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (aiPrompt.isNotBlank()) {
                                onRunAiAssistant("Generate", aiPrompt)
                                onUpdateState { it.copy(activeTab = 4) }
                            }
                        },
                        enabled = !state.isBusy && aiPrompt.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.isBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Ask AI")
                        }
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
