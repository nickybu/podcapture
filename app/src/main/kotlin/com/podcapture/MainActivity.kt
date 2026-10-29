package com.podcapture

import android.Manifest
import android.content.Context
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.podcapture.data.settings.SettingsDataStore
import com.podcapture.ui.navigation.PodCaptureNavHost
import com.podcapture.ui.theme.PodCaptureTheme
import com.podcapture.ui.theme.ThemeColors
import com.podcapture.ui.theme.parseHexColor
import kotlinx.coroutines.flow.combine
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

        setContent {
            val themeColorsFlow = remember {
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
            }
            val themeColors by themeColorsFlow.collectAsState(initial = ThemeColors.Default)

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

    private companion object {
        const val PERMISSION_PREFS = "permission_prompts"
        const val KEY_ASKED_NOTIFICATIONS = "asked_post_notifications"
    }
}
