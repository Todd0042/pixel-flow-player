package com.pixelflow.player.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class SavedPlaybackSession(
    val bucketId: String,
    val folderName: String,
    val favoriteFilter: FavoriteFilter,
    val orientation: VideoOrientation,
    val playOrder: PlayOrder,
    val currentVideoId: Long,
    val currentVideoTitle: String,
    val currentPositionMs: Long,
    val playlistVideoIds: List<Long>,
    val timestamp: Long,
    val isFavoritesAlbum: Boolean = false,
    val isAllVideosAlbum: Boolean = false,
    val isCustomTree: Boolean = false,
    val treeUri: Uri? = null
)

object PlaybackPersistenceManager {
    private const val PREFS_NAME = "pixelflow_playback_state"
    private const val KEY_SESSION_DATA = "saved_session_json"

    fun saveSession(
        context: Context,
        folder: VideoFolder,
        favoriteFilter: FavoriteFilter,
        orientation: VideoOrientation,
        playOrder: PlayOrder,
        currentVideo: VideoItem,
        currentPositionMs: Long,
        playlist: List<VideoItem>
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("bucketId", folder.bucketId)
            put("folderName", folder.name)
            put("favoriteFilter", favoriteFilter.name)
            put("orientation", orientation.name)
            put("playOrder", playOrder.name)
            put("currentVideoId", currentVideo.id)
            put("currentVideoTitle", currentVideo.title)
            put("currentPositionMs", currentPositionMs)
            put("timestamp", System.currentTimeMillis())
            put("isFavoritesAlbum", folder.isFavoritesAlbum)
            put("isAllVideosAlbum", folder.isAllVideosAlbum)
            put("isCustomTree", folder.isCustomTree)
            put("treeUri", folder.treeUri?.toString() ?: "")

            val idArray = JSONArray()
            playlist.forEach { idArray.put(it.id) }
            put("playlistVideoIds", idArray)
        }

        prefs.edit().putString(KEY_SESSION_DATA, json.toString()).apply()
    }

    fun updatePosition(context: Context, videoId: Long, positionMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SESSION_DATA, null) ?: return
        try {
            val json = JSONObject(raw)
            json.put("currentVideoId", videoId)
            json.put("currentPositionMs", positionMs)
            json.put("timestamp", System.currentTimeMillis())
            prefs.edit().putString(KEY_SESSION_DATA, json.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSavedSession(context: Context): SavedPlaybackSession? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SESSION_DATA, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val bucketId = json.optString("bucketId", "")
            val folderName = json.optString("folderName", "Videos")
            val favFilterStr = json.optString("favoriteFilter", FavoriteFilter.ALL.name)
            val favFilter = try { FavoriteFilter.valueOf(favFilterStr) } catch (_: Exception) { FavoriteFilter.ALL }
            val orientStr = json.optString("orientation", VideoOrientation.PORTRAIT.name)
            val orientation = try { VideoOrientation.valueOf(orientStr) } catch (_: Exception) { VideoOrientation.PORTRAIT }
            val orderStr = json.optString("playOrder", PlayOrder.NEWEST.name)
            val playOrder = try { PlayOrder.valueOf(orderStr) } catch (_: Exception) { PlayOrder.NEWEST }
            val currentVideoId = json.optLong("currentVideoId", -1L)
            val currentVideoTitle = json.optString("currentVideoTitle", "Video")
            val currentPositionMs = json.optLong("currentPositionMs", 0L)
            val timestamp = json.optLong("timestamp", 0L)
            val isFavoritesAlbum = json.optBoolean("isFavoritesAlbum", false)
            val isAllVideosAlbum = json.optBoolean("isAllVideosAlbum", false)
            val isCustomTree = json.optBoolean("isCustomTree", false)
            val treeUriStr = json.optString("treeUri", "")
            val treeUri = if (treeUriStr.isNotEmpty()) Uri.parse(treeUriStr) else null

            val idArray = json.optJSONArray("playlistVideoIds")
            val playlistVideoIds = mutableListOf<Long>()
            if (idArray != null) {
                for (i in 0 until idArray.length()) {
                    playlistVideoIds.add(idArray.getLong(i))
                }
            }

            SavedPlaybackSession(
                bucketId = bucketId,
                folderName = folderName,
                favoriteFilter = favFilter,
                orientation = orientation,
                playOrder = playOrder,
                currentVideoId = currentVideoId,
                currentVideoTitle = currentVideoTitle,
                currentPositionMs = currentPositionMs,
                playlistVideoIds = playlistVideoIds,
                timestamp = timestamp,
                isFavoritesAlbum = isFavoritesAlbum,
                isAllVideosAlbum = isAllVideosAlbum,
                isCustomTree = isCustomTree,
                treeUri = treeUri
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clearSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SESSION_DATA).apply()
    }
}
