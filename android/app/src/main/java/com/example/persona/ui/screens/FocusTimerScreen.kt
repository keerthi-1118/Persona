package com.example.persona.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.data.PersonaRepository
import com.example.persona.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TimerMode(val minutes: Int, val label: String, val color: Color) {
    Focus(25, "Focus", Accent),
    ShortBreak(5, "Short Break", Green),
    LongBreak(15, "Long Break", Purple)
}

@Composable
fun FocusTimerScreen(repository: PersonaRepository) {
    var mode by remember { mutableStateOf(TimerMode.Focus) }
    var secondsLeft by remember { mutableStateOf(mode.minutes * 60) }
    var isRunning by remember { mutableStateOf(false) }

    val totalSeconds = mode.minutes * 60
    val progress = if (totalSeconds > 0) secondsLeft.toFloat() / totalSeconds else 0f

    // Timer loop effect
    LaunchedEffect(isRunning, mode) {
        if (!isRunning) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
        isRunning = false
        // Play notification or reset
        secondsLeft = mode.minutes * 60
    }

    // Reset timer when switching modes
    LaunchedEffect(mode) {
        isRunning = false
        secondsLeft = mode.minutes * 60
    }

    val minutesStr = String.format("%02d", secondsLeft / 60)
    val secondsStr = String.format("%02d", secondsLeft % 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Timer Mode Selectors
        Row(
            modifier = Modifier
                .background(Glass2, RoundedCornerShape(25.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TimerMode.values().forEach { timerMode ->
                val isActive = mode == timerMode
                Button(
                    onClick = { mode = timerMode },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) timerMode.color else Color.Transparent
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = timerMode.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Bg else TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Circular Timer Display
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(CircleShape)
                .background(BgElevated),
            contentAlignment = Alignment.Center
        ) {
            // Circular progress indicator ring
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize().padding(8.dp),
                color = mode.color,
                strokeWidth = 8.dp,
                trackColor = Border
            )

            // Inner Time Text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$minutesStr:$secondsStr",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = (-2).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = mode.name.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reset Button
            OutlinedButton(
                onClick = {
                    isRunning = false
                    secondsLeft = mode.minutes * 60
                },
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier
                    .height(50.dp)
                    .width(110.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(BorderLight)
                )
            ) {
                Text("Reset", fontWeight = FontWeight.Bold)
            }

            // Start / Pause Button
            Button(
                onClick = { isRunning = !isRunning },
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier
                    .height(50.dp)
                    .width(150.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mode.color)
            ) {
                Text(
                    text = if (isRunning) "Pause" else "Start",
                    fontWeight = FontWeight.Bold,
                    color = Bg,
                    fontSize = 16.sp
                )
            }
        }
    }
}
