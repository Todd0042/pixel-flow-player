package com.pixelflow.player.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository {

    suspend fun getVideoFolders(context: Context): List<VideoFolder> = withContext(Dispatchers.IO) {
        val folderMap = mutableMapOf<String, MutableList<VideoItem>>()

        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Video.Media.ORIENTATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection.add(MediaStore.MediaColumns.IS_FAVORITE)
        }

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                val orientationCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.ORIENTATION)
                } else -1

                val favoriteCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video_$id"
                    val bucketId = cursor.getString(bucketIdCol) ?: "unknown"
                    val bucketName = if (bucketNameCol != -1) cursor.getString(bucketNameCol) ?: "Videos" else "Videos"
                    val duration = cursor.getLong(durationCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val dateAdded = cursor.getLong(dateAddedCol)
                    val dateModified = cursor.getLong(dateModifiedCol)
                    val size = cursor.getLong(sizeCol)
                    val rotation = if (orientationCol != -1) cursor.getInt(orientationCol) else 0
                    val isFav = if (favoriteCol != -1) cursor.getInt(favoriteCol) == 1 else false

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val videoItem = VideoItem(
                        id = id,
                        uri = contentUri,
                        title = name,
                        durationMs = duration,
                        width = width,
                        height = height,
                        rotationDegrees = rotation,
                        dateAdded = dateAdded,
                        dateModified = dateModified,
                        size = size,
                        bucketId = bucketId,
                        bucketName = bucketName,
                        isFavorite = isFav
                    )

                    folderMap.getOrPut(bucketId) { mutableListOf() }.add(videoItem)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        folderMap.map { (bucketId, videos) ->
            val firstVideo = videos.firstOrNull()
            val folderName = firstVideo?.bucketName ?: "Videos"
            val portraitCount = videos.count { it.isPortrait }
            val landscapeCount = videos.count { it.isLandscape }

            VideoFolder(
                bucketId = bucketId,
                name = folderName,
                coverUri = firstVideo?.uri,
                totalCount = videos.size,
                portraitCount = portraitCount,
                landscapeCount = landscapeCount
            )
        }.sortedWith(
            compareByDescending<VideoFolder> { it.name.equals("Camera", ignoreCase = true) }
                .thenByDescending { it.totalCount }
        )
    }

    suspend fun getVideosForFolder(
        context: Context,
        folder: VideoFolder,
        orientation: VideoOrientation,
        playOrder: PlayOrder
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        val rawVideos = if (folder.isCustomTree && folder.treeUri != null) {
            loadVideosFromCustomTree(context, folder.treeUri)
        } else {
            loadVideosFromBucket(context, folder.bucketId)
        }

        // Accurately filter by orientation (re-inspecting dimensions if 0)
        val filtered = rawVideos.map { video ->
            if (video.width == 0 || video.height == 0) {
                fillMissingDimensions(context, video)
            } else {
                video
            }
        }.filter { video ->
            when (orientation) {
                VideoOrientation.PORTRAIT -> video.isPortrait
                VideoOrientation.LANDSCAPE -> video.isLandscape
            }
        }

        // Apply selected play order
        when (playOrder) {
            PlayOrder.NEWEST -> filtered.sortedByDescending { it.dateAdded }
            PlayOrder.OLDEST -> filtered.sortedBy { it.dateAdded }
            PlayOrder.LONGEST -> filtered.sortedByDescending { it.durationMs }
            PlayOrder.RANDOM -> filtered.shuffled()
        }
    }

    private fun loadVideosFromBucket(context: Context, bucketId: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Video.Media.ORIENTATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection.add(MediaStore.MediaColumns.IS_FAVORITE)
        }

        val selection = "${MediaStore.Video.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId)
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                val orientationCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.ORIENTATION)
                } else -1

                val favoriteCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video_$id"
                    val bId = cursor.getString(bucketIdCol) ?: bucketId
                    val bName = if (bucketNameCol != -1) cursor.getString(bucketNameCol) ?: "Videos" else "Videos"
                    val duration = cursor.getLong(durationCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val dateAdded = cursor.getLong(dateAddedCol)
                    val dateModified = cursor.getLong(dateModifiedCol)
                    val size = cursor.getLong(sizeCol)
                    val rotation = if (orientationCol != -1) cursor.getInt(orientationCol) else 0
                    val isFav = if (favoriteCol != -1) cursor.getInt(favoriteCol) == 1 else false

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    list.add(
                        VideoItem(
                            id = id,
                            uri = contentUri,
                            title = name,
                            durationMs = duration,
                            width = width,
                            height = height,
                            rotationDegrees = rotation,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            size = size,
                            bucketId = bId,
                            bucketName = bName,
                            isFavorite = isFav
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
    }

    private fun loadVideosFromCustomTree(context: Context, treeUri: Uri): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val docFile = DocumentFile.fromTreeUri(context, treeUri) ?: return list
        val folderName = docFile.name ?: "Custom Folder"

        val files = docFile.listFiles()
        var syntheticId = -1L

        for (file in files) {
            if (file.isFile && (file.type?.startsWith("video/") == true || isVideoExtension(file.name))) {
                val uri = file.uri
                val name = file.name ?: "Video"
                val size = file.length()
                val lastModified = file.lastModified() / 1000

                val (w, h, rot, dur) = extractMetadata(context, uri)

                list.add(
                    VideoItem(
                        id = syntheticId--,
                        uri = uri,
                        title = name,
                        durationMs = dur,
                        width = w,
                        height = h,
                        rotationDegrees = rot,
                        dateAdded = lastModified,
                        dateModified = lastModified,
                        size = size,
                        bucketId = treeUri.toString(),
                        bucketName = folderName,
                        isFavorite = false
                    )
                )
            }
        }

        return list
    }

    suspend fun getCustomFolderInfo(context: Context, treeUri: Uri): VideoFolder? = withContext(Dispatchers.IO) {
        val docFile = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
        val folderName = docFile.name ?: "Selected Folder"

        val videos = loadVideosFromCustomTree(context, treeUri)
        if (videos.isEmpty()) {
            return@withContext VideoFolder(
                bucketId = treeUri.toString(),
                name = folderName,
                coverUri = null,
                totalCount = 0,
                portraitCount = 0,
                landscapeCount = 0,
                isCustomTree = true,
                treeUri = treeUri
            )
        }

        val portraitCount = videos.count { it.isPortrait }
        val landscapeCount = videos.count { it.isLandscape }

        VideoFolder(
            bucketId = treeUri.toString(),
            name = folderName,
            coverUri = videos.firstOrNull()?.uri,
            totalCount = videos.size,
            portraitCount = portraitCount,
            landscapeCount = landscapeCount,
            isCustomTree = true,
            treeUri = treeUri
        )
    }

    private fun fillMissingDimensions(context: Context, item: VideoItem): VideoItem {
        val (w, h, rot, dur) = extractMetadata(context, item.uri)
        return item.copy(
            width = if (w > 0) w else item.width,
            height = if (h > 0) h else item.height,
            rotationDegrees = if (rot != 0) rot else item.rotationDegrees,
            durationMs = if (dur > 0) dur else item.durationMs
        )
    }

    private fun extractMetadata(context: Context, uri: Uri): Tuple4<Int, Int, Int, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            val w = wStr?.toIntOrNull() ?: 0
            val h = hStr?.toIntOrNull() ?: 0
            val rot = rotStr?.toIntOrNull() ?: 0
            val dur = durStr?.toLongOrNull() ?: 0L
            Tuple4(w, h, rot, dur)
        } catch (e: Exception) {
            Tuple4(0, 0, 0, 0L)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun isVideoExtension(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase()
        return lower.endsWith(".mp4") ||
                lower.endsWith(".mkv") ||
                lower.endsWith(".mov") ||
                lower.endsWith(".webm") ||
                lower.endsWith(".3gp") ||
                lower.endsWith(".avi") ||
                lower.endsWith(".ts")
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
