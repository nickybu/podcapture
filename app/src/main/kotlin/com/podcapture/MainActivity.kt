package com.podcapture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.podcapture.data.settings.SettingsDataStore
import com.podcapture.ui.navigation.DeepLinkEvents
import com.podcapture.ui.navigation.PodCaptureNavHost
import com.podcapture.ui.theme.PodCaptureTheme
import com.podcapture.ui.theme.ThemeColors
import com.podcapture.ui.theme.parseHexColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settingsDataStore: SettingsDataStore by inject()

    // Result is ignored: downloads work either way, the progress notification is just hidden.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionOnce()
        // On re-creation (rotation, process restore) the launch intent is delivered again,
        // but the request was already handled the first time.
        if (savedInstanceState == null) handleOpenNowPlaying(intent)

        val themeColorsFlow = themeColorsFlow()
        // Read once, synchronously, so the first frame already has the user's palette
        // instead of flashing ThemeColors.Default until DataStore emits. Three keys from a
        // small preferences file: about a millisecond, and DataStore caches it afterwards.
        val initialThemeColors = runBlocking { themeColorsFlow.first() }

        setContent {
            // Starts at the value read above, so the first emission is equal and doesn't
            // recompose; later emissions only arrive when the palette actually changes.
            val themeColors by themeColorsFlow.collectAsState(initial = initialThemeColors)

            PodCaptureTheme(themeColors = themeColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PodCaptureNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenNowPlaying(intent)
    }

    /** The media notification's tap intent asks for the player of whatever is loaded. */
    private fun handleOpenNowPlaying(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) != true) return
        // Consumed, so a later re-creation from this intent doesn't navigate again.
        intent.removeExtra(EXTRA_OPEN_NOW_PLAYING)
        DeepLinkEvents.request(DeepLinkEvents.DeepLink.OpenNowPlaying)
    }

    private fun themeColorsFlow(): Flow<ThemeColors> =
        combine(
            settingsDataStore.themeBackgroundColor,
            settingsDataStore.themeAccent1Color,
            settingsDataStore.themeAccent2Color
        ) { background, accent1, accent2 ->
            ThemeColors(
                backgroundColor = parseHexColor(background) ?: ThemeColors.Default.backgroundColor,
                accent1Color = parseHexColor(accent1) ?: ThemeColors.Default.accent1Color,
                accent2Color = parseHexColor(accent2) ?: ThemeColors.Default.accent2Color
            )
        }
            // Every settings write re-emits all three keys; only a palette change matters.
            .distinctUntilChanged()

    /**
     * Android 13+ hides the download progress notification until POST_NOTIFICATIONS is granted.
     * Ask once, ever; if the user declines they can enable it in system settings.
     */
    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return

        val prefs = getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return
        prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        /** Boolean extra on the media session's activity intent: open the current player. */
        const val EXTRA_OPEN_NOW_PLAYING = "com.podcapture.extra.OPEN_NOW_PLAYING"

        private const val PERMISSION_PREFS = "permission_prompts"
        private const val KEY_ASKED_NOTIFICATIONS = "asked_post_notifications"
    }
}
