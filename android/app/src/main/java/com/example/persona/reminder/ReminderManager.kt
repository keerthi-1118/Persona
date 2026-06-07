package com.example.persona.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.persona.data.network.Task
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

object ReminderManager {

    fun scheduleReminder(context: Context, taskId: String, title: String, description: String?, startTimeStr: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        try {
            // Parse ISO timestamp: YYYY-MM-DDTHH:MM:SS or similar
            val ldt = LocalDateTime.parse(startTimeStr.substringBefore("Z").substringBefore("+"))
            val triggerMs = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            // Do not schedule past alarms
            if (triggerMs <= System.currentTimeMillis()) return

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("task_id", taskId)
                putExtra("title", title)
                putExtra("description", description ?: "")
            }
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId.hashCode(),
                intent,
                flags
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerMs,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMs,
                    pendingIntent
                )
            }
            System.out.println("Scheduled alarm for task $taskId ($title) at $startTimeStr")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelReminder(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, NotificationReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            flags
        )
        alarmManager.cancel(pendingIntent)
    }

    fun scheduleAllFutureTasks(context: Context, tasks: List<Task>) {
        for (task in tasks) {
            if (task.status == "pending" && !task.startTime.isNullOrEmpty()) {
                scheduleReminder(
                    context = context,
                    taskId = task.id,
                    title = task.title,
                    description = task.description,
                    startTimeStr = task.startTime
                )
            } else {
                cancelReminder(context, task.id)
            }
        }
    }
}
