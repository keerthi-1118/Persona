package com.example.persona.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.*

// ─── Corner glow colours ──────────────────────────────────────────────────────
private val CORNER_COLORS = listOf(
    Color(0xFF3B82F6),   // Blue       — top-left
    Color(0xFFA855F7),   // Purple     — top-right
    Color(0xFF06B6D4),   // Cyan       — bottom-left
    Color(0xFFEC4899),   // Pink       — bottom-right
)

/**
 * Hex grid background with:
 * • Dark, near-invisible hex grid (always on)
 * • 4 coloured glow orbs orbiting from each corner — they smoothly
 *   illuminate the hexes they drift over with their corner colour
 * • Touch/drag ripple effect
 */
@Composable
fun WavingGradientBackground(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    val anim = rememberInfiniteTransition(label = "hexbg")

    // Each corner orb has its own independent phase & speed so they never sync up
    val phase1 by anim.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(7000, easing = LinearEasing), RepeatMode.Restart
        ), label = "p1"
    )
    val phase2 by anim.animateFloat(
        initialValue = PI.toFloat(),
        targetValue = (3f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(10500, easing = LinearEasing), RepeatMode.Restart
        ), label = "p2"
    )
    val phase3 by anim.animateFloat(
        initialValue = (PI * 0.5f).toFloat(),
        targetValue = (PI * 2.5f).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(8800, easing = LinearEasing), RepeatMode.Restart
        ), label = "p3"
    )
    val phase4 by anim.animateFloat(
        initialValue = (PI * 1.5f).toFloat(),
        targetValue = (PI * 3.5f).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(12000, easing = LinearEasing), RepeatMode.Restart
        ), label = "p4"
    )

    // Gentle breathing pulse for the glow intensity
    val pulse by anim.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.00f,
        animationSpec = infiniteRepeatable(
            tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart  = { touchPos = it },
                        onDrag       = { ch, _ -> touchPos = ch.position },
                        onDragEnd    = { touchPos = null },
                        onDragCancel = { touchPos = null }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = { offset ->
                        touchPos = offset
                        tryAwaitRelease()
                        touchPos = null
                    })
                }
        ) {
            val W = size.width
            val H = size.height
            if (W == 0f || H == 0f) return@Canvas

            // Solid dark base
            drawRect(Color(0xFF030712))

            // ── Orb orbit sizes (each corner has slightly different orbit) ──
            val orbitW = W * 0.28f
            val orbitH = H * 0.22f

            // ── 4 glow orb positions: each stays near its corner ────────────
            val glowPositions = listOf(
                // Top-left corner
                Offset(
                    x = W * 0.02f + orbitW * 0.5f * (1f + cos(phase1)),
                    y = H * 0.02f + orbitH * 0.5f * (1f + sin(phase1))
                ),
                // Top-right corner
                Offset(
                    x = W * 0.98f - orbitW * 0.5f * (1f + cos(phase2)),
                    y = H * 0.02f + orbitH * 0.5f * (1f + sin(phase2))
                ),
                // Bottom-left corner
                Offset(
                    x = W * 0.02f + orbitW * 0.5f * (1f + cos(phase3)),
                    y = H * 0.98f - orbitH * 0.5f * (1f + sin(phase3))
                ),
                // Bottom-right corner
                Offset(
                    x = W * 0.98f - orbitW * 0.5f * (1f + cos(phase4)),
                    y = H * 0.98f - orbitH * 0.5f * (1f + sin(phase4))
                ),
            )

            // How far each glow can reach (fraction of screen diagonal)
            val glowReach = W * 0.52f

            // ── Hex geometry ─────────────────────────────────────────────────
            val hexR    = 22.dp.toPx()
            val colStep = hexR * sqrt(3f)
            val rowStep = hexR * 1.5f
            val cols    = (W / colStep).toInt() + 3
            val rows    = (H / rowStep).toInt() + 3
            val touchShineR = 85.dp.toPx()

            val hexPath = Path()

            for (row in -1..rows) {
                for (col in -1..cols) {
                    val cx = col * colStep + (if (row % 2 != 0) colStep * 0.5f else 0f)
                    val cy = row * rowStep
                    val hexCenter = Offset(cx, cy)

                    // ── Find dominant glow colour & strength for this hex ────
                    var dominantColor    = Color.White
                    var dominantStrength = 0f
                    var totalStrength    = 0f

                    for (i in 0..3) {
                        val dist      = (hexCenter - glowPositions[i]).getDistance()
                        val raw       = (1f - dist / glowReach).coerceIn(0f, 1f)
                        val strength  = raw * raw * pulse   // quadratic falloff × breath
                        totalStrength += strength
                        if (strength > dominantStrength) {
                            dominantStrength = strength
                            dominantColor    = CORNER_COLORS[i]
                        }
                    }

                    val glowAlpha = totalStrength.coerceIn(0f, 1f)

                    // ── Touch/drag shine ─────────────────────────────────────
                    val shine = touchPos?.let { tp ->
                        val d = (hexCenter - tp).getDistance()
                        val s = (1f - d / touchShineR).coerceIn(0f, 1f)
                        s * s
                    } ?: 0f

                    // ── Build hex path ────────────────────────────────────────
                    hexPath.reset()
                    for (k in 0..5) {
                        val a  = (60.0 * k - 30.0) * PI / 180.0
                        val px = cx + hexR * cos(a).toFloat()
                        val py = cy + hexR * sin(a).toFloat()
                        if (k == 0) hexPath.moveTo(px, py) else hexPath.lineTo(px, py)
                    }
                    hexPath.close()

                    // ── FILL: very subtle colored fill only where glow reaches
                    val fillAlpha = (glowAlpha * 0.14f + shine * 0.30f).coerceIn(0f, 0.45f)
                    if (fillAlpha > 0.005f) {
                        val fillColor = if (shine > 0.05f) Color.White else dominantColor
                        drawPath(hexPath, fillColor.copy(alpha = fillAlpha))
                    }

                    // ── BORDER: always-on dim grid + colored glow on top ─────
                    val baseBorder   = 0.07f                         // always-visible dim grid
                    val glowBorder   = glowAlpha * 0.80f             // colored glow contribution
                    val shineBorder  = shine * 0.85f
                    val borderAlpha  = (baseBorder + glowBorder + shineBorder).coerceIn(0f, 1f)
                    val borderColor  = when {
                        shine > 0.05f         -> Color.White
                        glowAlpha > 0.05f     -> dominantColor
                        else                  -> Color.White
                    }
                    drawPath(
                        hexPath,
                        borderColor.copy(alpha = borderAlpha),
                        style = Stroke(width = 1.4f)
                    )

                    // ── TOUCH SHINE: crisp white flash on tap/drag ───────────
                    if (shine > 0.04f) {
                        drawPath(hexPath, Color.White.copy(alpha = shine * 0.45f))
                        drawPath(
                            hexPath,
                            Color.White.copy(alpha = shine * 0.90f),
                            style = Stroke(width = 2.2f)
                        )
                    }
                }
            }
        }

        content()
    }
}
