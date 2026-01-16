package com.podcapture.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.podcapture.audio.AudioPlayerService
import com.podcapture.audio.PlayerState
import com.podcapture.capture.CaptureManager
import com.podcapture.data.repository.AudioFileRepository
import com.podcapture.data.settings.SettingsDataStore
import com.podcapture.ui.components.MiniPlayer
import com.podcapture.ui.home.HomeScreen
import com.podcapture.ui.player.PlayerScreen
import com.podcapture.ui.settings.SettingsScreen
import com.podcapture.ui.viewer.ViewerScreen
import org.koin.compose.koinInject

@Composable
fun PodCaptureNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val audioPlayerService: AudioPlayerService = koinInject()
    val audioFileRepository: AudioFileRepository = koinInject()
    val settingsDataStore: SettingsDataStore = koinInject()
    val captureManager: CaptureManager = koinInject()

    val playbackState by audioPlayerService.playbackState.collectAsState()
    val currentAudioFileId by audioPlayerService.currentAudioFileId.collectAsState()
    val skipIntervalSeconds by settingsDataStore.skipIntervalSeconds.collectAsState(initial = 10)
    val captureState by captureManager.captureState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Get current audio file name
    val currentAudioFile by remember(currentAudioFileId) {
        currentAudioFileId?.let { audioFileRepository.getFileById(it) }
            ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)

    // Determine if we should show mini player
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isOnPlayerScreen = currentRoute?.contains("Player") == true

    // Show mini player when audio is loaded and not on player screen
    val showMiniPlayer = currentAudioFileId != null &&
            !isOnPlayerScreen &&
            playbackState.playerState != PlayerState.IDLE

    // Handle capture messages from mini player
    LaunchedEffect(captureState.lastCaptureSuccess) {
        captureState.lastCaptureSuccess?.let { message ->
            snackbarHostState.showSnackbar(message)
            captureManager.clearMessages()
        }
    }

    LaunchedEffect(captureState.lastCaptureError) {
        captureState.lastCaptureError?.let { error ->
            snackbarHostState.showSnackbar(error)
            captureManager.clearMessages()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = NavRoute.Home,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showMiniPlayer) 72.dp else 0.dp)
        ) {
            composable<NavRoute.Home> {
                HomeScreen(
                    onNavigateToPlayer = { audioFileId ->
                        navController.navigate(NavRoute.Player(audioFileId))
                    },
                    onNavigateToSettings = {
                        navController.navigate(NavRoute.Settings)
                    }
                )
            }

            composable<NavRoute.Player> { backStackEntry ->
                val player = backStackEntry.toRoute<NavRoute.Player>()
                PlayerScreen(
                    audioFileId = player.audioFileId,
                    seekToMs = player.seekToMs,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToViewer = { audioFileId, captureId ->
                        navController.navigate(NavRoute.Viewer(audioFileId, captureId))
                    }
                )
            }

            composable<NavRoute.Viewer> { backStackEntry ->
                val viewer = backStackEntry.toRoute<NavRoute.Viewer>()
                ViewerScreen(
                    audioFileId = viewer.audioFileId,
                    scrollToCaptureId = viewer.scrollToCaptureId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToTimestamp = { audioFileId, timestampMs ->
                        // Pop back to player and navigate with seek position
                        navController.popBackStack()
                        navController.navigate(NavRoute.Player(audioFileId, seekToMs = timestampMs)) {
                            // Replace the current player instance
                            popUpTo(NavRoute.Home) { inclusive = false }
                        }
                    }
                )
            }

            composable<NavRoute.Settings> {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // Snackbar for capture messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showMiniPlayer) 120.dp else 16.dp)
        )

        // Mini player at bottom
        AnimatedVisibility(
            visible = showMiniPlayer,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            MiniPlayer(
                audioFileName = currentAudioFile?.name,
                playbackState = playbackState,
                skipIntervalSeconds = skipIntervalSeconds,
                isCapturing = captureState.isCapturing,
                onPlayPause = { audioPlayerService.togglePlayPause() },
                onRewind = { audioPlayerService.rewind(skipIntervalSeconds * 1000L) },
                onFastForward = { audioPlayerService.fastForward(skipIntervalSeconds * 1000L) },
                onCapture = { captureManager.capture() },
                onClick = {
                    currentAudioFileId?.let { id ->
                        navController.navigate(NavRoute.Player(id))
                    }
                }
            )
        }
    }
}
