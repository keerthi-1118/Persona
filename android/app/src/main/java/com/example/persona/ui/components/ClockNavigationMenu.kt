package com.example.persona.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.persona.theme.*
import com.example.persona.ui.main.PersonaTab
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ClockNavigationMenu(
    selectedTab: PersonaTab,
    onTabSelected: (PersonaTab) -> Unit,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isMenuOpen by remember { mutableStateOf(false) }
    var dragAngleRad by remember { mutableStateOf<Double?>(null) }
    var dragDistance by remember { mutableStateOf<Float?>(null) }
    var hoveredTab by remember { mutableStateOf<PersonaTab?>(null) }

    val vibrate = { duration: Long ->
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {}
    }
    
    // Track local time for real-time hands
    var time by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = LocalTime.now()
            delay(1000)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        // Full screen transparent overlay to close menu when clicking outside
        if (isMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isMenuOpen = false
                    }
            )
        }

        // Fan Out Menu Items
        val menuRadius by animateDpAsState(
            targetValue = if (isMenuOpen) 130.dp else 0.dp,
            animationSpec = tween(durationMillis = 350)
        )
        val menuAlpha by animateFloatAsState(
            targetValue = if (isMenuOpen) 1f else 0f,
            animationSpec = tween(durationMillis = 250)
        )
        val menuScale by animateFloatAsState(
            targetValue = if (isMenuOpen) 1f else 0f,
            animationSpec = tween(durationMillis = 300)
        )

        val tabs = PersonaTab.values()
        tabs.forEachIndexed { index, tab ->
            // Angle from 180 (left) to 270 (up) fanning out to the upper-left quadrant
            val angleDeg = 180.0 + (index * 18.0)
            val angleRad = Math.toRadians(angleDeg)
            val offsetX = (menuRadius.value * cos(angleRad)).dp
            val offsetY = (menuRadius.value * sin(angleRad)).dp

            val isTabActive = selectedTab == tab
            val isHighlighted = isTabActive || hoveredTab == tab
            val tabScaleFactor = if (hoveredTab == tab) 1.2f else 1.0f

            Box(
                modifier = Modifier
                    .offset(x = offsetX, y = offsetY)
                    .alpha(menuAlpha)
                    .scale(menuScale * tabScaleFactor)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC070B19))
                    .border(if (isHighlighted) 2.dp else 1.dp, if (isHighlighted) Accent else Color(0x26FFFFFF), CircleShape)
                    .clickable(enabled = isMenuOpen) {
                        onTabSelected(tab)
                        isMenuOpen = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getTabIcon(tab),
                    contentDescription = tab.title,
                    tint = if (isHighlighted) AccentLight else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Floating Hover Label Badge (pop up when finger/arrow is over the icon)
        if (isMenuOpen && hoveredTab != null) {
            Box(
                modifier = Modifier
                    .offset(x = (-80).dp, y = (-170).dp)
                    .background(Color(0xCC070B19), RoundedCornerShape(6.dp))
                    .border(1.dp, Accent, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = hoveredTab!!.title.uppercase(),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        val resolvedTextSecondary = TextSecondary
        val resolvedTextMuted = TextMuted

        // Main Clock Button
        Box(
            modifier = Modifier
                .padding(bottom = 16.dp, end = 16.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(BgElevated)
                .border(2.dp, if (isMenuOpen) Accent else BorderLight, CircleShape)
                .pointerInput(Unit) {
                    val abortRadiusPx = 15.dp.toPx()
                    val doubleTapTimeout = 300L
                    var lastClickTime = 0L

                    forEachGesture {
                        awaitPointerEventScope {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val longPressTimeout = viewConfiguration.longPressTimeoutMillis

                            // Wait for long press or release (up)
                            val dragStart = withTimeoutOrNull(longPressTimeout) {
                                waitForUpOrCancellation()
                            }

                            if (dragStart == null) {
                                // 1. Long Press detected! Start fanned out menu drag
                                isMenuOpen = true
                                vibrate(15)

                                // Track drag movement
                                var pointerId = down.id
                                val center = Offset(size.width / 2f, size.height / 2f)

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.find { it.id == pointerId } ?: break
                                    if (!change.pressed) break // released

                                    val pos = change.position
                                    val touchVector = Offset(pos.x - center.x, pos.y - center.y)
                                    dragDistance = touchVector.getDistance()
                                    dragAngleRad = Math.atan2(touchVector.y.toDouble(), touchVector.x.toDouble()).let {
                                        if (it < 0) it + 2.0 * Math.PI else it
                                    }

                                    val angleDeg = Math.toDegrees(dragAngleRad!!).let { if (it < 0) it + 360 else it }
                                    if (dragDistance!! >= abortRadiusPx) {
                                        val closest = tabs.minByOrNull { tab ->
                                            val tabAngle = 180.0 + (tab.ordinal * 18.0)
                                            var diff = Math.abs(tabAngle - angleDeg)
                                            if (diff > 180.0) diff = 360.0 - diff
                                            diff
                                        }
                                        if (closest != null) {
                                            val tabAngle = 180.0 + (closest.ordinal * 18.0)
                                            var diff = Math.abs(tabAngle - angleDeg)
                                            if (diff > 180.0) diff = 360.0 - diff
                                            val nextHovered = if (diff <= 45.0) closest else null
                                            if (nextHovered != hoveredTab) {
                                                hoveredTab = nextHovered
                                                vibrate(5)
                                            }
                                        }
                                    } else {
                                        hoveredTab = null
                                    }
                                    change.consume()
                                }

                                // Drag end
                                hoveredTab?.let { tab ->
                                    onTabSelected(tab)
                                }
                                isMenuOpen = false
                                dragAngleRad = null
                                dragDistance = null
                                hoveredTab = null
                                vibrate(15)
                            } else {
                                // 2. Released before long press threshold (Tap or Double Tap)
                                val upTime = System.currentTimeMillis()
                                if (upTime - lastClickTime < doubleTapTimeout) {
                                    // Double Tap
                                    onDoubleTap()
                                    lastClickTime = 0L // reset
                                } else {
                                    // Single Tap
                                    onTabSelected(PersonaTab.Dashboard)
                                    lastClickTime = upTime
                                }
                                dragStart.consume()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f
                
                // Draw 12 hour ticks
                for (i in 0 until 12) {
                    val angleRad = Math.toRadians(i * 30.0 - 90.0)
                    val isMajor = i % 3 == 0
                    val tickLen = if (isMajor) 6.dp.toPx() else 4.dp.toPx()
                    val strokeW = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                    val tickColor = if (isMajor) resolvedTextSecondary else resolvedTextMuted

                    val startX = center.x + (radius - 4.dp.toPx() - tickLen) * cos(angleRad).toFloat()
                    val startY = center.y + (radius - 4.dp.toPx() - tickLen) * sin(angleRad).toFloat()
                    val endX = center.x + (radius - 4.dp.toPx()) * cos(angleRad).toFloat()
                    val endY = center.y + (radius - 4.dp.toPx()) * sin(angleRad).toFloat()

                    drawLine(
                        color = tickColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }

                // Calculate hand rotations (0° = 12 o'clock, clockwise)
                val hourAngle = Math.toRadians((time.hour % 12) * 30.0 + time.minute * 0.5 - 90.0)
                val minuteAngle = Math.toRadians(time.minute * 6.0 + time.second * 0.1 - 90.0)

                // Hour Hand
                val hourHandLength = radius * 0.45f
                val hourX = center.x + hourHandLength * cos(hourAngle).toFloat()
                val hourY = center.y + hourHandLength * sin(hourAngle).toFloat()
                drawLine(
                    color = AccentLight,
                    start = center,
                    end = Offset(hourX, hourY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Minute Hand (longer, pointer style)
                val activeMinuteAngle = when {
                    dragAngleRad != null -> dragAngleRad!!
                    isMenuOpen -> Math.toRadians(180.0 + 2.5 * 18.0)
                    else -> minuteAngle
                }
                val activeMinuteHandLength = when {
                    dragDistance != null -> dragDistance!!.coerceIn(radius * 0.70f, radius * 1.5f)
                    isMenuOpen -> radius * 1.0f
                    else -> radius * 0.70f
                }
                val minX = center.x + activeMinuteHandLength * cos(activeMinuteAngle).toFloat()
                val minY = center.y + activeMinuteHandLength * sin(activeMinuteAngle).toFloat()
                drawLine(
                    color = Accent,
                    start = center,
                    end = Offset(minX, minY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Center Pin Dot
                drawCircle(
                    color = Accent,
                    radius = 3.5.dp.toPx(),
                    center = center
                )
            }
        }
    }
}

fun getTabIcon(tab: PersonaTab): ImageVector {
    return when (tab) {
        PersonaTab.Dashboard -> Icons.Outlined.Home
        PersonaTab.Planner -> Icons.Outlined.CalendarMonth
        PersonaTab.Assignments -> Icons.Outlined.Assignment
        PersonaTab.Habits -> Icons.Outlined.CheckCircle
        PersonaTab.Expenses -> Icons.Outlined.AccountBalanceWallet
        PersonaTab.Notes -> Icons.Outlined.Description
    }
}
