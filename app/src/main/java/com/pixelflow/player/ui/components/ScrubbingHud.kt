package com.pixelflow.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelflow.player.ui.theme.PixelAccentBlue
import java.util.Locale

@Composable
fun ScrubbingHud(
    visible: Boolean,
    speed: Float = 1.0f,
    deltaSeconds: Float,
    targetPositionMs: Long,
    durationMs: Long,
    isForward: Boolean = true,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        val isPaused = speed <= 0f
        val icon = when {
            isPaused -> Icons.Default.Pause
            isForward -> Icons.Default.FastForward
            else -> Icons.Default.FastRewind
        }
        val sign = if (deltaSeconds >= 0) "+" else ""
        val deltaText = String.format(Locale.US, "%s%.1fs", sign, deltaSeconds)
        val speedText = if (isPaused) "PAUSED" else String.format(Locale.US, "%.1fx", speed)

        Column(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PixelAccentBlue,
                    modifier = Modifier.size(26.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = speedText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "($deltaText)",
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speed deflection gauge bar
            Canvas(
                modifier = Modifier
                    .width(140.dp)
                    .height(4.dp)
            ) {
                val barWidth = size.width
                val barHeight = size.height
                val centerX = barWidth / 2f

                // Track background
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.2f),
                    cornerRadius = CornerRadius(barHeight / 2f)
                )

                // Center indicator tick
                drawRect(
                    color = Color.White.copy(alpha = 0.6f),
                    topLeft = Offset(centerX - 1.dp.toPx(), 0f),
                    size = Size(2.dp.toPx(), barHeight)
                )

                if (!isPaused) {
                    val progress = (speed / 7.5f).coerceIn(0.05f, 1f)
                    val fillWidth = (barWidth / 2f) * progress
                    if (isForward) {
                        drawRoundRect(
                            color = PixelAccentBlue,
                            topLeft = Offset(centerX, 0f),
                            size = Size(fillWidth, barHeight),
                            cornerRadius = CornerRadius(barHeight / 2f)
                        )
                    } else {
                        drawRoundRect(
                            color = PixelAccentBlue,
                            topLeft = Offset(centerX - fillWidth, 0f),
                            size = Size(fillWidth, barHeight),
                            cornerRadius = CornerRadius(barHeight / 2f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val timeDisplay = if (durationMs > 0) {
                "${formatPreciseTime(targetPositionMs)} / ${formatPreciseTime(durationMs)}"
            } else {
                formatPreciseTime(targetPositionMs)
            }

            Text(
                text = timeDisplay,
                color = PixelAccentBlue.copy(alpha = 0.9f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun formatPreciseTime(millis: Long): String {
    if (millis <= 0) return "0:00.0"
    val totalTenths = millis / 100
    val tenths = totalTenths % 10
    val totalSeconds = totalTenths / 10
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d.%d", hours, minutes, seconds, tenths)
    } else {
        String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
    }
}
