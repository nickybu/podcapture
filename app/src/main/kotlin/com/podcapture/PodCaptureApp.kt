package com.podcapture

import android.app.Application
import com.podcapture.di.appModule
import com.podcapture.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class PodCaptureApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@PodCaptureApp)
            modules(appModule, networkModule)
        }
    }
}
