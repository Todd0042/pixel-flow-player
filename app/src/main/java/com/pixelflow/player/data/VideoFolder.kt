package com.pixelflow.player.data

import android.net.Uri

data class VideoFolder(
    val bucketId: String,
    val name: String,
    val coverUri: Uri?,
    val totalCount: Int,
    val portraitCount: Int,
    val landscapeCount: Int,
    val isCustomTree: Boolean = false,
    val treeUri: Uri? = null
)
