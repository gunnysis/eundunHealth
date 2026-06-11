package com.gunnys.eundunhealth.ui.auth

import com.gunnys.eundunhealth.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResendConfirmationControllerTest {
    @Test
    fun `성공 시 쿨다운 60초 시작 후 감소`() = runTest {
        val repo = mockk<AuthRepository>()
        coEvery { repo.resendConfirmation(any()) } returns Result.success(Unit)
        val c = ResendConfirmationController(repo, this)

        c.resend("a@b.com")
        runCurrent() // 첫 delay 전까지 실행 → 쿨다운 60 설정
        assertEquals(60, c.cooldownSec.value)
        advanceTimeBy(2_500) // 1s·2s delay 2회 발화(3s 전) → 58
        assertEquals(58, c.cooldownSec.value)
    }

    @Test
    fun `쿨다운 중이면 재요청 무시`() = runTest {
        val repo = mockk<AuthRepository>()
        coEvery { repo.resendConfirmation(any()) } returns Result.success(Unit)
        val c = ResendConfirmationController(repo, this)

        c.resend("a@b.com")
        runCurrent()
        assertEquals(60, c.cooldownSec.value)
        c.resend("a@b.com") // cooldown>0 → no-op
        runCurrent()
        assertEquals(60, c.cooldownSec.value)
    }
}
