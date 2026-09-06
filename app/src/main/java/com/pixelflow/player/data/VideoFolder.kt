package com.pixelflow.player.data

import android.net.Uri

data class VideoFolder(
    val bucketId: String,
    val name: String,
    val coverUri: Uri?,
    val totalCount: Int,
    val portraitCount: Int,
    val landscapeCount: Int,
    val favoriteCount: Int = 0,
    val favoritePortraitCount: Int = 0,
    val favoriteLandscapeCount: Int = 0,
    val isFavoritesAlbum: Boolean = false,
    val isCustomTree: Boolean = false,
    val treeUri: Uri? = null
)
