package com.gunnys.eundunhealth

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.CachePolicy
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid

/**
 * Coil 의 싱글톤 ImageLoader 를 [SingletonImageLoader.Factory] 로 직접 구성한다.
 *
 * 이전엔 Hilt `CoilModule` 이 동일 설정의 ImageLoader 를 제공했지만 어디에도 주입되지 않아
 * (Compose `AsyncImage`/`SubcomposeAsyncImage` 는 싱글톤을 쓰므로) GIF 디코더가 붙지 않은
 * coil 기본 싱글톤이 사용됐고, 운동 시연 GIF 가 첫 프레임만 보이는(정지) 회귀가 있었다.
 * 공식 권장 방식대로 Application 이 팩토리를 구현해 싱글톤에 직접 연결한다.
 */
@HiltAndroidApp
class EundunHealthApplication :
    Application(),
    SingletonImageLoader.Factory {
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
