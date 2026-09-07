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
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.pixelflow.player.MainActivity
import com.pixelflow.player.pip.PipManager
import com.pixelflow.player.data.FavoritesHelper
import com.pixelflow.player.data.VideoFolder
import com.pixelflow.player.data.VideoItem
import com.pixelflow.player.data.VideoOrientation
import com.pixelflow.player.data.VideoScaleMode
import com.pixelflow.player.ui.components.DoubleTapSeekHud
import com.pixelflow.player.ui.components.FavoriteHeartHud
import com.pixelflow.player.ui.components.ScalingHud
import com.pixelflow.player.ui.components.ScrubbingHud
import com.pixelflow.player.ui.components.SwipeDirection
import com.pixelflow.player.ui.components.SwipeIndicatorHud
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

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    folder: VideoFolder,
    videos: List<VideoItem>,
    orientation: VideoOrientation,
    isInPipMode: Boolean = false,
    onExitPlayer: () -> Unit
) {
    if (videos.isEmpty()) {
        LaunchedEffect(Unit) { onExitPlayer() }
        return
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var currentIndex by remember { mutableIntStateOf(0) }
    val currentVideo = videos.getOrElse(currentIndex) { videos[0] }

    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(currentVideo.durationMs) }
    var isControlsVisible by remember { mutableStateOf(false) }

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

    LaunchedEffect(pipAspectRatio, isPlaying, pipSourceRectHint) {
        updatePipParams(autoEnter = true)
    }

    // HUD States
    var showScaleHud by remember { mutableStateOf(false) }
    var showFavoriteHud by remember { mutableStateOf(false) }
    var lastFavoriteStatus by remember { mutableStateOf(currentVideo.isFavorite) }
    var showSwipeHud by remember { mutableStateOf(false) }
    var lastSwipeDirection by remember { mutableStateOf(SwipeDirection.NEXT) }

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
        if (result.resultCode == Activity.RESULT_OK) {
            currentVideo.isFavorite = !currentVideo.isFavorite
            lastFavoriteStatus = currentVideo.isFavorite
            showFavoriteHud = true
            vibrate(60L)
            scope.launch {
                delay(1600)
                showFavoriteHud = false
            }
        }
    }

    fun triggerFavoriteToggle() {
        vibrate(50L)
        val request = FavoritesHelper.createFavoriteIntentSenderRequest(
            context,
            currentVideo,
            !currentVideo.isFavorite
        )
        if (request != null) {
            favoriteLauncher.launch(request)
        } else {
            currentVideo.isFavorite = !currentVideo.isFavorite
            lastFavoriteStatus = currentVideo.isFavorite
            showFavoriteHud = true
            scope.launch {
                delay(1600)
                showFavoriteHud = false
            }
        }
    }

    // Configure ExoPlayer with USAGE_MEDIA, handleAudioFocus = true, and WakeMode for Android Auto & Car routing
    val exoPlayer = remember {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            setSeekParameters(SeekParameters.EXACT)
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    // MediaSession for Android Auto detection, car speaker audio routing, and steering wheel controls
    val sessionActivityPendingIntent = remember {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    val mediaSession = remember(exoPlayer) {
        MediaSession.Builder(context, exoPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setId("PixelFlowSession")
            .build()
    }

    DisposableEffect(mediaSession) {
        onDispose {
            mediaSession.release()
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

    fun skipToNext(showIndicator: Boolean = true) {
        if (videos.isEmpty()) return
        val targetIndex = (currentIndex + 1) % videos.size
        exoPlayer.seekToDefaultPosition(targetIndex)
        exoPlayer.play()

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
        val targetIndex = if (currentIndex - 1 < 0) videos.size - 1 else currentIndex - 1
        exoPlayer.seekToDefaultPosition(targetIndex)
        exoPlayer.play()

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
                } else {
                    exoPlayer.play()
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
        exoPlayer.setMediaItems(mediaItems, 0, 0L)
        exoPlayer.prepare()
        exoPlayer.play()

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newIndex = exoPlayer.currentMediaItemIndex
                if (newIndex in videos.indices) {
                    currentIndex = newIndex
                    val currentVid = videos[newIndex]
                    currentPositionMs = 0L
                    durationMs = currentVid.durationMs
                    currentVid.isFavorite = FavoritesHelper.isFavorite(context, currentVid)
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

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()

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

    // Auto-hide controls overlay after 3.5s
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            delay(3500)
            isControlsVisible = false
        }
    }

    fun cycleScaleMode() {
        vibrate(35L)
        scaleMode = scaleMode.next()
        playerViewRef?.resizeMode = scaleMode.resizeMode
        showScaleHud = true
        scope.launch {
            delay(1300)
            showScaleHud = false
        }
    }

    BackHandler(enabled = !isInPipMode) {
        onExitPlayer()
    }

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

                    addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                        val loc = IntArray(2)
                        v.getLocationOnScreen(loc)
                        if (v.width > 0 && v.height > 0) {
                            pipSourceRectHint = Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
                        }
                    }

                    var isLiveScrubbing = false
                    var wasPlayingBeforeScrub = false
                    var scrubStartMs = 0L
                    var currentScrubDeltaX = 0f
                    var currentScrubDeltaY = 0f
                    var scrubJob: Job? = null
                    var hideScrubHudJob: Job? = null

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

                    val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDown(e: MotionEvent): Boolean {
                            return true
                        }

                        override fun onLongPress(e: MotionEvent) {
                            if (!isLiveScrubbing) {
                                pendingSingleTapJob?.cancel()
                                consecutiveTaps = 0
                                triggerFavoriteToggle()
                            }
                        }

                        override fun onFling(
                            e1: MotionEvent?,
                            e2: MotionEvent,
                            velocityX: Float,
                            velocityY: Float
                        ): Boolean {
                            if (e1 == null) return false
                            if (isLiveScrubbing) return true

                            val deltaX = e2.x - e1.x
                            val deltaY = e2.y - e1.y

                            pendingSingleTapJob?.cancel()
                            consecutiveTaps = 0

                            // Swiping UP is Next video, Swiping DOWN is Previous video
                            if (abs(velocityY) > abs(velocityX) && abs(deltaY) > 80) {
                                showScrubHud = false
                                val viewHeight = if (height > 0) height.toFloat() else resources.displayMetrics.heightPixels.toFloat()
                                if (deltaY < 0) {
                                    // Swipe UP -> NEXT video (only on top 2/3 of screen to avoid conflicting with Android Home gesture)
                                    if (e1.y <= viewHeight * (2f / 3f)) {
                                        skipToNext()
                                        return true
                                    }
                                } else {
                                    // Swipe DOWN -> PREVIOUS video
                                    skipToPrevious()
                                    return true
                                }
                            }

                            return false
                        }
                    })

                    setOnTouchListener { _, event ->
                        if (isInPipMode) return@setOnTouchListener false
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                touchDownX = event.x
                                touchDownY = event.y
                                touchDownTime = System.currentTimeMillis()
                                currentScrubDeltaX = 0f
                                currentScrubDeltaY = 0f
                                isLiveScrubbing = false
                            }

                            MotionEvent.ACTION_MOVE -> {
                                val deltaX = event.x - touchDownX
                                val deltaY = event.y - touchDownY
                                currentScrubDeltaX = deltaX
                                currentScrubDeltaY = deltaY

                                if (!isLiveScrubbing) {
                                    // If predominantly horizontal and dragged past threshold (30px), start scrubbing immediately!
                                    if (abs(deltaX) > 30f && abs(deltaX) > abs(deltaY) * 1.2f) {
                                        isLiveScrubbing = true
                                        pendingSingleTapJob?.cancel()
                                        consecutiveTaps = 0
                                        lastTapZone = TapZone.NONE
                                        hideScrubHudJob?.cancel()

                                        wasPlayingBeforeScrub = exoPlayer.isPlaying
                                        scrubStartMs = exoPlayer.currentPosition
                                        exoPlayer.volume = 0f // Mute audio during scrub for clean visual seek
                                        vibrate(25L)

                                        scrubJob?.cancel()
                                        scrubJob = scope.launch {
                                            var lastBackwardSeekTime = 0L

                                            while (isActive && isLiveScrubbing) {
                                                val dx = currentScrubDeltaX
                                                val absDx = abs(dx)
                                                val deadZone = 25f
                                                val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                                                val maxDist = screenWidth * 0.45f

                                                if (absDx <= deadZone) {
                                                    // In dead zone: pause / hold position
                                                    if (exoPlayer.isPlaying) {
                                                        exoPlayer.pause()
                                                    }
                                                    scrubHudSpeed = 0f
                                                    scrubHudIsForward = dx >= 0
                                                    scrubHudTargetMs = exoPlayer.currentPosition
                                                    scrubHudDeltaSeconds = (exoPlayer.currentPosition - scrubStartMs) / 1000f
                                                    showScrubHud = true
                                                } else {
                                                    val effDist = absDx - deadZone
                                                    val norm = (effDist / maxDist).coerceIn(0f, 1f)
                                                    // Power curve capped strictly to +/- 7.5x
                                                    val speed = (0.25f + 7.25f * norm.pow(1.5f)).coerceIn(0.25f, 7.5f)
                                                    val roundedSpeed = (round(speed * 10f) / 10f).coerceIn(0.25f, 7.5f)

                                                    if (dx > 0) {
                                                        // Forward scrubbing: native playback speed capped strictly at 7.5x
                                                        scrubHudIsForward = true
                                                        scrubHudSpeed = roundedSpeed
                                                        showScrubHud = true

                                                        exoPlayer.setPlaybackSpeed(roundedSpeed)
                                                        val maxDur = if (durationMs > 0) durationMs else 1L
                                                        if (!exoPlayer.isPlaying && exoPlayer.currentPosition < maxDur) {
                                                            exoPlayer.play()
                                                        }

                                                        currentPositionMs = exoPlayer.currentPosition
                                                        scrubHudTargetMs = exoPlayer.currentPosition
                                                        scrubHudDeltaSeconds = (exoPlayer.currentPosition - scrubStartMs) / 1000f
                                                    } else {
                                                        // Backward scrubbing: throttled seeks capped strictly at 7.5x
                                                        scrubHudIsForward = false
                                                        scrubHudSpeed = roundedSpeed
                                                        showScrubHud = true

                                                        if (exoPlayer.isPlaying) {
                                                            exoPlayer.pause()
                                                        }

                                                        val now = System.currentTimeMillis()
                                                        if (now - lastBackwardSeekTime >= 65L) {
                                                            val dtSec = if (lastBackwardSeekTime == 0L) 0.065f else ((now - lastBackwardSeekTime).coerceAtMost(100L) / 1000f)
                                                            val stepMs = (roundedSpeed * dtSec * 1000f).toLong().coerceAtLeast(10L)
                                                            val newPos = (exoPlayer.currentPosition - stepMs).coerceAtLeast(0L)

                                                            if (roundedSpeed > 2.5f) {
                                                                exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                                                            } else {
                                                                exoPlayer.setSeekParameters(SeekParameters.EXACT)
                                                            }
                                                            exoPlayer.seekTo(newPos)
                                                            lastBackwardSeekTime = now
                                                            currentPositionMs = newPos
                                                            scrubHudTargetMs = newPos
                                                            scrubHudDeltaSeconds = (newPos - scrubStartMs) / 1000f
                                                        }
                                                    }
                                                }
                                                delay(33) // ~30 fps updates
                                            }
                                        }
                                    }
                                }
                            }

                            MotionEvent.ACTION_UP -> {
                                if (isLiveScrubbing) {
                                    isLiveScrubbing = false
                                    scrubJob?.cancel()

                                    // Restore playback speed, exact seek parameters, and audio volume
                                    exoPlayer.setPlaybackSpeed(1.0f)
                                    exoPlayer.setSeekParameters(SeekParameters.EXACT)
                                    exoPlayer.volume = 1.0f

                                    if (wasPlayingBeforeScrub) {
                                        exoPlayer.play()
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
                                } else {
                                    // Check if this was a tap (small travel distance & short press duration)
                                    val moveDist = Math.hypot(
                                        (event.x - touchDownX).toDouble(),
                                        (event.y - touchDownY).toDouble()
                                    )
                                    val pressDuration = System.currentTimeMillis() - touchDownTime

                                    if (moveDist < 35 && pressDuration < 320) {
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
                                                    // Double Tap in Middle 1/3: Cycle Zoom / Crop Modes
                                                    pendingSingleTapJob?.cancel()
                                                    consecutiveTaps = 0
                                                    lastTapZone = TapZone.NONE
                                                    lastTapUpTime = 0L
                                                    cycleScaleMode()
                                                } else {
                                                    // Single Tap in Middle 1/3: Schedule controls toggle
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
                                                    // Double (or 3rd, 4th, etc.) Tap on Left 1/3: Jump back in 5s increments
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
                                                    // First tap on Left: schedule single tap toggle if no 2nd tap follows
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
                                                    // Double (or 3rd, 4th, etc.) Tap on Right 1/3: Jump forward in 5s increments
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
                                                    // First tap on Right: schedule single tap toggle if no 2nd tap follows
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
                            MotionEvent.ACTION_CANCEL -> {
                                if (isLiveScrubbing) {
                                    isLiveScrubbing = false
                                    scrubJob?.cancel()
                                    exoPlayer.setPlaybackSpeed(1.0f)
                                    exoPlayer.setSeekParameters(SeekParameters.EXACT)
                                    exoPlayer.volume = 1.0f
                                    if (wasPlayingBeforeScrub) {
                                        exoPlayer.play()
                                    } else {
                                        exoPlayer.pause()
                                    }
                                    showScrubHud = false
                                }
                                pendingSingleTapJob?.cancel()
                                consecutiveTaps = 0
                            }
                        }
                        gestureDetector.onTouchEvent(event)
                        true
                    }
                }
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

                    // Favorite button
                    IconButton(
                        onClick = { triggerFavoriteToggle() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (currentVideo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite in Photos",
                            tint = if (currentVideo.isFavorite) PixelHeartRed else Color.White
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
                }

                // Center Play/Pause & Skip Buttons
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    IconButton(
                        onClick = { skipToPrevious() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                isPlaying = false
                            } else {
                                exoPlayer.play()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier
                            .background(Color.White, CircleShape)
                            .size(68.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = { skipToNext() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
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
                    // Gesture hints pill
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress Slider
                    var isSeeking by remember { mutableStateOf(false) }
                    var seekProgress by remember { mutableFloatStateOf(0f) }

                    val progressFraction = if (durationMs > 0) {
                        if (isSeeking) seekProgress else (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    } else 0f

                    Slider(
                        value = progressFraction,
                        onValueChange = { fraction ->
                            isSeeking = true
                            seekProgress = fraction
                        },
                        onValueChangeFinished = {
                            val targetMs = (seekProgress * durationMs).toLong()
                            exoPlayer.seekTo(targetMs)
                            currentPositionMs = targetMs
                            isSeeking = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPositionMs),
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
