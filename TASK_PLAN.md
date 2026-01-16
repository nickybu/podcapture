# PodCapture Task Plan

## Overview
This document outlines the implementation plan for new features and bug fixes.

---

## Bug Fix: Preserve Playhead Position When Re-opening Player

**Problem:** When leaving the Player screen and re-opening, the playhead resets to 0 instead of maintaining the saved position.

**Root Cause:** The `PlayerViewModel.loadAudioFile()` only seeks to `lastPositionMs` on first load, but when navigating away and back, a new ViewModel instance may be created, or the audio isn't properly reloaded from the saved position.

**Files to modify:**
- `app/src/main/kotlin/com/podcapture/ui/player/PlayerViewModel.kt`

**Implementation:**
1. Check if AudioPlayerService already has the same audio loaded (by comparing URI)
2. If same audio, don't reload - just sync state
3. If different audio or not loaded, load and seek to saved position
4. Ensure position is saved before navigating away (already implemented in `onCleared`)

---

## Feature 1: Background Audio Playback

**Description:** Allow audio to continue playing when the app is in the background.

**Files to create:**
- `app/src/main/kotlin/com/podcapture/audio/PlaybackService.kt` - Foreground service

**Files to modify:**
- `app/src/main/AndroidManifest.xml` - Register service
- `app/src/main/kotlin/com/podcapture/audio/AudioPlayerService.kt` - Integrate with foreground service
- `app/src/main/kotlin/com/podcapture/di/AppModule.kt` - Update DI if needed

**Implementation:**
1. Create `PlaybackService` extending `MediaSessionService`
2. Move ExoPlayer initialization to the service
3. Configure MediaSession for lock screen/notification controls
4. Create notification channel for playback notification
5. Show persistent notification with play/pause controls
6. Handle audio focus changes
7. Update `AudioPlayerService` to bind to the foreground service

**Manifest additions:**
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" /> <!-- Already present -->
<service
    android:name=".audio.PlaybackService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="false">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

---

## Feature 2: Rewind/FastForward Interval Setting

**Description:** Add setting for rewind/fastforward amount (5-30s in 5s intervals).

**Files to modify:**
- `app/src/main/kotlin/com/podcapture/data/settings/SettingsDataStore.kt` - Add new preference
- `app/src/main/kotlin/com/podcapture/ui/settings/SettingsScreen.kt` - Add UI for setting
- `app/src/main/kotlin/com/podcapture/ui/settings/SettingsViewModel.kt` - Add state and setter
- `app/src/main/kotlin/com/podcapture/ui/player/PlayerViewModel.kt` - Use setting value
- `app/src/main/kotlin/com/podcapture/ui/player/PlayerScreen.kt` - Display current interval

**Implementation:**
1. Add `SKIP_INTERVAL_SECONDS` preference key to SettingsDataStore (default: 10)
2. Add flow and setter method
3. Update SettingsViewModel to expose skip interval
4. Add radio button group in SettingsScreen for 5, 10, 15, 20, 25, 30 seconds
5. Update PlayerViewModel to observe skip interval setting
6. Pass interval to `onRewind()` and `onFastForward()` calls
7. Update UI to show current interval (e.g., "10s" label on buttons)

---

## Feature 3: Support for m4a and Other Audio Formats

**Description:** Add support for m4a, flac, ogg, and other common audio formats.

**Files to modify:**
- `app/src/main/kotlin/com/podcapture/ui/home/HomeScreen.kt` - Update file picker MIME types
- `app/src/main/kotlin/com/podcapture/data/model/AudioFile.kt` - Update format field documentation

**Implementation:**
1. Update file picker MIME types array:
```kotlin
arrayOf(
    "audio/*"  // Accept all audio formats (ExoPlayer handles most)
)
```
   Or be more specific:
```kotlin
arrayOf(
    "audio/mpeg",           // MP3
    "audio/wav",            // WAV
    "audio/x-wav",          // WAV alternate
    "audio/mp4",            // M4A
    "audio/x-m4a",          // M4A alternate
    "audio/aac",            // AAC
    "audio/flac",           // FLAC
    "audio/ogg",            // OGG Vorbis
    "audio/opus"            // Opus
)
```
2. ExoPlayer already supports these formats natively, no decoder changes needed

---

## Feature 4: Capture Popup When Playhead Reaches Capture Window

**Description:** Show a small popup when the playhead enters a capture's time window.

**Files to modify:**
- `app/src/main/kotlin/com/podcapture/ui/player/PlayerViewModel.kt` - Track active capture
- `app/src/main/kotlin/com/podcapture/ui/player/PlayerScreen.kt` - Show popup UI

**Implementation:**
1. Add `activeCapture: Capture?` to `PlayerUiState`
2. Add `lastShownCaptureId: String?` to track which capture was just shown (prevent re-showing)
3. In position update flow, check if current position falls within any capture's window
4. When entering a capture window (and it wasn't just shown), set `activeCapture`
5. Auto-dismiss after 5 seconds or when user taps dismiss
6. Create `CapturePopup` composable:
   - Small card at bottom of screen
   - Shows "Capture #N" with timestamp
   - Tap to navigate to Viewer at that capture
   - Dismiss button (X)

**UI Design:**
```
┌─────────────────────────────────────┐
│ 📌 Capture 3 at 15:42              │
│ "The transcribed text preview..."   │
│                          [View] [X] │
└─────────────────────────────────────┘
```

---

## Feature 5: Show First and Last Listened Timestamps

**Description:** Display when an audio file was first and last listened to.

**Files to modify:**
- `app/src/main/kotlin/com/podcapture/data/model/AudioFile.kt` - Add `firstPlayedAt` field
- `app/src/main/kotlin/com/podcapture/data/dao/AudioFileDao.kt` - Update queries if needed
- `app/src/main/kotlin/com/podcapture/data/repository/AudioFileRepository.kt` - Update play tracking
- `app/src/main/kotlin/com/podcapture/ui/home/HomeScreen.kt` - Update UI to show both timestamps

**Implementation:**
1. Add migration to add `firstPlayedAt` column to audio_files table
2. Update `AudioFile` entity:
```kotlin
val firstPlayedAt: Long? = null,  // Epoch millis, set once on first play
val lastPlayedAt: Long? = null,   // Already exists - updated each play
```
3. Update repository to set `firstPlayedAt` only if null when updating lastPlayedAt
4. Update HomeScreen card to show:
   - "First: Jan 15" (if played)
   - "Last: 2h ago" (relative time)

---

## Feature 6: Replace Drag Speed Control with +/- Buttons

**Description:** Replace the draggable speed control with simple increment/decrement buttons.

**Files to modify:**
- `app/src/main/kotlin/com/podcapture/ui/player/PlayerScreen.kt` - Replace component

**Files to delete (or keep for reference):**
- `app/src/main/kotlin/com/podcapture/ui/components/DraggableSpeedControl.kt` - No longer needed

**Implementation:**
1. Create new `SpeedControl` composable in PlayerScreen:
```kotlin
@Composable
private fun SpeedControl(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = {
                val newSpeed = (currentSpeed - 0.1f).coerceAtLeast(0.5f)
                onSpeedChange(newSpeed)
            }
        ) {
            Icon(Icons.Default.Remove, "Decrease speed")
        }

        Text(
            text = String.format("%.1fx", currentSpeed),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center
        )

        IconButton(
            onClick = {
                val newSpeed = (currentSpeed + 0.1f).coerceAtMost(2.0f)
                onSpeedChange(newSpeed)
            }
        ) {
            Icon(Icons.Default.Add, "Increase speed")
        }
    }
}
```
2. Replace `DraggableSpeedControl` usage with new `SpeedControl`
3. Remove "Speed (drag to adjust)" label

---

## Implementation Order (Recommended)

1. **Bug Fix: Playhead Position** - Quick fix, improves UX immediately
2. **Feature 6: Speed Control Buttons** - Simple UI change
3. **Feature 3: Audio Formats** - One-line change
4. **Feature 2: Skip Interval Setting** - Builds on existing settings pattern
5. **Feature 5: Listen Timestamps** - Database migration + UI
6. **Feature 4: Capture Popup** - Medium complexity, good UX improvement
7. **Feature 1: Background Playback** - Most complex, requires foreground service

---

## Database Migration Required

For Feature 5, a Room migration is needed:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE audio_files ADD COLUMN firstPlayedAt INTEGER DEFAULT NULL"
        )
    }
}
```

Update `PodCaptureDatabase`:
```kotlin
@Database(
    entities = [AudioFile::class, Capture::class],
    version = 2,  // Increment version
    exportSchema = false
)
abstract class PodCaptureDatabase : RoomDatabase() {
    // ... add migration to builder
}
```
