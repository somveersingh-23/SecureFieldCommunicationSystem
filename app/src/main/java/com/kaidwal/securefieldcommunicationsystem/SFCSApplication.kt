package com.kaidwal.securefieldcommunicationsystem

import android.app.Application
import timber.log.Timber

class SFCSApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("SFCS Application Started")
    }
}
