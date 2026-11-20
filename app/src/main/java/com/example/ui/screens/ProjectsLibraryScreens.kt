package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AiProject
import com.example.data.model.LibraryItem
import com.example.ui.components.MarkdownText

@Composable
fun ProjectsScreen(
    projects: List<AiProject>,
    activeProjectId: String?,
    onSaveProject: (id: String?, name: String, category: String, instructions: String, description: String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onStartProjectChat: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<AiProject?>(null) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Website development") }
    var description by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }

    val categories = listOf(
        "Website development", "Game development", "Schoolwork",
        "Research", "Business ideas", "Programming", "Personal projects"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PROJECTS WORKSPACE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("Dedicated AI Workspaces", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    editingProject = null
                    name = ""
                    category = "Programming"
                    description = ""
                    instructions = ""
                    showDialog = true
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("create_project_button")
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Project", fontWeight = FontWeight.Bold)
            }
        }

        if (projects.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No projects yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Create a project for Website dev, Game dev, Schoolwork, or Research with custom AI instructions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(projects, key = { it.id }) { proj ->
                    val isActive = proj.id == activeProjectId
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(18.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(proj.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(proj.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Row {
                                    IconButton(onClick = {
                                        editingProject = proj
                                        name = proj.name
                                        category = proj.category
                                        description = proj.description
                                        instructions = proj.instructions
                                        showDialog = true
                                    }) {
                                        Icon(Icons.Outlined.Edit, "Edit project")
                                    }
                                    IconButton(onClick = { onDeleteProject(proj.id) }) {
                                        Icon(Icons.Outlined.Delete, "Delete project", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            if (proj.description.isNotBlank()) {
                                Text(proj.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (proj.instructions.isNotBlank()) {
                                Text(
                                    "Instructions: ${proj.instructions}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { onStartProjectChat(proj.id) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Chat in Project")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingProject == null) "New Project" else "Edit Project") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Project Name") },
                        singleLine = true
                    )
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        maxLines = 2
                    )
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        label = { Text("Custom AI Instructions") },
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSaveProject(editingProject?.id, name, category, instructions, description)
                            showDialog = false
                        }
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun LibraryScreen(
    items: List<LibraryItem>,
    onSaveItem: (id: String?, title: String, folder: String, itemType: String, content: String, language: String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onShareItem: (LibraryItem) -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf("All") }
    var previewItem by remember { mutableStateOf<LibraryItem?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<LibraryItem?>(null) }
    var titleInput by remember { mutableStateOf("") }
    var folderInput by remember { mutableStateOf("General") }
    var typeInput by remember { mutableStateOf("note") }
    var contentInput by remember { mutableStateOf("") }

    val folders = remember(items) {
        listOf("All") + items.map { it.folder }.distinct().sorted()
    }

    val filtered = remember(items, search, selectedFolder) {
        items.filter { item ->
            (selectedFolder == "All" || item.folder == selectedFolder) &&
                (search.isBlank() ||
                    item.title.contains(search, ignoreCase = true) ||
                    item.content.contains(search, ignoreCase = true) ||
                    item.folder.contains(search, ignoreCase = true))
        }
    }

    val uploadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                val fileName = context.contentResolver.query(it, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && idx >= 0) c.getString(idx) else "Uploaded File"
                } ?: "Uploaded File"
                val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r ->
                    r.readText().take(90000)
                }.orEmpty()
                onSaveItem(null, fileName, "Uploads", "document", content.ifBlank { "Uploaded: $fileName" }, "text")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PERSONAL LIBRARY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("Files, Notes & Saved Outputs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { uploadPicker.launch(arrayOf("text/*", "application/json", "application/xml")) }
                ) {
                    Icon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Upload")
                }
                Button(
                    onClick = {
                        editTarget = null
                        titleInput = ""
                        folderInput = "Notes"
                        typeInput = "note"
                        contentInput = ""
                        showCreateDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Note", fontWeight = FontWeight.Bold)
                }
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search library by title, folder, or content...") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            folders.forEach { f ->
                FilterChip(
                    selected = selectedFolder == f,
                    onClick = { selectedFolder = f },
                    leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(15.dp)) },
                    label = { Text(f) }
                )
            }
        }

        if (filtered.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Your library is empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Save AI responses, upload documents, or create notes and folders securely synced to your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                            .clickable { previewItem = item }
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${item.folder} • ${item.itemType.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Row {
                                    IconButton(onClick = {
                                        editTarget = item
                                        titleInput = item.title
                                        folderInput = item.folder
                                        typeInput = item.itemType
                                        contentInput = item.content
                                        showCreateDialog = true
                                    }) {
                                        Icon(Icons.AutoMirrored.Outlined.DriveFileMove, "Rename or move")
                                    }
                                    IconButton(onClick = { onShareItem(item) }) {
                                        Icon(Icons.Outlined.Share, "Download / Share")
                                    }
                                    IconButton(onClick = { onDeleteItem(item.id) }) {
                                        Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            Text(
                                text = item.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    previewItem?.let { item ->
        AlertDialog(
            onDismissRequest = { previewItem = null },
            title = { Text(item.title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MarkdownText(markdown = item.content)
                }
            },
            confirmButton = {
                Button(onClick = { onShareItem(item) }) { Text("Share / Export") }
            },
            dismissButton = {
                TextButton(onClick = { previewItem = null }) { Text("Close") }
            }
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (editTarget == null) "New Library Item" else "Edit / Move Library Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = titleInput, onValueChange = { titleInput = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(value = folderInput, onValueChange = { folderInput = it }, label = { Text("Folder") }, singleLine = true)
                    OutlinedTextField(value = contentInput, onValueChange = { contentInput = it }, label = { Text("Content") }, maxLines = 6)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (titleInput.isNotBlank()) {
                            onSaveItem(editTarget?.id, titleInput, folderInput.ifBlank { "General" }, typeInput, contentInput, "markdown")
                            showCreateDialog = false
                        }
                    },
                    enabled = titleInput.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}
