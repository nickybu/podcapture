# Changelog

All notable changes to PodCapture are documented in this file.

## Unreleased

### New Features
- Settings has a Transcription section showing the speech model's state and size on disk, with Download (with progress) and Delete buttons
- The Obsidian export dialog can choose your vault folder directly when none is set, then exports straight away

### Improvements
- Success messages in Settings and the captures viewer appear as snackbars; clean OPML imports show a one-line summary, imports with failures still list them in a dialog
- The captures viewer header shows the episode and podcast titles instead of "Podcast Episode Captures"
- The export dialog's Share button is now "Share as Text", to tell it apart from the toolbar Share (which sends the capture file for local audio)
- Release APK is about half the size (R8 shrinking); unused storage permissions removed and Auto Backup rules added (#34)
- Podcast feeds refresh in parallel and are cached; `http://` RSS feeds now work (tried over https first) (#38)
- Home stays smooth during playback, shows History when you have no bookmarks, and the bookmark grid no longer clips rows on wide screens (#35)
- The app asks once for notification permission on Android 13+ so download progress can be shown (#31)

### Bug Fixes
- The transcript formatting editor keeps the caret and selection while typing and after Highlight/Bold/Clear (#30)
- Obsidian export never overwrites an existing note; a taken name becomes "Title (2).md" (#30)
- The podcast search spinner no longer gets stuck, and OPML import failures are reported instead of silently ignored (#30)
- Partially typed API keys and theme colors are kept locally; colors are only saved once valid (#30)
- Queueing a download while another runs no longer starts a duplicate, and cancelling a download stops it (#31)
- Refreshing a custom RSS podcast or re-downloading an episode no longer wipes its playback position and finished state (#32)
- Unbookmarking a podcast offers to delete its downloaded episodes (#32)
- Local audio files save their playhead position and play history again (#33)
- Returning to the player no longer re-applies a capture's seek, skipping while loading no longer jumps to 0, and playback speed persists across restarts (#33)
- The podcast detail screen keeps episodes' finished marks and no longer scans storage on every download progress tick (#36)
- The mini player and snackbars respect system insets, and tapping the mini player no longer stacks duplicate players (#36)
- Captures from HE-AAC and parametric-stereo episodes are trimmed and transcribed at the right sample rate (#37)
- Failed transcriptions and captures from a player that is not ready are reported instead of being saved as captures (#37)
- Two captures at once share one speech model download instead of corrupting it (#37)

## 2026-01-16

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
- Choose from 5 to 60 seconds in 5-second steps
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
