package com.pixelflow.player.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.GestureDetector
import android.view.MotionEvent
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.pixelflow.player.data.FavoritesHelper
import com.pixelflow.player.data.VideoFolder
import com.pixelflow.player.data.VideoItem
import com.pixelflow.player.data.VideoOrientation
import com.pixelflow.player.data.VideoScaleMode
import com.pixelflow.player.ui.components.FavoriteHeartHud
import com.pixelflow.player.ui.components.ScalingHud
import com.pixelflow.player.ui.components.SwipeDirection
import com.pixelflow.player.ui.components.SwipeIndicatorHud
import com.pixelflow.player.ui.theme.PixelHeartRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    val currentVideo = videos[currentIndex]

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
            // Fallback for non-MediaStore URIs or direct toggle
            currentVideo.isFavorite = !currentVideo.isFavorite
            lastFavoriteStatus = currentVideo.isFavorite
            showFavoriteHud = true
            scope.launch {
                delay(1600)
                showFavoriteHud = false
            }
        }
    }

    // Initialize ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    fun playVideoAtIndex(newIndex: Int, direction: SwipeDirection? = null) {
        val boundedIndex = when {
            newIndex < 0 -> videos.size - 1
            newIndex >= videos.size -> 0
            else -> newIndex
        }

        currentIndex = boundedIndex
        val nextVideo = videos[boundedIndex]
        currentPositionMs = 0L
        durationMs = nextVideo.durationMs

        // Check if favorite in MediaStore
        nextVideo.isFavorite = FavoritesHelper.isFavorite(context, nextVideo)

        val mediaItem = MediaItem.fromUri(nextVideo.uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
        isPlaying = true

        if (direction != null) {
            lastSwipeDirection = direction
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
            // Lock screen orientation to requested user selection
            activity.requestedOrientation = when (orientation) {
                VideoOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                VideoOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            // Cutout mode for complete edge-to-edge past the notch/camera
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

        // Start playing first video
        playVideoAtIndex(0)

        // ExoPlayer listener for auto-advance and state changes
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // Auto advance to next video
                    playVideoAtIndex(currentIndex + 1, SwipeDirection.NEXT)
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

            // Restore standard window configuration on exit
            if (window != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Coroutine to poll playback progress
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

                    val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDown(e: MotionEvent): Boolean = true

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            isControlsVisible = !isControlsVisible
                            return true
                        }

                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            cycleScaleMode()
                            return true
                        }

                        override fun onLongPress(e: MotionEvent) {
                            triggerFavoriteToggle()
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

                            val isPortrait = orientation == VideoOrientation.PORTRAIT

                            if (isPortrait) {
                                // Portrait: Swipe Up/Down
                                if (abs(deltaY) > abs(deltaX) && abs(deltaY) > 80) {
                                    if (deltaY < 0) {
                                        // Swipe UP -> Next video
                                        playVideoAtIndex(currentIndex + 1, SwipeDirection.NEXT)
                                    } else {
                                        // Swipe DOWN -> Previous video
                                        playVideoAtIndex(currentIndex - 1, SwipeDirection.PREVIOUS)
                                    }
                                    return true
                                }
                            } else {
                                // Landscape: Swipe Left/Right
                                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 80) {
                                    if (deltaX < 0) {
                                        // Swipe LEFT -> Next video
                                        playVideoAtIndex(currentIndex + 1, SwipeDirection.NEXT)
                                    } else {
                                        // Swipe RIGHT -> Previous video
                                        playVideoAtIndex(currentIndex - 1, SwipeDirection.PREVIOUS)
                                    }
                                    return true
                                }
                            }
                            return false
                        }
                    })

                    setOnTouchListener { _, event ->
                        gestureDetector.onTouchEvent(event)
                        true
                    }
                }
            }
        )

        // Scaling HUD (on Double Tap)
        ScalingHud(
            visible = showScaleHud,
            scaleMode = scaleMode,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        )

        // Favorite HUD (on Long Press)
        FavoriteHeartHud(
            visible = showFavoriteHud,
            isFavorite = lastFavoriteStatus,
            modifier = Modifier.align(Alignment.Center)
        )

        // Swipe Action HUD (on Swipe Up/Down or Left/Right)
        SwipeIndicatorHud(
            visible = showSwipeHud,
            direction = lastSwipeDirection,
            isPortrait = orientation == VideoOrientation.PORTRAIT,
            title = currentVideo.title,
            currentIndex = currentIndex,
            totalCount = videos.size,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        )

        // Unobtrusive Controls Overlay (toggled via Single Tap)
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
                        onClick = { playVideoAtIndex(currentIndex - 1, SwipeDirection.PREVIOUS) },
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
                        onClick = { playVideoAtIndex(currentIndex + 1, SwipeDirection.NEXT) },
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
                            text = if (orientation == VideoOrientation.PORTRAIT) {
                                "Swipe Up/Down: Skip • Double-Tap: Scale • Hold: Favorite"
                            } else {
                                "Swipe Left/Right: Skip • Double-Tap: Scale • Hold: Favorite"
                            },
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
