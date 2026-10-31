package com.pixelflow.player.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest

object DeleteHelper {

    /**
     * Creates an IntentSenderRequest to prompt the user to move the given videos to trash
     * via Google Photos / Android MediaStore (Android 11+ / API 30+).
     * Returns null if API < 30 or if no URIs could be resolved.
     */
    fun createDeleteIntentSenderRequest(
        context: Context,
        videos: List<VideoItem>
    ): IntentSenderRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || videos.isEmpty()) return null

        val mediaStoreUris = videos.mapNotNull { video ->
            FavoritesHelper.resolveMediaStoreUri(context, video)
        }.distinct()

        if (mediaStoreUris.isEmpty()) return null

        return try {
            val pendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver,
                mediaStoreUris
            )
            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Direct delete fallback for Android 10 or lower, or SAF document files.
     */
    fun directDelete(context: Context, videos: List<VideoItem>): Int {
        var count = 0
        for (video in videos) {
            try {
                val resolvedUri = FavoritesHelper.resolveMediaStoreUri(context, video) ?: video.uri
                val deleted = context.contentResolver.delete(resolvedUri, null, null)
                if (deleted > 0) count++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return count
    }
}
