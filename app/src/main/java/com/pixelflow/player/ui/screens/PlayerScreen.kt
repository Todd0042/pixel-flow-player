package com.pixelflow.player.ui.screens

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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

    // HUD States
    var showScaleHud by remember { mutableStateOf(false) }
    var showFavoriteHud by remember { mutableStateOf(false) }
    var lastFavoriteStatus by remember { mutableStateOf(currentVideo.isFavorite) }
    var showSwipeHud by remember { mutableStateOf(false) }
    var lastSwipeDirection by remember { mutableStateOf(SwipeDirection.NEXT) }

    // Scrubbing HUD State (Horizontal drag)
    var showScrubHud by remember { mutableStateOf(false) }
    var scrubHudDeltaSeconds by remember { mutableFloatStateOf(0f) }
    var scrubHudTargetMs by remember { mutableLongStateOf(0L) }

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

    BackHandler {
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

                    var isLiveScrubbing = false
                    var scrubBaseMs = 0L
                    var targetSeekMs = 0L

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
                            pendingSingleTapJob?.cancel()
                            consecutiveTaps = 0
                            triggerFavoriteToggle()
                        }

                        override fun onScroll(
                            e1: MotionEvent?,
                            e2: MotionEvent,
                            distanceX: Float,
                            distanceY: Float
                        ): Boolean {
                            if (e1 == null) return false
                            val totalDeltaX = e2.x - e1.x
                            val totalDeltaY = e2.y - e1.y

                            // If finger dragged, cancel single/double tap detection
                            if (abs(totalDeltaX) > 25 || abs(totalDeltaY) > 25) {
                                pendingSingleTapJob?.cancel()
                                consecutiveTaps = 0
                            }

                            // Horizontal Drag: Fine-tuned scrubbing (0.1 to 5.0 seconds)
                            if (abs(totalDeltaX) > abs(totalDeltaY) && abs(totalDeltaX) > 25) {
                                isLiveScrubbing = true
                                val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                                val fraction = (totalDeltaX / (screenWidth * 0.45f)).coerceIn(-1.0f, 1.0f)
                                val rawSeconds = fraction * 5.0f
                                val roundedSec = (round(rawSeconds * 10f) / 10f)

                                val maxDur = if (durationMs > 0) durationMs else 1L
                                targetSeekMs = (scrubBaseMs + (roundedSec * 1000).toLong()).coerceIn(0L, maxDur)

                                scrubHudDeltaSeconds = roundedSec
                                scrubHudTargetMs = targetSeekMs
                                showScrubHud = true
                                return true
                            }
                            return false
                        }

                        override fun onFling(
                            e1: MotionEvent?,
                            e2: MotionEvent,
                            velocityX: Float,
                            velocityY: Float
                        ): Boolean {
                            if (e1 == null) return false
                            val deltaX = e2.x - e1.x
                            val deltaY = e2.y - e1.y

                            pendingSingleTapJob?.cancel()
                            consecutiveTaps = 0

                            // Swiping UP and DOWN is ALWAYS Previous and Next respectively
                            if (abs(velocityY) > abs(velocityX) && abs(deltaY) > 80) {
                                isLiveScrubbing = false
                                showScrubHud = false
                                if (deltaY < 0) {
                                    // Swipe UP -> PREVIOUS video
                                    skipToPrevious()
                                } else {
                                    // Swipe DOWN -> NEXT video
                                    skipToNext()
                                }
                                return true
                            }

                            // Quick horizontal fling when not actively dragging: quick scrub jump (0.5 to 5.0s)
                            if (abs(velocityX) > abs(velocityY) && abs(deltaX) > 80 && !isLiveScrubbing) {
                                val jumpSec = if (deltaX > 0) {
                                    (abs(velocityX) / 1000f).coerceIn(0.5f, 5.0f)
                                } else {
                                    -(abs(velocityX) / 1000f).coerceIn(0.5f, 5.0f)
                                }
                                val fineJumpSec = (round(jumpSec * 10f) / 10f)
                                val maxDur = if (durationMs > 0) durationMs else 1L
                                val target = (exoPlayer.currentPosition + (fineJumpSec * 1000).toLong()).coerceIn(0L, maxDur)

                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                                vibrate(30L)

                                scrubHudDeltaSeconds = fineJumpSec
                                scrubHudTargetMs = target
                                showScrubHud = true
                                scope.launch {
                                    delay(900)
                                    showScrubHud = false
                                }
                                return true
                            }

                            return false
                        }
                    })

                    setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                touchDownX = event.x
                                touchDownY = event.y
                                touchDownTime = System.currentTimeMillis()
                                scrubBaseMs = exoPlayer.currentPosition
                                isLiveScrubbing = false
                            }
                            MotionEvent.ACTION_UP -> {
                                if (isLiveScrubbing) {
                                    // Commit the fine-tuned scrub position
                                    exoPlayer.seekTo(targetSeekMs)
                                    currentPositionMs = targetSeekMs
                                    vibrate(30L)
                                    scope.launch {
                                        delay(750)
                                        showScrubHud = false
                                    }
                                    isLiveScrubbing = false
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
                                isLiveScrubbing = false
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
            visible = showScaleHud,
            scaleMode = scaleMode,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        )

        // Double-Tap Jump HUD (Left 1/3: -5s/tap, Right 1/3: +5s/tap)
        DoubleTapSeekHud(
            visible = showDoubleTapSeekHud,
            isForward = isDoubleTapForward,
            totalSeconds = doubleTapTotalSeconds,
            modifier = Modifier
                .align(if (isDoubleTapForward) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 48.dp)
        )

        // Scrubbing HUD (on Left/Right drag)
        ScrubbingHud(
            visible = showScrubHud,
            deltaSeconds = scrubHudDeltaSeconds,
            targetPositionMs = scrubHudTargetMs,
            durationMs = durationMs,
            modifier = Modifier.align(Alignment.Center)
        )

        // Favorite HUD (on Long Press)
        FavoriteHeartHud(
            visible = showFavoriteHud,
            isFavorite = lastFavoriteStatus,
            modifier = Modifier.align(Alignment.Center)
        )

        // Swipe Action HUD (on Swipe Up/Down: Previous / Next)
        SwipeIndicatorHud(
            visible = showSwipeHud,
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
            visible = isControlsVisible,
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
