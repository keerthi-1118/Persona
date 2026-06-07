package com.example.persona.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import com.example.persona.ui.components.ApplyDialogBlur
import androidx.compose.material.icons.Icons

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.data.PersonaRepository
import com.example.persona.data.network.Note
import com.example.persona.theme.*
import com.example.persona.ui.components.GlassyCard
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(repository: PersonaRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Dialog inputs
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(0) }

    fun loadNotes() {
        scope.launch {
            isLoading = true
            try {
                val resp = repository.getNotes()
                if (resp.isSuccessful) {
                    notes = resp.body() ?: emptyList()
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                isLoading = false
            }
        }
    }

    fun saveNote() {
        scope.launch {
            try {
                val note = editingNote
                val resp = if (note != null) {
                    repository.updateNote(
                        note.id,
                        mapOf(
                            "title" to title,
                            "content" to content,
                            "pinned" to pinned
                        )
                    )
                } else {
                    repository.createNote(
                        title = if (title.isEmpty()) "Untitled Note" else title,
                        content = content,
                        tagsJson = "[]",
                        color = "#1e1e2e",
                        pinned = pinned
                    )
                }
                if (resp.isSuccessful) {
                    showDialog = false
                    editingNote = null
                    title = ""
                    content = ""
                    pinned = 0
                    loadNotes()
                    Toast.makeText(context, "Note saved!", Toast.LENGTH_SHORT).show()
                } else {
                    val errorMsg = resp.errorBody()?.string() ?: "Unknown error"
                    Toast.makeText(context, "Failed to save note: $errorMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteNote(nid: String) {
        scope.launch {
            try {
                repository.deleteNote(nid)
                showDialog = false
                editingNote = null
                loadNotes()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LaunchedEffect(Unit) {
        loadNotes()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Laptop Style Header: Title + Theme Toggle and Actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Notes", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("My Notes", fontSize = 13.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    editingNote = null
                    title = ""
                    content = ""
                    pinned = 0
                    showDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("+ Note", fontSize = 12.sp, color = Bg, fontWeight = FontWeight.Bold)
            }
        }

        // Notes Grid
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        } else if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No notes created yet.", color = TextMuted, fontSize = 14.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notes) { note ->
                    GlassyCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clickable {
                                editingNote = note
                                title = note.title ?: ""
                                content = note.content ?: ""
                                pinned = note.pinned
                                showDialog = true
                            },
                        shape = RoundedCornerShape(14.dp),
                        border = if (note.pinned == 1) BorderStroke(
                            width = 2.dp,
                            brush = androidx.compose.ui.graphics.SolidColor(Amber)
                        ) else null
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = note.title ?: "Untitled Note",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (note.pinned == 1) {
                                    Text("📌", fontSize = 10.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = note.content ?: "",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Note Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            modifier = Modifier.border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (LocalPersonaColors.current.bg == Color(0xFF0A1128)) {
                        listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f))
                    } else {
                        listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.20f))
                    }
                ),
                shape = RoundedCornerShape(28.dp)
            ),
            shape = RoundedCornerShape(28.dp),
            containerColor = DialogBg,
            title = {
                ApplyDialogBlur()
                Text(if (editingNote != null) "Edit Note" else "Create Note", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Write something...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        maxLines = 10
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = pinned == 1,
                            onCheckedChange = { pinned = if (it) 1 else 0 },
                            colors = CheckboxDefaults.colors(checkedColor = Amber)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pin Note to Top", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editingNote != null) {
                        Button(
                            onClick = { deleteNote(editingNote!!.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Red)
                        ) {
                            Text("Delete", color = Color.White)
                        }
                    }
                    Button(
                        onClick = { saveNote() },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text("Save", color = Bg)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}
