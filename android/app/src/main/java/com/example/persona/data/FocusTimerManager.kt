package com.example.persona.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.persona.theme.Accent
import com.example.persona.theme.Green
import com.example.persona.theme.Purple
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TimerMode(val minutes: Int, val label: String, val color: Color) {
    Focus(25, "Focus", Accent),
    ShortBreak(5, "Short Break", Green),
    LongBreak(15, "Long Break", Purple)
}

object FocusTimerManager {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    var mode by mutableStateOf(TimerMode.Focus)
        private set

    var secondsLeft by mutableStateOf(TimerMode.Focus.minutes * 60)
        private set

    var isRunning by mutableStateOf(false)
        private set

    fun start() {
        if (isRunning) return
        isRunning = true
        timerJob = scope.launch {
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft -= 1
            }
            isRunning = false
            reset()
        }
    }

    fun pause() {
        isRunning = false
        timerJob?.cancel()
        timerJob = null
    }

    fun reset() {
        pause()
        secondsLeft = mode.minutes * 60
    }

    fun setTimerMode(newMode: TimerMode) {
        pause()
        mode = newMode
        secondsLeft = newMode.minutes * 60
    }
}
