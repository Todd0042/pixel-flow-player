package com.pixelflow.player.data

import android.net.Uri
import java.util.Locale
import java.util.concurrent.TimeUnit

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val dateAdded: Long,
    val dateModified: Long,
    val size: Long,
    val bucketId: String,
    val bucketName: String,
    var isFavorite: Boolean = false,
    var skipCount: Int = 0
) {
    val formattedSize: String
        get() {
            val mb = size.toDouble() / (1024 * 1024)
            return if (mb >= 1024) {
                String.format(Locale.US, "%.1f GB", mb / 1024)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }
    val effectiveWidth: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) height else width

    val effectiveHeight: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) width else height

    val isPortrait: Boolean
        get() = effectiveHeight >= effectiveWidth

    val isLandscape: Boolean
        get() = effectiveWidth > effectiveHeight

    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "0:00"
            val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%d:%02d", minutes, seconds)
            }
        }
}
