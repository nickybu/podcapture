package com.podcapture.di

import com.podcapture.audio.AudioPlayerService
import com.podcapture.capture.CaptureManager
import com.podcapture.data.db.PodCaptureDatabase
import com.podcapture.data.repository.AudioFileRepository
import com.podcapture.data.repository.CaptureRepository
import com.podcapture.data.repository.MarkdownManager
import com.podcapture.data.repository.TagRepository
import com.podcapture.data.settings.SettingsDataStore
import com.podcapture.transcription.AudioExtractor
import com.podcapture.transcription.VoskModelManager
import com.podcapture.transcription.VoskTranscriptionService
import com.podcapture.ui.home.HomeViewModel
import com.podcapture.ui.player.PlayerViewModel
import com.podcapture.ui.search.PodcastDetailViewModel
import com.podcapture.ui.search.PodcastSearchViewModel
import com.podcapture.ui.settings.SettingsViewModel
import com.podcapture.ui.viewer.ViewerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Database
    single { PodCaptureDatabase.getInstance(androidContext()) }
    single { get<PodCaptureDatabase>().audioFileDao() }
    single { get<PodCaptureDatabase>().captureDao() }
    single { get<PodCaptureDatabase>().tagDao() }

    // Settings
    single { SettingsDataStore(androidContext()) }

    // Markdown Manager
    single { MarkdownManager(androidContext()) }

    // Repositories
    single { AudioFileRepository(get()) }
    single { CaptureRepository(get(), get()) }
    single { TagRepository(get()) }

    // Audio player
    single { AudioPlayerService(androidContext()) }

    // Transcription
    single { VoskModelManager(androidContext()) }
    single { AudioExtractor(androidContext()) }
    single { VoskTranscriptionService(androidContext(), get(), get()) }

    // Capture Manager (for mini player capture)
    single { CaptureManager(get(), get(), get(), get(), get(), get()) }

    // ViewModels
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { params -> PlayerViewModel(params.get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { params -> ViewerViewModel(params.get(), androidContext(), get(), get(), get(), get(), get()) }
    viewModel { PodcastSearchViewModel(get()) }
    viewModel { params -> PodcastDetailViewModel(params.get(), get()) }
}
