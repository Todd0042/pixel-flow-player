package com.pixelflow.player.pip

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.core.content.ContextCompat
import com.pixelflow.player.R

object PipManager {

    const val ACTION_PIP_PREVIOUS = "com.pixelflow.player.PIP_PREVIOUS"
    const val ACTION_PIP_PLAY_PAUSE = "com.pixelflow.player.PIP_PLAY_PAUSE"
    const val ACTION_PIP_NEXT = "com.pixelflow.player.PIP_NEXT"

    fun getClampedAspectRatio(videoWidth: Int, videoHeight: Int): Rational {
        val w = if (videoWidth > 0) videoWidth else 16
        val h = if (videoHeight > 0) videoHeight else 9
        // Android PiP limits: 0.418410f <= numerator / denominator <= 2.39f
        val ratio = (w.toFloat() / h.toFloat()).coerceIn(0.42f, 2.38f)
        return Rational((ratio * 1000).toInt(), 1000)
    }

    fun buildPipParams(
        context: Context,
        aspectRatio: Rational = Rational(16, 9),
        isPlaying: Boolean = true,
        sourceRectHint: Rect? = null,
        autoEnterEnabled: Boolean = true
    ): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        val builder = PictureInPictureParams.Builder()
        builder.setAspectRatio(aspectRatio)

        if (sourceRectHint != null && sourceRectHint.width() > 0 && sourceRectHint.height() > 0) {
            builder.setSourceRectHint(sourceRectHint)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnterEnabled)
            builder.setSeamlessResizeEnabled(true)
        }

        // Remote actions inside the floating PiP window
        val actions = ArrayList<RemoteAction>()

        // 1. Previous Video Action
        val prevIntent = PendingIntent.getBroadcast(
            context,
            101,
            Intent(ACTION_PIP_PREVIOUS).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        actions.add(
            RemoteAction(
                Icon.createWithResource(context, R.drawable.ic_pip_prev),
                "Previous",
                "Previous Video",
                prevIntent
            )
        )

        // 2. Play/Pause Action
        val playPauseIntent = PendingIntent.getBroadcast(
            context,
            102,
            Intent(ACTION_PIP_PLAY_PAUSE).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIcon = if (isPlaying) {
            Icon.createWithResource(context, R.drawable.ic_pip_pause)
        } else {
            Icon.createWithResource(context, R.drawable.ic_pip_play)
        }
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        actions.add(
            RemoteAction(
                playPauseIcon,
                playPauseTitle,
                playPauseTitle,
                playPauseIntent
            )
        )

        // 3. Next Video Action
        val nextIntent = PendingIntent.getBroadcast(
            context,
            103,
            Intent(ACTION_PIP_NEXT).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        actions.add(
            RemoteAction(
                Icon.createWithResource(context, R.drawable.ic_pip_next),
                "Next",
                "Next Video",
                nextIntent
            )
        )

        builder.setActions(actions)
        return builder.build()
    }

    fun enterPip(activity: Activity, params: PictureInPictureParams?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || params == null) return false
        return try {
            activity.enterPictureInPictureMode(params)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun registerPipReceiver(
        context: Context,
        onPrevious: () -> Unit,
        onPlayPause: () -> Unit,
        onNext: () -> Unit
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_PIP_PREVIOUS -> onPrevious()
                    ACTION_PIP_PLAY_PAUSE -> onPlayPause()
                    ACTION_PIP_NEXT -> onNext()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_PREVIOUS)
            addAction(ACTION_PIP_PLAY_PAUSE)
            addAction(ACTION_PIP_NEXT)
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        return receiver
    }

    fun unregisterPipReceiver(context: Context, receiver: BroadcastReceiver?) {
        if (receiver != null) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
