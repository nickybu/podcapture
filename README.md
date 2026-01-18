# PodCapture

An Android app for discovering podcasts, capturing moments, and transcribing them for later reference.

## Features

### Podcast Discovery
- Search podcasts by title, author, or topic using the Podcast Index API
- Bookmark podcasts to build your library
- Browse latest episodes from bookmarked podcasts (updated daily)
- Stream or download episodes for offline listening

### Capture & Transcribe
- Play episodes with a waveform timeline and adjustable skip intervals
- Capture audio segments around moments of interest
- Automatic transcription using offline speech recognition
- Configurable capture window (how much audio before/after the moment to include)

### Organize & Export
- Tag captures for easy organization and filtering
- Add personal notes to any capture
- Export to Obsidian with customizable frontmatter and YAML formatting
- Share captures as markdown files

### Personalization
- Customize the app's color scheme with hex color inputs
- Set background and accent colors to match your style
- Theme changes apply instantly across the entire app

## Tech Stack

- Kotlin with Jetpack Compose
- Media3 (ExoPlayer) for audio playback
- Room for local database
- Vosk for offline speech-to-text
- Podcast Index API for podcast data
- Koin for dependency injection
- DataStore for preferences

## Requirements

- Android 9.0 (API 28) or higher
- ~50MB for the speech recognition model (downloaded on first use)

## Building

```bash
./gradlew assembleDebug
```

## License

MIT
