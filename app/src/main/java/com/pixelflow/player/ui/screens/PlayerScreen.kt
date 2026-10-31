package com.pixelflow.player.ui.screens

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import com.pixelflow.player.MainActivity
import com.pixelflow.player.pip.PipManager
import com.pixelflow.player.data.FavoriteFilter
import com.pixelflow.player.data.FavoritesHelper
import com.pixelflow.player.data.PlayOrder
import com.pixelflow.player.data.PlaybackPersistenceManager
import com.pixelflow.player.data.PlaybackStatsDatabase
import android.media.AudioManager
import android.provider.Settings
import com.pixelflow.player.data.VideoFolder
import com.pixelflow.player.data.VideoItem
import com.pixelflow.player.data.VideoOrientation
import com.pixelflow.player.data.VideoScaleMode
import com.pixelflow.player.ui.screens.PlayerViewModel
import com.pixelflow.player.ui.components.BrightnessVolumeHud
import com.pixelflow.player.ui.components.DoubleTapSeekHud
import com.pixelflow.player.ui.components.EdgeSliderType
import com.pixelflow.player.ui.components.FavoriteHeartHud
import com.pixelflow.player.ui.components.JogFrameHud
import com.pixelflow.player.ui.components.ScalingHud
import com.pixelflow.player.ui.components.ScrubbingHud
import com.pixelflow.player.ui.components.SpeedBoostHud
import com.pixelflow.player.ui.components.SwipeDirection
import com.pixelflow.player.ui.components.SwipeIndicatorHud
import com.pixelflow.player.ui.components.TimelineThumbnailPreview
import com.pixelflow.player.ui.components.ZoomHud
import com.pixelflow.player.ui.theme.PixelAccentBlue
import com.pixelflow.player.ui.theme.PixelHeartRed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

private enum class TapZone {
    NONE, LEFT, MIDDLE, RIGHT
}

private enum class TouchGestureState {
    IDLE,
    SCRUBBING,
    JOG_SHUTTLE,
    VERTICAL_SWIPE,
    BRIGHTNESS_SLIDER,
    VOLUME_SLIDER,
    SPEED_BOOST,
    PINCH_ZOOM,
    PANNING
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    folder: VideoFolder,
    videos: List<VideoItem>,
    orientation: VideoOrientation,
    favoriteFilter: FavoriteFilter = FavoriteFilter.ALL,
    playOrder: PlayOrder = PlayOrder.NEWEST,
    initialIndex: Int = 0,
    initialPositionMs: Long = 0L,
    isInPipMode: Boolean = false,
    viewModel: PlayerViewModel = viewModel(),
    onExitPlayer: () -> Unit
) {
    if (videos.isEmpty()) {
        LaunchedEffect(Unit) { onExitPlayer() }
        return
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lifecycle-managed ExoPlayer via PlayerViewModel
    val exoPlayer = viewModel.player

    var currentIndex by remember {
        mutableIntStateOf(initialIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0)))
    }
    var lastActiveIndex by remember { mutableIntStateOf(currentIndex) }

    fun getCurrentVideo(): VideoItem {
        val idx = exoPlayer.currentMediaItemIndex.takeIf { it in videos.indices }
            ?: currentIndex.coerceIn(0, videos.size - 1)
        return videos[idx]
    }

    val currentVideo = getCurrentVideo()

    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(initialPositionMs) }
    var durationMs by remember { mutableLongStateOf(currentVideo.durationMs) }
    var isControlsVisible by remember { mutableStateOf(false) }
    var isTimelineSeeking by remember { mutableStateOf(false) }
    var timelineSeekProgress by remember { mutableFloatStateOf(0f) }

    var customPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isOneFpsMode by remember { mutableStateOf(false) }

    fun resumePlayback() {
        viewModel.resumePlayback(customPlaybackSpeed, isOneFpsMode)
        isPlaying = true
    }

    fun stepOneFrame(forward: Boolean) {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            isPlaying = false
        }
        viewModel.stepOneFrame(forward)
        currentPositionMs = exoPlayer.currentPosition
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    viewModel.onLifecycleStop(
                        isInPipMode = isInPipMode,
                        folder = folder,
                        favoriteFilter = favoriteFilter,
                        orientation = orientation,
                        playOrder = playOrder,
                        currentVideo = getCurrentVideo(),
                        currentPositionMs = currentPositionMs,
                        playlist = videos
                    )
                }
                Lifecycle.Event.ON_START -> {
                    viewModel.onLifecycleStart(
                        isInPipMode = isInPipMode,
                        targetIndex = currentIndex,
                        targetPositionMs = currentPositionMs
                    )
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Picture-in-Picture State & Helpers
    var pipAspectRatio by remember {
        val initialRatio = if (currentVideo.width > 0 && currentVideo.height > 0) {
            PipManager.getClampedAspectRatio(currentVideo.width, currentVideo.height)
        } else {
            if (orientation == VideoOrientation.PORTRAIT) Rational(9, 16) else Rational(16, 9)
        }
        mutableStateOf(initialRatio)
    }
    var pipSourceRectHint by remember { mutableStateOf<Rect?>(null) }
    var resetZoomAction by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var showZoomHud by remember { mutableStateOf(false) }
    var zoomHudScale by remember { mutableFloatStateOf(1.0f) }

    fun updatePipParams(autoEnter: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            val params = PipManager.buildPipParams(
                context = context,
                aspectRatio = pipAspectRatio,
                isPlaying = isPlaying,
                sourceRectHint = pipSourceRectHint,
                autoEnterEnabled = autoEnter
            )
            if (params != null) {
                try {
                    activity.setPictureInPictureParams(params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun enterPipMode() {
        resetZoomAction?.invoke(false)
        if (activity != null) {
            val params = PipManager.buildPipParams(
                context = context,
                aspectRatio = pipAspectRatio,
                isPlaying = isPlaying,
                sourceRectHint = pipSourceRectHint,
                autoEnterEnabled = true
            )
            PipManager.enterPip(activity, params)
        }
    }

    LaunchedEffect(pipAspectRatio, isPlaying, pipSourceRectHint, isTimelineSeeking) {
        updatePipParams(autoEnter = !isTimelineSeeking)
    }

    // HUD States
    var showScaleHud by remember { mutableStateOf(false) }
    var showFavoriteHud by remember { mutableStateOf(false) }
    var isCurrentVideoFavorite by remember { mutableStateOf(currentVideo.isFavorite) }
    var lastFavoriteStatus by remember { mutableStateOf(currentVideo.isFavorite) }
    var pendingFavoriteVideo by remember { mutableStateOf<VideoItem?>(null) }
    var showSwipeHud by remember { mutableStateOf(false) }
    var lastSwipeDirection by remember { mutableStateOf(SwipeDirection.NEXT) }

    LaunchedEffect(currentIndex) {
        val activeVid = getCurrentVideo()
        val fav = FavoritesHelper.isFavorite(context, activeVid)
        isCurrentVideoFavorite = fav
        activeVid.isFavorite = fav
        lastFavoriteStatus = fav
        durationMs = activeVid.durationMs
        if (activeVid.width > 0 && activeVid.height > 0) {
            pipAspectRatio = PipManager.getClampedAspectRatio(activeVid.width, activeVid.height)
        }
    }

    // Scrubbing HUD State (Horizontal drag)
    var showScrubHud by remember { mutableStateOf(false) }
    var scrubHudSpeed by remember { mutableFloatStateOf(1f) }
    var scrubHudDeltaSeconds by remember { mutableFloatStateOf(0f) }
    var scrubHudTargetMs by remember { mutableLongStateOf(0L) }
    var scrubHudIsForward by remember { mutableStateOf(true) }

    // Double/Multi-Tap Seek HUD State (Left 1/3 = -5s, Right 1/3 = +5s)
    var showDoubleTapSeekHud by remember { mutableStateOf(false) }
    var isDoubleTapForward by remember { mutableStateOf(true) }
    var doubleTapTotalSeconds by remember { mutableIntStateOf(5) }

    // Edge Sliders State (Left 15% Brightness, Right 15% Volume)
    var showBrightnessHud by remember { mutableStateOf(false) }
    var brightnessProgress by remember { mutableFloatStateOf(0.5f) }
    var showVolumeHud by remember { mutableStateOf(false) }
    var volumeProgress by remember { mutableFloatStateOf(0.5f) }
    var hideEdgeSliderJob by remember { mutableStateOf<Job?>(null) }

    // Press & Hold 2.0x Quick Speed Boost State
    var isSpeedBoosting by remember { mutableStateOf(false) }
    var speedBoostJob by remember { mutableStateOf<Job?>(null) }

    // Paused Precision Jog-Shuttle State
    var showJogHud by remember { mutableStateOf(false) }
    var jogFrameDelta by remember { mutableIntStateOf(0) }
    var jogTargetMs by remember { mutableLongStateOf(0L) }
    var jogStartMs by remember { mutableLongStateOf(0L) }
    var hideJogHudJob by remember { mutableStateOf<Job?>(null) }

    // Seek Gatekeeper for smooth reverse scrubbing & jog shuttle (prevents decoder starvation)
    var isSeekInProgress by remember { mutableStateOf(false) }
    var pendingSeekPositionMs by remember { mutableStateOf<Long?>(null) }

    // Playback Speed Menu State (Persisted across playlist items)
    var customSpeedInputText by remember { mutableStateOf("1.0") }
    var showSpeedMenu by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun vibrate(durationMs: Long = 40L) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Favorite launcher for Android 11+ MediaStore request
    val favoriteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Re-enable PiP callbacks after system dialog finishes
        (activity as? MainActivity)?.onUserLeavePipTrigger = {
            enterPipMode()
        }
        updatePipParams(autoEnter = !isTimelineSeeking)

        val targetVideo = pendingFavoriteVideo ?: getCurrentVideo()
        if (result.resultCode == Activity.RESULT_OK) {
            vibrate(60L)
        } else {
            // User denied or cancelled system prompt: revert so it stays in sync with Google Photos
            val reverted = !lastFavoriteStatus
            targetVideo.isFavorite = reverted
            if (getCurrentVideo().uri == targetVideo.uri) {
                isCurrentVideoFavorite = reverted
            }
            lastFavoriteStatus = reverted
            showFavoriteHud = false
            PlaybackStatsDatabase.getInstance(context).setFavoriteSync(targetVideo.uri, reverted)
        }
        pendingFavoriteVideo = null
    }

    fun triggerFavoriteToggle() {
        val targetVideo = getCurrentVideo()
        pendingFavoriteVideo = targetVideo

        val currentFav = FavoritesHelper.isFavorite(context, targetVideo)
        val newFav = !currentFav

        isCurrentVideoFavorite = newFav
        targetVideo.isFavorite = newFav
        lastFavoriteStatus = newFav
        showFavoriteHud = true
        vibrate(50L)

        // 1. Immediately persist to our local database so it is guaranteed saved
        PlaybackStatsDatabase.getInstance(context).setFavoriteSync(targetVideo.uri, newFav)

        scope.launch {
            delay(1600)
            showFavoriteHud = false
        }

        // 2. Attempt silent MediaStore update first (works if app has write permission or created file)
        val mediaStoreUri = FavoritesHelper.resolveMediaStoreUri(context, targetVideo)
        if (mediaStoreUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var directSuccess = false
            try {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_FAVORITE, if (newFav) 1 else 0)
                }
                val updatedCount = context.contentResolver.update(mediaStoreUri, values, null, null)
                if (updatedCount > 0) {
                    directSuccess = true
                }
            } catch (e: Exception) {
                // Not owner or security exception; fall back to createFavoriteRequest
            }

            if (!directSuccess) {
                // Temporarily disable PiP auto-trigger so Android's PermissionActivity isn't aborted
                (activity as? MainActivity)?.onUserLeavePipTrigger = null
                updatePipParams(autoEnter = false)

                val request = FavoritesHelper.createFavoriteIntentSenderRequest(
                    context,
                    targetVideo,
                    newFav
                )
                if (request != null) {
                    try {
                        favoriteLauncher.launch(request)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Restore PiP if launch failed
                        (activity as? MainActivity)?.onUserLeavePipTrigger = { enterPipMode() }
                        updatePipParams(autoEnter = !isTimelineSeeking)
                    }
                } else {
                    // Restore PiP if no request could be created
                    (activity as? MainActivity)?.onUserLeavePipTrigger = { enterPipMode() }
                    updatePipParams(autoEnter = !isTimelineSeeking)
                }
            }
        }
    }

    // Prepare full playlist with rich metadata for Android Auto and Car Head Unit displays
    val mediaItems = remember(videos) {
        videos.map { video ->
            MediaItem.Builder()
                .setUri(video.uri)
                .setMediaId(video.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(video.title)
                        .setArtist(folder.name)
                        .setAlbumTitle(folder.name)
                        .setDisplayTitle(video.title)
                        .build()
                )
                .build()
        }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    fun toggleOneFpsMode() {
        val willBeActive = !isOneFpsMode
        isOneFpsMode = willBeActive
        if (willBeActive) {
            // Disable audio track so StandaloneMediaClock handles sub-0.1x speed without audio buffer distortion/stalling
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            exoPlayer.volume = 0f

            val fps = exoPlayer.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
            val speed = (1f / fps).coerceIn(0.005f, 0.1f)
            exoPlayer.setPlaybackSpeed(speed)
            if (!exoPlayer.isPlaying) {
                resumePlayback()
            }
            vibrate(30L)
        } else {
            // Restore normal audio and custom playback speed
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()
            exoPlayer.setPlaybackSpeed(customPlaybackSpeed)
            exoPlayer.volume = 1.0f
            vibrate(20L)
        }
    }

    fun skipToNext(showIndicator: Boolean = true) {
        if (videos.isEmpty()) return
        val currentIdx = exoPlayer.currentMediaItemIndex.takeIf { it in videos.indices } ?: currentIndex
        val currentVid = videos[currentIdx]
        val watchedMs = exoPlayer.currentPosition
        val dur = if (durationMs > 0) durationMs else currentVid.durationMs
        val isUnder5Sec = dur in 1..4999L
        val isViewed = if (isUnder5Sec) {
            watchedMs >= (dur * 0.5f)
        } else {
            watchedMs >= 5000L || (dur > 0 && watchedMs >= (dur * 0.9f))
        }

        scope.launch {
            val db = PlaybackStatsDatabase.getInstance(context)
            if (isViewed) {
                db.recordView(currentVid.uri, currentVid.bucketId)
            } else {
                db.recordSkip(currentVid.uri, currentVid.bucketId)
            }
        }

        if (isOneFpsMode) {
            toggleOneFpsMode()
        }

        val targetIndex = (currentIdx + 1) % videos.size
        currentIndex = targetIndex
        lastActiveIndex = targetIndex
        resetZoomAction?.invoke(false)
        exoPlayer.seekToDefaultPosition(targetIndex)
        resumePlayback()

        if (showIndicator) {
            lastSwipeDirection = SwipeDirection.NEXT
            showSwipeHud = true
            vibrate(45L)
            scope.launch {
                delay(1200)
                showSwipeHud = false
            }
        }
    }

    fun skipToPrevious(showIndicator: Boolean = true) {
        if (videos.isEmpty()) return
        val currentIdx = exoPlayer.currentMediaItemIndex.takeIf { it in videos.indices } ?: currentIndex
        val currentVid = videos[currentIdx]
        val watchedMs = exoPlayer.currentPosition
        val dur = if (durationMs > 0) durationMs else currentVid.durationMs
        val isUnder5Sec = dur in 1..4999L
        val isViewed = if (isUnder5Sec) {
            watchedMs >= (dur * 0.5f)
        } else {
            watchedMs >= 5000L || (dur > 0 && watchedMs >= (dur * 0.9f))
        }

        scope.launch {
            val db = PlaybackStatsDatabase.getInstance(context)
            if (isViewed) {
                db.recordView(currentVid.uri, currentVid.bucketId)
            } else {
                db.recordSkip(currentVid.uri, currentVid.bucketId)
            }
        }

        if (isOneFpsMode) {
            toggleOneFpsMode()
        }

        val targetIndex = if (currentIdx - 1 < 0) videos.size - 1 else currentIdx - 1
        currentIndex = targetIndex
        lastActiveIndex = targetIndex
        resetZoomAction?.invoke(false)
        exoPlayer.seekToDefaultPosition(targetIndex)
        resumePlayback()

        if (showIndicator) {
            lastSwipeDirection = SwipeDirection.PREVIOUS
            showSwipeHud = true
            vibrate(45L)
            scope.launch {
                delay(1200)
                showSwipeHud = false
            }
        }
    }

    // Register PiP RemoteAction receiver and onUserLeaveHint fallback
    DisposableEffect(context) {
        val receiver = PipManager.registerPipReceiver(
            context = context,
            onPrevious = { skipToPrevious() },
            onPlayPause = {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                    isPlaying = false
                } else {
                    resumePlayback()
                }
            },
            onNext = { skipToNext() }
        )

        (activity as? MainActivity)?.onUserLeavePipTrigger = {
            enterPipMode()
        }

        onDispose {
            PipManager.unregisterPipReceiver(context, receiver)
            (activity as? MainActivity)?.onUserLeavePipTrigger = null
            updatePipParams(autoEnter = false)
        }
    }

    // Configure Activity Window for True Immersive Edge-to-Edge Fullscreen
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            activity.requestedOrientation = when (orientation) {
                VideoOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                VideoOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        // Set playlist into ExoPlayer and start playback
        val startIdx = initialIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
        exoPlayer.setMediaItems(mediaItems, startIdx, initialPositionMs)
        exoPlayer.prepare()
        resumePlayback()

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (isOneFpsMode) {
                    isOneFpsMode = false
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .build()
                    exoPlayer.setPlaybackSpeed(customPlaybackSpeed)
                    exoPlayer.volume = 1.0f
                }

                val newIndex = exoPlayer.currentMediaItemIndex

                // If video naturally completed and transitioned to next, mark as viewed
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && lastActiveIndex in videos.indices) {
                    val finishedVid = videos[lastActiveIndex]
                    scope.launch {
                        PlaybackStatsDatabase.getInstance(context).recordView(finishedVid.uri, finishedVid.bucketId)
                    }
                }
                lastActiveIndex = newIndex

                if (newIndex in videos.indices) {
                    currentIndex = newIndex
                    resetZoomAction?.invoke(false)
                    val currentVid = videos[newIndex]
                    currentPositionMs = 0L
                    durationMs = currentVid.durationMs
                    val fav = FavoritesHelper.isFavorite(context, currentVid)
                    currentVid.isFavorite = fav
                    isCurrentVideoFavorite = fav
                    lastFavoriteStatus = fav
                    if (currentVid.width > 0 && currentVid.height > 0) {
                        pipAspectRatio = PipManager.getClampedAspectRatio(currentVid.width, currentVid.height)
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    pipAspectRatio = PipManager.getClampedAspectRatio(videoSize.width, videoSize.height)
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                if (isOneFpsMode) {
                    val fps = exoPlayer.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
                    val speed = (1f / fps).coerceIn(0.005f, 0.1f)
                    exoPlayer.setPlaybackSpeed(speed)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val pos = exoPlayer.currentPosition
                if (pos > 0) {
                    currentPositionMs = pos
                }
                viewModel.saveCurrentState(
                    folder = folder,
                    favoriteFilter = favoriteFilter,
                    orientation = orientation,
                    playOrder = playOrder,
                    currentVideo = getCurrentVideo(),
                    currentPositionMs = currentPositionMs,
                    playlist = videos
                )
                // PlayerManager handles rate-limited player.prepare() recovery
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    if (exoPlayer.playWhenReady) {
                        isPlaying = true
                    }
                    isSeekInProgress = false
                    val pending = pendingSeekPositionMs
                    if (pending != null) {
                        pendingSeekPositionMs = null
                        isSeekInProgress = true
                        exoPlayer.seekTo(pending)
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    isSeekInProgress = false
                    val pending = pendingSeekPositionMs
                    if (pending != null) {
                        pendingSeekPositionMs = null
                        isSeekInProgress = true
                        exoPlayer.seekTo(pending)
                    }
                }
            }

            override fun onRenderedFirstFrame() {
                isSeekInProgress = false
                val pending = pendingSeekPositionMs
                if (pending != null) {
                    pendingSeekPositionMs = null
                    isSeekInProgress = true
                    exoPlayer.seekTo(pending)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            playerViewRef?.player = null
            playerViewRef = null
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            viewModel.saveCurrentState(
                folder = folder,
                favoriteFilter = favoriteFilter,
                orientation = orientation,
                playOrder = playOrder,
                currentVideo = getCurrentVideo(),
                currentPositionMs = currentPositionMs,
                playlist = videos
            )
            if (isOneFpsMode) {
                isOneFpsMode = false
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .build()
                exoPlayer.setPlaybackSpeed(1.0f)
                exoPlayer.volume = 1.0f
            }

            if (window != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Coroutine to track playback progress
    LaunchedEffect(isPlaying) {
        while (isActive) {
            if (isPlaying) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration
                if (dur > 0) durationMs = dur
            }
            delay(400)
        }
    }

    fun cycleScaleMode() {
        vibrate(35L)
        scaleMode = scaleMode.next()
        playerViewRef?.resizeMode = scaleMode.resizeMode
        resetZoomAction?.invoke(false)
        showScaleHud = true
        scope.launch {
            delay(1300)
            showScaleHud = false
        }
    }

    BackHandler(enabled = !isInPipMode) {
        if (zoomHudScale > 1.05f) {
            resetZoomAction?.invoke(true)
        } else {
            onExitPlayer()
        }
    }

    val currentTriggerFavoriteToggle by rememberUpdatedState(::triggerFavoriteToggle)
    val currentSkipToNext by rememberUpdatedState(::skipToNext)
    val currentSkipToPrevious by rememberUpdatedState(::skipToPrevious)
    val currentCycleScaleMode by rememberUpdatedState(::cycleScaleMode)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video Player View with Custom Touch Gesture Listener
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = scaleMode.resizeMode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    playerViewRef = this
                    clipChildren = true

                    fun getVideoTarget(): View = (videoSurfaceView?.parent as? View) ?: videoSurfaceView ?: this

                    (videoSurfaceView?.parent as? ViewGroup)?.let { frame ->
                        frame.clipChildren = false
                        frame.clipToPadding = false
                    }

                    addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                        val loc = IntArray(2)
                        v.getLocationOnScreen(loc)
                        if (v.width > 0 && v.height > 0) {
                            pipSourceRectHint = Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
                        }
                        (videoSurfaceView?.parent as? ViewGroup)?.let { frame ->
                            frame.clipChildren = false
                            frame.clipToPadding = false
                        }
                    }

                    var isLiveScrubbing = false
                    var wasPlayingBeforeScrub = false
                    var scrubStartMs = 0L
                    var currentScrubDeltaX = 0f
                    var currentScrubDeltaY = 0f
                    var scrubJob: Job? = null
                    var hideScrubHudJob: Job? = null
                    var wasScrubbingInSession = false
                    var lastScrubEndTime = 0L
                    var touchState = TouchGestureState.IDLE

                    // Touch tracking for zone taps & multi-tap detection
                    var touchDownX = 0f
                    var touchDownY = 0f
                    var touchDownTime = 0L

                    var lastTapUpTime = 0L
                    var lastTapZone = TapZone.NONE
                    var consecutiveTaps = 0
                    var accumulatedSeekSeconds = 0

                    var pendingSingleTapJob: Job? = null
                    var multiTapResetJob: Job? = null
                    var hideDoubleTapHudJob: Job? = null

                    // Pinch-to-zoom & pan state
                    var currentZoom = 1.0f
                    var currentPanX = 0f
                    var currentPanY = 0f
                    var isPinching = false
                    var isPanning = false
                    var isTwoFingerPanning = false
                    var lastCentroidX = 0f
                    var lastCentroidY = 0f
                    var lastPinchEndTime = 0L
                    var lastTouchX = 0f
                    var lastTouchY = 0f
                    var lastFocusX = 0f
                    var lastFocusY = 0f
                    var hideZoomHudJob: Job? = null

                    val resetZoom: (Boolean) -> Unit = { animate ->
                        val target = getVideoTarget()
                        currentZoom = 1.0f
                        currentPanX = 0f
                        currentPanY = 0f
                        showZoomHud = false
                        zoomHudScale = 1.0f
                        hideZoomHudJob?.cancel()
                        if (animate) {
                            target.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .translationX(0f)
                                .translationY(0f)
                                .setDuration(220)
                                .start()
                        } else {
                            target.animate().cancel()
                            target.scaleX = 1.0f
                            target.scaleY = 1.0f
                            target.translationX = 0f
                            target.translationY = 0f
                        }
                    }
                    resetZoomAction = resetZoom

                    val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                            touchState = TouchGestureState.PINCH_ZOOM
                            isPinching = true
                            lastFocusX = detector.focusX
                            lastFocusY = detector.focusY
                            val target = getVideoTarget()
                            target.animate().cancel()

                            if (isLiveScrubbing || wasScrubbingInSession) {
                                isLiveScrubbing = false
                                wasScrubbingInSession = false
                                scrubJob?.cancel()
                                if (isOneFpsMode) {
                                    val fps = exoPlayer.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
                                    val speed = (1f / fps).coerceIn(0.005f, 0.1f)
                                    exoPlayer.setPlaybackSpeed(speed)
                                } else {
                                    exoPlayer.setPlaybackSpeed(customPlaybackSpeed)
                                    exoPlayer.volume = 1.0f
                                }
                                exoPlayer.setSeekParameters(SeekParameters.EXACT)
                                if (wasPlayingBeforeScrub) {
                                    resumePlayback()
                                } else {
                                    exoPlayer.pause()
                                }
                                showScrubHud = false
                            }

                            pendingSingleTapJob?.cancel()
                            consecutiveTaps = 0
                            lastTapZone = TapZone.NONE
                            return true
                        }

                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            val target = getVideoTarget()
                            val prevZoom = currentZoom
                            val factor = detector.scaleFactor
                            val newZoom = (currentZoom * factor).coerceIn(0.85f, 5.5f)
                            currentZoom = newZoom

                            val viewW = if (this@apply.width > 0) this@apply.width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
                            val viewH = if (this@apply.height > 0) this@apply.height.toFloat() else resources.displayMetrics.heightPixels.toFloat()
                            val contentBaseW = if (target.width > 0) target.width.toFloat() else viewW
                            val contentBaseH = if (target.height > 0) target.height.toFloat() else viewH

                            val contentW = contentBaseW * currentZoom
                            val contentH = contentBaseH * currentZoom
                            val maxPanX = ((contentW - viewW) / 2f).coerceAtLeast(0f)
                            val maxPanY = ((contentH - viewH) / 2f).coerceAtLeast(0f)

                            val focusX = detector.focusX
                            val focusY = detector.focusY
                            val k = if (prevZoom > 0f) currentZoom / prevZoom else 1f
                            val centerX = viewW / 2f
                            val centerY = viewH / 2f
                            val deltaPanX = -(k - 1f) * (focusX - (centerX + currentPanX)) + (focusX - lastFocusX)
                            val deltaPanY = -(k - 1f) * (focusY - (centerY + currentPanY)) + (focusY - lastFocusY)

                            currentPanX = (currentPanX + deltaPanX).coerceIn(-maxPanX, maxPanX)
                            currentPanY = (currentPanY + deltaPanY).coerceIn(-maxPanY, maxPanY)

                            lastFocusX = focusX
                            lastFocusY = focusY

                            target.scaleX = currentZoom
                            target.scaleY = currentZoom
                            target.translationX = currentPanX
                            target.translationY = currentPanY

                            zoomHudScale = currentZoom
                            showZoomHud = true
                            hideZoomHudJob?.cancel()

                            return true
                        }

                        override fun onScaleEnd(detector: ScaleGestureDetector) {
                            val target = getVideoTarget()
                            lastPinchEndTime = System.currentTimeMillis()

                            if (currentZoom < 1.0f) {
                                currentZoom = 1.0f
                                currentPanX = 0f
                                currentPanY = 0f
                                target.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .translationX(0f)
                                    .translationY(0f)
                                    .setDuration(220)
                                    .start()
                            } else if (currentZoom > 5.0f) {
                                currentZoom = 5.0f
                                val viewW = if (this@apply.width > 0) this@apply.width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
                                val viewH = if (this@apply.height > 0) this@apply.height.toFloat() else resources.displayMetrics.heightPixels.toFloat()
                                val contentBaseW = if (target.width > 0) target.width.toFloat() else viewW
                                val contentBaseH = if (target.height > 0) target.height.toFloat() else viewH
                                val maxPanX = ((contentBaseW * 5.0f - viewW) / 2f).coerceAtLeast(0f)
                                val maxPanY = ((contentBaseH * 5.0f - viewH) / 2f).coerceAtLeast(0f)
                                currentPanX = currentPanX.coerceIn(-maxPanX, maxPanX)
                                currentPanY = currentPanY.coerceIn(-maxPanY, maxPanY)
                                target.animate()
                                    .scaleX(5.0f)
                                    .scaleY(5.0f)
                                    .translationX(currentPanX)
                                    .translationY(currentPanY)
                                    .setDuration(220)
                                    .start()
                            }

                            zoomHudScale = currentZoom
                            hideZoomHudJob?.cancel()
                            hideZoomHudJob = scope.launch {
                                delay(1100)
                                showZoomHud = false
                            }
                        }
                    })

                    val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val maxAudioVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    var initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    var initialBrightness = 0.5f

                    var twoFingerDownTime = 0L
                    var twoFingerDownCentroidX = 0f
                    var twoFingerDownCentroidY = 0f
                    var lastJogSeekTime = 0L

                    val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDown(e: MotionEvent): Boolean {
                            return true
                        }

                        override fun onLongPress(e: MotionEvent) {
                            if (touchState == TouchGestureState.SPEED_BOOST || isSpeedBoosting) return
                            if (touchState == TouchGestureState.IDLE && !isLiveScrubbing && !isPinching && currentZoom <= 1.05f) {
                                val viewW = if (width > 0) width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
                                // Long press in center region (35% to 65% of screen) toggles favorite in Google Photos
                                if (e.x in (viewW * 0.35f)..(viewW * 0.65f)) {
                                    pendingSingleTapJob?.cancel()
                                    consecutiveTaps = 0
                                    currentTriggerFavoriteToggle.invoke()
                                }
                            }
                        }

                        override fun onFling(
                            e1: MotionEvent?,
                            e2: MotionEvent,
                            velocityX: Float,
                            velocityY: Float
                        ): Boolean {
                            if (e1 == null) return false
                            if (isLiveScrubbing || wasScrubbingInSession || isPinching || currentZoom > 1.05f || System.currentTimeMillis() - lastPinchEndTime < 300L) {
                                return false
                            }
                            if (touchState != TouchGestureState.IDLE && touchState != TouchGestureState.VERTICAL_SWIPE) {
                                return false
                            }
                            if (System.currentTimeMillis() - lastScrubEndTime < 500L) {
                                return false
                            }

                            val deltaX = e2.x - e1.x
                            val deltaY = e2.y - e1.y

                            pendingSingleTapJob?.cancel()
                            consecutiveTaps = 0

                            // Swiping UP is Next video, Swiping DOWN is Previous video (in middle 70% of screen)
                            val viewW = if (width > 0) width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
                            if (e1.x in (viewW * 0.15f)..(viewW * 0.85f)) {
                                if (abs(velocityY) > abs(velocityX) && abs(deltaY) > 80) {
                                    showScrubHud = false
                                    val viewHeight = if (height > 0) height.toFloat() else resources.displayMetrics.heightPixels.toFloat()
                                    if (deltaY < 0) {
                                        // Swipe UP -> NEXT video (only on top 2/3 of screen to avoid conflicting with Android Home gesture)
                                        if (e1.y <= viewHeight * (2f / 3f)) {
                                            currentSkipToNext.invoke(true)
                                            return true
                                        }
                                    } else {
                                        // Swipe DOWN -> PREVIOUS video
                                        currentSkipToPrevious.invoke(true)
                                        return true
                                    }
                                }
                            }

                            return false
                        }
                    })

                    setOnTouchListener { _, event ->
                        if (isInPipMode) return@setOnTouchListener false

                        scaleDetector.onTouchEvent(event)

                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                touchState = TouchGestureState.IDLE
                                wasScrubbingInSession = false
                                touchDownX = event.x
                                touchDownY = event.y
                                lastTouchX = event.x
                                lastTouchY = event.y
                                touchDownTime = System.currentTimeMillis()
                                currentScrubDeltaX = 0f
                                currentScrubDeltaY = 0f
                                isLiveScrubbing = false
                                isPinching = false
                                isPanning = false
                                isTwoFingerPanning = false

                                initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                initialBrightness = (activity?.window?.attributes?.screenBrightness ?: -1f).let {
                                    if (it in 0f..1f) it else {
                                        try {
                                            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                                        } catch (_: Exception) { 0.5f }
                                    }
                                }

                                // Check for Speed Boost long-press on sides if video is actively playing
                                speedBoostJob?.cancel()
                                if (exoPlayer.isPlaying) {
                                    speedBoostJob = scope.launch {
                                        delay(380)
                                        if (touchState == TouchGestureState.IDLE && !isLiveScrubbing && !isPinching) {
                                            val viewW = if (width > 0) width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
                                            // Press & Hold on left or right 35% gives 2.0x quick speed boost (YouTube style)
                                            if (touchDownX < viewW * 0.35f || touchDownX > viewW * 0.65f) {
                                                touchState = TouchGestureState.SPEED_BOOST
                                                isSpeedBoosting = true
                                                exoPlayer.setPlaybackSpeed(2.0f)
                                                vibrate(25L)
                                            }
                                        }
                                    }
                                }
                            }

                            MotionEvent.ACTION_POINTER_DOWN -> {
                                speedBoostJob?.cancel()
                                isPinching = true
                                isTwoFingerPanning = true
                                twoFingerDownTime = System.currentTimeMillis()
                                if (event.pointerCount >= 2) {
                                    lastCentroidX = (event.getX(0) + event.getX(1)) / 2f
                                    lastCentroidY = (event.getY(0) + event.getY(1)) / 2f
                                    twoFingerDownCentroidX = lastCentroidX
                                    twoFingerDownCentroidY = lastCentroidY
                                }
                                pendingSingleTapJob?.cancel()
                                consecutiveTaps = 0
                                lastTapZone = TapZone.NONE
                                if (isLiveScrubbing) {
                                    isLiveScrubbing = false
                                    scrubJob?.cancel()
                                    if (isOneFpsMode) {
                                        val fps = exoPlayer.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
                                        val speed = (1f / fps).coerceIn(0.005f, 0.1f)
                                        exoPlayer.setPlaybackSpeed(speed)
                                    } else {
                                        exoPlayer.setPlaybackSpeed(customPlaybackSpeed)
                                        exoPlayer.volume = 1.0f
                                    }
                                    exoPlayer.setSeekParameters(SeekParameters.EXACT)
                                    if (wasPlayingBeforeScrub) resumePlayback() else exoPlayer.pause()
                                    showScrubHud = false
                                }
                            }

                            MotionEvent.ACTION_MOVE -> {
                                if (event.pointerCount >= 2) {
                                    isPinching = true
                                    val cX = (event.getX(0) + event.getX(1)) / 2f
                                    val cY = (event.getY(0) + event.getY(1)) / 2f
                                    if (!isTwoFingerPanning) {
                                        lastCentroidX = cX
                                        lastCentroidY = cY
                                        isTwoFingerPanning = true
                                    } else {
                                        val dx = cX - lastCentroidX
                                        val dy = cY - lastCentroidY
                                        lastCentroidX = cX
                                        lastCentroidY = cY

                                        // Two-Finger Swipe DOWN to Enter PiP:
                                        if (currentZoom <= 1.05f && System.currentTimeMillis() - twoFingerDownTime > 80L) {
                                            val deltaCentroidY = cY - twoFingerDownCentroidY
                                            if (deltaCentroidY > 150f && abs(deltaCentroidY) > abs(cX - twoFingerDownCentroidX) * 1.5f) {
                                                enterPipMode()
                                                return@setOnTouchListener true
                                            }
                                        }

                                        if (currentZoom > 1.01f) {
                                            val target = getVideoTarget()
                                            val viewW = if (this@apply.width > 0) this@apply.width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
                                            val viewH = if (this@apply.height > 0) this@apply.height.toFloat() else resources.displayMetrics.heightPixels.toFloat()
                                            val contentBaseW = if (target.width > 0) target.width.toFloat() else viewW
                                            val contentBaseH = if (target.height > 0) target.height.toFloat() else viewH
                                            val maxPanX = ((contentBaseW * currentZoom - viewW) / 2f).coerceAtLeast(0f)
                                            val maxPanY = ((contentBaseH * currentZoom - viewH) / 2f).coerceAtLeast(0f)

                                            currentPanX = (currentPanX + dx).coerceIn(-maxPanX, maxPanX)
                                            currentPanY = (currentPanY + dy).coerceIn(-maxPanY, maxPanY)

                                            target.translationX = currentPanX
                                            target.translationY = currentPanY
                                        }
                                    }
                                } else {
                                    // Single finger:
                                    isTwoFingerPanning = false
                                    if (abs(event.x - touchDownX) > 15f || abs(event.y - touchDownY) > 15f) {
                                        speedBoostJob?.cancel()
                                    }

                                    if (System.currentTimeMillis() - lastPinchEndTime < 150L) {
                                        lastTouchX = event.x
                                        lastTouchY = event.y
                                    } else {
                                        val deltaX = event.x - touchDownX
                                        val deltaY = event.y - touchDownY
                                        val viewW = if (this@apply.width > 0) this@apply.width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
                                        val viewH = if (this@apply.height > 0) this@apply.height.toFloat() else resources.displayMetrics.heightPixels.toFloat()

                                        if (touchState == TouchGestureState.IDLE && !isLiveScrubbing) {
                                            // 1. Check Vertical Edge Sliders (Left 15% Brightness, Right 15% Volume)
                                            if (touchDownX < viewW * 0.15f && abs(deltaY) > 25f && abs(deltaY) > abs(deltaX) * 1.5f) {
                                                touchState = TouchGestureState.BRIGHTNESS_SLIDER
                                                pendingSingleTapJob?.cancel()
                                                consecutiveTaps = 0
                                                hideEdgeSliderJob?.cancel()
                                            } else if (touchDownX > viewW * 0.85f && abs(deltaY) > 25f && abs(deltaY) > abs(deltaX) * 1.5f) {
                                                touchState = TouchGestureState.VOLUME_SLIDER
                                                pendingSingleTapJob?.cancel()
                                                consecutiveTaps = 0
                                                hideEdgeSliderJob?.cancel()
                                            } else if (abs(deltaX) > 30f && abs(deltaX) > abs(deltaY) * 1.2f) {
                                                // 2. Check Horizontal Drag: Paused Jog-Shuttle vs Live Scrubbing
                                                pendingSingleTapJob?.cancel()
                                                consecutiveTaps = 0
                                                lastTapZone = TapZone.NONE

                                                if (!exoPlayer.isPlaying) {
                                                    // Paused: Precision Jog-Shuttle Mode
                                                    touchState = TouchGestureState.JOG_SHUTTLE
                                                    jogStartMs = exoPlayer.currentPosition
                                                    hideJogHudJob?.cancel()
                                                    showJogHud = true
                                                    vibrate(20L)
                                                } else {
                                                    // Playing: Standard Velocity Scrubbing Mode
                                                    isLiveScrubbing = true
                                                    wasScrubbingInSession = true
                                                    touchState = TouchGestureState.SCRUBBING
                                                    hideScrubHudJob?.cancel()
                                                    wasPlayingBeforeScrub = true
                                                    scrubStartMs = exoPlayer.currentPosition
                                                    exoPlayer.volume = 0f
                                                    vibrate(25L)

                                                    scrubJob?.cancel()
                                                    scrubJob = scope.launch {
                                                        var lastBackwardTickTime = 0L
                                                        var lastSeekDispatchTime = 0L
                                                        var backwardSimulatedPositionMs = scrubStartMs

                                                        while (isActive && isLiveScrubbing) {
                                                            val dx = currentScrubDeltaX
                                                            val absDx = abs(dx)
                                                            val deadZone = 25f
                                                            val maxDist = viewW * 0.45f

                                                            if (absDx <= deadZone) {
                                                                if (exoPlayer.isPlaying) {
                                                                    exoPlayer.pause()
                                                                }
                                                                scrubHudSpeed = 0f
                                                                scrubHudIsForward = dx >= 0
                                                                scrubHudTargetMs = currentPositionMs
                                                                scrubHudDeltaSeconds = (currentPositionMs - scrubStartMs) / 1000f
                                                                showScrubHud = true
                                                            } else {
                                                                val effDist = absDx - deadZone
                                                                val norm = (effDist / maxDist).coerceIn(0f, 1f)
                                                                val speed = (0.1f + 7.4f * norm.pow(1.5f)).coerceIn(0.1f, 7.5f)
                                                                val roundedSpeed = (round(speed * 10f) / 10f).coerceIn(0.1f, 7.5f)

                                                                if (dx > 0) {
                                                                    scrubHudIsForward = true
                                                                    scrubHudSpeed = roundedSpeed
                                                                    showScrubHud = true

                                                                    exoPlayer.setPlaybackSpeed(roundedSpeed)
                                                                    val maxDur = if (durationMs > 0) durationMs else 1L
                                                                    if (!exoPlayer.isPlaying && exoPlayer.currentPosition < maxDur) {
                                                                        resumePlayback()
                                                                    }

                                                                    currentPositionMs = exoPlayer.currentPosition
                                                                    backwardSimulatedPositionMs = exoPlayer.currentPosition
                                                                    scrubHudTargetMs = exoPlayer.currentPosition
                                                                    scrubHudDeltaSeconds = (exoPlayer.currentPosition - scrubStartMs) / 1000f
                                                                } else {
                                                                    scrubHudIsForward = false
                                                                    scrubHudSpeed = roundedSpeed
                                                                    showScrubHud = true

                                                                    if (exoPlayer.isPlaying) {
                                                                        exoPlayer.pause()
                                                                    }

                                                                    val now = System.currentTimeMillis()
                                                                    val dtSec = if (lastBackwardTickTime == 0L) 0.033f else ((now - lastBackwardTickTime).coerceAtMost(80L) / 1000f)
                                                                    val stepMs = (roundedSpeed * dtSec * 1000f).toLong().coerceAtLeast(4L)
                                                                    backwardSimulatedPositionMs = (backwardSimulatedPositionMs - stepMs).coerceAtLeast(0L)
                                                                    lastBackwardTickTime = now

                                                                    currentPositionMs = backwardSimulatedPositionMs
                                                                    scrubHudTargetMs = backwardSimulatedPositionMs
                                                                    scrubHudDeltaSeconds = (backwardSimulatedPositionMs - scrubStartMs) / 1000f

                                                                    val minIntervalMs = 55L
                                                                    val seekParams = SeekParameters.CLOSEST_SYNC

                                                                    if (now - lastSeekDispatchTime >= minIntervalMs && !isSeekInProgress) {
                                                                        isSeekInProgress = true
                                                                        lastSeekDispatchTime = now
                                                                        exoPlayer.setSeekParameters(seekParams)
                                                                        exoPlayer.seekTo(backwardSimulatedPositionMs)
                                                                    } else {
                                                                        pendingSeekPositionMs = backwardSimulatedPositionMs
                                                                    }
                                                                }
                                                            }
                                                            delay(33)
                                                        }
                                                    }
                                                }
                                            } else if (touchState == TouchGestureState.IDLE && abs(deltaY) > 20f && abs(deltaY) > abs(deltaX) * 1.1f && touchDownX in (viewW * 0.15f)..(viewW * 0.85f)) {
                                                touchState = TouchGestureState.VERTICAL_SWIPE
                                                pendingSingleTapJob?.cancel()
                                                consecutiveTaps = 0
                                                lastTapZone = TapZone.NONE
                                            }
                                        }

                                        currentScrubDeltaX = deltaX
                                        currentScrubDeltaY = deltaY

                                        if (touchState == TouchGestureState.BRIGHTNESS_SLIDER) {
                                            val deltaPct = -(deltaY / (viewH * 0.65f))
                                            val newB = (initialBrightness + deltaPct).coerceIn(0.01f, 1.0f)
                                            brightnessProgress = newB
                                            showBrightnessHud = true
                                            val window = activity?.window
                                            if (window != null) {
                                                val lp = window.attributes
                                                lp.screenBrightness = newB
                                                window.attributes = lp
                                            }
                                        } else if (touchState == TouchGestureState.VOLUME_SLIDER) {
                                            val deltaPct = -(deltaY / (viewH * 0.65f))
                                            val newV = (initialVolume + deltaPct * maxAudioVolume).coerceIn(0f, maxAudioVolume.toFloat())
                                            volumeProgress = newV / maxAudioVolume.toFloat()
                                            showVolumeHud = true
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newV.toInt(), 0)
                                        } else if (touchState == TouchGestureState.JOG_SHUTTLE) {
                                            val fps = exoPlayer.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
                                            val frameDurationMs = (1000f / fps).toLong().coerceAtLeast(16L)
                                            val frames = (deltaX / 14f).toInt()
                                            jogFrameDelta = frames
                                            val target = (jogStartMs + frames * frameDurationMs).coerceIn(0L, if (durationMs > 0) durationMs else 1L)
                                            jogTargetMs = target
                                            currentPositionMs = target

                                            val now = System.currentTimeMillis()
                                            if (now - lastJogSeekTime >= 75L && !isSeekInProgress) {
                                                isSeekInProgress = true
                                                lastJogSeekTime = now
                                                exoPlayer.setSeekParameters(SeekParameters.EXACT)
                                                exoPlayer.seekTo(target)
                                            } else {
                                                pendingSeekPositionMs = target
                                            }
                                        }
                                    }
                                }
                            }

                            MotionEvent.ACTION_POINTER_UP -> {
                                if (event.pointerCount == 2) {
                                    val twoFingerDuration = System.currentTimeMillis() - twoFingerDownTime
                                    val cX = (event.getX(0) + event.getX(1)) / 2f
                                    val cY = (event.getY(0) + event.getY(1)) / 2f
                                    val dist = Math.hypot((cX - twoFingerDownCentroidX).toDouble(), (cY - twoFingerDownCentroidY).toDouble())
                                    if (twoFingerDuration < 280L && dist < 35.0) {
                                        // Quick 2-finger tap toggles Play/Pause!
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                            isPlaying = false
                                        } else {
                                            resumePlayback()
                                        }
                                        vibrate(30L)
                                    }
                                }

                                val remainingIndex = if (event.actionIndex == 0) 1 else 0
                                if (remainingIndex < event.pointerCount) {
                                    lastTouchX = event.getX(remainingIndex)
                                    lastTouchY = event.getY(remainingIndex)
                                }
                                lastPinchEndTime = System.currentTimeMillis()
                                isPinching = false
                                isTwoFingerPanning = false
                            }

                            MotionEvent.ACTION_UP -> {
                                speedBoostJob?.cancel()
                                if (isSpeedBoosting) {
                                    isSpeedBoosting = false
                                    exoPlayer.setPlaybackSpeed(customPlaybackSpeed)
                                    vibrate(15L)
                                }
                                if (touchState == TouchGestureState.BRIGHTNESS_SLIDER || touchState == TouchGestureState.VOLUME_SLIDER) {
                                    hideEdgeSliderJob?.cancel()
                                    hideEdgeSliderJob = scope.launch {
                                        delay(900)
                                        showBrightnessHud = false
                                        showVolumeHud = false
                                    }
                                }
                                if (touchState == TouchGestureState.JOG_SHUTTLE) {
                                    pendingSeekPositionMs = null
                                    exoPlayer.setSeekParameters(SeekParameters.EXACT)
                                    exoPlayer.seekTo(jogTargetMs)
                                    hideJogHudJob?.cancel()
                                    hideJogHudJob = scope.launch {
                                        delay(900)
                                        showJogHud = false
                                    }
                                }
                                isTwoFingerPanning = false
                                if (isLiveScrubbing || wasScrubbingInSession) {
                                    isLiveScrubbing = false
                                    scrubJob?.cancel()
                                    lastScrubEndTime = System.currentTimeMillis()
                                    pendingSeekPositionMs = null

                                    if (isOneFpsMode) {
                                        val fps = exoPlayer.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
                                        val speed = (1f / fps).coerceIn(0.005f, 0.1f)
                                        exoPlayer.setPlaybackSpeed(speed)
                                    } else {
                                        exoPlayer.setPlaybackSpeed(customPlaybackSpeed)
                                        exoPlayer.volume = 1.0f
                                    }
                                    exoPlayer.setSeekParameters(SeekParameters.EXACT)

                                    if (!wasPlayingBeforeScrub || currentPositionMs != exoPlayer.currentPosition) {
                                        exoPlayer.seekTo(currentPositionMs)
                                    }

                                    if (wasPlayingBeforeScrub) {
                                        resumePlayback()
                                    } else {
                                        exoPlayer.pause()
                                    }

                                    currentPositionMs = exoPlayer.currentPosition
                                    scrubHudTargetMs = exoPlayer.currentPosition
                                    vibrate(25L)

                                    hideScrubHudJob?.cancel()
                                    hideScrubHudJob = scope.launch {
                                        delay(800)
                                        showScrubHud = false
                                    }
                                } else if (isPinching || System.currentTimeMillis() - lastPinchEndTime < 200L) {
                                    isPinching = false
                                } else if (isPanning) {
                                    isPanning = false
                                } else {
                                    val moveDist = Math.hypot(
                                        (event.x - touchDownX).toDouble(),
                                        (event.y - touchDownY).toDouble()
                                    )
                                    val pressDuration = System.currentTimeMillis() - touchDownTime

                                    if (moveDist < 35 && pressDuration < 320) {
                                        if (currentZoom > 1.05f) {
                                            val now = System.currentTimeMillis()
                                            val timeSinceLastUp = now - lastTapUpTime
                                            if (timeSinceLastUp < 320) {
                                                pendingSingleTapJob?.cancel()
                                                consecutiveTaps = 0
                                                lastTapZone = TapZone.NONE
                                                lastTapUpTime = 0L
                                                vibrate(35L)
                                                resetZoom(true)

                                                zoomHudScale = 1.0f
                                                showZoomHud = true
                                                hideZoomHudJob?.cancel()
                                                hideZoomHudJob = scope.launch {
                                                    delay(800)
                                                    showZoomHud = false
                                                }
                                            } else {
                                                lastTapUpTime = now
                                                pendingSingleTapJob?.cancel()
                                                pendingSingleTapJob = scope.launch {
                                                    delay(320)
                                                    isControlsVisible = !isControlsVisible
                                                }
                                            }
                                        } else {
                                            val viewWidth = width.toFloat()
                                            val zone = when {
                                                event.x < viewWidth / 3f -> TapZone.LEFT
                                                event.x > viewWidth * 2f / 3f -> TapZone.RIGHT
                                                else -> TapZone.MIDDLE
                                            }

                                            val now = System.currentTimeMillis()
                                            val timeSinceLastUp = now - lastTapUpTime

                                            when (zone) {
                                                TapZone.MIDDLE -> {
                                                    if (lastTapZone == TapZone.MIDDLE && timeSinceLastUp < 320) {
                                                        pendingSingleTapJob?.cancel()
                                                        consecutiveTaps = 0
                                                        lastTapZone = TapZone.NONE
                                                        lastTapUpTime = 0L
                                                        currentCycleScaleMode.invoke()
                                                    } else {
                                                        lastTapZone = TapZone.MIDDLE
                                                        lastTapUpTime = now
                                                        pendingSingleTapJob?.cancel()
                                                        pendingSingleTapJob = scope.launch {
                                                            delay(320)
                                                            isControlsVisible = !isControlsVisible
                                                            lastTapZone = TapZone.NONE
                                                        }
                                                    }
                                                }

                                                TapZone.LEFT -> {
                                                    val maxTapWindow = if (consecutiveTaps >= 2) 650 else 320
                                                    if (lastTapZone == TapZone.LEFT && timeSinceLastUp < maxTapWindow) {
                                                        pendingSingleTapJob?.cancel()
                                                        consecutiveTaps++
                                                        accumulatedSeekSeconds += 5

                                                        val maxDur = if (durationMs > 0) durationMs else 1L
                                                        val newPos = (exoPlayer.currentPosition - 5000L).coerceIn(0L, maxDur)
                                                        exoPlayer.seekTo(newPos)
                                                        currentPositionMs = newPos
                                                        vibrate(30L)

                                                        isDoubleTapForward = false
                                                        doubleTapTotalSeconds = accumulatedSeekSeconds
                                                        showDoubleTapSeekHud = true
                                                        hideDoubleTapHudJob?.cancel()
                                                        hideDoubleTapHudJob = scope.launch {
                                                            delay(900)
                                                            showDoubleTapSeekHud = false
                                                        }

                                                        lastTapUpTime = now
                                                        multiTapResetJob?.cancel()
                                                        multiTapResetJob = scope.launch {
                                                            delay(650)
                                                            consecutiveTaps = 0
                                                            accumulatedSeekSeconds = 0
                                                            lastTapZone = TapZone.NONE
                                                        }
                                                    } else {
                                                        consecutiveTaps = 1
                                                        accumulatedSeekSeconds = 0
                                                        lastTapZone = TapZone.LEFT
                                                        lastTapUpTime = now
                                                        multiTapResetJob?.cancel()
                                                        pendingSingleTapJob?.cancel()
                                                        pendingSingleTapJob = scope.launch {
                                                            delay(320)
                                                            if (consecutiveTaps == 1) {
                                                                isControlsVisible = !isControlsVisible
                                                                consecutiveTaps = 0
                                                                lastTapZone = TapZone.NONE
                                                            }
                                                        }
                                                    }
                                                }

                                                TapZone.RIGHT -> {
                                                    val maxTapWindow = if (consecutiveTaps >= 2) 650 else 320
                                                    if (lastTapZone == TapZone.RIGHT && timeSinceLastUp < maxTapWindow) {
                                                        pendingSingleTapJob?.cancel()
                                                        consecutiveTaps++
                                                        accumulatedSeekSeconds += 5

                                                        val maxDur = if (durationMs > 0) durationMs else 1L
                                                        val newPos = (exoPlayer.currentPosition + 5000L).coerceIn(0L, maxDur)
                                                        exoPlayer.seekTo(newPos)
                                                        currentPositionMs = newPos
                                                        vibrate(30L)

                                                        isDoubleTapForward = true
                                                        doubleTapTotalSeconds = accumulatedSeekSeconds
                                                        showDoubleTapSeekHud = true
                                                        hideDoubleTapHudJob?.cancel()
                                                        hideDoubleTapHudJob = scope.launch {
                                                            delay(900)
                                                            showDoubleTapSeekHud = false
                                                        }

                                                        lastTapUpTime = now
                                                        multiTapResetJob?.cancel()
                                                        multiTapResetJob = scope.launch {
                                                            delay(650)
                                                            consecutiveTaps = 0
                                                            accumulatedSeekSeconds = 0
                                                            lastTapZone = TapZone.NONE
                                                        }
                                                    } else {
                                                        consecutiveTaps = 1
                                                        accumulatedSeekSeconds = 0
                                                        lastTapZone = TapZone.RIGHT
                                                        lastTapUpTime = now
                                                        multiTapResetJob?.cancel()
                                                        pendingSingleTapJob?.cancel()
                                                        pendingSingleTapJob = scope.launch {
                                                            delay(320)
                                                            if (consecutiveTaps == 1) {
                                                                isControlsVisible = !isControlsVisible
                                                                consecutiveTaps = 0
                                                                lastTapZone = TapZone.NONE
                                                            }
                                                        }
                                                    }
                                                }

                                                TapZone.NONE -> {}
                                            }
                                        }
                                    }
                                }
                            }

                            MotionEvent.ACTION_CANCEL -> {
                                speedBoostJob?.cancel()
                                if (isSpeedBoosting) {
                                    isSpeedBoosting = false
                                    exoPlayer.setPlaybackSpeed(customPlaybackSpeed)
                                }
                                showBrightnessHud = false
                                showVolumeHud = false
                                showJogHud = false
                                pendingSeekPositionMs = null

                                isTwoFingerPanning = false
                                if (isLiveScrubbing) {
                                    isLiveScrubbing = false
                                    scrubJob?.cancel()
                                    if (isOneFpsMode) {
                                        val fps = exoPlayer.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
                                        val speed = (1f / fps).coerceIn(0.005f, 0.1f)
                                        exoPlayer.setPlaybackSpeed(speed)
                                    } else {
                                        exoPlayer.setPlaybackSpeed(customPlaybackSpeed)
                                        exoPlayer.volume = 1.0f
                                    }
                                    exoPlayer.setSeekParameters(SeekParameters.EXACT)
                                    if (wasPlayingBeforeScrub) {
                                        resumePlayback()
                                    } else {
                                        exoPlayer.pause()
                                    }
                                    showScrubHud = false
                                }
                                isPinching = false
                                isPanning = false
                                pendingSingleTapJob?.cancel()
                                consecutiveTaps = 0
                                touchState = TouchGestureState.IDLE
                                wasScrubbingInSession = false
                            }
                        }
                        val allowGestureDetector = (event.actionMasked == MotionEvent.ACTION_DOWN ||
                                touchState == TouchGestureState.IDLE ||
                                touchState == TouchGestureState.VERTICAL_SWIPE) &&
                                !isLiveScrubbing && !wasScrubbingInSession
                        if (allowGestureDetector) {
                            gestureDetector.onTouchEvent(event)
                        }
                        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                            touchState = TouchGestureState.IDLE
                            wasScrubbingInSession = false
                        }
                        true
                    }
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = scaleMode.resizeMode
            },
            onRelease = { playerView ->
                playerView.player = null
            }
        )

        // Scaling HUD (on Middle 1/3 Double Tap)
        ScalingHud(
            visible = showScaleHud && !isInPipMode,
            scaleMode = scaleMode,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        )

        // Zoom HUD (on Pinch to Zoom)
        ZoomHud(
            visible = showZoomHud && !isInPipMode,
            zoomScale = zoomHudScale,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        )

        // 2.0x Quick Speed Boost HUD (Press & Hold on sides)
        SpeedBoostHud(
            visible = isSpeedBoosting && !isInPipMode,
            speedMultiplier = 2.0f,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 54.dp)
        )

        // Brightness HUD (Left edge vertical slide)
        BrightnessVolumeHud(
            visible = showBrightnessHud && !isInPipMode,
            type = EdgeSliderType.BRIGHTNESS,
            progress = brightnessProgress,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp)
        )

        // Volume HUD (Right edge vertical slide)
        BrightnessVolumeHud(
            visible = showVolumeHud && !isInPipMode,
            type = EdgeSliderType.VOLUME,
            progress = volumeProgress,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp)
        )

        // Paused Precision Jog-Shuttle HUD (Horizontal drag while paused)
        JogFrameHud(
            visible = showJogHud && !isInPipMode,
            frameDelta = jogFrameDelta,
            targetPositionMs = jogTargetMs,
            modifier = Modifier.align(Alignment.Center)
        )

        // Background Decoder Recovery Banner
        if (viewModel.isRecoveringDecoder && !isInPipMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xDD1E1E24),
                    border = BorderStroke(1.dp, PixelAccentBlue.copy(alpha = 0.6f)),
                    modifier = Modifier.clickable { resumePlayback() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = PixelAccentBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Reconnecting decoder... Tap to resume",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Double-Tap Jump HUD (Left 1/3: -5s/tap, Right 1/3: +5s/tap)
        DoubleTapSeekHud(
            visible = showDoubleTapSeekHud && !isInPipMode,
            isForward = isDoubleTapForward,
            totalSeconds = doubleTapTotalSeconds,
            modifier = Modifier
                .align(if (isDoubleTapForward) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 48.dp)
        )

        // Scrubbing HUD (on Left/Right drag)
        ScrubbingHud(
            visible = showScrubHud && !isInPipMode,
            speed = scrubHudSpeed,
            deltaSeconds = scrubHudDeltaSeconds,
            targetPositionMs = scrubHudTargetMs,
            durationMs = durationMs,
            isForward = scrubHudIsForward,
            modifier = Modifier.align(Alignment.Center)
        )

        // Favorite HUD (on Long Press)
        FavoriteHeartHud(
            visible = showFavoriteHud && !isInPipMode,
            isFavorite = lastFavoriteStatus,
            modifier = Modifier.align(Alignment.Center)
        )

        // Swipe Action HUD (on Swipe Up/Down: Previous / Next)
        SwipeIndicatorHud(
            visible = showSwipeHud && !isInPipMode,
            direction = lastSwipeDirection,
            title = currentVideo.title,
            currentIndex = currentIndex,
            totalCount = videos.size,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        )

        // Controls Overlay (toggled via Single Tap)
        AnimatedVisibility(
            visible = isControlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isControlsVisible = false
                    }
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onExitPlayer,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Folders",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentVideo.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${folder.name} • ${currentIndex + 1} of ${videos.size}",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Playback Speed menu button (far left of top right button group)
                    Box {
                        IconButton(
                            onClick = {
                                customSpeedInputText = formatSpeed(customPlaybackSpeed)
                                showSpeedMenu = true
                            },
                            modifier = Modifier
                                .background(
                                    if (customPlaybackSpeed != 1.0f) PixelAccentBlue.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.5f),
                                    CircleShape
                                )
                                .border(
                                    width = if (customPlaybackSpeed != 1.0f) 1.5.dp else 0.dp,
                                    color = if (customPlaybackSpeed != 1.0f) PixelAccentBlue else Color.Transparent,
                                    shape = CircleShape
                                )
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Playback Speed",
                                tint = if (customPlaybackSpeed != 1.0f) PixelAccentBlue else Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false },
                            properties = PopupProperties(focusable = true),
                            modifier = Modifier
                                .background(Color(0xFF202124), RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.width(210.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Title row with Reset button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Playback Speed",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (customPlaybackSpeed != 1.0f) {
                                        Text(
                                            text = "Reset",
                                            color = PixelAccentBlue,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    customPlaybackSpeed = 1.0f
                                                    customSpeedInputText = "1.0"
                                                    if (!isOneFpsMode) {
                                                        exoPlayer.setPlaybackSpeed(1.0f)
                                                    }
                                                }
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Stepper row: [-]   1.0x   [+]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // [-] Button: decreases by 50% of currently shown value
                                    IconButton(
                                        onClick = {
                                            val newSpeed = (customPlaybackSpeed * 0.5f).coerceIn(0.1f, 10.0f)
                                            val rounded = round(newSpeed * 100f) / 100f
                                            customPlaybackSpeed = rounded
                                            customSpeedInputText = formatSpeed(rounded)
                                            if (!isOneFpsMode) {
                                                exoPlayer.setPlaybackSpeed(rounded)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Decrease speed by 50%",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Current speed display
                                    Text(
                                        text = "${formatSpeed(customPlaybackSpeed)}x",
                                        color = if (customPlaybackSpeed != 1.0f) PixelAccentBlue else Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    // [+] Button: increases in 25% increments
                                    IconButton(
                                        onClick = {
                                            val newSpeed = (customPlaybackSpeed + 0.25f).coerceIn(0.1f, 10.0f)
                                            val rounded = round(newSpeed * 100f) / 100f
                                            customPlaybackSpeed = rounded
                                            customSpeedInputText = formatSpeed(rounded)
                                            if (!isOneFpsMode) {
                                                exoPlayer.setPlaybackSpeed(rounded)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Increase speed by 25%",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // "Custom speed" label
                                Text(
                                    text = "Custom speed",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Text box for custom speed entry (limited to numbers, no alpha)
                                OutlinedTextField(
                                    value = customSpeedInputText,
                                    onValueChange = { input ->
                                        val filtered = input.filter { it.isDigit() || it == '.' }
                                        if (filtered.count { it == '.' } <= 1 && filtered.length <= 5) {
                                            customSpeedInputText = filtered
                                            val parsed = filtered.toFloatOrNull()
                                            if (parsed != null && parsed in 0.1f..10.0f) {
                                                val rounded = round(parsed * 100f) / 100f
                                                customPlaybackSpeed = rounded
                                                if (!isOneFpsMode) {
                                                    exoPlayer.setPlaybackSpeed(rounded)
                                                }
                                            }
                                        }
                                    },
                                    placeholder = {
                                        Text(
                                            text = "0.1 - 10.0",
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 13.sp
                                        )
                                    },
                                    trailingIcon = {
                                        Text(
                                            text = "x",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            val parsed = customSpeedInputText.toFloatOrNull()
                                            if (parsed != null) {
                                                val clamped = parsed.coerceIn(0.1f, 10.0f)
                                                val rounded = round(clamped * 100f) / 100f
                                                customPlaybackSpeed = rounded
                                                customSpeedInputText = formatSpeed(rounded)
                                                if (!isOneFpsMode) {
                                                    exoPlayer.setPlaybackSpeed(rounded)
                                                }
                                            } else {
                                                customSpeedInputText = formatSpeed(customPlaybackSpeed)
                                            }
                                            keyboardController?.hide()
                                        }
                                    ),
                                    textStyle = LocalTextStyle.current.copy(
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PixelAccentBlue,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                        cursorColor = PixelAccentBlue
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Scaling mode toggle button
                    IconButton(
                        onClick = { cycleScaleMode() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Picture-in-Picture button
                    IconButton(
                        onClick = { enterPipMode() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Picture-in-Picture",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Favorite button (far right button)
                    IconButton(
                        onClick = { triggerFavoriteToggle() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isCurrentVideoFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite in Photos",
                            tint = if (isCurrentVideoFavorite) PixelHeartRed else Color.White
                        )
                    }
                }

                // Center Play/Pause, Frame Stepping (-1F, +1F), 1 FPS & Skip Buttons
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = { skipToPrevious() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Step Backward -1 Frame Button
                    IconButton(
                        onClick = { stepOneFrame(forward = false) },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                            .size(42.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "-1",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 11.sp
                            )
                            Text(
                                text = "FR",
                                color = PixelAccentBlue,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 8.sp
                            )
                        }
                    }

                    // Main Play/Pause Button
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                isPlaying = false
                            } else {
                                resumePlayback()
                            }
                        },
                        modifier = Modifier
                            .background(Color.White, CircleShape)
                            .size(62.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Step Forward +1 Frame Button
                    IconButton(
                        onClick = { stepOneFrame(forward = true) },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                            .size(42.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "+1",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 11.sp
                            )
                            Text(
                                text = "FR",
                                color = PixelAccentBlue,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 8.sp
                            )
                        }
                    }

                    // 1 FPS Mode Toggle Button
                    IconButton(
                        onClick = { toggleOneFpsMode() },
                        modifier = Modifier
                            .background(
                                if (isOneFpsMode) PixelAccentBlue else Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isOneFpsMode) Color.White else Color.White.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                            .size(44.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "1",
                                color = if (isOneFpsMode) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 12.sp
                            )
                            Text(
                                text = "FPS",
                                color = if (isOneFpsMode) Color.Black else PixelAccentBlue,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 9.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = { skipToNext() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom Timeline & Gesture Hints
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    // Gesture hints pill (fade out while actively seeking to leave room for preview)
                    AnimatedVisibility(
                        visible = !isTimelineSeeking,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Row(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2-Tap Left: -5s • Middle: Zoom • Right: +5s • Up/Down: Prev/Next",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress Slider with floating thumbnail preview
                    val progressFraction = if (durationMs > 0) {
                        if (isTimelineSeeking) timelineSeekProgress else (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    } else 0f

                    val previewTargetMs = if (durationMs > 0) {
                        (timelineSeekProgress * durationMs).toLong().coerceIn(0L, durationMs)
                    } else 0L

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val totalWidth = maxWidth

                        // Floating thumbnail preview card hovering directly above the slider thumb
                        TimelineThumbnailPreview(
                            visible = isTimelineSeeking,
                            video = currentVideo,
                            targetTimestampMs = previewTargetMs,
                            progressFraction = progressFraction,
                            durationMs = durationMs,
                            totalWidth = totalWidth,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(y = (-36).dp)
                        )

                        Slider(
                            value = progressFraction,
                            onValueChange = { fraction ->
                                isTimelineSeeking = true
                                timelineSeekProgress = fraction
                            },
                            onValueChangeFinished = {
                                val targetMs = (timelineSeekProgress * durationMs).toLong().coerceIn(0L, durationMs)
                                exoPlayer.seekTo(targetMs)
                                currentPositionMs = targetMs
                                isTimelineSeeking = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(if (isTimelineSeeking) previewTargetMs else currentPositionMs),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatTime(durationMs),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun formatSpeed(speed: Float): String {
    val rounded = round(speed * 100f) / 100f
    return if (rounded % 1f == 0f) {
        String.format(java.util.Locale.US, "%.1f", rounded)
    } else {
        val str = String.format(java.util.Locale.US, "%.2f", rounded)
        if (str.endsWith('0')) str.dropLast(1) else str
    }
}
