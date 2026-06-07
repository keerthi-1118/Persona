package com.example.persona.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import com.example.persona.ui.components.ApplyDialogBlur
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.data.PersonaRepository
import com.example.persona.data.network.Expense
import com.example.persona.data.network.ExpenseSummaryItem
import com.example.persona.theme.*
import com.example.persona.ui.components.GlassyCard
import kotlinx.coroutines.launch
import java.time.LocalDate
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(repository: PersonaRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var summaryByCategory by remember { mutableStateOf<List<ExpenseSummaryItem>>(emptyList()) }
    var totalSpent by remember { mutableStateOf(0.0) }
    var totalIncome by remember { mutableStateOf(0.0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // AI Analysis States
    var isAnalyzing by remember { mutableStateOf(false) }
    var aiAnalysis by remember { mutableStateOf<String?>(null) }
    var aiAdvice by remember { mutableStateOf<String?>(null) }
    var aiUnnecessarySpending by remember { mutableStateOf<String?>(null) }
    var aiHighestCategories by remember { mutableStateOf<String?>(null) }
    var aiSpendingPatterns by remember { mutableStateOf<String?>(null) }
    var aiSuggestions by remember { mutableStateOf<String?>(null) }

    fun triggerAiAnalysis() {
        scope.launch {
            isAnalyzing = true
            try {
                val currentMonth = LocalDate.now().toString().substring(0, 7)
                val resp = repository.analyzeExpenses(currentMonth)
                if (resp.isSuccessful && resp.body() != null) {
                    val data = resp.body()!!
                    aiAnalysis = data["analysis"]
                    aiAdvice = data["advice"]
                    aiUnnecessarySpending = data["unnecessarySpending"]
                    aiHighestCategories = data["highestCategories"]
                    aiSpendingPatterns = data["spendingPatterns"]
                    aiSuggestions = data["suggestions"]
                } else {
                    aiAnalysis = "Failed to fetch analysis from server."
                }
            } catch (e: Exception) {
                aiAnalysis = "Connection error: ${e.localizedMessage}"
            } finally {
                isAnalyzing = false
            }
        }
    }

    // Dialog inputs
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("food") }
    var description by remember { mutableStateOf("") }
    var editingExpenseId by remember { mutableStateOf<String?>(null) }

    val categories = listOf("food", "transport", "books", "entertainment", "health", "shopping", "other", "Income")

    fun loadExpenses() {
        scope.launch {
            isLoading = true
            try {
                val currentMonth = LocalDate.now().toString().substring(0, 7) // "YYYY-MM"
                
                // Get list and summary
                val resp = repository.getExpenses(currentMonth)
                if (resp.isSuccessful && resp.body() != null) {
                    expenses = resp.body()!!.expenses
                    summaryByCategory = resp.body()!!.summary
                }

                // Get totals
                val totalResp = repository.getExpensesTotal(currentMonth)
                if (totalResp.isSuccessful && totalResp.body() != null) {
                    totalSpent = totalResp.body()!!.totalSpent
                    totalIncome = totalResp.body()!!.totalIncome
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                isLoading = false
            }
        }
    }

    fun saveExpense() {
        scope.launch {
            try {
                val amtDouble = amount.toDoubleOrNull() ?: 0.0
                if (amtDouble <= 0) {
                    Toast.makeText(context, "Please enter a valid amount greater than 0", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val descParam = if (description.isEmpty()) null else description
                val resp = if (editingExpenseId != null) {
                    repository.updateExpense(editingExpenseId!!, amtDouble, category, descParam)
                } else {
                    repository.createExpense(
                        amount = amtDouble,
                        category = category,
                        description = descParam,
                        date = LocalDate.now().toString()
                    )
                }
                if (resp.isSuccessful) {
                    showAddDialog = false
                    editingExpenseId = null
                    amount = ""
                    category = "food"
                    description = ""
                    loadExpenses()
                    Toast.makeText(context, "Transaction saved!", Toast.LENGTH_SHORT).show()
                } else {
                    val errorMsg = resp.errorBody()?.string() ?: "Unknown error"
                    Toast.makeText(context, "Failed to save: $errorMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteExpense(eid: String) {
        scope.launch {
            try {
                repository.deleteExpense(eid)
                loadExpenses()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LaunchedEffect(Unit) {
        loadExpenses()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Laptop Style Header: Title + Theme Toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Expenses", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Monthly Ledger", fontSize = 13.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            // Balance Header Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Spent Card
                GlassyCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp, 8.dp).clip(CircleShape).background(Color(0xFFFF6B6B).copy(alpha = 0.7f)))
                            Text("Total Spent", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "₹${String.format("%.2f", totalSpent)}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }

                // Income Card
                GlassyCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp, 8.dp).clip(CircleShape).background(Color(0xFF34D399).copy(alpha = 0.7f)))
                            Text("Total Income", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "₹${String.format("%.2f", totalIncome)}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF34D399)
                        )
                    }
                }
            }
        }

        // Spending Breakdown Round Chart (Donut Chart)
        item {
            val expenseSummary = summaryByCategory.filter { it.category != "Income" && it.total > 0 }
            val totalExpense = expenseSummary.sumOf { it.total }
            if (totalExpense > 0) {
                GlassyCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderLight)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Spending Breakdown",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Donut Chart Canvas
                            Box(
                                modifier = Modifier.size(110.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Canvas(modifier = Modifier.size(110.dp)) {
                                    var startAngle = -90f
                                    expenseSummary.forEach { item ->
                                        val sweepAngle = 360f * (item.total / totalExpense).toFloat()
                                        drawArc(
                                            color = when (item.category.lowercase(java.util.Locale.ROOT)) {
                                                "food" -> Amber
                                                "transport" -> Teal
                                                "books" -> Accent
                                                "entertainment" -> Red
                                                "health" -> Green
                                                "shopping" -> Pink
                                                else -> Purple
                                            },
                                            startAngle = startAngle,
                                            sweepAngle = sweepAngle,
                                            useCenter = false,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = 10.dp.toPx(),
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                                            )
                                        )
                                        startAngle += sweepAngle
                                    }
                                }
                                
                                // Total inside center
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Spent", fontSize = 10.sp, color = TextMuted)
                                    Text(
                                        text = "₹${String.format("%.2f", totalExpense)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                            
                            // Legend
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                expenseSummary.forEach { item ->
                                    val percentage = (item.total / totalExpense) * 100
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(
                                                    when (item.category.lowercase(java.util.Locale.ROOT)) {
                                                        "food" -> Amber
                                                        "transport" -> Teal
                                                        "books" -> Accent
                                                        "entertainment" -> Red
                                                        "health" -> Green
                                                        "shopping" -> Pink
                                                        else -> Purple
                                                    }
                                                )
                                        )
                                        Text(
                                            text = "${item.category.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }} (${String.format("%.1f", percentage)}%)",
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            // AI Expense Assistant Card
            GlassyCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Glass2),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = "AI Assistant",
                                    tint = Purple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "AI Expense Assistant",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Powered by Gemini AI",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        // Only show small action button if we already analyzed
                        if (aiAnalysis != null && !isAnalyzing) {
                            TextButton(
                                onClick = { triggerAiAnalysis() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Re-analyze", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isAnalyzing) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Analyzing your spending habits...",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Accent,
                                trackColor = Glass2
                            )
                        }
                    } else if (aiAnalysis == null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Get instant insights on where you are spending unnecessarily, identify redundant items, and receive custom budget tips.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                            Button(
                                onClick = { triggerAiAnalysis() },
                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Analyze Spending", fontSize = 12.sp, color = Bg, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 1. Unnecessary spending highlight badge
                            aiUnnecessarySpending?.let { unnecessary ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Purple.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                        .border(1.dp, Purple.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.MoneyOff,
                                            contentDescription = "Unnecessary Spending",
                                            tint = Purple,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = unnecessary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Purple
                                        )
                                    }
                                }
                            }

                            // 2. Main Analysis Paragraph
                            Column {
                                Text(
                                    text = "MONTHLY INSIGHTS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = aiAnalysis!!,
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    lineHeight = 17.sp
                                )
                            }

                            // Highest Categories
                            aiHighestCategories?.let { hc ->
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.BarChart,
                                            contentDescription = null,
                                            tint = Teal,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "HIGHEST CATEGORIES",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Teal,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = hc,
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        lineHeight = 17.sp
                                    )
                                }
                            }

                            // Spending Patterns
                            aiSpendingPatterns?.let { sp ->
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ShowChart,
                                            contentDescription = null,
                                            tint = Accent,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "SPENDING PATTERNS",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Accent,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = sp,
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        lineHeight = 17.sp
                                    )
                                }
                            }

                            // 3. Actionable Advice
                            aiAdvice?.let { advice ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Glass, RoundedCornerShape(10.dp))
                                        .border(1.dp, BorderLight, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Lightbulb,
                                            contentDescription = null,
                                            tint = Amber,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "ADVICE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Amber,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = advice,
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        lineHeight = 17.sp
                                    )
                                }
                            }

                            // Suggestions to reduce spending
                            aiSuggestions?.let { sug ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Purple.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                        .border(1.dp, Purple.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Spa,
                                            contentDescription = null,
                                            tint = Purple,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "SUGGESTIONS",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Purple,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = sug,
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Expense Button Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transaction History",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Button(
                    onClick = {
                        editingExpenseId = null
                        amount = ""
                        category = "food"
                        description = ""
                        showAddDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("+ Transaction", fontSize = 12.sp, color = Bg, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Transaction History List Items
        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
        } else if (expenses.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("No transactions logged this month.", color = TextMuted, fontSize = 14.sp)
                }
            }
        } else {
            items(expenses) { exp ->
                val isIncome = exp.category == "Income"
                GlassyCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when (exp.category) {
                            "food" -> Icons.Outlined.Fastfood
                            "transport" -> Icons.Outlined.DirectionsBus
                            "books" -> Icons.Outlined.MenuBook
                            "entertainment" -> Icons.Outlined.Movie
                            "health" -> Icons.Outlined.LocalHospital
                            "shopping" -> Icons.Outlined.ShoppingBag
                            "Income" -> Icons.Outlined.AttachMoney
                            else -> Icons.Outlined.Label
                        }
                        val iconColor = when (exp.category) {
                            "food" -> Amber
                            "transport" -> Teal
                            "books" -> Accent
                            "entertainment" -> Red
                            "health" -> Green
                            "shopping" -> Pink
                            else -> Purple
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(iconColor.copy(alpha = 0.12f))
                                .border(1.dp, iconColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = exp.category,
                                tint = iconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))

                        // Details
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exp.description ?: exp.category.uppercase(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = exp.date,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        // Price and Actions
                        Text(
                            text = "${if (isIncome) "+" else "-"}₹${String.format("%.2f", exp.amount)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isIncome) Color(0xFF34D399) else Color(0xFFFF6B6B),
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        IconButton(
                            onClick = {
                                editingExpenseId = exp.id
                                amount = exp.amount.toString()
                                category = exp.category
                                description = exp.description ?: ""
                                showAddDialog = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit",
                                tint = Accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { deleteExpense(exp.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Transaction Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingExpenseId = null
                amount = ""
                category = "food"
                description = ""
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
                Text(if (editingExpenseId != null) "Edit Transaction" else "Log Transaction", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (₹)") },
                        placeholder = { Text("0.00") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    // Category Row with horizontal scrolling and glass border pills
                    Column {
                        Text("Category", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = category == cat
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSelected) AccentDim else Glass,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Accent else Border,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { category = cat }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = cat.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AccentLight else TextMuted
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description / Note") },
                        placeholder = { Text("e.g. Lunch at cafeteria") },
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
                    onClick = { saveExpense() },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    enabled = amount.isNotEmpty()
                ) {
                    Text("Save", color = Bg)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    editingExpenseId = null
                    amount = ""
                    category = "food"
                    description = ""
                }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}
