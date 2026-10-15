package com.gunnys.eundunhealth.data.translation

import android.util.Log
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.Sentry
import org.junit.Test

class ExerciseTranslationReporterTest {
    @Test
    fun `누락 관측은 중복을 제거하고 고유 50개까지만 남긴다`() {
        mockkStatic(Log::class)
        mockkStatic(Sentry::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Sentry.addBreadcrumb(any<String>()) } just runs
        try {
            val reporter = ExerciseTranslationReporter()

            repeat(60) { reporter.missing("exercise", "id-$it") }
            repeat(10) { reporter.missing("exercise", "id-$it") }

            verify(exactly = 50) { Log.w(any(), any<String>()) }
            verify(exactly = 50) { Sentry.addBreadcrumb(any<String>()) }
        } finally {
            unmockkStatic(Log::class)
            unmockkStatic(Sentry::class)
        }
    }
}
