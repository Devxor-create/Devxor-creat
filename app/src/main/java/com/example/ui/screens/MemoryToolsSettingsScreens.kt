package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.SpaceXAiEngine
import com.example.core.AppThemeMode
import com.example.data.model.AiMemory
import com.example.data.model.UserProfile
import com.example.ui.components.SystemPromptBottomSheet
import com.example.ui.state.SpaceXRoute

@Composable
fun MemoryScreen(
    memoryEnabled: Boolean,
    memories: List<AiMemory>,
    onToggleMemory: (Boolean) -> Unit,
    onSaveMemory: (id: String?, content: String, category: String) -> Unit,
    onDeleteMemory: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AiMemory?>(null) }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Preference") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("AI MEMORY & PERSONALIZATION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("Saved Memories", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable SpaceX AI Memory", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "When enabled, Gemini honors these saved preferences in conversations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = memoryEnabled, onCheckedChange = onToggleMemory)
            }
        }

        Button(
            onClick = {
                editing = null
                content = ""
                category = "Preference"
                showDialog = true
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add Memory", fontWeight = FontWeight.Bold)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(memories, key = { it.id }) { mem ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mem.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(mem.content, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = {
                            editing = mem
                            content = mem.content
                            category = mem.category
                            showDialog = true
                        }) {
                            Icon(Icons.Outlined.Edit, "Edit memory")
                        }
                        IconButton(onClick = { onDeleteMemory(mem.id) }) {
                            Icon(Icons.Outlined.Delete, "Delete memory", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editing == null) "Add Memory" else "Edit Memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Preference", "Coding", "Project", "Style").forEach { cat ->
                            FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                        }
                    }
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Memory Detail") },
                        placeholder = { Text("e.g., Always explain with concise Kotlin examples") },
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (content.isNotBlank()) {
                            onSaveMemory(editing?.id, content, category)
                            showDialog = false
                        }
                    },
                    enabled = content.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ToolsAndModelsScreen(
    selectedModelId: String,
    onSelectModel: (String) -> Unit,
    onNavigate: (SpaceXRoute) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("CAPABILITIES & INTEGRATIONS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("Active Models & Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "All tools below are natively wired through Firebase AI Logic and Android system services.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(SpaceXAiEngine.supportedModels) { model ->
            val selected = model.id == selectedModelId
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(model.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(model.badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { onSelectModel(model.id) },
                        enabled = !selected,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (selected) "Active Default" else "Set as Default")
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text("BUILT-IN INTEGRATIONS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }

        items(SpaceXAiEngine.builtInTools) { tool ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Extension, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${tool.category} • ${tool.statusLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(tool.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onNavigate(SpaceXRoute.CreativeStudio) }) { Text("Open Media AI") }
                OutlinedButton(onClick = { onNavigate(SpaceXRoute.CodeStudio) }) { Text("Open Code Studio") }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    profile: UserProfile?,
    userEmail: String?,
    themeMode: AppThemeMode,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSaveProfile: (displayName: String, username: String, bio: String, language: String, style: String) -> Unit,
    onSaveSystemPrompt: (String) -> Unit = {},
    onToggleMemory: (Boolean) -> Unit,
    onExportAllData: () -> Unit,
    onSignOut: () -> Unit
) {
    var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var username by remember(profile?.username) { mutableStateOf(profile?.username.orEmpty()) }
    var bio by remember(profile?.bio) { mutableStateOf(profile?.bio.orEmpty()) }
    var language by remember(profile?.language) { mutableStateOf(profile?.language ?: "English") }
    var responseStyle by remember(profile?.responseStyle) { mutableStateOf(profile?.responseStyle ?: "Balanced") }
    var showSystemPromptSheet by remember { mutableStateOf(false) }
    val activeCustomPrompt = profile?.customSystemPrompt?.trim().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("SETTINGS & ACCOUNT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("Mission Configuration", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // Connected Google Account & Subscription Tier
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("CONNECTED ACCOUNT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(userEmail ?: profile?.email ?: "Google Authenticated User", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Plan: SpaceX AI ${profile?.tier ?: "FREE"} • Firestore Cloud Isolation Active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Profile Editor
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("PROFILE & AI PERSONA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = language, onValueChange = { language = it }, label = { Text("Preferred Language") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = responseStyle, onValueChange = { responseStyle = it }, label = { Text("Response Style") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { onSaveProfile(displayName, username, bio, language, responseStyle) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("settings_save_profile_button")
                ) {
                    Icon(Icons.Outlined.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Profile Changes")
                }
            }
        }

        // Custom System Prompt Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (activeCustomPrompt.isNotEmpty()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(18.dp)
                )
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CUSTOM SYSTEM PROMPT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = if (activeCustomPrompt.isNotEmpty()) "ACTIVE" else "DEFAULT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (activeCustomPrompt.isNotEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (activeCustomPrompt.isNotEmpty()) activeCustomPrompt
                    else "Define a custom persona, role, or specialized instructions for SpaceX AI across every chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (activeCustomPrompt.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showSystemPromptSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("settings_system_prompt_button")
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (activeCustomPrompt.isNotEmpty()) "Edit System Prompt" else "Set System Prompt")
                    }
                    if (activeCustomPrompt.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { onSaveSystemPrompt("") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("settings_clear_system_prompt_button")
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }
        }

        // Appearance & Memory Preferences
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("APPEARANCE & THEME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = themeMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("theme_mode_selector")
                ) {
                    val modes = AppThemeMode.entries
                    modes.forEachIndexed { index, mode ->
                        val icon = when (mode) {
                            AppThemeMode.LIGHT -> Icons.Outlined.LightMode
                            AppThemeMode.DARK -> Icons.Outlined.DarkMode
                            AppThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
                        }
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { onSelectThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = themeMode == mode) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("theme_mode_${mode.storageKey}")
                        ) {
                            Text(
                                text = mode.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("AI Memory Enabled", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = profile?.memoryEnabled ?: true, onCheckedChange = onToggleMemory)
                }
            }
        }

        // Data Export & Sign Out
        OutlinedButton(
            onClick = onExportAllData,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Export Account Data (JSON)")
        }

        Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("sign_out_button")
        ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sign Out", fontWeight = FontWeight.Bold)
        }
    }

    if (showSystemPromptSheet) {
        SystemPromptBottomSheet(
            currentPrompt = activeCustomPrompt,
            onDismiss = { showSystemPromptSheet = false },
            onSavePrompt = onSaveSystemPrompt
        )
    }
}

@Composable
fun HelpSupportScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("HELP & DOCUMENTATION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("SpaceX AI Guide", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        listOf(
            "AI Chat & Grounding" to "Toggle Web Search or Google Maps in the chat composer to ground answers in live web sources and places. Enable High Thinking for complex math, STEM, and code reasoning.",
            "Code, Website & Game Studios" to "Generate full HTML/CSS/JS websites and playable HTML5 Canvas games, edit code across file tabs, toggle mobile/desktop viewports, and save directly to your cloud workspace.",
            "Creative Media Studio" to "Generate or edit images using Nano Banana 2.1 and Gemini 3 Pro Image with custom aspect ratios and 1K-4K outputs, or analyze uploaded photos and videos.",
            "Privacy & Isolation" to "Every conversation, project, library item, and memory is isolated per Google-authenticated user inside Firebase Firestore security rules."
        ).forEach { (title, desc) ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
