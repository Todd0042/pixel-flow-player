package com.pixelflow.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelflow.player.ui.theme.PixelAccentBlue

enum class EdgeSliderType {
    BRIGHTNESS,
    VOLUME
}

@Composable
fun BrightnessVolumeHud(
    visible: Boolean,
    type: EdgeSliderType,
    progress: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val pct = (progress * 100f).toInt().coerceIn(0, 100)
        val icon = when (type) {
            EdgeSliderType.BRIGHTNESS -> when {
                progress > 0.65f -> Icons.Default.BrightnessHigh
                progress > 0.25f -> Icons.Default.BrightnessMedium
                else -> Icons.Default.BrightnessLow
            }
            EdgeSliderType.VOLUME -> when {
                progress <= 0.01f -> Icons.AutoMirrored.Filled.VolumeMute
                progress > 0.5f -> Icons.AutoMirrored.Filled.VolumeUp
                else -> Icons.AutoMirrored.Filled.VolumeDown
            }
        }
        val label = if (type == EdgeSliderType.BRIGHTNESS) "Brightness" else "Volume"

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = PixelAccentBlue,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$pct%",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(6.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width((44.dp * progress.coerceIn(0f, 1f)))
                        .background(PixelAccentBlue, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}
