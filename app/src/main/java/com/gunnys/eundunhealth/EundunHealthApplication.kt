package com.gunnys.eundunhealth

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid

@HiltAndroidApp
class EundunHealthApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val dsn = BuildConfig.SENTRY_DSN
        SentryAndroid.init(this) { options ->
            options.dsn = dsn
            if (dsn.isBlank()) {
                options.isEnabled = false
            } else {
                options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.2
                options.environment = if (BuildConfig.DEBUG) "development" else "production"
                options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            }
        }
    }
}
