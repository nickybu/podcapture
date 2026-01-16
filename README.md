# PodCapture

An Android app for capturing and transcribing moments from podcasts and audio files.

## Features

- **Audio Playback**: Play audio files with a waveform timeline, scrubbing support, and adjustable skip intervals
- **Capture Moments**: Capture audio segments with automatic transcription using Vosk (offline speech recognition)
- **Organize with Tags**: Tag captures for easy organization and filtering
- **Notes**: Add personal notes to any capture
- **Obsidian Export**: Export captures to Obsidian with customizable frontmatter tags and YAML formatting
- **Markdown Export**: Share captures as markdown files

## Tech Stack

- **Kotlin** with Jetpack Compose for UI
- **Media3 (ExoPlayer)** for audio playback
- **Room** for local database
- **Vosk** for offline speech-to-text transcription
- **Koin** for dependency injection
- **DataStore** for preferences
- **Storage Access Framework** for Obsidian vault integration

## Requirements

- Android 9.0 (API 28) or higher
- ~50MB for the Vosk speech recognition model (downloaded on first use)

## Building

```bash
./gradlew assembleDebug
```

## License

MIT
