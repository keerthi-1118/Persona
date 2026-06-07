package com.example.persona.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.persona.MainActivity
import com.example.persona.data.PersonaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmailSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = PersonaRepository(applicationContext)
        try {
            // First, trigger sync of emails on the backend
            val syncResponse = repository.syncEmails()
            if (!syncResponse.isSuccessful) {
                if (syncResponse.code() == 401) {
                    // Unauthorized: User is probably logged out, stop trying
                    return@withContext Result.failure()
                }
                return@withContext Result.retry()
            }

            // Next, retrieve any unnotified matched emails
            val unnotifiedResponse = repository.getUnnotifiedEmails()
            if (unnotifiedResponse.isSuccessful) {
                val emails = unnotifiedResponse.body()
                if (emails != null && emails.isNotEmpty()) {
                    val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val channelId = "persona_emails"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            channelId,
                            "AI Priority Emails",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Notifications for matched priority emails"
                        }
                        notificationManager.createNotificationChannel(channel)
                    }

                    for (email in emails) {
                        val emailId = (email["email_id"] as? String) ?: (email["id"] as? String) ?: ""
                        val sender = (email["sender"] as? String) ?: "Unknown Sender"
                        val subject = (email["subject"] as? String) ?: "No Subject"
                        val summary = (email["summary"] as? String) ?: "Priority email matched."

                        val mainIntent = Intent(applicationContext, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        val pFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                        val pendingIntent = PendingIntent.getActivity(applicationContext, emailId.hashCode(), mainIntent, pFlags)

                        val notification = NotificationCompat.Builder(applicationContext, channelId)
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle("Priority Mail: $subject")
                            .setContentText("$sender: $summary")
                            .setStyle(NotificationCompat.BigTextStyle().bigText("From: $sender\n\n$summary"))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .setContentIntent(pendingIntent)
                            .build()

                        notificationManager.notify(emailId.hashCode(), notification)
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
