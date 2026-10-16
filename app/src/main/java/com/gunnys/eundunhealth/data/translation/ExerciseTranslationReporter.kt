package com.gunnys.eundunhealth.data.translation

import android.util.Log
import io.sentry.Sentry
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ExerciseTranslation"
private const val MAX_MISSING_REPORTS = 50

@Singleton
class ExerciseTranslationReporter @Inject constructor() {
    private val missingLock = Any()
    private val reportedMissing = mutableSetOf<String>()

    fun loadFailed(error: Throwable) {
        Log.e(TAG, "운동 번역 에셋을 읽지 못해 영문으로 대체합니다.", error)
        Sentry.captureException(error)
    }

    fun missing(kind: String, key: String) {
        if (key.isBlank()) return
        val marker = "$kind:$key"
        val shouldReport = synchronized(missingLock) {
            if (reportedMissing.size >= MAX_MISSING_REPORTS) false else reportedMissing.add(marker)
        }
        if (!shouldReport) return
        Log.w(TAG, "운동 번역 누락 [$kind]: $key")
        Sentry.addBreadcrumb("exercise translation missing [$kind]: $key")
    }
}
