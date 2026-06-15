package com.gunnys.eundunhealth.data.remote.interceptor

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class TokenAuthenticatorTest {

    private fun response401(token: String?, retried: Boolean = false): Response {
        val builder = Request.Builder().url("http://test.local/x")
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (retried) builder.header("X-Retry-Auth", "true")
        return Response.Builder()
            .request(builder.build())
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()
    }

    @Test
    fun `동시 401 은 refresh 를 한 번만 호출한다 (thundering-herd 방지)`() {
        val calls = AtomicInteger(0)
        val refresher = SessionRefresher {
            calls.incrementAndGet()
            "new-token"
        }
        val holder = AtomicReference<String?>("old-token")
        val auth = TokenAuthenticator(refresher, holder)

        val threads = (1..8).map { Thread { auth.authenticate(null, response401("old-token")) } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(1, calls.get())
        assertEquals("new-token", holder.get())
    }

    @Test
    fun `이미 갱신된 토큰이면 refresh 없이 새 토큰으로 재시도한다`() {
        val calls = AtomicInteger(0)
        val refresher = SessionRefresher {
            calls.incrementAndGet()
            "unused"
        }
        val holder = AtomicReference<String?>("new-token") // 다른 스레드가 이미 갱신
        val auth = TokenAuthenticator(refresher, holder)

        val retried = auth.authenticate(null, response401("old-token"))

        assertEquals(0, calls.get())
        assertNotNull(retried)
        assertEquals("Bearer new-token", retried!!.header("Authorization"))
    }

    @Test
    fun `타임아웃·네트워크 실패는 토큰을 무효화하지 않는다 (강제 로그아웃 방지)`() {
        val refresher = SessionRefresher { throw IOException("network down") }
        val holder = AtomicReference<String?>("old-token")
        val auth = TokenAuthenticator(refresher, holder)

        val result = auth.authenticate(null, response401("old-token"))

        assertNull(result) // 이 요청은 401 을 표면화
        assertEquals("old-token", holder.get()) // 토큰 보존 → 다음 요청이 재시도 가능
    }

    @Test
    fun `세션이 실제로 없으면(refresh null) 토큰을 무효화한다`() {
        val refresher = SessionRefresher { null }
        val holder = AtomicReference<String?>("old-token")
        val auth = TokenAuthenticator(refresher, holder)

        val result = auth.authenticate(null, response401("old-token"))

        assertNull(result)
        assertNull(holder.get())
    }

    @Test
    fun `이미 재시도한 요청(X-Retry-Auth)은 더 갱신하지 않는다`() {
        val calls = AtomicInteger(0)
        val refresher = SessionRefresher {
            calls.incrementAndGet()
            "x"
        }
        val holder = AtomicReference<String?>("old-token")
        val auth = TokenAuthenticator(refresher, holder)

        assertNull(auth.authenticate(null, response401("old-token", retried = true)))
        assertEquals(0, calls.get())
    }
}
