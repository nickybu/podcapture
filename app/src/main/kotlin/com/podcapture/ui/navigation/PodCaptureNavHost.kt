package com.podcapture.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
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
import com.podcapture.ui.captures.CapturesScreen
import com.podcapture.ui.components.MiniPlayer
import com.podcapture.ui.home.HomeScreen
import com.podcapture.ui.player.EpisodePlayerScreen
import com.podcapture.ui.player.PlayerScreen
import com.podcapture.ui.search.PodcastDetailScreen
import com.podcapture.ui.search.PodcastSearchScreen
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
    val isOnPlayerScreen = navBackStackEntry?.destination?.let {
        it.hasRoute<NavRoute.Player>() || it.hasRoute<NavRoute.EpisodePlayer>()
    } == true

    // Show mini player when audio is loaded and not on player screen
    val showMiniPlayer = currentAudioFileId != null &&
            !isOnPlayerScreen &&
            playbackState.playerState != PlayerState.IDLE

    // Measured height of the mini player, including the navigation bar inset it pads itself
    // by, so content and the snackbar clear it whatever the navigation mode.
    var miniPlayerHeightPx by remember { mutableIntStateOf(0) }
    val miniPlayerHeight = with(LocalDensity.current) {
        if (showMiniPlayer) miniPlayerHeightPx.toDp() else 0.dp
    }

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
                .padding(bottom = miniPlayerHeight)
                // The mini player already covers the navigation bar; stop screens' Scaffolds
                // from adding that inset again above it.
                .consumeWindowInsets(PaddingValues(bottom = miniPlayerHeight))
        ) {
            composable<NavRoute.Home> {
                HomeScreen(
                    onNavigateToPlayer = { audioFileId ->
                        navController.navigate(NavRoute.Player(audioFileId))
                    },
                    onNavigateToSettings = {
                        navController.navigate(NavRoute.Settings)
                    },
                    onNavigateToPodcastSearch = {
                        navController.navigate(NavRoute.PodcastSearch)
                    },
                    onNavigateToPodcastDetail = { podcastId ->
                        navController.navigate(NavRoute.PodcastDetail(podcastId))
                    },
                    onNavigateToEpisodePlayer = { episodeId, podcastId ->
                        navController.navigate(NavRoute.EpisodePlayer(episodeId, podcastId))
                    },
                    onNavigateToCaptures = {
                        navController.navigate(NavRoute.Captures)
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

                        // Check if this is an episode capture
                        if (audioFileId.startsWith("episode_")) {
                            // Extract episodeId from "episode_{id}" format
                            val episodeId = audioFileId.removePrefix("episode_").toLongOrNull()
                            if (episodeId != null) {
                                // Navigate to episode player with seek position
                                // Note: podcastId will be fetched by the ViewModel
                                navController.navigate(NavRoute.EpisodePlayer(episodeId, 0L, seekToMs = timestampMs)) {
                                    popUpTo(NavRoute.Home) { inclusive = false }
                                }
                            }
                        } else {
                            navController.navigate(NavRoute.Player(audioFileId, seekToMs = timestampMs)) {
                                popUpTo(NavRoute.Home) { inclusive = false }
                            }
                        }
                    }
                )
            }

            composable<NavRoute.Settings> {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<NavRoute.PodcastSearch> {
                PodcastSearchScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPodcastSelected = { podcastId ->
                        navController.navigate(NavRoute.PodcastDetail(podcastId))
                    },
                    onEpisodeSelected = { episodeId, podcastId ->
                        navController.navigate(NavRoute.EpisodePlayer(episodeId, podcastId))
                    }
                )
            }

            composable<NavRoute.PodcastDetail> { backStackEntry ->
                val detail = backStackEntry.toRoute<NavRoute.PodcastDetail>()
                PodcastDetailScreen(
                    podcastId = detail.podcastId,
                    onNavigateBack = { navController.popBackStack() },
                    onEpisodeSelected = { episode ->
                        navController.navigate(NavRoute.EpisodePlayer(episode.id, episode.podcastId))
                    }
                )
            }

            composable<NavRoute.EpisodePlayer> { backStackEntry ->
                val player = backStackEntry.toRoute<NavRoute.EpisodePlayer>()
                EpisodePlayerScreen(
                    episodeId = player.episodeId,
                    podcastId = player.podcastId,
                    seekToMs = player.seekToMs,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToViewer = { audioFileId, captureId ->
                        navController.navigate(NavRoute.Viewer(audioFileId, captureId))
                    }
                )
            }

            composable<NavRoute.Captures> {
                CapturesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenCapture = { audioFileId, captureId ->
                        navController.navigate(NavRoute.Viewer(audioFileId, scrollToCaptureId = captureId))
                    }
                )
            }
        }

        // Snackbar for capture messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(
                    if (showMiniPlayer) {
                        Modifier.padding(bottom = miniPlayerHeight + 8.dp)
                    } else {
                        Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                    }
                )
        )

        // Mini player at bottom
        AnimatedVisibility(
            visible = showMiniPlayer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { miniPlayerHeightPx = it.height },
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
                        // Navigate to the appropriate player based on ID type
                        if (id.startsWith("episode_")) {
                            val episodeId = id.removePrefix("episode_").toLongOrNull()
                            if (episodeId != null) {
                                navController.openPlayer(NavRoute.EpisodePlayer(episodeId, 0L))
                            }
                        } else {
                            navController.openPlayer(NavRoute.Player(id))
                        }
                    }
                }
            )
        }
    }
}

/**
 * Opens the player for the mini player's track without stacking a second copy.
 *
 * The mini player is visible on the Viewer, which sits above the player it was opened from,
 * so first pop back to the nearest player of the same kind; if that is the track that is
 * playing, we're done. Otherwise push it (single-top, so a player left on top is replaced).
 * Unlike popping to Home, this keeps Podcast detail › Player › Back returning to the detail.
 */
private fun NavHostController.openPlayer(route: NavRoute) {
    val isOpen = when (route) {
        is NavRoute.EpisodePlayer ->
            popBackStack<NavRoute.EpisodePlayer>(inclusive = false) &&
                currentBackStackEntry?.toRoute<NavRoute.EpisodePlayer>()?.episodeId == route.episodeId
        is NavRoute.Player ->
            popBackStack<NavRoute.Player>(inclusive = false) &&
                currentBackStackEntry?.toRoute<NavRoute.Player>()?.audioFileId == route.audioFileId
        else -> false
    }
    if (!isOpen) {
        navigate(route) { launchSingleTop = true }
    }
}
