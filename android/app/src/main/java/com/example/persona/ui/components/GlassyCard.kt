package com.example.persona.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.example.persona.theme.LocalPersonaColors

@Composable
fun GlassyCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    border: BorderStroke? = null, // Default will auto-resolve inside
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalPersonaColors.current
    val isDark = colors.bg != Color(0xFFF3F4F6)

    // Liquid glass background gradient
    val bgBrush = Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                Color(0xFF38BDF8).copy(alpha = 0.08f), // Subtle sky-blue tint
                Color.White.copy(alpha = 0.02f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.70f),
                Color.White.copy(alpha = 0.35f)
            )
        },
        start = Offset(0f, 0f),
        end = Offset(500f, 1000f)
    )

    // Specular highlight outline border
    val cardBorder = border ?: BorderStroke(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = if (isDark) {
                listOf(
                    Color.White.copy(alpha = 0.28f),
                    Color.White.copy(alpha = 0.06f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.65f),
                    Color.White.copy(alpha = 0.20f)
                )
            },
            start = Offset(0f, 0f),
            end = Offset(150f, 150f)
        )
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = shape,
        border = cardBorder
    ) {
        Column(
            modifier = Modifier
                .background(bgBrush)
                .fillMaxWidth()
        ) {
            content()
        }
    }
}

/**
 * Applies a premium hardware-accelerated background blur behind the dialog window on Android 12+ (API 31+).
 * Also adjusts dialog window parameters to support custom glassy background transparency.
 */
@Composable
fun ApplyDialogBlur() {
    val view = LocalView.current
    DisposableEffect(view) {
        val parent = view.parent
        if (parent is DialogWindowProvider) {
            val window = parent.window
            // Use lighter dim for glass dialogue window to keep background details visible
            window.setDimAmount(0.35f)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.setBackgroundBlurRadius(50) // 50px blur depth
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
        onDispose {}
    }
}
