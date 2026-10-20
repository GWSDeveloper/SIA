package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.core.content.FileProvider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SiaBgDark
import com.example.ui.theme.SiaBorder
import com.example.ui.theme.SiaCardBg
import com.example.ui.theme.SiaCyan
import com.example.ui.theme.SiaRed
import com.example.ui.theme.SiaSurfaceDark
import com.example.ui.theme.SiaSurfaceVariantDark
import com.example.ui.theme.SiaTextMuted
import com.example.ui.theme.SiaTextPrimary
import com.example.ui.theme.SiaTextSecondary
import com.example.ui.theme.SiaViolet
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WorkspaceItem(
    val file: File,
    val isDirectory: Boolean,
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long
)

@Composable
fun WorkspaceScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rootWorkspaceDir = remember {
        File(context.filesDir, "workspace").apply {
            if (!exists()) {
                mkdirs()
                // Seed a welcome note
                val welcome = File(this, "Welcome_to_SIA.txt")
                if (!welcome.exists()) {
                    welcome.writeText("Welcome to SIA Workspace!\n\nThis is your internal space where SIA can create, view, and organize notes, summaries, and documents.\n\nTry telling SIA: 'Create a note called WorkoutPlan with 3 exercises' or create files manually using the + button below.")
                }
            }
        }
    }

    var currentDirectory by remember { mutableStateOf(rootWorkspaceDir) }
    var items by remember { mutableStateOf<List<WorkspaceItem>>(emptyList()) }
    var selectedFileToView by remember { mutableStateOf<File?>(null) }
    var fileContentToEdit by remember { mutableStateOf("") }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<File?>(null) }

    fun refresh() {
        val files = currentDirectory.listFiles()?.toList() ?: emptyList()
        items = files.map {
            WorkspaceItem(
                file = it,
                isDirectory = it.isDirectory,
                name = it.name,
                sizeBytes = if (it.isDirectory) 0 else it.length(),
                lastModifiedMs = it.lastModified()
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
    }

    LaunchedEffect(currentDirectory) {
        refresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SiaBgDark)
    ) {
        // Workspace Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SiaSurfaceDark)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Workspace",
                    color = SiaTextPrimary,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "/workspace${currentDirectory.path.removePrefix(rootWorkspaceDir.path)}",
                    color = SiaTextMuted,
                    fontSize = 12.sp
                )
            }

            Row {
                IconButton(
                    onClick = { showCreateFolderDialog = true },
                    modifier = Modifier.testTag("workspace_new_folder_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "New Folder",
                        tint = SiaViolet
                    )
                }

                IconButton(
                    onClick = { showCreateFileDialog = true },
                    modifier = Modifier.testTag("workspace_new_file_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                        contentDescription = "New File",
                        tint = SiaCyan
                    )
                }
            }
        }

        // Back button if in subdirectory
        if (currentDirectory != rootWorkspaceDir) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { currentDirectory = currentDirectory.parentFile ?: rootWorkspaceDir }
                    .background(SiaSurfaceVariantDark)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "← Back to Parent Directory", color = SiaCyan, fontSize = 13.sp)
            }
        }

        // File and folder list
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = SiaTextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Workspace is Empty",
                        color = SiaTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Tap + to create a file or ask SIA to write notes.",
                        color = SiaTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items, key = { it.file.absolutePath }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SiaCardBg),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SiaBorder.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                            .clickable {
                                if (item.isDirectory) {
                                    currentDirectory = item.file
                                } else {
                                    selectedFileToView = item.file
                                    fileContentToEdit = try { item.file.readText() } catch (_: Exception) { "" }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (item.isDirectory) SiaViolet.copy(alpha = 0.2f) else SiaCyan.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (item.isDirectory) SiaViolet else SiaCyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = item.name,
                                        color = SiaTextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(item.lastModifiedMs))
                                    val sizeInfo = if (item.isDirectory) "Folder" else "${item.sizeBytes} B"
                                    Text(
                                        text = "$sizeInfo • $dateStr",
                                        color = SiaTextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showDeleteConfirmDialog = item.file }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = SiaTextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // View / Edit File Dialog
    if (selectedFileToView != null) {
        val file = selectedFileToView!!
        AlertDialog(
            onDismissRequest = { selectedFileToView = null },
            title = {
                Text(text = file.name, color = SiaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = fileContentToEdit,
                    onValueChange = { fileContentToEdit = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SiaCyan,
                        unfocusedBorderColor = SiaBorder,
                        focusedTextColor = SiaTextPrimary,
                        unfocusedTextColor = SiaTextPrimary,
                        focusedContainerColor = SiaSurfaceDark,
                        unfocusedContainerColor = SiaSurfaceDark
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            file.writeText(fileContentToEdit)
                            refresh()
                        } catch (_: Exception) {}
                        selectedFileToView = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SiaCyan, contentColor = SiaBgDark)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                Row {
                    Button(
                        onClick = {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    putExtra(android.content.Intent.EXTRA_TEXT, fileContentToEdit)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, file.name)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                val chooser = android.content.Intent.createChooser(sendIntent, "Share ${file.name}").apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(chooser)
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SiaSurfaceVariantDark, contentColor = SiaCyan)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { selectedFileToView = null }) {
                        Text("Close", color = SiaTextSecondary)
                    }
                }
            },
            containerColor = SiaCardBg
        )
    }

    // Create New File Dialog
    if (showCreateFileDialog) {
        var newFileName by remember { mutableStateOf("") }
        var newFileContent by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create New Note/File", color = SiaTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("File Name (e.g. MyNote.txt)", color = SiaTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SiaCyan,
                            unfocusedBorderColor = SiaBorder,
                            focusedTextColor = SiaTextPrimary,
                            unfocusedTextColor = SiaTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFileContent,
                        onValueChange = { newFileContent = it },
                        label = { Text("Content", color = SiaTextSecondary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SiaCyan,
                            unfocusedBorderColor = SiaBorder,
                            focusedTextColor = SiaTextPrimary,
                            unfocusedTextColor = SiaTextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            val sanitized = newFileName.trim().let { if (it.contains(".")) it else "$it.txt" }
                            val newFile = File(currentDirectory, sanitized)
                            try {
                                newFile.writeText(newFileContent)
                                refresh()
                            } catch (_: Exception) {}
                        }
                        showCreateFileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SiaCyan, contentColor = SiaBgDark)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
                    Text("Cancel", color = SiaTextSecondary)
                }
            },
            containerColor = SiaCardBg
        )
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create New Folder", color = SiaTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name", color = SiaTextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SiaViolet,
                        unfocusedBorderColor = SiaBorder,
                        focusedTextColor = SiaTextPrimary,
                        unfocusedTextColor = SiaTextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            val newFolder = File(currentDirectory, folderName.trim())
                            if (!newFolder.exists()) {
                                newFolder.mkdirs()
                                refresh()
                            }
                        }
                        showCreateFolderDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SiaViolet, contentColor = SiaTextPrimary)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel", color = SiaTextSecondary)
                }
            },
            containerColor = SiaCardBg
        )
    }

    // Confirm Delete Dialog
    if (showDeleteConfirmDialog != null) {
        val target = showDeleteConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete Confirmation", color = SiaTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete '${target.name}'?",
                    color = SiaTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (target.isDirectory) {
                            target.deleteRecursively()
                        } else {
                            target.delete()
                        }
                        showDeleteConfirmDialog = null
                        refresh()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SiaRed, contentColor = SiaTextPrimary)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel", color = SiaTextSecondary)
                }
            },
            containerColor = SiaCardBg
        )
    }
}
