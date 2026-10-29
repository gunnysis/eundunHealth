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

    @Test
    fun `직접 입력은 0_5 스텝에 강제 반올림되지 않고 소수점 첫째 자리(0_1 단위)를 그대로 보존한다`() {
        // 기존 버그: muscleMassKg=30.0f에서 30.2f를 직접 입력하면 0.5 스텝 인덱스(40)로 반올림되어
        // 다시 30.0f가 반환되어 '적용'을 눌러도 아무 변화가 없었음.
        assertEquals(30.2f, roundAndClampMetricValue(30.2f, 10f..60f, decimals = 1), 0.001f)
        assertEquals(19.8f, roundAndClampMetricValue(19.8f, 5f..50f, decimals = 1), 0.001f)
        assertEquals(175f, roundAndClampMetricValue(175.4f, 140f..210f, decimals = 0), 0.001f)
    }

    @Test
    fun `직접 입력 파서는 공백 및 쉼표 소수점 구분자를 안전하게 처리한다`() {
        assertEquals(31.4f, parseMetricDirectInput(" 31.4 ") ?: -1f, 0.001f)
        assertEquals(18.6f, parseMetricDirectInput("18,6") ?: -1f, 0.001f)
    }
}
