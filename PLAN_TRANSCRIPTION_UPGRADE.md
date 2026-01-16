# Transcription Upgrade Plan: Vosk to Whisper

## Executive Summary

This document outlines the plan to replace Vosk speech recognition with a more accurate local transcription solution. After researching available options, **whisper.cpp** (via Android bindings) is the recommended replacement due to its superior accuracy, active development, and proven Android compatibility.

---

## 1. Research Findings

### 1.1 Current Solution: Vosk

**Vosk** is currently used for offline speech recognition in PodCapture.

| Aspect | Details |
|--------|---------|
| Model Size | ~50MB (vosk-model-small-en-us-0.15) |
| Accuracy | Moderate - works for clear speech, struggles with accents/noise |
| Speed | Fast inference (~0.5x real-time on modern devices) |
| Languages | Multiple models available |
| Maintenance | Active but slower development |
| Word Error Rate (WER) | ~15-20% on clean audio, higher with noise |

**Current Implementation:**
- `VoskModelManager.kt` - Downloads and manages model files
- `VoskTranscriptionService.kt` - Handles transcription via Vosk API
- `AudioExtractor.kt` - Extracts PCM audio segments (reusable)

### 1.2 Option A: Whisper.cpp (RECOMMENDED)

**whisper.cpp** is a C/C++ port of OpenAI's Whisper model optimized for edge deployment.

| Aspect | Details |
|--------|---------|
| Repository | github.com/ggerganov/whisper.cpp |
| License | MIT |
| Android Support | Yes - via JNI bindings |
| Accuracy | Excellent - state-of-the-art for local transcription |
| Word Error Rate | ~5-8% (tiny), ~4-6% (base), ~3-5% (small) on clean audio |

**Model Sizes:**

| Model | Size | RAM Required | Relative Speed | Accuracy |
|-------|------|--------------|----------------|----------|
| tiny | ~75MB | ~400MB | 1x (fastest) | Good |
| tiny.en | ~75MB | ~400MB | 1x | Better (English only) |
| base | ~142MB | ~500MB | 0.7x | Very Good |
| base.en | ~142MB | ~500MB | 0.7x | Excellent (English only) |
| small | ~466MB | ~1GB | 0.4x | Excellent |
| small.en | ~466MB | ~1GB | 0.4x | Best (English only) |

**Android Libraries Available:**
1. **whisper.cpp Android example** - Official JNI bindings in whisper.cpp repo
2. **whisper-android-java** (github.com/litongjava/whisper-android-java) - Java wrapper
3. **whisper.kt** (github.com/aallam/whisper.kt) - Kotlin multiplatform bindings

**Pros:**
- Significantly better accuracy than Vosk (2-3x improvement in WER)
- Active development with regular optimizations
- Quantized models available (INT8) for smaller size and faster inference
- Supports timestamps and word-level confidence
- Growing ecosystem of Android libraries
- MIT licensed

**Cons:**
- Larger model sizes (tiny is 75MB vs Vosk's 50MB)
- Higher memory requirements
- Slower inference than Vosk (but still real-time capable)
- Requires native library compilation or pre-built binaries

### 1.3 Option B: Faster Whisper

**Faster Whisper** is a reimplementation using CTranslate2 for faster inference.

| Aspect | Details |
|--------|---------|
| Primary Use | Desktop/Server |
| Android Support | Limited - No official Android bindings |
| Speed | 4x faster than original Whisper |
| Model Format | CTranslate2 |

**Why Not Recommended for Android:**
- CTranslate2 doesn't have Android NDK support
- Would require significant porting effort
- No existing Android libraries
- Better suited for server-side deployment

### 1.4 Option C: Google ML Kit Speech Recognition

| Aspect | Details |
|--------|---------|
| Provider | Google |
| Model Size | ~50MB |
| Offline | Yes (with downloaded model) |
| Accuracy | Good, but less than Whisper |
| Integration | Easy via ML Kit SDK |

**Why Not Recommended:**
- Accuracy lower than Whisper
- Limited customization options
- Dependent on Google Play Services
- Less transparent about model updates

### 1.5 Option D: Sherpa-ONNX

| Aspect | Details |
|--------|---------|
| Repository | github.com/k2-fsa/sherpa-onnx |
| License | Apache 2.0 |
| Android Support | Yes - official Android bindings |
| Models | Multiple (Whisper, Zipformer, Paraformer) |

**Interesting Alternative:**
- Can run Whisper models via ONNX
- Also supports other efficient models
- Good Android support
- Worth considering as a backup option

---

## 2. Comparison Summary

| Feature | Vosk (Current) | Whisper.cpp | Faster Whisper | ML Kit |
|---------|---------------|-------------|----------------|--------|
| Accuracy (WER) | 15-20% | 5-8% (tiny) | 5-8% | 12-15% |
| Model Size | 50MB | 75-466MB | 75-466MB | 50MB |
| Speed (RTF) | 0.5x | 1-2x | 0.3x | 0.5x |
| Android Support | Excellent | Good | Poor | Excellent |
| Maintenance | Active | Very Active | Very Active | Google |
| License | Apache 2.0 | MIT | MIT | Proprietary |
| Timestamps | Basic | Excellent | Excellent | Basic |

**RTF = Real-Time Factor (lower is faster, 1x = real-time)

---

## 3. Recommendation

### Primary Recommendation: **whisper.cpp with tiny.en model**

**Justification:**
1. **Accuracy**: 2-3x better word error rate than Vosk
2. **Model Size**: tiny.en at 75MB is acceptable (only 25MB larger than current)
3. **Speed**: Real-time capable on modern Android devices (min SDK 28)
4. **Android Ecosystem**: Multiple libraries available, active community
5. **Future-proof**: Whisper is the industry standard; continuous improvements
6. **Timestamps**: Better timestamp support for podcast segments

### Secondary Recommendation: **Sherpa-ONNX** (backup)

If whisper.cpp integration proves problematic, Sherpa-ONNX provides a good alternative with official Android support and the ability to run Whisper models.

---

## 4. Implementation Plan

### Phase 1: Setup and Dependencies (1-2 days)

1. **Add whisper.cpp Android dependency**
   - Option A: Use pre-built AAR from a maintained library
   - Option B: Build whisper.cpp from source with Android NDK

   ```kotlin
   // build.gradle.kts
   dependencies {
       // Replace Vosk
       // implementation(libs.vosk.android)

       // Add Whisper (example - actual dependency may vary)
       implementation("com.github.litongjava:whisper-android:1.x.x")
       // OR use JitPack for whisper.cpp bindings
   }
   ```

2. **Update native library packaging**
   - Add ARM64-v8a and armeabi-v7a native libraries
   - Configure APK splits if needed for size optimization

### Phase 2: Model Management (1-2 days)

1. **Create `WhisperModelManager.kt`**
   - Similar structure to `VoskModelManager.kt`
   - Download tiny.en model (ggml-tiny.en.bin) on first use
   - Support model switching (tiny/base) in future
   - Implement progress tracking

   ```kotlin
   class WhisperModelManager(private val context: Context) {
       companion object {
           // Hugging Face hosts whisper.cpp compatible models
           private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin"
           private const val MODEL_FILE_NAME = "ggml-tiny.en.bin"
           private const val MODEL_SIZE_BYTES = 75_000_000L // ~75MB
       }

       // Similar StateFlow pattern as VoskModelManager
       // Download to app's internal storage
       // Verify checksum after download
   }
   ```

### Phase 3: Transcription Service (2-3 days)

1. **Create `WhisperTranscriptionService.kt`**
   - Match interface of `VoskTranscriptionService`
   - Handle audio format requirements (16kHz mono PCM)
   - Implement transcription with progress callbacks

   ```kotlin
   class WhisperTranscriptionService(
       private val context: Context,
       private val modelManager: WhisperModelManager,
       private val audioExtractor: AudioExtractor
   ) {
       private var whisperContext: WhisperContext? = null

       suspend fun transcribe(
           audioUri: Uri,
           startMs: Long,
           endMs: Long
       ): TranscriptionResult = withContext(Dispatchers.Default) {
           val model = modelManager.getModelPath()
           val segment = audioExtractor.extractSegment(audioUri, startMs, endMs)

           // Initialize whisper context if needed
           if (whisperContext == null) {
               whisperContext = WhisperContext.createContext(model)
           }

           // Transcribe with whisper.cpp
           // Returns text with optional timestamps
       }
   }
   ```

2. **Update AudioExtractor if needed**
   - Whisper expects 16kHz mono float32 audio
   - Current implementation outputs 16kHz mono int16
   - May need minor conversion adjustment

### Phase 4: Integration and Testing (2-3 days)

1. **Update Dependency Injection**
   - Replace Vosk bindings with Whisper in Koin modules
   - Ensure backward compatibility for migration

2. **Update UI Components**
   - Model download progress (already exists, reuse)
   - Potentially add model selection in settings (future)

3. **Testing**
   - Unit tests for transcription service
   - Integration tests with sample audio
   - Performance benchmarks on various devices
   - Compare accuracy against Vosk baseline

### Phase 5: Migration and Cleanup (1 day)

1. **Data Migration**
   - No user data migration needed (transcription is per-session)
   - Clean up old Vosk model files on upgrade

2. **Remove Vosk Dependencies**
   - Remove `vosk-android` from dependencies
   - Delete `VoskModelManager.kt`
   - Delete `VoskTranscriptionService.kt`

3. **Documentation**
   - Update README with new requirements
   - Document model download process

---

## 5. Migration Steps

### For Clean Install Users
No action needed - app will download Whisper model on first transcription.

### For Existing Users (Vosk Installed)
1. App detects upgrade on launch
2. Prompt user: "Improved transcription available - Download new model?"
3. Download Whisper model in background
4. Delete old Vosk model files after successful download
5. Continue with normal operation

### Rollback Plan
If critical issues arise:
1. Keep Vosk code in separate branch for 2 releases
2. Can revert via hotfix if needed
3. Model files are separate, so both can coexist temporarily

---

## 6. Potential Issues and Mitigations

### Issue 1: Larger Model Size
**Risk**: Users may not want to download 75MB
**Mitigation**:
- Show clear progress during download
- Allow transcription to work offline after initial download
- Consider offering "lite" mode with smaller model in future

### Issue 2: Memory Usage
**Risk**: Whisper requires ~400MB RAM for tiny model
**Mitigation**:
- Min SDK 28 ensures 3GB+ RAM on most devices
- Release model when not in use
- Add memory warning for low-end devices

### Issue 3: Slower Inference
**Risk**: Whisper is slower than Vosk
**Mitigation**:
- Use quantized INT8 models where available
- Show progress indicator during transcription
- Process in background with notification
- Consider GPU acceleration (NNAPI) in future

### Issue 4: Native Library Compatibility
**Risk**: JNI crashes on some devices
**Mitigation**:
- Use well-maintained library with broad device testing
- Implement crash handling with fallback error messages
- Report crashes to analytics for quick fixes

### Issue 5: Model Download Failures
**Risk**: Large download may fail on poor connections
**Mitigation**:
- Implement resume capability for downloads
- Store partially downloaded files
- Retry with exponential backoff
- Consider CDN or multiple mirror URLs

---

## 7. Timeline Estimate

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1: Setup | 1-2 days | None |
| Phase 2: Model Management | 1-2 days | Phase 1 |
| Phase 3: Transcription Service | 2-3 days | Phase 2 |
| Phase 4: Integration & Testing | 2-3 days | Phase 3 |
| Phase 5: Migration & Cleanup | 1 day | Phase 4 |
| **Total** | **7-11 days** | |

---

## 8. Success Metrics

1. **Accuracy Improvement**: Target 50%+ reduction in word error rate
2. **User Experience**: Transcription completes within 2x real-time
3. **Reliability**: <1% crash rate related to transcription
4. **Adoption**: Model download completion rate >95%

---

## 9. Future Enhancements

After initial migration, consider:

1. **Model Selection**: Allow users to choose tiny/base/small based on needs
2. **Language Support**: Add multilingual model option
3. **Streaming Transcription**: Real-time transcription during playback
4. **Speaker Diarization**: Identify different speakers (separate library needed)
5. **GPU Acceleration**: Leverage NNAPI for faster inference

---

## 10. References

- whisper.cpp: https://github.com/ggerganov/whisper.cpp
- OpenAI Whisper: https://github.com/openai/whisper
- Whisper Model Card: https://huggingface.co/openai/whisper-tiny.en
- Sherpa-ONNX: https://github.com/k2-fsa/sherpa-onnx
- Vosk: https://alphacephei.com/vosk/

---

## Appendix A: File Changes Summary

### Files to Create
- `app/src/main/kotlin/com/podcapture/transcription/WhisperModelManager.kt`
- `app/src/main/kotlin/com/podcapture/transcription/WhisperTranscriptionService.kt`
- `app/src/main/kotlin/com/podcapture/transcription/WhisperContext.kt` (if needed)

### Files to Modify
- `app/build.gradle.kts` - Update dependencies
- `gradle/libs.versions.toml` - Add Whisper library version
- DI module file - Update bindings

### Files to Delete (after migration)
- `app/src/main/kotlin/com/podcapture/transcription/VoskModelManager.kt`
- `app/src/main/kotlin/com/podcapture/transcription/VoskTranscriptionService.kt`

### Files to Keep
- `app/src/main/kotlin/com/podcapture/transcription/AudioExtractor.kt` (reusable)

---

*Plan created: January 2026*
*Author: Claude Code Assistant*
*Status: Ready for Review*
