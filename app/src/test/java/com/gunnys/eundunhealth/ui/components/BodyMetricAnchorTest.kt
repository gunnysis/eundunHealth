package com.gunnys.eundunhealth.ui.components

import com.gunnys.eundunhealth.domain.model.Gender
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyMetricAnchorTest {

    @Test
    fun `남성 선택 시 한국 성인 남성 중앙값(174cm, 72kg)을 초기 앵커로 반환한다`() {
        assertEquals(174f, BodyMetricDefaults.defaultHeightFor(Gender.MALE), 0.01f)
        assertEquals(72.0f, BodyMetricDefaults.defaultWeightFor(Gender.MALE), 0.01f)
    }

    @Test
    fun `여성 선택 시 한국 성인 여성 중앙값(161cm, 54kg)을 초기 앵커로 반환한다`() {
        assertEquals(161f, BodyMetricDefaults.defaultHeightFor(Gender.FEMALE), 0.01f)
        assertEquals(54.0f, BodyMetricDefaults.defaultWeightFor(Gender.FEMALE), 0.01f)
    }
}
