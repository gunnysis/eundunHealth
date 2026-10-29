package com.gunnys.eundunhealth.ui.update

import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayInAppUpdateCheckerTest {

    @Test
    fun `UPDATE_AVAILABLE 이고 IMMEDIATE 허용 시 true 반환`() {
        val info = mockk<AppUpdateInfo> {
            every { updateAvailability() } returns UpdateAvailability.UPDATE_AVAILABLE
            every { isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) } returns true
        }
        assertTrue(isImmediateFlowEligible(info))
    }

    @Test
    fun `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS 이고 IMMEDIATE 허용 시 true 반환 (중단된 업데이트 복구)`() {
        val info = mockk<AppUpdateInfo> {
            every { updateAvailability() } returns UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            every { isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) } returns true
        }
        assertTrue(isImmediateFlowEligible(info))
    }

    @Test
    fun `UPDATE_NOT_AVAILABLE 이면 false 반환`() {
        val info = mockk<AppUpdateInfo> {
            every { updateAvailability() } returns UpdateAvailability.UPDATE_NOT_AVAILABLE
            every { isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) } returns true
        }
        assertFalse(isImmediateFlowEligible(info))
    }

    @Test
    fun `IMMEDIATE 가 허용되지 않으면 false 반환`() {
        val info = mockk<AppUpdateInfo> {
            every { updateAvailability() } returns UpdateAvailability.UPDATE_AVAILABLE
            every { isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) } returns false
        }
        assertFalse(isImmediateFlowEligible(info))
    }
}
