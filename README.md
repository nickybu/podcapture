# PodCapture

An Android app for capturing and transcribing moments from podcasts and audio files.

## Overview

PodCapture helps you save and transcribe the parts of podcasts that matter to you. Search for podcasts by name using the Podcast Index API, bookmark your favourites, and download episodes for offline listening. You can also open local audio files directly if you already have content on your device.

While listening, capture moments with a single tap. The app extracts a configurable window of audio around your capture point and transcribes it using OpenAI's Whisper model running locally on-device. No internet connection required for transcription, and your audio never leaves your phone.

## Playback

Episodes play with a visual waveform timeline that makes it easy to scrub through content. Skip intervals are adjustable in settings, and the app shows latest episodes from your bookmarked podcasts so you can quickly jump into new content.

## Captures

Each capture saves the transcribed text along with metadata about the episode and timestamp. Add tags to organise your captures and personal notes for additional context.

Captures can be exported to Obsidian with customizable frontmatter tags, or shared as markdown files to use however you like.

## Theming

Customise the app's colour scheme in settings. Set your own hex colours for the background and accent tones, with changes applying instantly across the app.

## Tech Stack

- Kotlin with Jetpack Compose
- Media3 (ExoPlayer) for audio playback
- Room for local database
- Whisper (via Sherpa-ONNX) for offline transcription
- Podcast Index API for podcast search
- Koin for dependency injection
- DataStore for preferences

## Requirements

- Android 9.0 (API 28) or higher
- ~40MB for the Whisper speech recognition model (downloaded on first use)

## Building

```bash
./gradlew assembleDebug
```

## License

MIT
