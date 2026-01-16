# Changelog

All notable changes to PodCapture are documented in this file.

## [Unreleased] - 2026-01-16

### New Features

#### Mini Player
- Persistent mini player appears at the bottom of the screen when navigating away from the Player
- Shows current track name, position, and playback controls
- Play/pause, rewind, and fast-forward buttons accessible from any screen
- Tap anywhere on the mini player to return to the full Player screen
- Progress bar shows current playback position
- Animated slide-in/out transitions

#### Waveform Timeline
- Visual waveform representation replaces the simple slider
- Unique waveform pattern generated for each audio file
- Capture markers displayed as dots with lines on the waveform
- Playhead indicator shows current position
- Tap anywhere on the waveform to seek to that position

#### Capture Notes
- Add personal notes to any capture
- Notes displayed before the transcription in the captures list
- Edit button (pen icon) on each capture card to add/edit notes
- Notes included in the exported markdown file under a "Notes" section
- Database migration adds notes column to captures table

#### Background Audio Playback
- Audio now continues playing when the app is in the background or the screen is off
- Lock screen and notification controls for play/pause, skip forward/backward
- Playback automatically pauses when headphones are disconnected
- Implemented using Media3's MediaSessionService for proper Android media integration

#### Configurable Skip Interval
- New setting to customize rewind/fast-forward interval
- Choose from 5, 10, 15, 20, 25, or 30 seconds
- Skip buttons now display the current interval (e.g., "15s" instead of hardcoded "10s")
- Located in Settings under "Playback" section

#### Expanded Audio Format Support
- Added support for M4A (AAC), FLAC, OGG Vorbis, Opus, and other common audio formats
- File picker now accepts all audio MIME types
- ExoPlayer handles decoding natively - no additional configuration needed

#### First & Last Listened Timestamps
- Audio files now display when they were first listened to (e.g., "First: Jan 15")
- Last listened time shown as relative time (e.g., "Last: 2h ago")
- Timestamps displayed on audio file cards in the home screen
- Database migration automatically adds the new field

#### Capture Popup Notification
- When the playhead enters a capture's time window, a popup appears at the bottom of the screen
- Shows capture number and a preview of the transcription
- Tap "View Captures" to navigate to the captures list
- Dismiss button to close the popup
- Each capture only triggers the popup once per session to avoid repeated interruptions
- Animated slide-in/fade-out transitions

### UI Improvements

#### Speed Control Redesign
- Replaced draggable speed control with simple +/- buttons
- More intuitive and easier to use with one hand
- Speed adjusts in 0.05x increments (0.5x to 2.0x range)
- Clear display of current speed with two decimal places (e.g., "1.25x")

### Bug Fixes

#### Playhead Position Persistence
- Fixed: Audio no longer resets to 0:00 when navigating away from and back to the player
- The app now detects if the same audio is already loaded and preserves the current position
- Position is still correctly restored from database when opening a different audio file

#### Capture Navigation
- Fixed: Clicking a capture now jumps to the capture's start time (windowStartMs) instead of when the capture button was pressed
- Capture popup now triggers at the start of the capture window, not throughout the entire window

#### M4A Playback
- Added MIME type hints when loading audio files to improve format detection
- Better support for M4A, AAC, FLAC, OGG, Opus, and other audio formats

### Technical Changes

- Database version upgraded to 3 (migration 2→3 adds `notes` column to captures)
- Refactored AudioPlayerService to use MediaController connected to PlaybackService
- PlaybackService registered as MediaSessionService for system integration
- Added animation imports for capture popup transitions
- AudioPlayerService now tracks current audio file ID for mini player
- Added MIME type hints when loading audio for better format compatibility

### Files Changed

**New Files:**
- `app/src/main/kotlin/com/podcapture/audio/PlaybackService.kt` - MediaSessionService for background playback
- `app/src/main/kotlin/com/podcapture/ui/components/MiniPlayer.kt` - Persistent mini player component
- `app/src/main/kotlin/com/podcapture/ui/components/WaveformTimeline.kt` - Waveform visualization timeline

**Modified Files:**
- `app/src/main/AndroidManifest.xml` - Added PlaybackService, INTERNET permission
- `app/src/main/kotlin/com/podcapture/audio/AudioPlayerService.kt` - Refactored to use MediaController, added currentAudioFileId tracking, MIME type support
- `app/src/main/kotlin/com/podcapture/data/db/AudioFileDao.kt` - Updated playback state query
- `app/src/main/kotlin/com/podcapture/data/db/CaptureDao.kt` - Added updateNotes method
- `app/src/main/kotlin/com/podcapture/data/db/PodCaptureDatabase.kt` - Added migrations 1→2 and 2→3
- `app/src/main/kotlin/com/podcapture/data/model/AudioFile.kt` - Added `firstPlayedAt` field
- `app/src/main/kotlin/com/podcapture/data/model/Capture.kt` - Added `notes` field
- `app/src/main/kotlin/com/podcapture/data/repository/CaptureRepository.kt` - Added updateCaptureNotes method
- `app/src/main/kotlin/com/podcapture/data/repository/MarkdownManager.kt` - Notes support in markdown export
- `app/src/main/kotlin/com/podcapture/data/settings/SettingsDataStore.kt` - Added skip interval setting
- `app/src/main/kotlin/com/podcapture/ui/home/HomeScreen.kt` - Added timestamp display, expanded MIME types
- `app/src/main/kotlin/com/podcapture/ui/home/HomeViewModel.kt` - Expanded audio format support
- `app/src/main/kotlin/com/podcapture/ui/navigation/PodCaptureNavHost.kt` - Added mini player integration
- `app/src/main/kotlin/com/podcapture/ui/player/PlayerScreen.kt` - Waveform timeline, speed control (0.05x), capture popup
- `app/src/main/kotlin/com/podcapture/ui/player/PlayerViewModel.kt` - Skip interval, capture popup logic, MIME type mapping
- `app/src/main/kotlin/com/podcapture/ui/settings/SettingsScreen.kt` - Added skip interval setting UI
- `app/src/main/kotlin/com/podcapture/ui/settings/SettingsViewModel.kt` - Skip interval state
- `app/src/main/kotlin/com/podcapture/ui/viewer/ViewerScreen.kt` - Notes display and edit dialog
- `app/src/main/kotlin/com/podcapture/ui/viewer/ViewerViewModel.kt` - Notes editing state and methods

**Removed/Deprecated:**
- `DraggableSpeedControl` component is no longer used (file can be deleted if desired)
- `TimelineSlider` component replaced by `WaveformTimeline`

---

## Previous Updates

See `IMPLEMENTATION_PLAN.md` for the original development roadmap and earlier implementation details.
