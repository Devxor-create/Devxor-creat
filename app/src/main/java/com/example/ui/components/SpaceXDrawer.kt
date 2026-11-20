package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
import com.example.ui.state.SpaceXRoute
import com.example.ui.state.groupConversationsByDate

@Composable
fun SpaceXDrawerContent(
    currentRoute: SpaceXRoute,
    profile: UserProfile?,
    conversations: List<Conversation>,
    activeConversationId: String?,
    onNewChat: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (Conversation, String) -> Unit,
    onTogglePin: (Conversation) -> Unit,
    onToggleArchive: (Conversation) -> Unit,
    onDuplicate: (Conversation) -> Unit,
    onShareConversation: (Conversation) -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    onNavigate: (SpaceXRoute) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }

    val filteredConversations = remember(conversations, searchQuery, showArchived) {
        conversations.filter { conv ->
            (showArchived || !conv.archived) &&
                (searchQuery.isBlank() ||
                    conv.title.contains(searchQuery, ignoreCase = true) ||
                    conv.lastMessagePreview.contains(searchQuery, ignoreCase = true))
        }
    }
    val pinnedList = remember(filteredConversations) { filteredConversations.filter { it.pinned } }
    val recentList = remember(filteredConversations) { filteredConversations.filter { !it.pinned } }
    val recentDateSections = remember(recentList) { groupConversationsByDate(recentList) }

    ModalDrawerSheet(
        modifier = Modifier.width(310.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 16.dp)
        ) {
            // Brand Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RocketLaunch,
                        contentDescription = "SpaceX AI",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SpaceX AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = profile?.displayName?.let { "@${profile.username} • ${profile.tier}" } ?: "Command Center",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // New Chat Button
            Button(
                onClick = onNewChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("drawer_new_chat_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("New Chat", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))

            // Search Conversations
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search conversations...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_search_input")
            )

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Studio & Core Navigation Modules
                item {
                    SectionHeader("WORKSPACES & STUDIOS")
                    DrawerModuleRow(
                        label = "AI Chat",
                        icon = Icons.Rounded.ChatBubbleOutline,
                        selected = currentRoute == SpaceXRoute.Chat,
                        testTag = "nav_chat",
                        onClick = { onNavigate(SpaceXRoute.Chat) }
                    )
                    DrawerModuleRow(
                        label = "Conversation History",
                        icon = Icons.Outlined.History,
                        selected = currentRoute == SpaceXRoute.History,
                        testTag = "nav_history",
                        onClick = { onNavigate(SpaceXRoute.History) }
                    )
                    DrawerModuleRow(
                        label = "Code Studio",
                        icon = Icons.Outlined.Code,
                        selected = currentRoute == SpaceXRoute.CodeStudio,
                        testTag = "nav_code_studio",
                        onClick = { onNavigate(SpaceXRoute.CodeStudio) }
                    )
                    DrawerModuleRow(
                        label = "Website Builder",
                        icon = Icons.Outlined.Language,
                        selected = currentRoute == SpaceXRoute.WebsiteBuilder,
                        testTag = "nav_website_builder",
                        onClick = { onNavigate(SpaceXRoute.WebsiteBuilder) }
                    )
                    DrawerModuleRow(
                        label = "Game Studio",
                        icon = Icons.Outlined.SportsEsports,
                        selected = currentRoute == SpaceXRoute.GameStudio,
                        testTag = "nav_game_studio",
                        onClick = { onNavigate(SpaceXRoute.GameStudio) }
                    )
                    DrawerModuleRow(
                        label = "Media & Image AI",
                        icon = Icons.Outlined.Image,
                        selected = currentRoute == SpaceXRoute.CreativeStudio,
                        testTag = "nav_creative_studio",
                        onClick = { onNavigate(SpaceXRoute.CreativeStudio) }
                    )
                    DrawerModuleRow(
                        label = "Voice AI",
                        icon = Icons.Outlined.Mic,
                        selected = currentRoute == SpaceXRoute.VoiceAi,
                        testTag = "nav_voice_ai",
                        onClick = { onNavigate(SpaceXRoute.VoiceAi) }
                    )
                    DrawerModuleRow(
                        label = "Projects",
                        icon = Icons.Outlined.AutoAwesome,
                        selected = currentRoute == SpaceXRoute.Projects,
                        testTag = "nav_projects",
                        onClick = { onNavigate(SpaceXRoute.Projects) }
                    )
                    DrawerModuleRow(
                        label = "Library",
                        icon = Icons.Outlined.Folder,
                        selected = currentRoute == SpaceXRoute.Library,
                        testTag = "nav_library",
                        onClick = { onNavigate(SpaceXRoute.Library) }
                    )
                    DrawerModuleRow(
                        label = "Memory",
                        icon = Icons.Outlined.Psychology,
                        selected = currentRoute == SpaceXRoute.Memory,
                        testTag = "nav_memory",
                        onClick = { onNavigate(SpaceXRoute.Memory) }
                    )
                    DrawerModuleRow(
                        label = "Tools & Models",
                        icon = Icons.Outlined.Extension,
                        selected = currentRoute == SpaceXRoute.ExploreTools,
                        testTag = "nav_tools",
                        onClick = { onNavigate(SpaceXRoute.ExploreTools) }
                    )
                }

                if (pinnedList.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        SectionHeader("PINNED CONVERSATIONS")
                    }
                    items(pinnedList, key = { "pin_${it.id}" }) { conv ->
                        ConversationDrawerItem(
                            conv = conv,
                            isActive = conv.id == activeConversationId && currentRoute == SpaceXRoute.Chat,
                            onSelect = { onSelectConversation(conv.id) },
                            onRename = {
                                renameTarget = conv
                                renameInput = conv.title
                            },
                            onTogglePin = { onTogglePin(conv) },
                            onToggleArchive = { onToggleArchive(conv) },
                            onDuplicate = { onDuplicate(conv) },
                            onShare = { onShareConversation(conv) },
                            onDelete = { deleteTarget = conv }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionHeader("RECENT CONVERSATIONS")
                        Row {
                            TextButton(onClick = { onNavigate(SpaceXRoute.History) }) {
                                Text(
                                    text = "View All",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            TextButton(onClick = { showArchived = !showArchived }) {
                                Text(
                                    text = if (showArchived) "Hide Archived" else "Archived",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                if (recentList.isEmpty()) {
                    item {
                        Text(
                            text = "No conversations yet. Start a new chat above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    recentDateSections.forEach { section ->
                        item(key = "drawer_date_${section.label}") {
                            Text(
                                text = section.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp)
                            )
                        }
                        items(section.items, key = { "rec_${it.id}" }) { conv ->
                            ConversationDrawerItem(
                                conv = conv,
                                isActive = conv.id == activeConversationId && currentRoute == SpaceXRoute.Chat,
                                onSelect = { onSelectConversation(conv.id) },
                                onRename = {
                                    renameTarget = conv
                                    renameInput = conv.title
                                },
                                onTogglePin = { onTogglePin(conv) },
                                onToggleArchive = { onToggleArchive(conv) },
                                onDuplicate = { onDuplicate(conv) },
                                onShare = { onShareConversation(conv) },
                                onDelete = { deleteTarget = conv }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DrawerModuleRow(
                    label = "Settings",
                    icon = Icons.Outlined.Settings,
                    selected = currentRoute == SpaceXRoute.Settings,
                    testTag = "nav_settings",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SpaceXRoute.Settings) }
                )
                DrawerModuleRow(
                    label = "Help",
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    selected = currentRoute == SpaceXRoute.Help,
                    testTag = "nav_help",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SpaceXRoute.Help) }
                )
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Title") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            onRenameConversation(target, renameInput.trim())
                        }
                        renameTarget = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { target ->
        DeleteConversationConfirmationDialog(
            conversation = target,
            onConfirmDelete = {
                onDeleteConversation(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
            onArchiveInstead = {
                onToggleArchive(target)
                deleteTarget = null
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun DrawerModuleRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = bg,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConversationDrawerItem(
    conv: Conversation,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val bg = if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(
                if (isActive) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (conv.pinned) {
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = "Pinned",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conv.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (conv.archived) {
                Text(
                    text = "Archived",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "Conversation actions",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = { menuExpanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text(if (conv.pinned) "Unpin" else "Pin") },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                    onClick = { menuExpanded = false; onTogglePin() }
                )
                DropdownMenuItem(
                    text = { Text(if (conv.archived) "Unarchive" else "Archive") },
                    leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                    onClick = { menuExpanded = false; onToggleArchive() }
                )
                DropdownMenuItem(
                    text = { Text("Duplicate") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                    onClick = { menuExpanded = false; onDuplicate() }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Outlined.Share, null) },
                    onClick = { menuExpanded = false; onShare() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}
