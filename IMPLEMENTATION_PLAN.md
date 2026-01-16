# PodCapture MVP - Implementation Plan

## Overview
A podcast/audio player for Android (Pixel 9a) built with Kotlin Multiplatform, featuring local audio playback with a "capture" feature that transcribes audio segments using Vosk.

## Tech Stack
- **Framework:** Kotlin Multiplatform (KMP) with Compose Multiplatform
- **Audio:** ExoPlayer (Android native, wrapped for KMP)
- **Speech-to-Text:** Vosk (offline, on-device)
- **Database:** Room (KMP-compatible)
- **DI:** Koin
- **Target:** Android API 35, Pixel 9a (arm64-v8a)

---

## Phase 1: Project Setup

### Step 1.1: Create KMP Project
- [ ] Create new Kotlin Multiplatform project via Android Studio
- [ ] Project name: `podcapture`
- [ ] Package: `com.podcapture`
- [ ] Configure for Android target only (iOS/web later)
- [ ] Set minimum SDK to 28, target SDK 35

### Step 1.2: Configure Gradle Dependencies
- [ ] Set up version catalog (`gradle/libs.versions.toml`)
- [ ] Add dependencies:
  - Compose Multiplatform BOM
  - Compose Material3
  - Navigation Compose
  - ExoPlayer (Media3)
  - Room + KSP
  - Koin
  - Vosk Android
  - Kotlinx Coroutines
  - Kotlinx Serialization
  - Kotlinx DateTime

### Step 1.3: Project Structure
```
podcapture/
├── app/                           # Android application module
│   └── src/main/
│       ├── kotlin/com/podcapture/
│       │   ├── PodCaptureApp.kt   # Application class
│       │   ├── MainActivity.kt
│       │   ├── di/                # Koin modules
│       │   ├── ui/
│       │   │   ├── theme/
│       │   │   ├── navigation/
│       │   │   ├── home/
│       │   │   ├── player/
│       │   │   └── viewer/
│       │   ├── data/
│       │   │   ├── db/
│       │   │   ├── repository/
│       │   │   └── model/
│       │   ├── audio/
│       │   │   └── AudioPlayerService.kt
│       │   └── transcription/
│       │       └── VoskTranscriptionService.kt
│       ├── res/
│       └── AndroidManifest.xml
└── gradle/
    └── libs.versions.toml
```

---

## Phase 2: Data Layer

### Step 2.1: Define Data Models
- [ ] Create `AudioFile` entity
```kotlin
@Entity(tableName = "audio_files")
data class AudioFile(
    @PrimaryKey val id: String,           // UUID
    val name: String,                      // Display name
    val filePath: String,                  // Full URI path
    val durationMs: Long,
    val format: String,                    // "mp3" or "wav"
    val lastPlayedAt: Long?,               // Epoch millis
    val lastPositionMs: Long,              // Resume position
    val addedAt: Long                      // Epoch millis
)
```

- [ ] Create `Capture` entity
```kotlin
@Entity(tableName = "captures")
data class Capture(
    @PrimaryKey val id: String,           // UUID
    val audioFileId: String,              // FK to AudioFile
    val timestampMs: Long,                // Position when captured
    val windowStartMs: Long,
    val windowEndMs: Long,
    val transcription: String,
    val createdAt: Long                   // Epoch millis
)
```

### Step 2.2: Create Room Database
- [ ] Create `AudioFileDao`
  - Insert/update audio file
  - Get all files ordered by lastPlayedAt DESC
  - Get file by ID
  - Update last played position
  - Delete file

- [ ] Create `CaptureDao`
  - Insert capture
  - Get captures by audioFileId ordered by timestampMs
  - Get capture by ID
  - Delete capture
  - Delete all captures for audioFileId

- [ ] Create `PodCaptureDatabase`
  - Version 1
  - Entities: AudioFile, Capture

### Step 2.3: Create Repositories
- [ ] `AudioFileRepository`
  - Wraps AudioFileDao
  - Exposes Flows for reactive UI

- [ ] `CaptureRepository`
  - Wraps CaptureDao
  - Handles markdown file generation

### Step 2.4: Markdown File Manager
- [ ] Create `MarkdownManager`
- [ ] File naming: `{sanitized_filepath_hash}_{filename}_captures.md`
  - Example: `a1b2c3d4_podcast_ep42_captures.md`
  - Hash first 8 chars of MD5(filePath) for uniqueness
- [ ] Storage location: `app_data/captures/`
- [ ] Functions:
  - `createOrUpdateMarkdownFile(audioFile, captures)`
  - `getMarkdownContent(audioFile): String`
  - `getMarkdownFilePath(audioFile): String`

### Markdown Format:
```markdown
# Captures: podcast_ep42.mp3
**Source:** /storage/emulated/0/Podcasts/podcast_ep42.mp3
**Generated:** 2025-01-15 10:30:00

---

## Capture at 00:15:30
**Window:** 00:15:00 → 00:16:00
**Captured:** 2025-01-15 10:30:15

> The transcribed text from Vosk appears here. This represents
> what was spoken during the capture window.

---

## Capture at 00:42:15
**Window:** 00:41:45 → 00:42:45
**Captured:** 2025-01-15 10:45:22

> Another transcription segment here.

---
```

---

## Phase 3: Audio Player Service

### Step 3.1: Create AudioPlayerService
- [ ] Wrap ExoPlayer (Media3) for audio playback
- [ ] Expose StateFlow for:
  - `playerState`: Playing, Paused, Stopped, Loading, Error
  - `currentPositionMs`: Long (updated every 100ms while playing)
  - `durationMs`: Long
  - `currentAudioFile`: AudioFile?

- [ ] Functions:
  - `loadFile(uri: Uri)`
  - `play()`
  - `pause()`
  - `stop()`
  - `seekTo(positionMs: Long)`
  - `seekRelative(deltaMs: Long)` // +/- seeking
  - `setPlaybackSpeed(speed: Float)`

### Step 3.2: Audio Segment Extraction
- [ ] Create `AudioExtractor`
- [ ] Function: `extractSegment(sourceUri: Uri, startMs: Long, endMs: Long): ByteArray`
- [ ] Use MediaExtractor + MediaCodec for decoding
- [ ] Output raw PCM for Vosk input

---

## Phase 4: Vosk Transcription Service

### Step 4.1: Vosk Setup
- [ ] Add Vosk Android dependency
- [ ] Download English model (vosk-model-small-en-us-0.15, ~40MB)
- [ ] Bundle model in assets or download on first launch
- [ ] Initialize Vosk recognizer on app start

### Step 4.2: Create VoskTranscriptionService
- [ ] Initialize with model path
- [ ] Function: `transcribe(audioData: ByteArray, sampleRate: Int): String`
- [ ] Handle transcription on background thread (Dispatchers.Default)
- [ ] Expose Flow for transcription progress/status

### Step 4.3: Capture Flow
- [ ] `performCapture(audioFile, currentPositionMs, windowSizeMs)`:
  1. Calculate windowStart = max(0, currentPositionMs - windowSizeMs)
  2. Calculate windowEnd = min(duration, currentPositionMs + windowSizeMs)
  3. Extract audio segment
  4. Run Vosk transcription
  5. Create Capture entity
  6. Save to database
  7. Update markdown file
  8. Return Capture

---

## Phase 5: UI - Navigation & Theme

### Step 5.1: Setup Navigation
- [ ] Create `NavGraph` with routes:
  - `home` - Home screen
  - `player/{audioFileId}` - Player screen
  - `viewer/{audioFileId}` - Markdown viewer

### Step 5.2: Create App Theme
- [ ] Define color scheme (dark-first, podcast-app aesthetic)
- [ ] Typography scale
- [ ] Component styles (buttons, cards, etc.)

---

## Phase 6: UI - Home Screen

### Step 6.1: Home Screen Layout
- [ ] App bar with title "PodCapture"
- [ ] Prominent "Open Audio File" FAB or button
- [ ] History section:
  - Section header: "Recent Files"
  - List of AudioFileCard items
  - Empty state if no history

### Step 6.2: AudioFileCard Component
- [ ] Display: filename, duration, last played time
- [ ] Capture count badge
- [ ] Tap → navigate to Player

### Step 6.3: File Picker Integration
- [ ] Use ActivityResultContracts.OpenDocument
- [ ] Filter for audio/mpeg, audio/wav
- [ ] On selection:
  1. Read file metadata (duration, format)
  2. Create/update AudioFile entity
  3. Navigate to Player

---

## Phase 7: UI - Player Screen

### Step 7.1: Player Screen Layout
```
┌─────────────────────────────────┐
│ ← Back            [View Captures]│
├─────────────────────────────────┤
│                                 │
│         Filename.mp3            │
│         00:15:30 / 01:23:45     │
│                                 │
│  ●────────●──────────────────   │ ← Timeline with markers
│                                 │
│     ⏪    [  ▶  ]    ⏩         │ ← Controls
│    -10s              +10s       │
│                                 │
│         [  CAPTURE  ]           │ ← Big capture button
│         Window: ±30s            │
│                                 │
│  Speed: [0.5x] [1x] [1.5x] [2x] │
│                                 │
└─────────────────────────────────┘
```

### Step 7.2: Player ViewModel
- [ ] Connect to AudioPlayerService
- [ ] Expose UI state:
  - isPlaying: Boolean
  - currentPosition: Long
  - duration: Long
  - captures: List<Capture>
  - captureWindowSize: Int (seconds)

- [ ] Actions:
  - onPlayPause()
  - onSeekTo(position)
  - onRewind()
  - onFastForward()
  - onCapture()
  - onSpeedChange(speed)
  - onCaptureWindowChange(seconds)

### Step 7.3: Timeline Component
- [ ] Horizontal progress bar/slider
- [ ] Draggable thumb for seeking
- [ ] Capture markers as small dots/ticks
- [ ] Tap marker → show capture preview tooltip

### Step 7.4: Playback Controls
- [ ] Play/Pause toggle button (large)
- [ ] Rewind button: tap = -10s, long press = continuous
- [ ] Forward button: tap = +10s, long press = continuous
- [ ] Speed selector row

### Step 7.5: Capture Button
- [ ] Large, prominent button
- [ ] Shows current window setting (e.g., "±30s")
- [ ] Tap → trigger capture flow
- [ ] Show loading indicator during transcription
- [ ] Show success snackbar with preview on completion
- [ ] Tap window label → open window size picker

### Step 7.6: Capture Window Picker
- [ ] Bottom sheet or dialog
- [ ] Options: 15s, 30s, 45s, 60s (before + after)
- [ ] Persist selection in DataStore

---

## Phase 8: UI - Markdown Viewer

### Step 8.1: Viewer Screen Layout
- [ ] App bar: "Captures" + share button
- [ ] Scrollable markdown content
- [ ] Each capture section is tappable

### Step 8.2: Markdown Rendering
- [ ] Parse markdown sections
- [ ] Render with proper styling
- [ ] Timestamps are clickable → return to player at that position

### Step 8.3: Share Functionality
- [ ] Share button in app bar
- [ ] Opens system share sheet with markdown file

---

## Phase 9: Polish & Testing

### Step 9.1: Error Handling
- [ ] File not found / access denied
- [ ] Unsupported format
- [ ] Transcription failure
- [ ] Storage full

### Step 9.2: Permissions
- [ ] READ_EXTERNAL_STORAGE (for older APIs)
- [ ] POST_NOTIFICATIONS (for playback notification)
- [ ] Handle permission requests gracefully

### Step 9.3: Pixel 9a Testing
- [ ] Test on physical device or emulator
- [ ] Verify 120Hz animations are smooth
- [ ] Test with various file sizes
- [ ] Verify Vosk performance on Tensor G4

### Step 9.4: Edge Cases
- [ ] Very long files (3+ hours)
- [ ] Very short files (<1 min)
- [ ] Capture at start/end of file
- [ ] Multiple rapid captures
- [ ] App backgrounding during capture

---

## Implementation Order (Step-by-Step)

### Sprint 1: Foundation
1. [ ] Create KMP project structure
2. [ ] Configure Gradle with all dependencies
3. [ ] Create data models (AudioFile, Capture)
4. [ ] Set up Room database and DAOs
5. [ ] Create repositories

### Sprint 2: Audio Playback
6. [ ] Implement AudioPlayerService with ExoPlayer
7. [ ] Create basic Player screen UI (no capture yet)
8. [ ] Implement play/pause/seek controls
9. [ ] Test audio playback with sample files

### Sprint 3: Home & Navigation
10. [ ] Set up navigation graph
11. [ ] Create Home screen with file picker
12. [ ] Implement history list
13. [ ] Connect Home → Player navigation

### Sprint 4: Capture Feature
14. [ ] Integrate Vosk SDK and model
15. [ ] Implement audio segment extraction
16. [ ] Create VoskTranscriptionService
17. [ ] Add Capture button to Player
18. [ ] Implement capture flow end-to-end
19. [ ] Create MarkdownManager

### Sprint 5: Timeline & Viewer
20. [ ] Add capture markers to timeline
21. [ ] Create Markdown Viewer screen
22. [ ] Add share functionality

### Sprint 6: Polish
23. [ ] Error handling throughout
24. [ ] Permission handling
25. [ ] Pixel 9a optimization
26. [ ] Final testing

---

## File Naming Convention for Captures

Given a file at `/storage/emulated/0/Podcasts/My Podcast/Episode 42.mp3`:

1. Extract filename: `Episode 42.mp3`
2. Sanitize filename: `episode_42`
3. Hash full path: `MD5("/storage/emulated/0/Podcasts/My Podcast/Episode 42.mp3")` → `a1b2c3d4e5f6...`
4. Take first 8 chars: `a1b2c3d4`
5. Final name: `a1b2c3d4_episode_42_captures.md`

This ensures:
- Unique files even for same-named files in different folders
- Human-readable filename included
- No illegal characters in filename

---

## Notes

- **Vosk Model:** Using `vosk-model-small-en-us-0.15` (~40MB) for balance of size/accuracy
- **Default Capture Window:** ±30 seconds (60s total)
- **Supported Formats:** MP3, WAV initially
- **Min Android:** API 28 (for MediaCodec reliability)
- **Target Android:** API 35 (Pixel 9a)
