package com.example.persona.data.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*
import com.google.gson.annotations.SerializedName
import kotlin.jvm.JvmSuppressWildcards

interface PersonaApiService {

    // ── Auth Endpoints ──────────────────────────────────────────
    @POST("api/auth/register")
    suspend fun register(@Body body: Map<String, String>): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body body: Map<String, String>): Response<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<MessageResponse>

    @GET("api/auth/me")
    suspend fun getCurrentUser(): Response<User>

    // ── Tasks Endpoints ─────────────────────────────────────────
    @GET("api/tasks/")
    suspend fun getTasks(
        @Query("status") status: String? = null,
        @Query("priority") priority: String? = null,
        @Query("date") date: String? = null
    ): Response<List<Task>>

    @POST("api/tasks/")
    suspend fun createTask(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<Task>

    @PUT("api/tasks/{id}")
    suspend fun updateTask(@Path("id") taskId: String, @Body body: @JvmSuppressWildcards Map<String, Any?>): Response<Task>

    @DELETE("api/tasks/{id}")
    suspend fun deleteTask(@Path("id") taskId: String): Response<MessageResponse>

    @PATCH("api/tasks/{id}/complete")
    suspend fun completeTask(@Path("id") taskId: String): Response<MessageResponse>

    // ── Habits Endpoints ────────────────────────────────────────
    @GET("api/habits/")
    suspend fun getHabits(): Response<List<HabitResponse>>

    @POST("api/habits/")
    suspend fun createHabit(@Body body: Map<String, String>): Response<Habit>

    @DELETE("api/habits/{id}")
    suspend fun deleteHabit(@Path("id") habitId: String): Response<MessageResponse>

    @PATCH("api/habits/{id}/check")
    suspend fun checkHabit(@Path("id") habitId: String): Response<HabitCheckResponse>

    // ── Expenses Endpoints ──────────────────────────────────────
    @GET("api/expenses/")
    suspend fun getExpenses(@Query("month") month: String? = null): Response<ExpensesResponse>

    @POST("api/expenses/")
    suspend fun createExpense(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<Expense>

    @DELETE("api/expenses/{id}")
    suspend fun deleteExpense(@Path("id") expenseId: String): Response<MessageResponse>

    @PUT("api/expenses/{id}")
    suspend fun updateExpense(@Path("id") expenseId: String, @Body body: @JvmSuppressWildcards Map<String, Any?>): Response<Expense>

    @GET("api/expenses/total")
    suspend fun getExpensesTotal(@Query("month") month: String? = null): Response<ExpenseTotalResponse>

    @GET("api/expenses/analyze")
    suspend fun analyzeExpenses(@Query("month") month: String? = null): Response<Map<String, String>>

    // ── Notes Endpoints ─────────────────────────────────────────
    @GET("api/notes/")
    suspend fun getNotes(): Response<List<Note>>

    @POST("api/notes/")
    suspend fun createNote(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<Note>

    @PUT("api/notes/{id}")
    suspend fun updateNote(@Path("id") noteId: String, @Body body: @JvmSuppressWildcards Map<String, Any?>): Response<Note>

    @DELETE("api/notes/{id}")
    suspend fun deleteNote(@Path("id") noteId: String): Response<MessageResponse>

    // ── Assignments Endpoints ───────────────────────────────────
    @GET("api/assignments/")
    suspend fun getAssignments(@Query("status") status: String? = null): Response<List<Assignment>>

    @POST("api/assignments/")
    suspend fun createAssignment(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<Assignment>

    @PUT("api/assignments/{id}")
    suspend fun updateAssignment(@Path("id") assignmentId: String, @Body body: @JvmSuppressWildcards Map<String, Any?>): Response<Assignment>

    @DELETE("api/assignments/{id}")
    suspend fun deleteAssignment(@Path("id") assignmentId: String): Response<MessageResponse>

    @POST("api/assignments/sync/classroom")
    suspend fun syncClassroom(): Response<MessageResponse>

    // ── Timetable / OCR Endpoints ────────────────────────────────
    @GET("api/timetable/")
    suspend fun getTimetable(): Response<List<TimetableItem>>

    @POST("api/timetable/")
    suspend fun addClass(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<MessageResponse>

    @DELETE("api/timetable/{id}")
    suspend fun deleteClass(@Path("id") blockId: String): Response<MessageResponse>

    @Multipart
    @POST("api/timetable/upload")
    suspend fun uploadTimetable(
        @Part image: MultipartBody.Part
    ): Response<TimetableUploadResponse>

    // ── Smart Planner Endpoints ──────────────────────────────────
    @POST("api/scheduler/auto-schedule")
    suspend fun autoSchedule(): Response<AutoScheduleResponse>

    @POST("api/planner/generate")
    suspend fun generateSchedule(): Response<PlannerGenerateResponse>

    // ── Email Sync Endpoints ─────────────────────────────────────
    @GET("api/email/status")
    suspend fun getEmailStatus(): Response<Map<String, Any>>

    @POST("api/email/config")
    suspend fun updateEmailConfig(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<MessageResponse>

    @GET("api/email/summaries")
    suspend fun getEmailSummaries(): Response<List<Map<String, Any>>>

    @GET("api/email/unnotified")
    suspend fun getUnnotifiedEmails(): Response<List<Map<String, Any>>>

    @POST("api/email/sync")
    suspend fun syncEmails(): Response<MessageResponse>
}

// ── Additional Network Responses ────────────────────────────────
data class HabitResponse(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val name: String,
    val icon: String,
    val color: String,
    @SerializedName("target_days") val targetDays: String,
    @SerializedName("done_today") val doneToday: Boolean,
    val streak: Int
)

data class HabitCheckResponse(
    @SerializedName("done_today") val doneToday: Boolean,
    val streak: Int
)

data class ExpensesResponse(
    val expenses: List<Expense>,
    val summary: List<ExpenseSummaryItem>
)

data class ExpenseSummaryItem(
    val category: String,
    val total: Double
)

data class ExpenseTotalResponse(
    val total: Double,
    @SerializedName("total_spent") val totalSpent: Double,
    @SerializedName("total_income") val totalIncome: Double,
    val month: String
)

data class TimetableUploadResponse(
    val message: String,
    @SerializedName("extracted_data") val extractedData: List<Map<String, Any>>
)

data class AutoScheduleResponse(
    val message: String,
    val scheduled: List<Map<String, Any>>
)

data class PlannerGenerateResponse(
    val message: String,
    val explanation: String,
    val tips: List<String>?,
    val scheduled: List<Map<String, Any>>
)
