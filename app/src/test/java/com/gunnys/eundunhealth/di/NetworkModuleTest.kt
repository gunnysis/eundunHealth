package com.gunnys.eundunhealth.di

import com.gunnys.eundunhealth.data.auth.MsalClientProvider
import com.gunnys.eundunhealth.data.remote.interceptor.RetryInterceptor
import io.mockk.mockk
import io.sentry.okhttp.SentryOkHttpInterceptor
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * [NetworkModule] 의 실제 OkHttpClient 빌더 및 인터셉터 체인 불변식을 JVM 단위 테스트에서 검증한다.
 *
 * **배경 (INC-2026-10-29-33 / EUNDUNHEALTH-G)**:
 * Sentry Gradle Plugin ASM 바이트코드 계측이 `SentryOkHttpInterceptor(captureFailedRequests=true)` 를
 * `RetryInterceptor` 안쪽(뒤)에 자동 주입하면, 1차 시도가 일시적 504/IOException 으로 실패하고 2차 재시도가
 * 200 OK 로 성공해도 1차 실패가 `SentryHttpClientException` 이슈로 오탐 보고된다.
 * 기존 단위 테스트는 `RetryInterceptor` 만 단독으로 테스트하고 `NetworkModule` 의 실제 OkHttpClient 빌더를
 * 호출하지 않아 이 인터셉터 순서 결함이 프로덕션 배포 후에야 발견되었다.
 */
class NetworkModuleTest {

    private val tokenHolder = AtomicReference<String?>(null)
    private val msalProvider = mockk<MsalClientProvider>(relaxed = true)

    @Test
    fun `backend OkHttpClient places SentryOkHttpInterceptor before RetryInterceptor`() {
        val client = NetworkModule.provideBackendOkHttpClient(tokenHolder, msalProvider)
        val sentryIdx = client.interceptors.indexOfFirst { it is SentryOkHttpInterceptor }
        val retryIdx = client.interceptors.indexOfFirst { it is RetryInterceptor }

        assertTrue("SentryOkHttpInterceptor가 등록되어 있어야 합니다", sentryIdx >= 0)
        assertTrue("RetryInterceptor가 등록되어 있어야 합니다", retryIdx >= 0)
        assertTrue(
            "SentryOkHttpInterceptor($sentryIdx)는 반드시 RetryInterceptor($retryIdx) 앞(바깥)에 위치해야 합니다",
            sentryIdx < retryIdx,
        )
    }

    @Test
    fun `meals OkHttpClient places SentryOkHttpInterceptor before RetryInterceptor`() {
        val client = NetworkModule.provideMealsOkHttpClient(tokenHolder, msalProvider)
        val sentryIdx = client.interceptors.indexOfFirst { it is SentryOkHttpInterceptor }
        val retryIdx = client.interceptors.indexOfFirst { it is RetryInterceptor }

        assertTrue("SentryOkHttpInterceptor가 등록되어 있어야 합니다", sentryIdx >= 0)
        assertTrue("RetryInterceptor가 등록되어 있어야 합니다", retryIdx >= 0)
        assertTrue(
            "SentryOkHttpInterceptor($sentryIdx)는 반드시 RetryInterceptor($retryIdx) 앞(바깥)에 위치해야 합니다",
            sentryIdx < retryIdx,
        )
    }

    @Test
    fun `exerciseDb OkHttpClient places SentryOkHttpInterceptor before RetryInterceptor`() {
        val client = NetworkModule.provideExerciseDbOkHttpClient()
        val sentryIdx = client.interceptors.indexOfFirst { it is SentryOkHttpInterceptor }
        val retryIdx = client.interceptors.indexOfFirst { it is RetryInterceptor }

        assertTrue("SentryOkHttpInterceptor가 등록되어 있어야 합니다", sentryIdx >= 0)
        assertTrue("RetryInterceptor가 등록되어 있어야 합니다", retryIdx >= 0)
        assertTrue(
            "SentryOkHttpInterceptor($sentryIdx)는 반드시 RetryInterceptor($retryIdx) 앞(바깥)에 위치해야 합니다",
            sentryIdx < retryIdx,
        )
    }
}
