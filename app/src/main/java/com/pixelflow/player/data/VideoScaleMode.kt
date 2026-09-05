package com.pixelflow.player.data

import androidx.media3.ui.AspectRatioFrameLayout

enum class VideoScaleMode(
    val label: String,
    val description: String,
    val resizeMode: Int
) {
    FIT(
        label = "Fit to Screen",
        description = "Full video visible with letterboxing",
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    ),
    ZOOM(
        label = "Crop to Fill",
        description = "Fills entire screen edge-to-edge",
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    ),
    FILL(
        label = "Stretch to Fill",
        description = "Stretches to fill display without cropping",
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
    );

    fun next(): VideoScaleMode {
        val entries = entries
        val nextIndex = (ordinal + 1) % entries.size
        return entries[nextIndex]
    }
}
