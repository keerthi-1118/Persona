package com.example.persona.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete

import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import com.example.persona.ui.components.ApplyDialogBlur
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.data.PersonaRepository
import com.example.persona.data.network.HabitResponse
import com.example.persona.theme.*
import com.example.persona.ui.components.GlassyCard
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(repository: PersonaRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var habits by remember { mutableStateOf<List<HabitResponse>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // Dialog inputs
    var habitName by remember { mutableStateOf("") }
    var habitIcon by remember { mutableStateOf("⭐") }
    var habitColor by remember { mutableStateOf("#6C63FF") }

    fun loadHabits() {
        scope.launch {
            isLoading = true
            try {
                val resp = repository.getHabits()
                if (resp.isSuccessful) {
                    habits = resp.body() ?: emptyList()
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                isLoading = false
            }
        }
    }

    fun addNewHabit() {
        scope.launch {
            try {
                if (habitName.trim().isEmpty()) {
                    Toast.makeText(context, "Please enter a habit name", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val resp = repository.createHabit(
                    name = habitName,
                    icon = habitIcon,
                    color = habitColor,
                    targetDaysJson = "[\"mon\",\"tue\",\"wed\",\"thu\",\"fri\",\"sat\",\"sun\"]"
                )
                if (resp.isSuccessful) {
                    showAddDialog = false
                    habitName = ""
                    habitIcon = "⭐"
                    habitColor = "#6C63FF"
                    loadHabits()
                    Toast.makeText(context, "Habit created!", Toast.LENGTH_SHORT).show()
                } else {
                    val errorMsg = resp.errorBody()?.string() ?: "Unknown error"
                    Toast.makeText(context, "Failed to create habit: $errorMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun toggleCheckIn(hid: String) {
        scope.launch {
            try {
                val resp = repository.checkHabit(hid)
                if (resp.isSuccessful) {
                    loadHabits()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun deleteHabit(hid: String) {
        scope.launch {
            try {
                repository.deleteHabit(hid)
                loadHabits()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LaunchedEffect(Unit) {
        loadHabits()
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
                Text("Habits", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Streaks Tracker", fontSize = 13.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("+ Habit", fontSize = 12.sp, color = Bg, fontWeight = FontWeight.Bold)
            }
        }

        // Habits List
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        } else if (habits.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No habits configured yet.", color = TextMuted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(habits) { habit ->
                    GlassyCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Habit Emoji/Icon
                            Text(
                                text = habit.icon,
                                fontSize = 28.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )

                            // Title and Streak Column
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = habit.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(imageVector = Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = Amber, modifier = Modifier.size(13.dp))
                                    Text(
                                        text = "${habit.streak} DAY STREAK",
                                        fontSize = 11.sp,
                                        color = Amber,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            // Completion Check-in Box
                            val isDone = habit.doneToday
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isDone) Green else Glass2,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { toggleCheckIn(habit.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isDone) "✓" else "",
                                    color = Bg,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // Delete button
                            IconButton(onClick = { deleteHabit(habit.id) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete Habit",
                                    tint = Red,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Habit Dialog
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
                Text("Create Habit", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = habitName,
                        onValueChange = { habitName = it },
                        label = { Text("Habit Name") },
                        placeholder = { Text("e.g. Meditate") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = habitIcon,
                        onValueChange = { habitIcon = it },
                        label = { Text("Habit Icon (Emoji)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = habitColor,
                        onValueChange = { habitColor = it },
                        label = { Text("Habit Color Hex (Optional)") },
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
                    onClick = { addNewHabit() },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    enabled = habitName.isNotEmpty()
                ) {
                    Text("Create", color = Bg)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}
