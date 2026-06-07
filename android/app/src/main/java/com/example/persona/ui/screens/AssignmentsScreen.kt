package com.example.persona.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons

import com.example.persona.ui.components.ApplyDialogBlur
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.persona.data.PersonaRepository
import com.example.persona.data.network.Assignment
import com.example.persona.data.network.NetworkClient
import com.example.persona.ui.auth.AuthViewModel
import com.example.persona.theme.*
import com.example.persona.ui.components.GlassyCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentsScreen(repository: PersonaRepository, authViewModel: AuthViewModel) {
    val scope = rememberCoroutineScope()
    var assignments by remember { mutableStateOf<List<Assignment>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("all") }
    var showAddDialog by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val currentUser by authViewModel.currentUser.collectAsState()
    val currentUserId = currentUser?.id

    // Dialog inputs
    var courseName by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") } // YYYY-MM-DD

    fun loadAssignments() {
        scope.launch {
            isLoading = true
            try {
                val filter = if (selectedFilter == "all") null else selectedFilter
                val resp = repository.getAssignments(filter)
                if (resp.isSuccessful) {
                    assignments = resp.body() ?: emptyList()
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                isLoading = false
            }
        }
    }

    var showLinkGoogleDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun syncGoogleClassroom() {
        scope.launch {
            isSyncing = true
            syncMessage = "Syncing with Classroom..."
            try {
                val resp = repository.syncClassroom()
                if (resp.isSuccessful) {
                    syncMessage = resp.body()?.message ?: "Synced successfully"
                    loadAssignments()
                } else {
                    val errStr = resp.errorBody()?.string() ?: ""
                    if (errStr.contains("No Google token found") || 
                        errStr.contains("connect Google Classroom") ||
                        errStr.contains("authenticated") ||
                        errStr.contains("Unauthorized") ||
                        errStr.contains("token") ||
                        resp.code() == 401) {
                        showLinkGoogleDialog = true
                        syncMessage = "Google account not connected or link expired."
                    } else {
                        syncMessage = "Sync failed: Make sure Google is linked."
                    }
                }
            } catch (e: Exception) {
                syncMessage = e.localizedMessage ?: "Sync error"
            } finally {
                isSyncing = false
            }
        }
    }

    fun addNewAssignment() {
        scope.launch {
            try {
                val dateVal = if (dueDate.isEmpty()) null else "${dueDate}T12:00:00"
                val resp = repository.createAssignment(
                    courseName = courseName,
                    title = title,
                    description = if (description.isEmpty()) null else description,
                    dueDate = dateVal,
                    status = "pending"
                )
                if (resp.isSuccessful) {
                    showAddDialog = false
                    courseName = ""
                    title = ""
                    description = ""
                    dueDate = ""
                    loadAssignments()
                    Toast.makeText(context, "Assignment created!", Toast.LENGTH_SHORT).show()
                } else {
                    val errorMsg = resp.errorBody()?.string() ?: "Unknown error"
                    Toast.makeText(context, "Failed to create assignment: $errorMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(selectedFilter) {
        loadAssignments()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Laptop Style Header: Title + Theme Toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Assignments", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("School Tasks", fontSize = 13.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            }
        }

        // Classroom Sync Trigger Card
        GlassyCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🔄 Classroom Sync", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Import assignments from Google Classroom", fontSize = 11.sp, color = TextMuted)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showLinkGoogleDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Link", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { syncGoogleClassroom() },
                            colors = ButtonDefaults.buttonColors(containerColor = Purple),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSyncing,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Sync", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                syncMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = Purple,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Filter Pills and Add Button Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all", "pending", "submitted").forEach { filter ->
                    val isActive = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .background(
                                if (isActive) AccentDim else Glass2,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = filter.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) AccentLight else TextMuted
                        )
                    }
                }
            }

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("+ Manual", fontSize = 11.sp, color = Bg, fontWeight = FontWeight.Bold)
            }
        }

        // Assignments List
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        } else if (assignments.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No assignments found.", color = TextMuted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(assignments) { asgmt ->
                    GlassyCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val dotColor = when (asgmt.status) {
                                "submitted", "graded" -> Green
                                "late" -> Red
                                else -> Amber
                            }
                            
                            // Status Dot
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(dotColor, RoundedCornerShape(5.dp))
                            )
                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = asgmt.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = asgmt.courseName,
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.SemiBold
                                )
                                asgmt.dueDate?.let {
                                    val readableDate = it.substring(0, 10)
                                    Text(
                                        text = "Due: $readableDate",
                                        fontSize = 11.sp,
                                        color = Amber,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            // Actions
                            if (asgmt.status == "pending") {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            repository.updateAssignment(asgmt.id, mapOf("status" to "submitted"))
                                            loadAssignments()
                                        }
                                    }
                                ) {
                                    Text("Submit", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Assignment Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
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
                Text("Add Assignment", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = courseName,
                        onValueChange = { courseName = it },
                        label = { Text("Course / Subject Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Assignment Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Due Date (YYYY-MM-DD)") },
                        placeholder = { Text("2026-05-30") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { addNewAssignment() },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    enabled = courseName.isNotEmpty() && title.isNotEmpty()
                ) {
                    Text("Add", color = Bg)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Google Link Dialog
    if (showLinkGoogleDialog) {
        val serverUrl = remember { NetworkClient.getBaseUrl(context) }
        val linkUrl = remember(serverUrl, currentUserId) {
            val base = if (serverUrl.endsWith("/")) "${serverUrl}api/auth/google" else "$serverUrl/api/auth/google"
            if (currentUserId != null) "$base?userId=$currentUserId" else base
        }
        
        AlertDialog(
            onDismissRequest = { showLinkGoogleDialog = false },
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
                Text("Link Google Account", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "To sync with Google Classroom, you need to connect your Google Account first. Tap \"Link Account\" to open the authentication page in your browser. Once complete, return here to sync.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLinkGoogleDialog = false
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("Link Account", color = Bg)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkGoogleDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}
