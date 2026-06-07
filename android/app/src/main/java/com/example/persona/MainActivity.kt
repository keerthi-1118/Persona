package com.example.persona

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.persona.theme.PersonaTheme

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.persona.data.PersonaRepository

import android.os.Build
import android.Manifest
import android.content.pm.PackageManager

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Request notification permissions for API 33+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    // Initialize notification channel
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(
            "persona_reminders",
            "Task Reminders",
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for student tasks and reminders"
        }
        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    // Schedule periodic Email Sync background worker
    try {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.persona.reminder.EmailSyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "EmailSyncWorker",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        System.out.println("Scheduled EmailSyncWorker successfully.")
    } catch (e: Exception) {
        System.err.println("Failed to schedule EmailSyncWorker: " + e.message)
    }

    enableEdgeToEdge()
    setContent {
      val context = LocalContext.current
      val repository = remember { PersonaRepository(context) }
      val isDark = true

      PersonaTheme(isDark = isDark) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(repository = repository)
        }
      }
    }
  }
}
