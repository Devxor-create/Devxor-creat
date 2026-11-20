package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.ui.components.BatchDeleteConversationsConfirmationDialog
import com.example.ui.components.DeleteConversationConfirmationDialog
import com.example.ui.components.MarkdownText
import com.example.ui.state.ConversationSearchUtils
import com.example.ui.state.HistoryInspectionState
import com.example.ui.state.groupConversationsByDate
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

private enum class HistoryFilter(val label: String) {
    ALL("All"),
    PINNED("Pinned"),
    ACTIVE("Active"),
    ARCHIVED("Archived")
}

private enum class HistorySort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    MOST_MESSAGES("Most Messages"),
    TITLE_AZ("Title A–Z")
}

@Composable
fun ConversationHistoryScreen(
    conversations: List<Conversation>,
    activeConversationId: String?,
    cachedMessagesByConvId: Map<String, List<String>> = emptyMap(),
    isDeepSearching: Boolean = false,
    onSearchQueryChanged: (String) -> Unit = {},
    onStartNewChat: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onInspectConversation: (String) -> Unit,
    onRenameConversation: (Conversation, String) -> Unit,
    onTogglePin: (Conversation) -> Unit,
    onToggleArchive: (Conversation) -> Unit,
    onDuplicate: (Conversation) -> Unit,
    onShareConversation: (Conversation) -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    onBatchPin: (List<String>, Boolean) -> Unit,
    onBatchArchive: (List<String>, Boolean) -> Unit,
    onBatchDelete: (List<String>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var selectedSort by remember { mutableStateOf(HistorySort.NEWEST) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        onSearchQueryChanged(searchQuery)
    }

    val totalCount = conversations.size
    val pinnedCount = remember(conversations) { conversations.count { it.pinned } }
    val archivedCount = remember(conversations) { conversations.count { it.archived } }
    val totalMessages = remember(conversations) { conversations.sumOf { it.messageCount } }

    val searchMatchByConvId = remember(conversations, searchQuery, selectedFilter, cachedMessagesByConvId) {
        val byFilter = conversations.filter { conv ->
            when (selectedFilter) {
                HistoryFilter.ALL -> true
                HistoryFilter.PINNED -> conv.pinned
                HistoryFilter.ACTIVE -> !conv.archived
                HistoryFilter.ARCHIVED -> conv.archived
            }
        }
        ConversationSearchUtils.filterConversationsByKeyword(
            conversations = byFilter,
            query = searchQuery,
            cachedMessagesByConvId = cachedMessagesByConvId
        ).associateBy { it.conversation.id }
    }

    val filteredConversations = remember(searchMatchByConvId, selectedSort) {
        val matchedConvs = searchMatchByConvId.values.map { it.conversation }
        when (selectedSort) {
            HistorySort.NEWEST -> matchedConvs.sortedWith(
                compareByDescending<Conversation> { it.updatedAt?.seconds ?: Long.MAX_VALUE }
                    .thenByDescending { it.pinned }
            )
            HistorySort.OLDEST -> matchedConvs.sortedBy { it.updatedAt?.seconds ?: Long.MAX_VALUE }
            HistorySort.MOST_MESSAGES -> matchedConvs.sortedByDescending { it.messageCount }
            HistorySort.TITLE_AZ -> matchedConvs.sortedBy { it.title.lowercase(Locale.getDefault()) }
        }
    }

    val useDateGrouping = selectedSort == HistorySort.NEWEST || selectedSort == HistorySort.OLDEST
    val dateSections = remember(filteredConversations, selectedSort) {
        if (useDateGrouping) {
            groupConversationsByDate(
                conversations = filteredConversations,
                oldestFirst = selectedSort == HistorySort.OLDEST
            )
        } else {
            emptyList()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Telemetry Header & Stats
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MISSION ARCHIVES",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Conversation History",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    selectionMode = !selectionMode
                                    if (!selectionMode) selectedIds = emptySet()
                                },
                                modifier = Modifier.testTag("history_select_mode_button")
                            ) {
                                Icon(Icons.Outlined.CheckBox, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (selectionMode) "Done" else "Manage")
                            }
                            Button(
                                onClick = onStartNewChat,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("history_new_chat_button")
                            ) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New")
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HistoryStatPill("Threads", totalCount.toString(), Modifier.weight(1f))
                        HistoryStatPill("Pinned", pinnedCount.toString(), Modifier.weight(1f))
                        HistoryStatPill("Archived", archivedCount.toString(), Modifier.weight(1f))
                        HistoryStatPill("Messages", totalMessages.toString(), Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Search + Sort Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search chats by keyword, topic, or message...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.testTag("history_search_clear_button")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_search_input")
                )

                Box {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            .clickable { sortMenuExpanded = true }
                            .testTag("history_sort_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Sort,
                                contentDescription = "Sort conversations",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        HistorySort.entries.forEach { sortOption ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (selectedSort == sortOption) "✓ ${sortOption.label}" else sortOption.label,
                                        fontWeight = if (selectedSort == sortOption) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    selectedSort = sortOption
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryFilter.entries.forEach { filter ->
                    val count = when (filter) {
                        HistoryFilter.ALL -> conversations.size
                        HistoryFilter.PINNED -> pinnedCount
                        HistoryFilter.ACTIVE -> conversations.count { !it.archived }
                        HistoryFilter.ARCHIVED -> archivedCount
                    }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text("${filter.label} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("history_filter_${filter.name.lowercase()}")
                    )
                }
            }

            AnimatedVisibility(visible = searchQuery.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isDeepSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Text(
                            text = if (isDeepSearching) {
                                "${filteredConversations.size} match(es) • scanning messages..."
                            } else {
                                "${filteredConversations.size} chat(s) matching \"${searchQuery.trim()}\""
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("history_search_results_summary")
                        )
                    }
                    TextButton(onClick = { searchQuery = "" }) {
                        Text("Clear")
                    }
                }
            }

            // Batch Management Toolbar
            AnimatedVisibility(visible = selectionMode) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedIds.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    val allVisible = filteredConversations.map { it.id }.toSet()
                                    selectedIds = if (selectedIds.containsAll(allVisible)) emptySet() else allVisible
                                }
                            ) {
                                Text("All")
                            }
                            IconButton(
                                enabled = selectedIds.isNotEmpty(),
                                onClick = {
                                    onBatchPin(selectedIds.toList(), true)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                }
                            ) {
                                Icon(Icons.Outlined.PushPin, "Pin selected")
                            }
                            IconButton(
                                enabled = selectedIds.isNotEmpty(),
                                onClick = {
                                    val archiveTarget = selectedFilter != HistoryFilter.ARCHIVED
                                    onBatchArchive(selectedIds.toList(), archiveTarget)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                }
                            ) {
                                Icon(
                                    if (selectedFilter == HistoryFilter.ARCHIVED) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                    "Archive or restore selected"
                                )
                            }
                            IconButton(
                                enabled = selectedIds.isNotEmpty(),
                                onClick = { showBatchDeleteConfirm = true }
                            ) {
                                Icon(Icons.Outlined.Delete, "Delete selected", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Conversations List
            if (filteredConversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching conversations" else "No conversations in this view",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "Try clearing your search query or switching filter tabs."
                            } else {
                                "Your cloud-synced Gemini conversations will appear here automatically."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = onStartNewChat) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start a New Chat")
                        }
                    }
                }
            } else {
                if (!selectionMode) {
                    Text(
                        text = "Tip: Swipe right to archive • Swipe left to delete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    if (useDateGrouping) {
                        dateSections.forEach { section ->
                            item(key = "section_header_${section.label}") {
                                HistoryDateSectionHeader(
                                    title = section.label,
                                    count = section.items.size
                                )
                            }
                            items(section.items, key = { it.id }) { conv ->
                                val isSelected = conv.id in selectedIds
                                val isActive = conv.id == activeConversationId
                                val matchedSnippet = searchMatchByConvId[conv.id]?.matchedMessageSnippet
                                SwipeableHistoryCard(
                                    conversation = conv,
                                    selectionMode = selectionMode,
                                    onSwipeArchive = { onToggleArchive(conv) },
                                    onSwipeDelete = { deleteTarget = conv }
                                ) {
                                    HistoryConversationCard(
                                        conversation = conv,
                                        searchQuery = searchQuery,
                                        matchedMessageSnippet = matchedSnippet,
                                        isActive = isActive,
                                        selectionMode = selectionMode,
                                        isSelected = isSelected,
                                        onToggleSelected = {
                                            selectedIds = if (isSelected) selectedIds - conv.id else selectedIds + conv.id
                                        },
                                        onInspect = { onInspectConversation(conv.id) },
                                        onOpen = { onOpenConversation(conv.id) },
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
                    } else {
                        items(filteredConversations, key = { it.id }) { conv ->
                            val isSelected = conv.id in selectedIds
                            val isActive = conv.id == activeConversationId
                            val matchedSnippet = searchMatchByConvId[conv.id]?.matchedMessageSnippet
                            SwipeableHistoryCard(
                                conversation = conv,
                                selectionMode = selectionMode,
                                onSwipeArchive = { onToggleArchive(conv) },
                                onSwipeDelete = { deleteTarget = conv }
                            ) {
                                HistoryConversationCard(
                                    conversation = conv,
                                    searchQuery = searchQuery,
                                    matchedMessageSnippet = matchedSnippet,
                                    isActive = isActive,
                                    selectionMode = selectionMode,
                                    isSelected = isSelected,
                                    onToggleSelected = {
                                        selectedIds = if (isSelected) selectedIds - conv.id else selectedIds + conv.id
                                    },
                                    onInspect = { onInspectConversation(conv.id) },
                                    onOpen = { onOpenConversation(conv.id) },
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
                    label = { Text("Conversation Title") },
                    modifier = Modifier.fillMaxWidth()
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

    if (showBatchDeleteConfirm) {
        BatchDeleteConversationsConfirmationDialog(
            selectedCount = selectedIds.size,
            onConfirmDelete = {
                onBatchDelete(selectedIds.toList())
                showBatchDeleteConfirm = false
                selectionMode = false
                selectedIds = emptySet()
            },
            onDismiss = { showBatchDeleteConfirm = false }
        )
    }
}

@Composable
private fun SwipeableHistoryCard(
    conversation: Conversation,
    selectionMode: Boolean,
    onSwipeArchive: () -> Unit,
    onSwipeDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onSwipeDelete()
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSwipeArchive()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !selectionMode,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isDelete = direction == SwipeToDismissBoxValue.EndToStart
            val isArchive = direction == SwipeToDismissBoxValue.StartToEnd

            val bgColor = when {
                isDelete -> MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                isArchive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                else -> Color.Transparent
            }
            val borderColor = when {
                isDelete -> MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
                isArchive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                else -> Color.Transparent
            }
            val alignment = if (isDelete) Alignment.CenterEnd else Alignment.CenterStart

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (isDelete) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Swipe left to delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (isArchive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (conversation.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            contentDescription = if (conversation.archived) "Restore" else "Archive",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (conversation.archived) "Restore" else "Archive",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    ) {
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryConversationCard(
    conversation: Conversation,
    searchQuery: String,
    matchedMessageSnippet: String?,
    isActive: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelected: () -> Unit,
    onInspect: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outline
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable {
                if (selectionMode) onToggleSelected() else onInspect()
            }
            .testTag("history_conversation_card_${conversation.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelected() }
                    )
                    Spacer(Modifier.width(4.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (conversation.pinned) {
                            HistoryBadge(
                                text = "PINNED",
                                highlighted = true
                            )
                        }
                        if (conversation.archived) {
                            HistoryBadge(text = "ARCHIVED")
                        }
                        if (isActive) {
                            HistoryBadge(text = "ACTIVE", highlighted = true)
                        }
                        HistoryBadge(text = conversation.modelId)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = ConversationSearchUtils.highlightKeywords(
                            text = conversation.title,
                            query = searchQuery,
                            highlightBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            highlightForeground = MaterialTheme.colorScheme.primary
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!selectionMode) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More actions")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Inspect Transcript") },
                                leadingIcon = { Icon(Icons.Outlined.Visibility, null) },
                                onClick = { menuExpanded = false; onInspect() }
                            )
                            DropdownMenuItem(
                                text = { Text("Resume in Chat") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Chat, null) },
                                onClick = { menuExpanded = false; onOpen() }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { menuExpanded = false; onRename() }
                            )
                            DropdownMenuItem(
                                text = { Text(if (conversation.pinned) "Unpin" else "Pin") },
                                leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                                onClick = { menuExpanded = false; onTogglePin() }
                            )
                            DropdownMenuItem(
                                text = { Text(if (conversation.archived) "Restore" else "Archive") },
                                leadingIcon = {
                                    Icon(
                                        if (conversation.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                        null
                                    )
                                },
                                onClick = { menuExpanded = false; onToggleArchive() }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = { menuExpanded = false; onDuplicate() }
                            )
                            DropdownMenuItem(
                                text = { Text("Share / Export") },
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

            if (conversation.lastMessagePreview.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = ConversationSearchUtils.highlightKeywords(
                        text = conversation.lastMessagePreview,
                        query = searchQuery,
                        highlightBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        highlightForeground = MaterialTheme.colorScheme.primary
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (searchQuery.isNotBlank() && !matchedMessageSnippet.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Text(
                        text = ConversationSearchUtils.highlightKeywords(
                            text = "Match: $matchedMessageSnippet",
                            query = searchQuery,
                            highlightBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
                            highlightForeground = MaterialTheme.colorScheme.primary
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${conversation.messageCount} msgs • ${formatTimestamp(conversation.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!selectionMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onInspect) {
                            Icon(Icons.Outlined.Visibility, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("View")
                        }
                        Button(
                            onClick = onOpen,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Chat, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationHistoryDetailScreen(
    conversationId: String,
    inspectionState: HistoryInspectionState,
    onLoadConversation: (String) -> Unit,
    onResumeInChat: (String) -> Unit,
    onShareTranscript: (Conversation, List<ChatMessage>) -> Unit,
    onSaveToLibrary: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onBack: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var transcriptSearch by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId) {
        onLoadConversation(conversationId)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when (inspectionState) {
                HistoryInspectionState.Idle, HistoryInspectionState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Loading transcript from Firestore...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is HistoryInspectionState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = inspectionState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onBack) { Text("Back") }
                                Button(onClick = { onLoadConversation(conversationId) }) { Text("Retry") }
                            }
                        }
                    }
                }

                is HistoryInspectionState.Success -> {
                    val conv = inspectionState.conversation
                    val allMessages = inspectionState.messages
                    val visibleMessages = remember(allMessages, transcriptSearch) {
                        val q = transcriptSearch.trim()
                        if (q.isEmpty()) allMessages
                        else allMessages.filter { it.text.contains(q, ignoreCase = true) }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${conv.modelId} • ${allMessages.size} message(s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = conv.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(
                                    onClick = { onResumeInChat(conv.id) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.testTag("history_detail_resume_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Chat, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Resume")
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val txt = allMessages.joinToString("\n\n") {
                                            "${it.role.uppercase()}: ${it.text}"
                                        }
                                        clipboardManager.setText(AnnotatedString(txt))
                                    }
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy Transcript")
                                }
                                OutlinedButton(onClick = { onShareTranscript(conv, allMessages) }) {
                                    Icon(Icons.Outlined.Share, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val txt = allMessages.joinToString("\n\n") {
                                            "### ${it.role.uppercase()}\n${it.text}"
                                        }
                                        onSaveToLibrary(conv.title, txt)
                                    }
                                ) {
                                    Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save to Library")
                                }
                                OutlinedButton(
                                    onClick = { showDeleteConfirm = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Delete")
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = transcriptSearch,
                        onValueChange = { transcriptSearch = it },
                        placeholder = { Text("Search within this conversation...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, "Search transcript") },
                        trailingIcon = {
                            if (transcriptSearch.isNotEmpty()) {
                                IconButton(onClick = { transcriptSearch = "" }) {
                                    Icon(Icons.Rounded.Close, "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_detail_search_input")
                    )

                    Spacer(Modifier.height(10.dp))

                    if (visibleMessages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (transcriptSearch.isNotBlank()) "No messages match your search." else "No messages saved in this conversation yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            items(visibleMessages, key = { it.id.ifBlank { "${it.role}_${it.text.hashCode()}" } }) { msg ->
                                HistoryMessageItem(
                                    message = msg,
                                    onCopy = { clipboardManager.setText(AnnotatedString(msg.text)) }
                                )
                            }
                        }
                    }

                    if (showDeleteConfirm) {
                        DeleteConversationConfirmationDialog(
                            conversation = conv,
                            onConfirmDelete = {
                                showDeleteConfirm = false
                                onDeleteConversation(conv.id)
                                onBack()
                            },
                            onDismiss = { showDeleteConfirm = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMessageItem(
    message: ChatMessage,
    onCopy: () -> Unit
) {
    val isUser = message.role == "user"
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isUser) {
                        HistoryBadge(text = "YOU", highlighted = true)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            HistoryBadge(text = "SPACEX AI • ${message.modelId}", highlighted = false)
                        }
                    }
                    Text(
                        text = formatTimestamp(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy message",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (!message.attachmentLabel.isNullOrBlank()) {
                Text(
                    text = "Attachment: ${message.attachmentLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            MarkdownText(
                markdown = message.text
            )

            if (message.groundingSources.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Sources (${message.groundingSources.size}): " +
                        message.groundingSources.joinToString(" • ") { it.substringBefore(" | ") },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun HistoryStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HistoryBadge(
    text: String,
    highlighted: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun HistoryDateSectionHeader(
    title: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
            .testTag("history_date_header_${title.lowercase().replace(" ", "_")}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

private fun formatTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return "Just now"
    return try {
        val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        fmt.format(timestamp.toDate())
    } catch (_: Exception) {
        "Synced"
    }
}
