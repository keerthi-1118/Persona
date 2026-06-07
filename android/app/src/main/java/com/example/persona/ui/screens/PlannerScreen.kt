package com.example.persona.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.data.PersonaRepository
import com.example.persona.data.network.Task
import com.example.persona.data.network.TimetableItem
import com.example.persona.theme.*
import com.example.persona.ui.components.GlassyCard
import kotlinx.coroutines.launch
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(repository: PersonaRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var timetableClasses by remember { mutableStateOf<List<TimetableItem>>(emptyList()) }
    var plannerMsg by remember { mutableStateOf<String?>(null) }
    var isScheduling by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // Navigation and filter tabs
    var currentView by remember { mutableStateOf("day") } // "day" or "week"
    var activeFilter by remember { mutableStateOf("all") } // "all", "pending", "in_progress", "done"

    // Upload OCR States
    var isUploading by remember { mutableStateOf(false) }
    var uploadMessage by remember { mutableStateOf<String?>(null) }

    // Manual Class Dialog States
    var showAddClassDialog by remember { mutableStateOf(false) }
    var newClassLabel by remember { mutableStateOf("") }
    var newClassDay by remember { mutableStateOf("mon") }
    var newClassStart by remember { mutableStateOf("09:00") }
    var newClassEnd by remember { mutableStateOf("10:00") }
    var newClassType by remember { mutableStateOf("class") }

    val daysOfWeek = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    fun loadSchedule() {
        scope.launch {
            isLoading = true
            try {
                // Fetch tasks for the selected date
                var statusParam: String? = when (activeFilter) {
                    "pending" -> "pending"
                    "in_progress" -> "in_progress"
                    "done" -> "completed"
                    else -> null
                }
                
                val resp = repository.getTasks(date = selectedDate.toString(), status = statusParam)
                if (resp.isSuccessful) {
                    tasks = resp.body() ?: emptyList()
                }

                // Fetch full weekly timetable
                val ttResp = repository.getTimetable()
                if (ttResp.isSuccessful) {
                    val allBlocks = ttResp.body() ?: emptyList()
                    val dowStr = selectedDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase()
                    timetableClasses = allBlocks.filter { it.dayOfWeek.lowercase().startsWith(dowStr) }
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                isLoading = false
            }
        }
    }

    fun triggerAutoSchedule() {
        scope.launch {
            isScheduling = true
            plannerMsg = "AI is scheduling..."
            try {
                val resp = repository.generateSchedule()
                if (resp.isSuccessful && resp.body() != null) {
                    val body = resp.body()!!
                    plannerMsg = body.message
                    loadSchedule()
                } else {
                    plannerMsg = "Scheduler failed"
                }
            } catch (e: Exception) {
                plannerMsg = e.localizedMessage ?: "Connection error"
            } finally {
                isScheduling = false
            }
        }
    }

    fun addNewClass() {
        scope.launch {
            try {
                val resp = repository.addClass(
                    dayOfWeek = newClassDay,
                    startTime = newClassStart,
                    endTime = newClassEnd,
                    label = newClassLabel,
                    type = newClassType
                )
                if (resp.isSuccessful) {
                    Toast.makeText(context, "Class block added!", Toast.LENGTH_SHORT).show()
                    showAddClassDialog = false
                    newClassLabel = ""
                    newClassStart = "09:00"
                    newClassEnd = "10:00"
                    loadSchedule()
                } else {
                    Toast.makeText(context, "Failed to add class block.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Photo picker launcher
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    isUploading = true
                    uploadMessage = "AI is reading timetable..."
                    try {
                        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                        val bytes = inputStream?.readBytes()
                        if (bytes != null) {
                            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                            val resp = repository.uploadTimetableImage(bytes, mimeType)
                            if (resp.isSuccessful) {
                                uploadMessage = resp.body()?.message ?: "Successfully imported timetable!"
                                loadSchedule()
                            } else {
                                uploadMessage = "OCR Sync failed: Server error."
                            }
                        } else {
                            uploadMessage = "Could not read image file."
                        }
                    } catch (e: Exception) {
                        uploadMessage = "OCR error: ${e.localizedMessage}"
                    } finally {
                        isUploading = false
                    }
                }
            }
        }
    )

    LaunchedEffect(selectedDate, activeFilter) {
        loadSchedule()
    }

    // Format header date (e.g. "Wednesday, 27 May")
    val headerDateStr = remember(selectedDate) {
        selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Laptop Style Header: Title + Robot Icon
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Planner", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(headerDateStr, fontSize = 13.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                }
                
                // Clickable robot icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Glass2)
                        .clickable { triggerAutoSchedule() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🤖", fontSize = 20.sp)
                }
            }
        }

        // Date Horizon Scroller Flanked by Arrow Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Arrow
                IconButton(
                    onClick = { selectedDate = selectedDate.minusDays(1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("◀", color = Accent, fontSize = 12.sp)
                }

                // Scroller List
                LazyRow(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // List of 7 days centered around selected date
                    val startDay = selectedDate.minusDays(2)
                    items((0..6).toList()) { offset ->
                        val date = startDay.plusDays(offset.toLong())
                        val isActive = date == selectedDate
                        
                        GlassyCard(
                            modifier = Modifier
                                .width(55.dp)
                                .clickable { selectedDate = date },
                            shape = RoundedCornerShape(12.dp),
                            border = if (isActive) BorderStroke(1.5.dp, Accent) else null
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(),
                                    fontSize = 9.sp,
                                    color = if (isActive) Accent else TextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    fontSize = 16.sp,
                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }

                // Right Arrow
                IconButton(
                    onClick = { selectedDate = selectedDate.plusDays(1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("▶", color = Accent, fontSize = 12.sp)
                }
            }
        }

        // Smart Scheduler Card (Matches Laptop UI style)
        item {
            GlassyCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Glass2),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🤖", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("AI Smart Scheduler", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Powered by Gemini AI", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Let Gemini AI intelligently plan your entire week — scheduling tasks around your classes and deadlines.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { triggerAutoSchedule() },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isScheduling,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🤖 ", fontSize = 12.sp)
                            Text("Generate My Daily Plan", fontSize = 12.sp, color = Bg, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    plannerMsg?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, fontSize = 11.sp, color = AccentLight, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Toggle View Pills: Day / Week
        item {
            Row(
                modifier = Modifier
                    .background(Glass2, RoundedCornerShape(20.dp))
                    .padding(2.dp)
            ) {
                listOf("day", "week").forEach { view ->
                    val isActive = currentView == view
                    Box(
                        modifier = Modifier
                            .background(
                                if (isActive) AccentDim else Color.Transparent,
                                RoundedCornerShape(18.dp)
                            )
                            .clickable { currentView = view }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = view.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) AccentLight else TextMuted
                        )
                    }
                }
            }
        }

        // Rendering Conditional Layout: Week View vs Day View
        if (currentView == "week") {
            // Week View Header
            item {
                Text("Weekly Overview", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            
            val startOfWeek = selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1)
            items((0..6).toList()) { offset ->
                val day = startOfWeek.plusDays(offset.toLong())
                val isToday = day == LocalDate.now()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isToday) AccentDim else Glass, RoundedCornerShape(8.dp))
                        .clickable {
                            selectedDate = day
                            currentView = "day"
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = day.format(DateTimeFormatter.ofPattern("d MMMM")),
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                    Text("➔", color = Accent, fontSize = 14.sp)
                }
            }
        } else {
            // Day View items:
            // Status Filter Pills (All, Pending, In Progress, Done)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("all", "pending", "in_progress", "done").forEach { filter ->
                        val isActive = activeFilter == filter
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isActive) AccentDim else Glass,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { activeFilter = filter }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = filter.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) AccentLight else TextMuted
                            )
                        }
                    }
                }
            }

            // Daily Timeline Subheader + Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Timeline",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Purple),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("🖼️ Upload", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        TextButton(
                            onClick = { showAddClassDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Accent),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("+ Add Class", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Vertical Hourly Timeline List
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
            } else {
                val startHour = 6
                val endHour = 22
                val hoursRange = (startHour..endHour).toList()

                items(hoursRange) { h ->
                    val hourStr = String.format("%02d:00", h)
                    
                    // Get items scheduled inside this hour slot
                    val hourClasses = timetableClasses.filter {
                        val startH = it.startTime.substringBefore(":").toIntOrNull() ?: -1
                        startH == h
                    }
                    val hourTasks = tasks.filter {
                        val taskTime = it.startTime?.substringAfter("T") ?: ""
                        val startH = taskTime.substringBefore(":").toIntOrNull() ?: -1
                        startH == h
                    }

                    val hasItems = hourClasses.isNotEmpty() || hourTasks.isNotEmpty()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min), // Forces the vertical line Box to span correctly
                        verticalAlignment = Alignment.Top
                    ) {
                        // 1. Hour Label Column
                        Text(
                            text = hourStr,
                            fontSize = 11.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .width(50.dp)
                                .padding(top = 10.dp)
                        )

                        // 2. Timeline Axis Column (Continuous Vertical Line + Node Dot)
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            // Line
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(BorderLight)
                            )
                            // Node circle
                            Box(
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (hasItems) Accent else TextMuted)
                            )
                        }

                        // 3. Schedule Cards Column
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Render Classes
                            hourClasses.forEach { tt ->
                                GlassyCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Border)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left Purple Indicator Line
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(28.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Purple)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tt.label ?: "Class",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "${tt.startTime} – ${tt.endTime}  |  ${(tt.type ?: "Class").uppercase()}",
                                                fontSize = 9.sp,
                                                color = TextMuted,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    repository.deleteClass(tt.id)
                                                    loadSchedule()
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = "Delete Class",
                                                tint = Red,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Render Tasks
                            hourTasks.forEach { task ->
                                val isDone = task.status == "completed"
                                GlassyCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Border)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left Blue Indicator Line
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(28.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Accent)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDone) TextMuted else TextPrimary
                                            )
                                            
                                            val startTime = task.startTime?.substringAfter("T")?.take(5) ?: ""
                                            val endTime = task.endTime?.substringAfter("T")?.take(5) ?: "?"
                                            Text(
                                                text = "$startTime – $endTime",
                                                fontSize = 9.sp,
                                                color = TextMuted,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        
                                        val badgeColor = when (task.priority) {
                                            "urgent", "high" -> Red
                                            "medium" -> Amber
                                            else -> Green
                                        }
                                        Text(
                                            text = task.priority.uppercase(),
                                            color = badgeColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(badgeColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                        
                                        if (isDone) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("✅", fontSize = 10.sp)
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

    // Add Manual Class Dialog
    if (showAddClassDialog) {
        AlertDialog(
            onDismissRequest = { showAddClassDialog = false },
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
                Text("Add Class Block", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newClassLabel,
                        onValueChange = { newClassLabel = it },
                        label = { Text("Class / Subject Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    
                    Column {
                        Text("Day of the Week", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            daysOfWeek.take(4).forEach { day ->
                                val isSelected = newClassDay == day
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) AccentDim else Glass,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { newClassDay = day }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AccentLight else TextMuted
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            daysOfWeek.drop(4).forEach { day ->
                                val isSelected = newClassDay == day
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) AccentDim else Glass,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { newClassDay = day }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AccentLight else TextMuted
                                    )
                                }
                            }
                            Box(modifier = Modifier.weight(1f))
                        }
                    }

                    OutlinedTextField(
                        value = newClassStart,
                        onValueChange = { newClassStart = it },
                        label = { Text("Start Time (HH:MM)") },
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
                        value = newClassEnd,
                        onValueChange = { newClassEnd = it },
                        label = { Text("End Time (HH:MM)") },
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
                    onClick = { addNewClass() },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    enabled = newClassLabel.isNotEmpty() && newClassStart.isNotEmpty() && newClassEnd.isNotEmpty()
                ) {
                    Text("Add", color = Bg)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddClassDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}
