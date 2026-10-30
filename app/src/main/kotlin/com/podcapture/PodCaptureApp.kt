package com.podcapture

import android.app.Application
import com.podcapture.di.appModule
import com.podcapture.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class PodCaptureApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            // The default (INFO) logs every definition as it is created; keep that to debug builds.
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)
            androidContext(this@PodCaptureApp)
            modules(appModule, networkModule)
        }
    }
}
