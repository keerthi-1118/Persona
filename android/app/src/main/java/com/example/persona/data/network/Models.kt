package com.example.persona.data.network

import com.google.gson.annotations.SerializedName

data class User(
    val id: String,
    val username: String,
    val email: String,
    @SerializedName("avatar_url") val avatarUrl: String?,
    val timezone: String?
)

data class Task(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val title: String,
    val description: String?,
    @SerializedName("start_time") val startTime: String?,
    @SerializedName("end_time") val endTime: String?,
    val priority: String, // low, medium, high, urgent
    val status: String, // pending, in_progress, completed, cancelled
    val category: String?,
    @SerializedName("is_scheduled") val isScheduled: Int?,
    @SerializedName("is_block") val isBlock: Boolean? = false
)

data class TaskBlock(
    val id: String,
    @SerializedName("task_id") val taskId: String,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String
)

data class Assignment(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("course_name") val courseName: String,
    @SerializedName("course_id") val courseId: String?,
    @SerializedName("assignment_id") val assignmentId: String?,
    val title: String,
    val description: String?,
    @SerializedName("due_date") val dueDate: String?,
    val status: String, // pending, submitted, late, graded
    val link: String?,
    val source: String? // manual, google_classroom
)

data class Expense(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val amount: Double,
    val category: String, // food, transport, books, entertainment, health, shopping, other, Income
    val description: String?,
    val date: String
)

data class Habit(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val name: String,
    val icon: String,
    val color: String,
    @SerializedName("target_days") val targetDays: String // JSON array e.g. "[\"mon\"]"
)

data class HabitLog(
    val id: String,
    @SerializedName("habit_id") val habitId: String,
    @SerializedName("user_id") val userId: String,
    val date: String,
    val completed: Int // 0 or 1
)

data class Note(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val title: String?,
    val content: String?,
    val tags: String?, // JSON array
    val color: String?,
    val pinned: Int // 0 or 1
)

data class FocusSession(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("task_id") val taskId: String?,
    val duration: Int,
    val type: String, // focus, short_break, long_break
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("ended_at") val endedAt: String?,
    val completed: Int
)

data class TimetableItem(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("day_of_week") val dayOfWeek: String, // mon, tue, wed, thu, fri, sat, sun
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    val label: String?,
    val type: String? // class, blocked, other
)

data class MessageResponse(
    val message: String
)

data class AuthResponse(
    val message: String,
    @SerializedName("user_id") val userId: String,
    val username: String
)

data class TimetableResponse(
    val message: String,
    val count: Int
)
