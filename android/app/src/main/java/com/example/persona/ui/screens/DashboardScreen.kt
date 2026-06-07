package com.example.persona.ui.screens

import com.example.persona.ui.components.GlassyCard
import com.example.persona.ui.components.ApplyDialogBlur

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp

import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Add

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import com.example.persona.data.FocusTimerManager
import com.example.persona.data.PersonaRepository
import com.example.persona.data.TimerMode
import com.example.persona.data.network.Assignment
import com.example.persona.data.network.HabitResponse
import com.example.persona.data.network.Task
import com.example.persona.theme.*
import com.example.persona.ui.main.PersonaTab
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

enum class DashboardCardType(val id: String, val title: String, val emoji: String) {
    StatsRow("stats_row", "Stats Today", "📊"),
    AiSmartPlanner("ai_smart_planner", "AI Smart Planner", "🤖"),
    FocusTimer("focus_timer", "Focus Timer", "⏱️"),
    TasksList("tasks_list", "Today's Tasks", "📝"),
    HabitsList("habits_list", "Habits Checklist", "🔥"),
    DueSoonList("due_soon_list", "Due Soon", "🔔"),
    AiEmailMonitor("ai_email_monitor", "AI Email Monitor", "✉️")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: PersonaRepository,
    isEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onNavigateToTab: (PersonaTab) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var habits by remember { mutableStateOf<List<HabitResponse>>(emptyList()) }
    var assignments by remember { mutableStateOf<List<Assignment>>(emptyList()) }
    var totalExpense by remember { mutableStateOf(0.0) }
    var username by remember { mutableStateOf("User") }
    var isLoading by remember { mutableStateOf(false) }

    // Add Task Dialog States
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }
    var newTaskDesc by remember { mutableStateOf("") }
    var newTaskPriority by remember { mutableStateOf("medium") }
    var newTaskStartTime by remember { mutableStateOf("") }
    var newTaskEndTime by remember { mutableStateOf("") }
    var newTaskCategory by remember { mutableStateOf("general") }

    // AI Email Monitor States
    var emailLinked by remember { mutableStateOf(false) }
    var emailConfig by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var emailSummaries by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isEmailSyncing by remember { mutableStateOf(false) }
    var showEmailConfigDialog by remember { mutableStateOf(false) }
    var emailCardExpanded by remember { mutableStateOf(true) }

    // SharedPreferences for Customizable Cards
    val sharedPrefs = remember { context.getSharedPreferences("persona_settings", Context.MODE_PRIVATE) }
    val defaultOrder = "stats_row,ai_smart_planner,focus_timer,tasks_list,habits_list,due_soon_list,ai_email_monitor"
    val defaultHidden = ""

    var cardOrder by remember {
        val saved = sharedPrefs.getString("dashboard_card_order", defaultOrder) ?: defaultOrder
        val loadedList = saved.split(",")
            .mapNotNull { id -> DashboardCardType.values().find { it.id == id } }
            .toMutableList()
        
        var updated = false
        DashboardCardType.values().forEach { type ->
            if (!loadedList.contains(type)) {
                loadedList.add(type)
                updated = true
            }
        }
        if (updated) {
            sharedPrefs.edit()
                .putString("dashboard_card_order", loadedList.joinToString(",") { it.id })
                .apply()
        }
        mutableStateOf(loadedList.toList())
    }

    var hiddenCards by remember {
        mutableStateOf(
            (sharedPrefs.getString("dashboard_hidden_cards", defaultHidden) ?: defaultHidden)
                .split(",")
                .filter { it.isNotEmpty() }
                .toSet()
        )
    }

    // Drag-and-Drop state variables
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Int, Int>() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val spacingPx = with(density) { 16.dp.toPx() }

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
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
                Text("Profile & Settings", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // User info block
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = username.take(1).uppercase(),
                                color = Bg,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Column {
                            Text(username, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                            Text("Student Account", color = TextSecondary, fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = BorderLight)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) {
                    Text("Logout", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }

    fun saveCardConfig(newOrder: List<DashboardCardType>, newHidden: Set<String>) {
        cardOrder = newOrder
        hiddenCards = newHidden
        sharedPrefs.edit()
            .putString("dashboard_card_order", newOrder.joinToString(",") { it.id })
            .putString("dashboard_hidden_cards", newHidden.joinToString(","))
            .apply()
    }

    fun handleDrag(index: Int, dragAmountY: Float) {
        val currentDraggedIndex = draggedIndex ?: return
        dragOffsetY += dragAmountY

        val currentHeight = itemHeights[currentDraggedIndex] ?: return

        if (dragOffsetY > 0) {
            if (currentDraggedIndex < cardOrder.lastIndex) {
                val nextHeight = itemHeights[currentDraggedIndex + 1] ?: return
                val swapThreshold = currentHeight / 2f + nextHeight / 2f + spacingPx / 2f
                if (dragOffsetY > swapThreshold) {
                    val list = cardOrder.toMutableList()
                    val temp = list[currentDraggedIndex]
                    list[currentDraggedIndex] = list[currentDraggedIndex + 1]
                    list[currentDraggedIndex + 1] = temp
                    
                    val tempHeight = itemHeights[currentDraggedIndex]
                    if (tempHeight != null) {
                        itemHeights[currentDraggedIndex] = nextHeight
                        itemHeights[currentDraggedIndex + 1] = tempHeight
                    }

                    saveCardConfig(list, hiddenCards)
                    draggedIndex = currentDraggedIndex + 1
                    dragOffsetY -= (nextHeight + spacingPx)
                    
                    try {
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(10, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } catch (e: Exception) {}
                }
            }
        } else if (dragOffsetY < 0) {
            if (currentDraggedIndex > 0) {
                val prevHeight = itemHeights[currentDraggedIndex - 1] ?: return
                val swapThreshold = -(currentHeight / 2f + prevHeight / 2f + spacingPx / 2f)
                if (dragOffsetY < swapThreshold) {
                    val list = cardOrder.toMutableList()
                    val temp = list[currentDraggedIndex]
                    list[currentDraggedIndex] = list[currentDraggedIndex - 1]
                    list[currentDraggedIndex - 1] = temp

                    val tempHeight = itemHeights[currentDraggedIndex]
                    if (tempHeight != null) {
                        itemHeights[currentDraggedIndex] = prevHeight
                        itemHeights[currentDraggedIndex - 1] = tempHeight
                    }

                    saveCardConfig(list, hiddenCards)
                    draggedIndex = currentDraggedIndex - 1
                    dragOffsetY += (prevHeight + spacingPx)
                    
                    try {
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(10, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } catch (e: Exception) {}
                }
            }
        }
    }

    fun loadEmailData() {
        scope.launch {
            try {
                val statusResp = repository.getEmailStatus()
                if (statusResp.isSuccessful && statusResp.body() != null) {
                    emailLinked = statusResp.body()!!["linked"] as? Boolean ?: false
                    emailConfig = statusResp.body()!!["config"] as? Map<String, Any> ?: emptyMap()
                }
                val summariesResp = repository.getEmailSummaries()
                if (summariesResp.isSuccessful && summariesResp.body() != null) {
                    emailSummaries = summariesResp.body()!!
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun loadDashboardData() {
        scope.launch {
            isLoading = true
            try {
                // Get user info
                val userResp = repository.getCurrentUser()
                if (userResp.isSuccessful) {
                    username = userResp.body()?.username ?: "User"
                }

                // Get tasks
                val tasksResp = repository.getTasks(date = LocalDate.now().toString())
                if (tasksResp.isSuccessful) {
                    tasks = tasksResp.body() ?: emptyList()
                    com.example.persona.reminder.ReminderManager.scheduleAllFutureTasks(context, tasks)
                }

                // Get habits
                val habitsResp = repository.getHabits()
                if (habitsResp.isSuccessful) {
                    habits = habitsResp.body() ?: emptyList()
                }

                // Get pending assignments
                val assignResp = repository.getAssignments("pending")
                if (assignResp.isSuccessful) {
                    assignments = assignResp.body() ?: emptyList()
                }

                // Get expense total for current month
                val currentMonth = LocalDate.now().toString().substring(0, 7) // "YYYY-MM"
                val expenseResp = repository.getExpensesTotal(currentMonth)
                if (expenseResp.isSuccessful) {
                    totalExpense = expenseResp.body()?.totalSpent ?: 0.0
                }

                loadEmailData()
            } catch (e: Exception) {
                // Ignore
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadDashboardData()
    }

    val completedTasksCount = tasks.count { it.status == "completed" }
    val totalTasksCount = tasks.size
    val activeAssignmentsCount = assignments.size
    val habitsRemaining = habits.count { !it.doneToday }
    val habitStreakMax = habits.maxOfOrNull { it.streak } ?: 0
    val productivityScore = if (totalTasksCount > 0) (completedTasksCount * 100) / totalTasksCount else 0

    // Time-based greeting
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 0..11 -> "Good morning! ☀️"
        in 12..16 -> "Good afternoon! 🌤️"
        else -> "Good evening! 🌙"
    }

    val formattedDate = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Laptop-Style Header: Greeting, Date, and Profile Avatar Circle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greeting,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = formattedDate,
                        fontSize = 13.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Profile settings
                Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Accent)
                            .clickable { showLogoutDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = username.take(1).uppercase(),
                            color = Bg,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
            }
        }



        // Render visible cards in defined order (in a single scrollable item container to track heights for drag-and-drop)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                cardOrder.forEachIndexed { index, cardType ->
                    val isVisible = cardType.id !in hiddenCards
                    
                    if (isVisible || isEditMode) {
                        val isCurrentDragged = draggedIndex == index
                        val translationY = if (isCurrentDragged) dragOffsetY else 0f
                        val scale = if (isCurrentDragged) 1.04f else 1f
                        val zIndex = if (isCurrentDragged) 1f else 0f

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(zIndex)
                                .graphicsLayer {
                                    this.translationY = translationY
                                    this.scaleX = scale
                                    this.scaleY = scale
                                }
                                .onGloballyPositioned { coordinates ->
                                    itemHeights[index] = coordinates.size.height
                                }
                                .animateContentSize()
                        ) {
                            // If edit mode is active, display control bar on top of the card
                            if (isEditMode) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BgElevated.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Drag Handle
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 8.dp)
                                                .size(28.dp)
                                                .background(Glass2, RoundedCornerShape(4.dp))
                                                .pointerInput(index) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            draggedIndex = index
                                                            dragOffsetY = 0f
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            handleDrag(index, dragAmount.y)
                                                        },
                                                        onDragEnd = {
                                                            draggedIndex = null
                                                            dragOffsetY = 0f
                                                            saveCardConfig(cardOrder, hiddenCards)
                                                        },
                                                        onDragCancel = {
                                                            draggedIndex = null
                                                            dragOffsetY = 0f
                                                        }
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("⠿", color = AccentLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text(cardType.emoji, fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
                                        Text(cardType.title, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Move Up Button
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val list = cardOrder.toMutableList()
                                                    val temp = list[index]
                                                    list[index] = list[index - 1]
                                                    list[index - 1] = temp
                                                    saveCardConfig(list, hiddenCards)
                                                }
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Text("▲", color = if (index > 0) Accent else TextMuted, fontSize = 10.sp)
                                        }

                                        // Move Down Button
                                        IconButton(
                                            onClick = {
                                                if (index < cardOrder.lastIndex) {
                                                    val list = cardOrder.toMutableList()
                                                    val temp = list[index]
                                                    list[index] = list[index + 1]
                                                    list[index + 1] = temp
                                                    saveCardConfig(list, hiddenCards)
                                                }
                                            },
                                            enabled = index < cardOrder.lastIndex,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Text("▼", color = if (index < cardOrder.lastIndex) Accent else TextMuted, fontSize = 10.sp)
                                        }

                                        // Hide/Show Toggle Button
                                        TextButton(
                                            onClick = {
                                                val set = hiddenCards.toMutableSet()
                                                if (isVisible) {
                                                    set.add(cardType.id)
                                                } else {
                                                    set.remove(cardType.id)
                                                }
                                                saveCardConfig(cardOrder, set)
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text(
                                                text = if (isVisible) "Hide" else "Show",
                                                color = if (isVisible) Red else Green,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            // Render Card content if visible (or faded out in edit mode)
                            Box(
                                modifier = Modifier.alpha(if (isVisible) 1f else 0.35f)
                            ) {
                                when (cardType) {
                                    DashboardCardType.StatsRow -> {
                                        // 📊 Laptop Stats Row: 4 side-by-side tiles
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Tile 1: Tasks Today
                                            StatTile(
                                                modifier = Modifier.weight(1f),
                                                emoji = "📝",
                                                value = "$completedTasksCount/$totalTasksCount",
                                                label = "Tasks Today"
                                            )
                                            // Tile 2: Focus Hours
                                            StatTile(
                                                modifier = Modifier.weight(1f),
                                                emoji = "⏱️",
                                                value = "${String.format(Locale.getDefault(), "%.1f", FocusTimerManager.secondsLeft.toFloat() / 3600)}h",
                                                label = "Focus Hours"
                                            )
                                            // Tile 3: Due Soon
                                            StatTile(
                                                modifier = Modifier.weight(1f),
                                                emoji = "🔔",
                                                value = "$activeAssignmentsCount",
                                                label = "Due Week"
                                            )
                                            // Tile 4: Max Streak / Score
                                            StatTile(
                                                modifier = Modifier.weight(1f),
                                                emoji = "🔥",
                                                value = "$productivityScore%",
                                                label = "Score"
                                            )
                                        }
                                    }
                                    DashboardCardType.AiSmartPlanner -> {
                                        // Minimized AI Smart Planner Card
                                        GlassyCard(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp)
                                            ) {
                                                var isPlanningLocal by remember { mutableStateOf(false) }
                                                var planMsgLocal by remember { mutableStateOf<String?>(null) }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(32.dp)
                                                                .clip(CircleShape)
                                                                .background(Glass2),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text("🤖", fontSize = 16.sp)
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                            Text("AI Smart Planner", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                                            Text("Optimized by Gemini AI", fontSize = 9.sp, color = TextMuted)
                                                        }
                                                    }
                                                    
                                                    Button(
                                                        onClick = {
                                                            isPlanningLocal = true
                                                            planMsgLocal = "AI planning..."
                                                            scope.launch {
                                                                try {
                                                                    val resp = repository.generateSchedule()
                                                                    if (resp.isSuccessful && resp.body() != null) {
                                                                        planMsgLocal = resp.body()!!.message
                                                                        loadDashboardData()
                                                                        Toast.makeText(context, "Plan updated!", Toast.LENGTH_SHORT).show()
                                                                    } else {
                                                                        planMsgLocal = "Planning failed"
                                                                    }
                                                                } catch (e: Exception) {
                                                                    planMsgLocal = "Connection error"
                                                                } finally {
                                                                    isPlanningLocal = false
                                                                }
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                                        shape = RoundedCornerShape(12.dp),
                                                        enabled = !isPlanningLocal,
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) {
                                                        Text(if (isPlanningLocal) "Planning..." else "Plan Day", color = Bg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                
                                                planMsgLocal?.let { msg ->
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = "✨ $msg",
                                                        fontSize = 11.sp,
                                                        color = AccentLight,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    DashboardCardType.FocusTimer -> {
                                        // ⏱️ Circular Focus Timer Card (Matches web design)
                                        GlassyCard(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                // Timer Mode selector pills at top
                                                Row(
                                                    modifier = Modifier
                                                        .background(Glass2, RoundedCornerShape(20.dp))
                                                        .padding(2.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    TimerMode.values().forEach { mode ->
                                                        val isActive = FocusTimerManager.mode == mode
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    if (isActive) mode.color else Color.Transparent,
                                                                    RoundedCornerShape(18.dp)
                                                                )
                                                                .clickable { FocusTimerManager.setTimerMode(mode) }
                                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                                        ) {
                                                            Text(
                                                                text = mode.label,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isActive) Bg else TextMuted
                                                            )
                                                        }
                                                    }
                                                }
                                                
                                                Spacer(modifier = Modifier.height(16.dp))

                                                // Circular progress clock ring
                                                val maxSecs = FocusTimerManager.mode.minutes * 60
                                                val progress = if (maxSecs > 0) FocusTimerManager.secondsLeft.toFloat() / maxSecs else 0f
                                                Box(
                                                    modifier = Modifier
                                                        .size(130.dp)
                                                        .clip(CircleShape)
                                                        .background(Bg),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        progress = { progress },
                                                        modifier = Modifier.fillMaxSize().padding(6.dp),
                                                        color = FocusTimerManager.mode.color,
                                                        strokeWidth = 6.dp,
                                                        trackColor = Border
                                                    )
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        val sec = FocusTimerManager.secondsLeft
                                                        val minStr = String.format(Locale.getDefault(), "%02d", sec / 60)
                                                        val secStr = String.format(Locale.getDefault(), "%02d", sec % 60)
                                                        Text(
                                                            text = "$minStr:$secStr",
                                                            fontSize = 24.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = TextPrimary
                                                        )
                                                        Text(
                                                            text = FocusTimerManager.mode.name,
                                                            fontSize = 9.sp,
                                                            color = TextMuted,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))

                                                // Start / Reset controls at bottom
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            if (FocusTimerManager.isRunning) FocusTimerManager.pause()
                                                            else FocusTimerManager.start()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = FocusTimerManager.mode.color),
                                                        shape = RoundedCornerShape(16.dp),
                                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(
                                                            text = if (FocusTimerManager.isRunning) "⏸ Pause" else "▶ Start",
                                                            color = Bg,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    OutlinedButton(
                                                        onClick = { FocusTimerManager.reset() },
                                                        shape = RoundedCornerShape(16.dp),
                                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                                        border = BorderStroke(1.dp, BorderLight)
                                                    ) {
                                                        Text("↺ Reset", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    DashboardCardType.TasksList -> {
                                        // Today's Tasks
                                        GlassyCard(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Icon(imageVector = Icons.Outlined.Assignment, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                                        Text("Today's Tasks", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                                    }
                                                    
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        TextButton(
                                                            onClick = { showAddTaskDialog = true },
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text("+ Add", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        TextButton(
                                                            onClick = { onNavigateToTab(PersonaTab.Planner) },
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text("View all →", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(10.dp))
                                                if (tasks.isEmpty()) {
                                                    Text("No tasks today.", color = TextMuted, fontSize = 12.sp)
                                                } else {
                                                    tasks.take(4).forEach { task ->
                                                         val isDone = task.status == "completed"
                                                         Row(
                                                             modifier = Modifier
                                                                 .fillMaxWidth()
                                                                 .padding(vertical = 6.dp),
                                                             verticalAlignment = Alignment.CenterVertically
                                                         ) {
                                                             // Custom premium check circle
                                                             Box(
                                                                 modifier = Modifier
                                                                     .size(20.dp)
                                                                     .clip(CircleShape)
                                                                     .background(if (isDone) Green else Color.Transparent)
                                                                     .border(1.5.dp, if (isDone) Green else TextMuted, CircleShape)
                                                                     .clickable {
                                                                         scope.launch {
                                                                             if (isDone) {
                                                                                 repository.updateTask(task.id, mapOf("status" to "pending"))
                                                                             } else {
                                                                                 repository.completeTask(task.id)
                                                                             }
                                                                             loadDashboardData()
                                                                         }
                                                                     },
                                                                 contentAlignment = Alignment.Center
                                                             ) {
                                                                 if (isDone) {
                                                                     Text("✓", color = Bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                                 }
                                                             }
                                                             
                                                             Spacer(modifier = Modifier.width(10.dp))
                                                             
                                                             Column(modifier = Modifier.weight(1f)) {
                                                                 Text(
                                                                     text = task.title,
                                                                     fontSize = 13.sp,
                                                                     color = if (isDone) TextMuted else TextPrimary,
                                                                     fontWeight = FontWeight.Bold
                                                                 )
                                                                 Spacer(modifier = Modifier.height(2.dp))
                                                                 // Metadata Row: Time or AI scheduled
                                                                 if (!task.startTime.isNullOrEmpty()) {
                                                                     val startTime = task.startTime.substringAfter("T").take(5)
                                                                     val endTime = task.endTime?.substringAfter("T")?.take(5) ?: ""
                                                                     val timeRange = if (endTime.isNotEmpty()) "$startTime – $endTime" else startTime
                                                                     Text(
                                                                         text = "🕒 $timeRange",
                                                                         fontSize = 10.sp,
                                                                         color = TextMuted,
                                                                         fontWeight = FontWeight.Medium
                                                                     )
                                                                 } else {
                                                                     Text(
                                                                         text = "⚡ AI scheduled",
                                                                         fontSize = 10.sp,
                                                                         color = AccentLight,
                                                                         fontWeight = FontWeight.Bold
                                                                     )
                                                                 }
                                                             }
                                                             
                                                             val badgeColor = when (task.priority) {
                                                                 "urgent", "high" -> Red
                                                                 "medium" -> Amber
                                                                 else -> Green
                                                             }
                                                             Text(
                                                                 text = task.priority.uppercase(),
                                                                 color = badgeColor,
                                                                 fontSize = 9.sp,
                                                                 fontWeight = FontWeight.Bold,
                                                                 modifier = Modifier
                                                                     .background(badgeColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                                     .padding(horizontal = 6.dp, vertical = 2.dp)
                                                             )
                                                             
                                                             Spacer(modifier = Modifier.width(6.dp))
                                                             
                                                             IconButton(
                                                                 onClick = {
                                                                     scope.launch {
                                                                         repository.deleteTask(task.id)
                                                                         loadDashboardData()
                                                                     }
                                                                 },
                                                                 modifier = Modifier.size(24.dp)
                                                             ) {
                                                                 Icon(
                                                                     imageVector = Icons.Outlined.Delete,
                                                                     contentDescription = "Delete",
                                                                     tint = Red,
                                                                     modifier = Modifier.size(16.dp)
                                                                 )
                                                             }
                                                         }
                                                     }
                                                 }
                                             }
                                         }
                                     }
                                    DashboardCardType.HabitsList -> {
                                         // Habits Checklist Card
                                         GlassyCard(
                                             modifier = Modifier.fillMaxWidth()
                                         ) {
                                             Column(modifier = Modifier.padding(16.dp)) {
                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     horizontalArrangement = Arrangement.SpaceBetween,
                                                     verticalAlignment = Alignment.CenterVertically
                                                 ) {
                                                     Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                         Icon(imageVector = Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(16.dp))
                                                         Text("Habits", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                                     }
                                                     TextButton(
                                                         onClick = { onNavigateToTab(PersonaTab.Habits) },
                                                         contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                         modifier = Modifier.height(28.dp)
                                                     ) {
                                                         Text("All →", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                     }
                                                 }
                                                 Spacer(modifier = Modifier.height(10.dp))
                                                 if (habits.isEmpty()) {
                                                     Text("No habits configured.", color = TextMuted, fontSize = 12.sp)
                                                 } else {
                                                     habits.take(4).forEach { habit ->
                                                         val iconText = if (habit.icon.isNullOrEmpty() || habit.icon.contains("<")) "🔥" else habit.icon
                                                         Row(
                                                             modifier = Modifier
                                                                 .fillMaxWidth()
                                                                 .padding(vertical = 4.dp),
                                                             verticalAlignment = Alignment.CenterVertically,
                                                             horizontalArrangement = Arrangement.SpaceBetween
                                                         ) {
                                                             Row(
                                                                 verticalAlignment = Alignment.CenterVertically,
                                                                 modifier = Modifier.weight(1f)
                                                             ) {
                                                                 Box(
                                                                     modifier = Modifier
                                                                         .size(32.dp)
                                                                         .clip(CircleShape)
                                                                         .background(Glass2),
                                                                     contentAlignment = Alignment.Center
                                                                 ) {
                                                                     Text(iconText, fontSize = 14.sp)
                                                                 }
                                                                 Spacer(modifier = Modifier.width(10.dp))
                                                                 Column {
                                                                     Text(
                                                                         text = habit.name,
                                                                         fontSize = 13.sp,
                                                                         fontWeight = FontWeight.Bold,
                                                                         color = TextPrimary
                                                                     )
                                                                     Text(
                                                                         text = "⚡ ${habit.streak} day streak",
                                                                         fontSize = 10.sp,
                                                                         color = TextMuted
                                                                     )
                                                                 }
                                                             }
                                                             Button(
                                                                 onClick = {
                                                                     scope.launch {
                                                                         repository.checkHabit(habit.id)
                                                                         loadDashboardData()
                                                                     }
                                                                 },
                                                                 colors = ButtonDefaults.buttonColors(
                                                                     containerColor = if (habit.doneToday) Green else Glass2
                                                                 ),
                                                                 shape = RoundedCornerShape(12.dp),
                                                                 contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                 modifier = Modifier.height(28.dp)
                                                             ) {
                                                                 Text(
                                                                     text = if (habit.doneToday) "Done" else "Check",
                                                                     color = if (habit.doneToday) Bg else TextSecondary,
                                                                     fontSize = 10.sp,
                                                                     fontWeight = FontWeight.Bold
                                                                 )
                                                             }
                                                         }
                                                     }
                                                 }
                                             }
                                         }
                                     }
                                    DashboardCardType.DueSoonList -> {
                                         // Due Soon Assignments Card
                                         GlassyCard(
                                             modifier = Modifier.fillMaxWidth()
                                         ) {
                                             Column(modifier = Modifier.padding(16.dp)) {
                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     horizontalArrangement = Arrangement.SpaceBetween,
                                                     verticalAlignment = Alignment.CenterVertically
                                                 ) {
                                                     Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                         Icon(imageVector = Icons.Outlined.Notifications, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                                         Text("Due Soon", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                                     }
                                                     TextButton(
                                                         onClick = { onNavigateToTab(PersonaTab.Assignments) },
                                                         contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                         modifier = Modifier.height(28.dp)
                                                     ) {
                                                         Text("All →", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                     }
                                                 }
                                                 Spacer(modifier = Modifier.height(10.dp))
                                                 if (assignments.isEmpty()) {
                                                     Text("No pending assignments.", color = TextMuted, fontSize = 12.sp)
                                                 } else {
                                                     assignments.take(3).forEach { assign ->
                                                         val diffDays = remember(assign.dueDate) {
                                                             assign.dueDate?.let {
                                                                 val due = LocalDate.parse(it.take(10))
                                                                 val diff = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), due)
                                                                 diff.toInt()
                                                             } ?: 99
                                                         }
                                                         val dotColor = when {
                                                             diffDays <= 1 -> Red
                                                             diffDays <= 3 -> Amber
                                                             else -> Green
                                                         }
                                                         Row(
                                                             modifier = Modifier
                                                                 .fillMaxWidth()
                                                                 .padding(vertical = 4.dp),
                                                             verticalAlignment = Alignment.CenterVertically
                                                         ) {
                                                             Box(
                                                                 modifier = Modifier
                                                                     .size(8.dp)
                                                                     .clip(CircleShape)
                                                                     .background(dotColor)
                                                             )
                                                             Spacer(modifier = Modifier.width(10.dp))
                                                             Column(modifier = Modifier.weight(1f)) {
                                                                 Text(assign.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                                 Text(assign.courseName, color = TextMuted, fontSize = 10.sp)
                                                             }
                                                             Text(
                                                                 text = when (diffDays) {
                                                                     0 -> "Due Today"
                                                                     1 -> "Due Tomorrow"
                                                                     else -> "Due in $diffDays days"
                                                                 },
                                                                 color = if (diffDays <= 1) Red else TextMuted,
                                                                 fontSize = 10.sp,
                                                                 fontWeight = FontWeight.Bold
                                                             )
                                                         }
                                                     }
                                                 }
                                             }
                                         }
                                     }
                                    DashboardCardType.AiEmailMonitor -> {
                                         // AI Email Monitor Card
                                         val skyBlue = Color(0xFF38BDF8)
                                         GlassyCard(
                                             modifier = Modifier.fillMaxWidth()
                                         ) {
                                             Column(
                                                 modifier = Modifier
                                                     .fillMaxWidth()
                                                     .padding(14.dp)
                                                     .animateContentSize()
                                             ) {
                                                 // ── Card Header Row ────────────────────────────
                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     horizontalArrangement = Arrangement.SpaceBetween,
                                                     verticalAlignment = Alignment.CenterVertically
                                                 ) {
                                                     Row(verticalAlignment = Alignment.CenterVertically) {
                                                         Box(
                                                             modifier = Modifier
                                                                 .size(30.dp)
                                                                 .clip(CircleShape)
                                                                 .background(skyBlue.copy(alpha = 0.12f)),
                                                             contentAlignment = Alignment.Center
                                                         ) {
                                                             Icon(
                                                                 imageVector = Icons.Outlined.Email,
                                                                 contentDescription = null,
                                                                 tint = skyBlue,
                                                                 modifier = Modifier.size(16.dp)
                                                             )
                                                         }
                                                         Spacer(modifier = Modifier.width(8.dp))
                                                         Column {
                                                             Text("AI Email Monitor", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                                             Text("Gmail Sync & Auto Scheduler", fontSize = 9.sp, color = TextMuted)
                                                         }
                                                     }

                                                     Row(
                                                         horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                         verticalAlignment = Alignment.CenterVertically
                                                     ) {
                                                         // Edit / Configure icon (professional)
                                                         Box(
                                                             modifier = Modifier
                                                                 .size(28.dp)
                                                                 .clip(RoundedCornerShape(8.dp))
                                                                 .background(Glass2)
                                                                 .clickable { showEmailConfigDialog = true },
                                                             contentAlignment = Alignment.Center
                                                         ) {
                                                             Icon(
                                                                 imageVector = Icons.Outlined.Tune,
                                                                 contentDescription = "Email Settings",
                                                                 tint = Color(0xFF38BDF8),
                                                                 modifier = Modifier.size(15.dp)
                                                             )
                                                         }

                                                         // Sync button
                                                         Button(
                                                             onClick = {
                                                                 isEmailSyncing = true
                                                                 scope.launch {
                                                                     try {
                                                                         val syncResp = repository.syncEmails()
                                                                         if (syncResp.isSuccessful) {
                                                                             Toast.makeText(context, syncResp.body()?.message ?: "Emails Synced!", Toast.LENGTH_SHORT).show()
                                                                             loadEmailData()
                                                                             loadDashboardData()
                                                                         } else {
                                                                             Toast.makeText(context, "Sync failed: " + syncResp.errorBody()?.string(), Toast.LENGTH_SHORT).show()
                                                                         }
                                                                     } catch (e: Exception) {
                                                                         Toast.makeText(context, "Connection error: " + e.localizedMessage, Toast.LENGTH_SHORT).show()
                                                                     } finally {
                                                                         isEmailSyncing = false
                                                                     }
                                                                 }
                                                             },
                                                             colors = ButtonDefaults.buttonColors(containerColor = skyBlue),
                                                             shape = RoundedCornerShape(10.dp),
                                                             enabled = !isEmailSyncing && emailLinked,
                                                             contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                             modifier = Modifier.height(26.dp)
                                                         ) {
                                                             Text(
                                                                 if (isEmailSyncing) "Syncing..." else "Sync",
                                                                 color = Color(0xFF06080F),
                                                                 fontSize = 10.sp,
                                                                 fontWeight = FontWeight.Bold
                                                             )
                                                         }

                                                         // Dropdown arrow to expand/collapse
                                                         Box(
                                                             modifier = Modifier
                                                                 .size(28.dp)
                                                                 .clip(RoundedCornerShape(8.dp))
                                                                 .background(Glass2)
                                                                 .clickable { emailCardExpanded = !emailCardExpanded },
                                                             contentAlignment = Alignment.Center
                                                         ) {
                                                             Icon(
                                                                 imageVector = if (emailCardExpanded)
                                                                     Icons.Outlined.KeyboardArrowUp
                                                                 else
                                                                     Icons.Outlined.KeyboardArrowDown,
                                                                 contentDescription = if (emailCardExpanded) "Collapse" else "Expand",
                                                                 tint = TextSecondary,
                                                                 modifier = Modifier.size(16.dp)
                                                             )
                                                         }
                                                     }
                                                 }

                                                 // ── Collapsible Body ──────────────────────────
                                                 if (emailCardExpanded) {
                                                     Spacer(modifier = Modifier.height(10.dp))

                                                     if (!emailLinked) {
                                                         Text(
                                                             text = "Connect your Google account in the Assignments screen to monitor your student emails.",
                                                             fontSize = 10.sp,
                                                             color = TextSecondary,
                                                             lineHeight = 15.sp
                                                         )
                                                     } else {
                                                         if (emailSummaries.isEmpty()) {
                                                             Text(
                                                                 text = "No unread emails synced. Tap Sync to scan.",
                                                                 fontSize = 10.sp,
                                                                 color = TextMuted,
                                                                 lineHeight = 14.sp
                                                             )
                                                         } else {
                                                             Column(
                                                                 verticalArrangement = Arrangement.spacedBy(6.dp)
                                                             ) {
                                                                 emailSummaries.take(3).forEach { summary ->
                                                                     val sender = summary["sender"] as? String ?: ""
                                                                     val subject = summary["subject"] as? String ?: ""
                                                                     val textSummary = summary["summary"] as? String ?: ""
                                                                     val action = summary["action_taken"] as? String ?: "none"
                                                                     val matchedKeywordsRaw = summary["matched_keywords"]
                                                                     val matchedKeywords: List<String> = when (matchedKeywordsRaw) {
                                                                         is List<*> -> matchedKeywordsRaw.mapNotNull { it?.toString() }
                                                                         is String -> {
                                                                             try {
                                                                                 com.google.gson.Gson().fromJson(matchedKeywordsRaw, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type)
                                                                             } catch (e: Exception) { emptyList() }
                                                                         }
                                                                         else -> emptyList()
                                                                     }

                                                                     // ── Compact Email Card ─────────────────
                                                                     Row(
                                                                         modifier = Modifier
                                                                             .fillMaxWidth()
                                                                             .background(Glass, RoundedCornerShape(8.dp))
                                                                             .border(1.dp, Border, RoundedCornerShape(8.dp))
                                                                             .padding(horizontal = 10.dp, vertical = 6.dp),
                                                                         verticalAlignment = Alignment.Top
                                                                     ) {
                                                                         // Dot indicator
                                                                         Box(
                                                                             modifier = Modifier
                                                                                 .padding(top = 3.dp)
                                                                                 .size(6.dp)
                                                                                 .clip(CircleShape)
                                                                                 .background(skyBlue)
                                                                         )
                                                                         Spacer(modifier = Modifier.width(8.dp))
                                                                         Column(modifier = Modifier.weight(1f)) {
                                                                             // Sender + badge
                                                                             Row(
                                                                                 modifier = Modifier.fillMaxWidth(),
                                                                                 horizontalArrangement = Arrangement.SpaceBetween,
                                                                                 verticalAlignment = Alignment.CenterVertically
                                                                             ) {
                                                                                 Text(
                                                                                     text = sender.substringBefore("<").trim().take(22),
                                                                                     fontSize = 10.sp,
                                                                                     fontWeight = FontWeight.Bold,
                                                                                     color = TextPrimary
                                                                                 )
                                                                                 if (action.startsWith("created_task")) {
                                                                                     Box(
                                                                                         modifier = Modifier
                                                                                             .background(Green.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                                                                                             .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                                     ) {
                                                                                         Text("⚡ Auto", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Green)
                                                                                     }
                                                                                 }
                                                                             }
                                                                             // Subject
                                                                             Text(
                                                                                 text = subject,
                                                                                 fontSize = 9.sp,
                                                                                 color = TextSecondary,
                                                                                 maxLines = 1
                                                                             )
                                                                             // Keywords row
                                                                             if (matchedKeywords.isNotEmpty()) {
                                                                                 Spacer(modifier = Modifier.height(3.dp))
                                                                                 Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                                     matchedKeywords.take(3).forEach { kw ->
                                                                                         Box(
                                                                                             modifier = Modifier
                                                                                                 .background(skyBlue.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                                                                 .border(1.dp, skyBlue.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                                                                 .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                                         ) {
                                                                                             Text(kw, color = skyBlue, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                                                                         }
                                                                                     }
                                                                                 }
                                                                             }
                                                                             // Summary
                                                                             if (textSummary.isNotEmpty()) {
                                                                                 Spacer(modifier = Modifier.height(3.dp))
                                                                                 Text(
                                                                                     text = textSummary,
                                                                                     fontSize = 9.sp,
                                                                                     color = TextMuted,
                                                                                     lineHeight = 13.sp,
                                                                                     maxLines = 2
                                                                                 )
                                                                             }
                                                                         }
                                                                     }
                                                                 }
                                                             }
                                                         }
                                                     }
                                                 }
                                             }
                                         }
                                     }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTaskDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddTaskDialog = false
                newTaskTitle = ""
                newTaskDesc = ""
                newTaskPriority = "medium"
                newTaskStartTime = ""
                newTaskEndTime = ""
                newTaskCategory = "general"
            },
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
                Text("Add Task", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTaskTitle,
                        onValueChange = { newTaskTitle = it },
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
                        value = newTaskDesc,
                        onValueChange = { newTaskDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    // Category selector pills
                    Column {
                        Text("Category", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("general", "routine", "goal", "reminder").forEach { cat ->
                                val isSelected = newTaskCategory == cat
                                val catColor = when (cat) {
                                    "routine" -> Purple
                                    "goal" -> Teal
                                    "reminder" -> Amber
                                    else -> Accent
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) catColor.copy(alpha = 0.2f) else Glass,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) catColor else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { newTaskCategory = cat }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cat.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) catColor else TextMuted
                                    )
                                }
                            }
                        }
                    }
                    
                    // Priority selector buttons
                    Column {
                        Text("Priority", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("low", "medium", "high", "urgent").forEach { priority ->
                                val isSelected = newTaskPriority == priority
                                val btnColor = when (priority) {
                                    "urgent", "high" -> Red
                                    "medium" -> Amber
                                    else -> Green
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) btnColor.copy(alpha = 0.2f) else Glass,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) btnColor else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { newTaskPriority = priority }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = priority.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) btnColor else TextMuted
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newTaskStartTime,
                        onValueChange = { newTaskStartTime = it },
                        label = { Text("Start Time (e.g. 09:00)") },
                        placeholder = { Text("09:00") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = newTaskEndTime,
                        onValueChange = { newTaskEndTime = it },
                        label = { Text("End Time (e.g. 10:00)") },
                        placeholder = { Text("10:00") },
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
                    onClick = {
                        if (newTaskTitle.trim().isEmpty()) {
                            Toast.makeText(context, "Title is required", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            val todayStr = LocalDate.now().toString()
                            val parsedStart = if (newTaskStartTime.isNotEmpty()) "${todayStr}T$newTaskStartTime:00" else null
                            val parsedEnd = if (newTaskEndTime.isNotEmpty()) "${todayStr}T$newTaskEndTime:00" else null

                            val resp = repository.createTask(
                                title = newTaskTitle,
                                description = newTaskDesc.ifEmpty { null },
                                startTime = parsedStart,
                                endTime = parsedEnd,
                                priority = newTaskPriority,
                                status = "pending",
                                category = newTaskCategory
                            )
                            if (resp.isSuccessful) {
                                Toast.makeText(context, "Task created!", Toast.LENGTH_SHORT).show()
                                showAddTaskDialog = false
                                newTaskTitle = ""
                                newTaskDesc = ""
                                newTaskPriority = "medium"
                                newTaskStartTime = ""
                                newTaskEndTime = ""
                                newTaskCategory = "general"
                                loadDashboardData()
                            } else {
                                Toast.makeText(context, "Failed to create task", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("Add Task", color = Bg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddTaskDialog = false
                    newTaskTitle = ""
                    newTaskDesc = ""
                    newTaskPriority = "medium"
                    newTaskStartTime = ""
                    newTaskEndTime = ""
                    newTaskCategory = "general"
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showEmailConfigDialog) {
        var senderInput by remember {
            mutableStateOf(
                (emailConfig["senders"] as? List<String>)?.joinToString(", ") ?: ""
            )
        }
        var domainInput by remember {
            mutableStateOf(
                (emailConfig["domains"] as? List<String>)?.joinToString(", ") ?: ""
            )
        }
        var newKeywordText by remember { mutableStateOf("") }
        var emailFiltersEnabled by remember {
            mutableStateOf(
                (emailConfig["enabled"] as? Number)?.toInt() != 0
            )
        }
        val keywordsListState = remember(emailConfig) {
            val list = mutableStateListOf<Pair<String, Boolean>>()
            val rawList = emailConfig["keywords"] as? List<*> ?: emptyList<Any>()
            rawList.forEach { item ->
                when (item) {
                    is Map<*, *> -> {
                        val text = item["text"] as? String ?: ""
                        val enabled = (item["enabled"] as? Boolean) ?: true
                        if (text.isNotEmpty()) {
                            list.add(Pair(text, enabled))
                        }
                    }
                    is String -> {
                        if (item.isNotEmpty()) {
                            list.add(Pair(item, true))
                        }
                    }
                }
            }
            list
        }

        AlertDialog(
            onDismissRequest = { showEmailConfigDialog = false },
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
                Text("Gmail Sync Filters", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Automatic Syncing", color = TextPrimary, fontSize = 13.sp)
                        Switch(
                            checked = emailFiltersEnabled,
                            onCheckedChange = { emailFiltersEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Bg,
                                checkedTrackColor = Accent,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = Glass2
                            )
                        )
                    }

                    Text(
                        "Input senders and domains as comma-separated values. Manage keywords dynamically below.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 15.sp
                    )

                    // ── Glass-style input fields ──
                    val glassFieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF7B2FFF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color.White.copy(alpha = 0.70f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.50f),
                        cursorColor = Color(0xFF00E5FF),
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                    )

                    OutlinedTextField(
                        value = senderInput,
                        onValueChange = { senderInput = it },
                        label = { Text("Sender Keywords (e.g. placements, advisor)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = glassFieldColors
                    )

                    OutlinedTextField(
                        value = domainInput,
                        onValueChange = { domainInput = it },
                        label = { Text("Allowed Domains (e.g. university.edu)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = glassFieldColors
                    )

                    HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 4.dp))

                    Text("Subject/Snippet Keyword Filters", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (keywordsListState.isEmpty()) {
                            Text("No keywords configured.", color = Color.White.copy(alpha = 0.40f), fontSize = 11.sp, modifier = Modifier.padding(6.dp))
                        } else {
                            keywordsListState.forEachIndexed { idx, item ->
                                val isActive = item.second
                                val chipBorder = if (isActive) {
                                    Brush.linearGradient(listOf(Color(0xFF7B2FFF), Color(0xFF00E5FF)))
                                } else {
                                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.10f)))
                                }
                                Row(
                                    modifier = Modifier
                                        .background(
                                            color = Color.White.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            brush = chipBorder,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .clickable { keywordsListState[idx] = Pair(item.first, !isActive) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Accent dot
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                color = if (isActive) Color(0xFF7B2FFF) else Color.White.copy(alpha = 0.30f),
                                                shape = CircleShape
                                            )
                                    )
                                    Text(
                                        text = item.first,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                    // × remove
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { keywordsListState.removeAt(idx) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("×", color = Color.White.copy(alpha = 0.50f), fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newKeywordText,
                            onValueChange = { newKeywordText = it },
                            placeholder = { Text("New keyword (e.g. CRC)", fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF7B2FFF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF00E5FF),
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                            )
                        )
                        // Gradient Add button
                        Box(
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF7B2FFF), Color(0xFF00E5FF))
                                    )
                                )
                                .clickable {
                                    val trimmed = newKeywordText.trim()
                                    if (trimmed.isNotEmpty() && !keywordsListState.any { it.first.equals(trimmed, ignoreCase = true) }) {
                                        keywordsListState.add(Pair(trimmed, true))
                                        newKeywordText = ""
                                    }
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Add", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                // Gradient pill Save button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF7B2FFF), Color(0xFF00E5FF))
                            )
                        )
                        .clickable {
                            val sendersList = senderInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val domainsList = domainInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val keywordsList = keywordsListState.map {
                                mapOf("text" to it.first, "enabled" to it.second)
                            }
                            val payload = mapOf(
                                "senders" to sendersList,
                                "domains" to domainsList,
                                "keywords" to keywordsList,
                                "enabled" to if (emailFiltersEnabled) 1 else 0
                            )
                            scope.launch {
                                try {
                                    val resp = repository.updateEmailConfig(payload)
                                    if (resp.isSuccessful) {
                                        Toast.makeText(context, "Filters updated!", Toast.LENGTH_SHORT).show()
                                        showEmailConfigDialog = false
                                        loadEmailData()
                                    } else {
                                        Toast.makeText(context, "Failed to update filters", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Connection error", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Save Filters", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailConfigDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.60f), fontSize = 14.sp)
                }
            }
        )
    }
}

@Composable
fun StatTile(
    modifier: Modifier = Modifier,
    emoji: String,
    value: String,
    label: String
) {
    val bColor = Border
    val blColor = BorderLight
    val bgEl = BgElevated
    val g2Color = Glass2
    val textP = TextPrimary
    val textM = TextMuted

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.28f),
                    Color.White.copy(alpha = 0.08f)
                ),
                start = Offset(0f, 0f),
                end = Offset(80f, 80f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.03f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(300f, 300f)
                    )
                )
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(g2Color),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = textP,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = textM,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}



// Helper modifiers
private fun Modifier.alpha(alpha: Float): Modifier = this.then(
    graphicsLayerAlphaModifier(alpha)
)

private fun graphicsLayerAlphaModifier(alpha: Float): Modifier {
    return Modifier.graphicsLayer {
        this.alpha = alpha
    }
}
