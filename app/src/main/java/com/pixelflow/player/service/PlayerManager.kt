package com.pixelflow.player.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.MediaSession
import com.pixelflow.player.MainActivity

@OptIn(UnstableApi::class)
class PlayerManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "PixelFlowPlayerManager"

        @Volatile
        private var instance: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager {
            return instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    val player: ExoPlayer = run {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(appContext).build().apply {
            setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            setSeekParameters(SeekParameters.EXACT)
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    private var mediaSession: MediaSession? = null
    var isRecoveringDecoder: Boolean = false
        private set

    var onDecoderRecoveryChanged: ((Boolean) -> Unit)? = null

    init {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(appContext, 0, intent, flags)

        mediaSession = MediaSession.Builder(appContext, player)
            .setId("PixelFlowGlobalSession")
            .setSessionActivity(pendingIntent)
            .build()

        var lastErrorTime = 0L
        var errorRetryCount = 0

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "onPlayerError: ${error.errorCodeName} (${error.errorCode})", error)
                val now = System.currentTimeMillis()
                if (now - lastErrorTime < 3000L) {
                    errorRetryCount++
                } else {
                    errorRetryCount = 1
                }
                lastErrorTime = now

                if (errorRetryCount <= 2) {
                    isRecoveringDecoder = true
                    onDecoderRecoveryChanged?.invoke(true)
                    // Attempt bounded self-healing recovery by re-preparing
                    player.prepare()
                } else {
                    Log.w(TAG, "Suppressed player.prepare() loop after $errorRetryCount errors in 3s")
                    isRecoveringDecoder = false
                    onDecoderRecoveryChanged?.invoke(false)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    errorRetryCount = 0
                    if (isRecoveringDecoder) {
                        isRecoveringDecoder = false
                        onDecoderRecoveryChanged?.invoke(false)
                    }
                }
            }
        })
    }

    fun getMediaSession(): MediaSession? = mediaSession

    fun resumePlayback(customSpeed: Float = 1.0f, isOneFpsMode: Boolean = false) {
        isRecoveringDecoder = false
        onDecoderRecoveryChanged?.invoke(false)

        if (player.playbackState == Player.STATE_IDLE) {
            Log.d(TAG, "Player is STATE_IDLE, calling prepare() before resuming")
            player.prepare()
        } else if (player.playbackState == Player.STATE_ENDED) {
            player.seekToDefaultPosition(player.currentMediaItemIndex)
        }

        if (isOneFpsMode) {
            val fps = player.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
            val speed = (1f / fps).coerceIn(0.005f, 0.1f)
            player.setPlaybackSpeed(speed)
        } else {
            player.setPlaybackSpeed(customSpeed)
        }

        player.volume = 1.0f
        player.play()
    }

    fun stepOneFrame(forward: Boolean) {
        if (player.isPlaying) {
            player.pause()
        }
        val fps = player.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
        val stepMs = (1000f / fps).toLong().coerceAtLeast(16L)
        val curPos = player.currentPosition
        val duration = player.duration
        val targetPos = if (forward) {
            if (duration > 0) (curPos + stepMs).coerceAtMost(duration) else curPos + stepMs
        } else {
            (curPos - stepMs).coerceAtLeast(0L)
        }
        player.setSeekParameters(SeekParameters.EXACT)
        player.seekTo(targetPos)
    }

    fun release() {
        try {
            mediaSession?.run {
                release()
                mediaSession = null
            }
            player.release()
            instance = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing PlayerManager", e)
        }
    }
}
