package com.pixelflow.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.pixelflow.player.data.VideoItem
import com.pixelflow.player.ui.theme.PixelAccentBlue
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun TimelineThumbnailPreview(
    visible: Boolean,
    video: VideoItem,
    targetTimestampMs: Long,
    progressFraction: Float,
    durationMs: Long,
    totalWidth: Dp,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        val context = LocalContext.current
        val density = LocalDensity.current

        // Calculate adaptive aspect ratio matching video orientation
        val isPortrait = video.isPortrait
        val ratio = if (video.effectiveWidth > 0 && video.effectiveHeight > 0) {
            (video.effectiveWidth.toFloat() / video.effectiveHeight.toFloat()).coerceIn(0.5f, 2.2f)
        } else {
            if (isPortrait) 9f / 16f else 16f / 9f
        }

        val (previewWidth, previewHeight) = if (ratio < 1f) {
            // Portrait-leaning (vertical card)
            val h = 124.dp
            val w = (124 * ratio).dp.coerceIn(68.dp, 120.dp)
            Pair(w, h)
        } else {
            // Landscape-leaning (widescreen card)
            val w = 152.dp
            val h = (152 / ratio).dp.coerceIn(76.dp, 114.dp)
            Pair(w, h)
        }

        // Quantize frame requests to prevent overloading the video decoder
        val quantizeStep = remember(durationMs) {
            when {
                durationMs > 10 * 60 * 1000L -> 1000L // 1s steps for >10 min
                durationMs > 2 * 60 * 1000L -> 500L   // 500ms steps for >2 min
                else -> 250L                          // 250ms steps for shorter clips
            }
        }
        val quantizedTarget = (targetTimestampMs / quantizeStep) * quantizeStep

        var debouncedFrameMs by remember(video.id) { mutableLongStateOf(quantizedTarget) }

        LaunchedEffect(quantizedTarget) {
            delay(45) // 45ms debounce during rapid finger drags
            debouncedFrameMs = quantizedTarget
        }

        // Horizontal positioning: align with slider thumb while keeping preview within screen bounds
        val totalWidthPx = with(density) { totalWidth.toPx() }
        val cardWidthPx = with(density) { previewWidth.toPx() + 8.dp.toPx() } // padding included
        val thumbRadiusPx = with(density) { 10.dp.toPx() }
        val availableTrackPx = (totalWidthPx - 2 * thumbRadiusPx).coerceAtLeast(1f)
        val thumbCenterPx = thumbRadiusPx + (progressFraction.coerceIn(0f, 1f) * availableTrackPx)

        val marginPx = with(density) { 2.dp.toPx() }
        val targetCardLeftPx = thumbCenterPx - (cardWidthPx / 2f)
        val maxLeftPx = (totalWidthPx - cardWidthPx - marginPx).coerceAtLeast(marginPx)
        val clampedCardLeftPx = targetCardLeftPx.coerceIn(marginPx, maxLeftPx)
        val cardOffsetXDp = with(density) { clampedCardLeftPx.toDp() }

        // Arrow indicator tracking the thumb precisely
        val arrowCenterRelPx = thumbCenterPx - clampedCardLeftPx
        val arrowWidthPx = with(density) { 12.dp.toPx() }
        val cornerPaddingPx = with(density) { 16.dp.toPx() }
        val clampedArrowLeftPx = (arrowCenterRelPx - arrowWidthPx / 2f)
            .coerceIn(cornerPaddingPx, cardWidthPx - cornerPaddingPx - arrowWidthPx)
        val arrowOffsetXDp = with(density) { clampedArrowLeftPx.toDp() }

        Column(
            modifier = Modifier.offset(x = cardOffsetXDp),
            horizontalAlignment = Alignment.Start
        ) {
            // Main Preview Bubble
            Surface(
                modifier = Modifier
                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.88f)
            ) {
                Column(
                    modifier = Modifier.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Video Thumbnail
                    val previewWidthPx = with(density) { previewWidth.roundToPx() }
                    val previewHeightPx = with(density) { previewHeight.roundToPx() }

                    Box(
                        modifier = Modifier
                            .width(previewWidth)
                            .height(previewHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(video.uri)
                                .decoderFactory(VideoFrameDecoder.Factory())
                                .videoFrameMillis(debouncedFrameMs)
                                .size(previewWidthPx, previewHeightPx)
                                .crossfade(false)
                                .build(),
                            contentDescription = "Thumbnail at ${formatTimestamp(targetTimestampMs)}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(previewWidth)
                                .height(previewHeight)
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // Timestamp pill badge
                    Row(
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(PixelAccentBlue, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatTimestamp(targetTimestampMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Downward pointing arrow caret
            Canvas(
                modifier = Modifier
                    .offset(x = arrowOffsetXDp)
                    .size(width = 12.dp, height = 6.dp)
            ) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, color = Color.Black.copy(alpha = 0.88f))
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
