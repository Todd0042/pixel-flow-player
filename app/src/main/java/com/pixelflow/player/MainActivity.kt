package com.pixelflow.player

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.pixelflow.player.data.PlayOrder
import com.pixelflow.player.data.VideoFolder
import com.pixelflow.player.data.VideoItem
import com.pixelflow.player.data.VideoOrientation
import com.pixelflow.player.data.VideoRepository
import com.pixelflow.player.ui.components.OrientationDialog
import com.pixelflow.player.ui.components.PlayOrderDialog
import com.pixelflow.player.ui.screens.FolderPickerScreen
import com.pixelflow.player.ui.screens.PlayerScreen
import com.pixelflow.player.ui.theme.PixelFlowTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val repository = VideoRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PixelFlowTheme {
                MainApp(repository = repository)
            }
        }
    }
}

private enum class ScreenState {
    FOLDER_PICKER,
    VIDEO_PLAYER
}

@Composable
private fun MainApp(repository: VideoRepository) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(ScreenState.FOLDER_PICKER) }

    // Selected state
    var selectedFolder by remember { mutableStateOf<VideoFolder?>(null) }
    var selectedOrientation by remember { mutableStateOf<VideoOrientation?>(null) }
    var currentPlaylist by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoadingPlaylist by remember { mutableStateOf(false) }

    // Dialog state
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

    fun startPlaying(folder: VideoFolder, orientation: VideoOrientation, order: PlayOrder) {
        isLoadingPlaylist = true
        scope.launch {
            val list = repository.getVideosForFolder(context, folder, orientation, order)
            isLoadingPlaylist = false
            if (list.isEmpty()) {
                Toast.makeText(
                    context,
                    "No ${orientation.label.lowercase()} videos found in ${folder.name}",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                selectedFolder = folder
                selectedOrientation = orientation
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
                    folderForOrientationDialog = folder
                }
            )

            // Step 2: Ask Portrait or Landscape
            folderForOrientationDialog?.let { folder ->
                OrientationDialog(
                    folder = folder,
                    onOrientationSelected = { orientation ->
                        selectedFolder = folder
                        selectedOrientation = orientation
                        folderForOrientationDialog = null
                        showPlayOrderDialog = true
                    },
                    onDismiss = {
                        folderForOrientationDialog = null
                    }
                )
            }

            // Step 3 & 4: Ask Newest, Oldest, Longest, or Random
            if (showPlayOrderDialog && selectedFolder != null && selectedOrientation != null) {
                val folder = selectedFolder!!
                val orientation = selectedOrientation!!
                val count = if (orientation == VideoOrientation.PORTRAIT) {
                    folder.portraitCount
                } else {
                    folder.landscapeCount
                }

                PlayOrderDialog(
                    orientation = orientation,
                    videoCount = count,
                    onOrderSelected = { playOrder ->
                        showPlayOrderDialog = false
                        startPlaying(folder, orientation, playOrder)
                    },
                    onDismiss = {
                        showPlayOrderDialog = false
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
