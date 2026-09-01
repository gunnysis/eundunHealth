package com.gunnys.eundunhealth.domain.repository

import android.app.Activity

/**
 * 인증 추상화.
 *
 * 브라우저 위임(Authorization Code + PKCE)으로 전환되면서 이메일/비밀번호 입력·검증·
 * 재발송·비밀번호 재설정이 전부 Entra 호스팅 페이지로 넘어갔다. 그래서 앱에 남는 진입점은
 * [authenticate] 하나뿐이다 — 로그인과 회원가입도 호스팅 페이지 안에서 갈린다.
 *
 * [authenticate] 가 `Activity` 를 받는 것은 MSAL 의 요구다(Custom Tab 을 띄울 호스트가
 * 필요하다). 도메인 계층이 안드로이드 타입을 참조하게 되지만, 이를 감추려면 구현이 하나뿐인
 * 추상화를 하나 더 만들어야 해서 득보다 실이 크다고 판단했다.
 */
interface AuthRepository {
    /**
     * 브라우저를 띄워 대화형 인증을 수행하고 사용자 ID(`oid`)를 반환한다.
     *
     * 사용자가 브라우저를 닫아 취소한 경우는 **실패가 아니다** — [AuthCancelledException] 으로
     * 구분해 돌려주므로 호출자는 에러 배너를 띄우지 말고 조용히 원상복귀해야 한다.
     */
    suspend fun authenticate(activity: Activity): Result<String>

    suspend fun signOut(): Result<Unit>

    suspend fun deleteAccount(): Result<Unit>

    suspend fun getCurrentUserId(): String?

    /** 캐시된 계정으로 무음 갱신을 시도한다. 세션이 없으면 null. */
    suspend fun restoreSession(): String?
}

/**
 * 사용자가 인증을 취소했음을 나타낸다(브라우저 닫기 등).
 *
 * 에러로 취급하면 사용자의 의도적 행동에 빨간 배너를 띄우게 된다 — 설계 §5.3.
 */
class AuthCancelledException : Exception("사용자가 인증을 취소했습니다")
