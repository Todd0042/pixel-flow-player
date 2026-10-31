package com.pixelflow.player.data

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest

object FavoritesHelper {

    /**
     * Checks whether the given video is currently marked as favorite.
     * Prioritizes user explicit setting in local database, falling back to MediaStore.
     */
    fun isFavorite(context: Context, video: VideoItem): Boolean {
        // 1. Check local DB override first
        val localFav = PlaybackStatsDatabase.getInstance(context).getFavoriteSync(video.uri.toString())
        if (localFav != null) {
            return localFav
        }

        // 2. Query MediaStore
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val mediaStoreUri = resolveMediaStoreUri(context, video) ?: return false

        val projection = arrayOf(MediaStore.MediaColumns.IS_FAVORITE)
        try {
            context.contentResolver.query(mediaStoreUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
                    if (colIdx != -1) {
                        return cursor.getInt(colIdx) == 1
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Creates an IntentSenderRequest to toggle the favorite status in MediaStore (syncs with Google Photos).
     * Returns null if not supported (API < 30) or if the URI cannot be resolved.
     */
    fun createFavoriteIntentSenderRequest(
        context: Context,
        video: VideoItem,
        setFavorite: Boolean
    ): IntentSenderRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        val mediaStoreUri = resolveMediaStoreUri(context, video) ?: return null

        return try {
            val pendingIntent = MediaStore.createFavoriteRequest(
                context.contentResolver,
                listOf(mediaStoreUri),
                setFavorite
            )
            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Resolves a media store content URI for the video item, even if it was loaded via SAF tree URI.
     * Returns null if the video does not exist in MediaStore.
     */
    fun resolveMediaStoreUri(context: Context, video: VideoItem): Uri? {
        val uriStr = video.uri.toString()
        if (uriStr.startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString())) {
            return video.uri
        }

        // If it's a MediaStore ID already:
        if (video.id > 0) {
            return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
        }

        // 1. Try looking up in MediaStore by title and size
        try {
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ? AND ${MediaStore.Video.Media.SIZE} = ?"
            val selectionArgs = arrayOf(video.title, video.size.toString())
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback: try looking up by DISPLAY_NAME alone
        try {
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(video.title)
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }
}
