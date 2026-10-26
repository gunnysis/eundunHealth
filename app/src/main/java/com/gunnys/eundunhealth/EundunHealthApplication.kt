package com.gunnys.eundunhealth

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.CachePolicy
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import javax.inject.Inject

/**
 * Coil 의 싱글톤 ImageLoader 를 [SingletonImageLoader.Factory] 로 직접 구성하고,
 * WorkManager 의 [Configuration.Provider] 를 통해 [HiltWorkerFactory] 를 등록한다.
 */
@HiltAndroidApp
class EundunHealthApplication :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

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

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            // ExerciseDB 의 .gif 운동 시연을 애니메이션 재생. API 28+ 는 플랫폼 디코더 사용.
            if (Build.VERSION.SDK_INT >= 28) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .crossfade(true)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build()
}
