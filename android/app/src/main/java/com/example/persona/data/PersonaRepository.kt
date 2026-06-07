package com.example.persona.data

import android.content.Context
import com.example.persona.data.network.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class PersonaRepository(private val context: Context) {
    private val apiService get() = NetworkClient.getApiService(context)
    private val prefs = context.getSharedPreferences("persona_theme_prefs", Context.MODE_PRIVATE)

    var isDarkTheme by mutableStateOf(prefs.getBoolean("is_dark_theme", true))
        private set

    fun updateDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean("is_dark_theme", isDark).apply()
        isDarkTheme = isDark
    }

    // ── Auth ──────────────────────────────────────────────────
    suspend fun register(username: String, email: String, password: String): Response<AuthResponse> {
        return apiService.register(mapOf("username" to username, "email" to email, "password" to password))
    }

    suspend fun login(email: String, password: String): Response<AuthResponse> {
        return apiService.login(mapOf("email" to email, "password" to password))
    }

    suspend fun logout(): Response<MessageResponse> {
        val resp = apiService.logout()
        if (resp.isSuccessful) {
            NetworkClient.logout()
        }
        return resp
    }

    suspend fun getCurrentUser(): Response<User> = apiService.getCurrentUser()

    // ── Tasks ─────────────────────────────────────────────────
    suspend fun getTasks(status: String? = null, priority: String? = null, date: String? = null) =
        apiService.getTasks(status, priority, date)

    suspend fun createTask(title: String, description: String?, startTime: String?, endTime: String?, priority: String, status: String, category: String?) =
        apiService.createTask(mapOf(
            "title" to title,
            "description" to description,
            "start_time" to startTime,
            "end_time" to endTime,
            "priority" to priority,
            "status" to status,
            "category" to category
        ))

    suspend fun updateTask(taskId: String, fields: Map<String, Any?>) =
        apiService.updateTask(taskId, fields)

    suspend fun deleteTask(taskId: String) = apiService.deleteTask(taskId)

    suspend fun completeTask(taskId: String) = apiService.completeTask(taskId)

    // ── Habits ────────────────────────────────────────────────
    suspend fun getHabits() = apiService.getHabits()

    suspend fun createHabit(name: String, icon: String, color: String, targetDaysJson: String) =
        apiService.createHabit(mapOf(
            "name" to name,
            "icon" to icon,
            "color" to color,
            "target_days" to targetDaysJson
        ))

    suspend fun deleteHabit(habitId: String) = apiService.deleteHabit(habitId)

    suspend fun checkHabit(habitId: String) = apiService.checkHabit(habitId)

    // ── Expenses ──────────────────────────────────────────────
    suspend fun getExpenses(month: String? = null) = apiService.getExpenses(month)

    suspend fun createExpense(amount: Double, category: String, description: String?, date: String?) =
        apiService.createExpense(mapOf(
            "amount" to amount,
            "category" to category,
            "description" to description,
            "date" to date
        ))

    suspend fun deleteExpense(expenseId: String) = apiService.deleteExpense(expenseId)

    suspend fun updateExpense(expenseId: String, amount: Double, category: String, description: String?) =
        apiService.updateExpense(expenseId, mapOf(
            "amount" to amount,
            "category" to category,
            "description" to description
        ))

    suspend fun getExpensesTotal(month: String? = null) = apiService.getExpensesTotal(month)

    suspend fun analyzeExpenses(month: String? = null) = apiService.analyzeExpenses(month)

    // ── Notes ─────────────────────────────────────────────────
    suspend fun getNotes() = apiService.getNotes()

    suspend fun createNote(title: String, content: String, tagsJson: String, color: String, pinned: Int) =
        apiService.createNote(mapOf(
            "title" to title,
            "content" to content,
            "tags" to tagsJson,
            "color" to color,
            "pinned" to pinned
        ))

    suspend fun updateNote(noteId: String, fields: Map<String, Any?>) = apiService.updateNote(noteId, fields)

    suspend fun deleteNote(noteId: String) = apiService.deleteNote(noteId)

    // ── Assignments ───────────────────────────────────────────
    suspend fun getAssignments(status: String? = null) = apiService.getAssignments(status)

    suspend fun createAssignment(courseName: String, title: String, description: String?, dueDate: String?, status: String) =
        apiService.createAssignment(mapOf(
            "course_name" to courseName,
            "title" to title,
            "description" to description,
            "due_date" to dueDate,
            "status" to status
        ))

    suspend fun updateAssignment(assignmentId: String, fields: Map<String, Any?>) = apiService.updateAssignment(assignmentId, fields)

    suspend fun deleteAssignment(assignmentId: String) = apiService.deleteAssignment(assignmentId)

    suspend fun syncClassroom() = apiService.syncClassroom()

    // ── Timetable / OCR ───────────────────────────────────────
    suspend fun getTimetable() = apiService.getTimetable()

    suspend fun addClass(dayOfWeek: String, startTime: String, endTime: String, label: String, type: String) =
        apiService.addClass(mapOf(
            "day_of_week" to dayOfWeek,
            "start_time" to startTime,
            "end_time" to endTime,
            "label" to label,
            "type" to type
        ))

    suspend fun deleteClass(blockId: String) = apiService.deleteClass(blockId)

    suspend fun uploadTimetableImage(bytes: ByteArray, mimeType: String): Response<TimetableUploadResponse> {
        val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull(), 0, bytes.size)
        val body = MultipartBody.Part.createFormData("image", "timetable.jpg", requestFile)
        return apiService.uploadTimetable(body)
    }

    // ── Smart Planner ─────────────────────────────────────────
    suspend fun autoSchedule() = apiService.autoSchedule()

    suspend fun generateSchedule() = apiService.generateSchedule()

    // ── Email Sync ────────────────────────────────────────────
    suspend fun getEmailStatus() = apiService.getEmailStatus()
    suspend fun updateEmailConfig(config: Map<String, Any?>) = apiService.updateEmailConfig(config)
    suspend fun getEmailSummaries() = apiService.getEmailSummaries()
    suspend fun getUnnotifiedEmails() = apiService.getUnnotifiedEmails()
    suspend fun syncEmails() = apiService.syncEmails()
}
