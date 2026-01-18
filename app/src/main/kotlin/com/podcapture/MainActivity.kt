package com.podcapture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.podcapture.data.settings.SettingsDataStore
import com.podcapture.ui.navigation.PodCaptureNavHost
import com.podcapture.ui.theme.PodCaptureTheme
import com.podcapture.ui.theme.ThemeColors
import com.podcapture.ui.theme.parseHexColor
import kotlinx.coroutines.flow.combine
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settingsDataStore: SettingsDataStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
}
