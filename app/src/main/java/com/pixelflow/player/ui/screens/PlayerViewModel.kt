package com.pixelflow.player.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.pixelflow.player.data.FavoriteFilter
import com.pixelflow.player.data.PlayOrder
import com.pixelflow.player.data.PlaybackPersistenceManager
import com.pixelflow.player.data.SavedPlaybackSession
import com.pixelflow.player.data.VideoFolder
import com.pixelflow.player.data.VideoItem
import com.pixelflow.player.data.VideoOrientation
import com.pixelflow.player.service.PlayerManager

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playerManager = PlayerManager.getInstance(application)
    val player get() = playerManager.player

    var isRecoveringDecoder by mutableStateOf(false)
        private set

    init {
        playerManager.onDecoderRecoveryChanged = { recovering ->
            isRecoveringDecoder = recovering
        }
    }

    fun resumePlayback(customSpeed: Float = 1.0f, isOneFpsMode: Boolean = false) {
        playerManager.resumePlayback(customSpeed, isOneFpsMode)
        isRecoveringDecoder = false
    }

    fun stepOneFrame(forward: Boolean) {
        playerManager.stepOneFrame(forward)
    }

    fun saveCurrentState(
        folder: VideoFolder,
        favoriteFilter: FavoriteFilter,
        orientation: VideoOrientation,
        playOrder: PlayOrder,
        currentVideo: VideoItem,
        currentPositionMs: Long,
        playlist: List<VideoItem>
    ) {
        PlaybackPersistenceManager.saveSession(
            context = getApplication(),
            folder = folder,
            favoriteFilter = favoriteFilter,
            orientation = orientation,
            playOrder = playOrder,
            currentVideo = currentVideo,
            currentPositionMs = currentPositionMs,
            playlist = playlist
        )
    }

    fun onLifecycleStop(
        isInPipMode: Boolean,
        folder: VideoFolder,
        favoriteFilter: FavoriteFilter,
        orientation: VideoOrientation,
        playOrder: PlayOrder,
        currentVideo: VideoItem,
        currentPositionMs: Long,
        playlist: List<VideoItem>
    ) {
        if (!isInPipMode) {
            saveCurrentState(
                folder = folder,
                favoriteFilter = favoriteFilter,
                orientation = orientation,
                playOrder = playOrder,
                currentVideo = currentVideo,
                currentPositionMs = currentPositionMs,
                playlist = playlist
            )
            // Voluntarily stop playback to free hardware video decoder gracefully
            player.stop()
        }
    }

    fun onLifecycleStart(isInPipMode: Boolean, targetIndex: Int, targetPositionMs: Long) {
        if (!isInPipMode && player.playbackState == androidx.media3.common.Player.STATE_IDLE) {
            isRecoveringDecoder = true
            player.prepare()
            if (targetIndex in 0 until player.mediaItemCount) {
                player.seekTo(targetIndex, targetPositionMs)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
