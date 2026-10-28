package com.gunnys.eundunhealth.ui.util

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * 햅틱 피드백 헬퍼.
 * 시스템의 터치 진동 설정(`isHapticFeedbackEnabled`)을 존중하며, 미지원 기기에서도 예외 없이 안전하게 동작한다.
 */
object HapticFeedbackHelper {
    fun performTick(haptic: HapticFeedback) {
        runCatching {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun performStepClick(haptic: HapticFeedback) {
        runCatching {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun performConfirm(view: View) {
        if (!view.isHapticFeedbackEnabled) return
        runCatching {
            val constant = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
            view.performHapticFeedback(constant)
        }
    }
}
