package com.pixelflow.player

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.pixelflow.player.data.FavoriteFilter
import com.pixelflow.player.data.PlayOrder
import com.pixelflow.player.data.PlaybackPersistenceManager
import com.pixelflow.player.data.SavedPlaybackSession
import com.pixelflow.player.data.VideoFolder
import com.pixelflow.player.data.VideoItem
import com.pixelflow.player.data.VideoOrientation
import com.pixelflow.player.data.VideoRepository
import com.pixelflow.player.ui.components.FavoriteFilterDialog
import com.pixelflow.player.ui.components.OrientationDialog
import com.pixelflow.player.ui.components.PlayOrderDialog
import com.pixelflow.player.ui.components.ResumeSessionDialog
import com.pixelflow.player.ui.screens.FolderPickerScreen
import com.pixelflow.player.ui.screens.PlayerScreen
import com.pixelflow.player.ui.theme.PixelFlowTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val repository = VideoRepository()

    var isInPipMode by mutableStateOf(false)
        private set

    var onUserLeavePipTrigger: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PixelFlowTheme {
                MainApp(
                    repository = repository,
                    isInPipMode = isInPipMode
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        onUserLeavePipTrigger?.invoke()
    }
}

private enum class ScreenState {
    FOLDER_PICKER,
    VIDEO_PLAYER
}

@Composable
private fun MainApp(repository: VideoRepository, isInPipMode: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var currentScreen by rememberSaveable { mutableStateOf(ScreenState.FOLDER_PICKER) }

    // Selected state
    var selectedFolder by remember { mutableStateOf<VideoFolder?>(null) }
    var selectedFavoriteFilter by rememberSaveable { mutableStateOf(FavoriteFilter.ALL) }
    var selectedOrientation by rememberSaveable { mutableStateOf<VideoOrientation?>(null) }
    var selectedPlayOrder by rememberSaveable { mutableStateOf(PlayOrder.NEWEST) }
    var initialPlayerIndex by rememberSaveable { mutableIntStateOf(0) }
    var initialPlayerPositionMs by rememberSaveable { mutableLongStateOf(0L) }
    var currentPlaylist by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoadingPlaylist by remember { mutableStateOf(false) }

    // Resume session prompt
    var savedSessionToPrompt by remember { mutableStateOf<SavedPlaybackSession?>(null) }

    // Dialog flow state
    var folderForFilterDialog by remember { mutableStateOf<VideoFolder?>(null) }
    var folderForOrientationDialog by remember { mutableStateOf<VideoFolder?>(null) }
    var showPlayOrderDialog by remember { mutableStateOf(false) }

    // Permissions check
    val requiredPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            }
            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            requiredPermissions.any {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.any { it }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && currentScreen == ScreenState.FOLDER_PICKER) {
            val saved = PlaybackPersistenceManager.getSavedSession(context)
            if (saved != null && (System.currentTimeMillis() - saved.timestamp) < 48 * 3600 * 1000L) {
                savedSessionToPrompt = saved
            }
        }
    }

    fun startPlaying(
        folder: VideoFolder,
        favoriteFilter: FavoriteFilter,
        orientation: VideoOrientation,
        order: PlayOrder
    ) {
        isLoadingPlaylist = true
        scope.launch {
            val list = repository.getVideosForFolder(context, folder, favoriteFilter, orientation, order)
            isLoadingPlaylist = false
            if (list.isEmpty()) {
                Toast.makeText(
                    context,
                    "No ${orientation.label.lowercase()} videos found matching selection",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                selectedFolder = folder
                selectedFavoriteFilter = favoriteFilter
                selectedOrientation = orientation
                selectedPlayOrder = order
                initialPlayerIndex = 0
                initialPlayerPositionMs = 0L
                currentPlaylist = list
                currentScreen = ScreenState.VIDEO_PLAYER
            }
        }
    }

    when (currentScreen) {
        ScreenState.FOLDER_PICKER -> {
            FolderPickerScreen(
                repository = repository,
                hasStoragePermission = hasPermission,
                onRequestPermission = {
                    permissionLauncher.launch(requiredPermissions)
                },
                onFolderSelected = { folder ->
                    selectedFolder = folder
                    folderForFilterDialog = folder
                }
            )

            // Step 1: Ask All, Favorites Only, or Non-Favorites Only
            folderForFilterDialog?.let { folder ->
                FavoriteFilterDialog(
                    folder = folder,
                    onFilterSelected = { filter ->
                        selectedFavoriteFilter = filter
                        folderForFilterDialog = null
                        folderForOrientationDialog = folder
                    },
                    onDismiss = {
                        folderForFilterDialog = null
                    }
                )
            }

            // Step 2: Ask Portrait or Landscape
            folderForOrientationDialog?.let { folder ->
                val effectivePortrait = when (selectedFavoriteFilter) {
                    FavoriteFilter.ALL -> folder.portraitCount
                    FavoriteFilter.FAVORITES_ONLY -> folder.favoritePortraitCount
                    FavoriteFilter.NON_FAVORITES_ONLY -> (folder.portraitCount - folder.favoritePortraitCount).coerceAtLeast(0)
                    FavoriteFilter.NON_FAVORITES_UNVIEWED -> folder.unviewedPortraitCount
                }
                val effectiveLandscape = when (selectedFavoriteFilter) {
                    FavoriteFilter.ALL -> folder.landscapeCount
                    FavoriteFilter.FAVORITES_ONLY -> folder.favoriteLandscapeCount
                    FavoriteFilter.NON_FAVORITES_ONLY -> (folder.landscapeCount - folder.favoriteLandscapeCount).coerceAtLeast(0)
                    FavoriteFilter.NON_FAVORITES_UNVIEWED -> folder.unviewedLandscapeCount
                }
                val displayFolder = folder.copy(
                    portraitCount = effectivePortrait,
                    landscapeCount = effectiveLandscape,
                    totalCount = effectivePortrait + effectiveLandscape
                )

                OrientationDialog(
                    folder = displayFolder,
                    onOrientationSelected = { orientation ->
                        selectedOrientation = orientation
                        folderForOrientationDialog = null
                        showPlayOrderDialog = true
                    },
                    onDismiss = {
                        folderForOrientationDialog = null
                    }
                )
            }

            // Step 3: Ask Newest, Oldest, Longest, or Random
            if (showPlayOrderDialog && selectedFolder != null && selectedOrientation != null) {
                val folder = selectedFolder!!
                val orientation = selectedOrientation!!
                val count = when (orientation) {
                    VideoOrientation.PORTRAIT -> when (selectedFavoriteFilter) {
                        FavoriteFilter.ALL -> folder.portraitCount
                        FavoriteFilter.FAVORITES_ONLY -> folder.favoritePortraitCount
                        FavoriteFilter.NON_FAVORITES_ONLY -> (folder.portraitCount - folder.favoritePortraitCount).coerceAtLeast(0)
                        FavoriteFilter.NON_FAVORITES_UNVIEWED -> folder.unviewedPortraitCount
                    }
                    VideoOrientation.LANDSCAPE -> when (selectedFavoriteFilter) {
                        FavoriteFilter.ALL -> folder.landscapeCount
                        FavoriteFilter.FAVORITES_ONLY -> folder.favoriteLandscapeCount
                        FavoriteFilter.NON_FAVORITES_ONLY -> (folder.landscapeCount - folder.favoriteLandscapeCount).coerceAtLeast(0)
                        FavoriteFilter.NON_FAVORITES_UNVIEWED -> folder.unviewedLandscapeCount
                    }
                }

                PlayOrderDialog(
                    orientation = orientation,
                    videoCount = count,
                    onOrderSelected = { playOrder ->
                        showPlayOrderDialog = false
                        startPlaying(folder, selectedFavoriteFilter, orientation, playOrder)
                    },
                    onDismiss = {
                        showPlayOrderDialog = false
                    }
                )
            }

            // Prompt user if there is a saved playback session to resume
            savedSessionToPrompt?.let { session ->
                ResumeSessionDialog(
                    session = session,
                    onResume = {
                        savedSessionToPrompt = null
                        isLoadingPlaylist = true
                        scope.launch {
                            val folder = VideoFolder(
                                bucketId = session.bucketId,
                                name = session.folderName,
                                coverUri = null,
                                totalCount = session.playlistVideoIds.size,
                                portraitCount = if (session.orientation == VideoOrientation.PORTRAIT) session.playlistVideoIds.size else 0,
                                landscapeCount = if (session.orientation == VideoOrientation.LANDSCAPE) session.playlistVideoIds.size else 0,
                                isFavoritesAlbum = session.isFavoritesAlbum,
                                isAllVideosAlbum = session.isAllVideosAlbum,
                                isCustomTree = session.isCustomTree,
                                treeUri = session.treeUri
                            )
                            val list = repository.getVideosForFolder(
                                context,
                                folder,
                                session.favoriteFilter,
                                session.orientation,
                                session.playOrder
                            )
                            isLoadingPlaylist = false
                            if (list.isNotEmpty()) {
                                var targetIdx = list.indexOfFirst { it.id == session.currentVideoId }
                                if (targetIdx < 0) targetIdx = 0
                                selectedFolder = folder
                                selectedFavoriteFilter = session.favoriteFilter
                                selectedOrientation = session.orientation
                                selectedPlayOrder = session.playOrder
                                initialPlayerIndex = targetIdx
                                initialPlayerPositionMs = session.currentPositionMs
                                currentPlaylist = list
                                currentScreen = ScreenState.VIDEO_PLAYER
                            } else {
                                Toast.makeText(context, "Saved session videos not found", Toast.LENGTH_SHORT).show()
                                PlaybackPersistenceManager.clearSession(context)
                            }
                        }
                    },
                    onDismiss = {
                        savedSessionToPrompt = null
                        PlaybackPersistenceManager.clearSession(context)
                    }
                )
            }

            if (isLoadingPlaylist) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        ScreenState.VIDEO_PLAYER -> {
            if (selectedFolder != null && selectedOrientation != null && currentPlaylist.isNotEmpty()) {
                PlayerScreen(
                    folder = selectedFolder!!,
                    videos = currentPlaylist,
                    orientation = selectedOrientation!!,
                    favoriteFilter = selectedFavoriteFilter,
                    playOrder = selectedPlayOrder,
                    initialIndex = initialPlayerIndex,
                    initialPositionMs = initialPlayerPositionMs,
                    isInPipMode = isInPipMode,
                    onExitPlayer = {
                        currentScreen = ScreenState.FOLDER_PICKER
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    currentScreen = ScreenState.FOLDER_PICKER
                }
            }
        }
    }
}
