package com.gunnys.eundunhealth.data.auth

import android.content.Context
import com.gunnys.eundunhealth.R
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MSAL 클라이언트 초기화를 감싼 홀더.
 *
 * Hilt 의 `@Provides` 는 동기여야 하는데 MSAL 초기화는 파일 I/O 를 동반하는 blocking 호출이라
 * 직접 제공할 수 없다. 그래서 클라이언트 대신 **이 홀더**를 주입하고, 최초 사용 시점에
 * IO 디스패처에서 한 번만 만든다.
 *
 * 이중 검사 잠금인 이유: 앱 시작 직후의 세션 복원과 사용자의 CTA 탭이 겹치면 초기화가 두 번
 * 돌 수 있다. MSAL 은 단일 계정 모드에서 중복 초기화에 관대하지 않다.
 */
@Singleton
class MsalClientProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()

    @Volatile
    private var client: ISingleAccountPublicClientApplication? = null

    // InjectDispatcher 억제 근거: 이 클래스는 단위 테스트 대상이 아니다. MSAL 의 정적 팩토리와
    // 실제 Context 를 요구하므로 디스패처를 주입해도 테스트 가능해지지 않는다. 이 프로젝트에는
    // 디스패처 주입 패턴 자체가 없어(사용처 1곳) 여기만 도입하면 일관성만 깨진다.
    // RedundantSuspendModifier 는 오탐이다 — mutex.withLock 과 withContext 둘 다 suspend 이므로
    // 이 modifier 를 빼면 컴파일되지 않는다. detekt 가 inline suspend 확장을 못 알아본다.
    @Suppress("InjectDispatcher", "RedundantSuspendModifier")
    suspend fun get(): ISingleAccountPublicClientApplication {
        client?.let { return it }
        return mutex.withLock {
            client ?: withContext(Dispatchers.IO) {
                PublicClientApplication.createSingleAccountPublicClientApplication(
                    context,
                    R.raw.auth_config_ciam,
                )
            }.also { client = it }
        }
    }
}
