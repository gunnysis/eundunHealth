package com.gunnys.eundunhealth.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * 일회성 이벤트(네비게이션·스낵바 등)를 라이프사이클-aware 하게 수집한다.
 *
 * `LaunchedEffect(Unit) { flow.collect { } }` 는 컴포지션 전 생애 동안 수집해, 화면이 STOPPED
 * (백그라운드)인 동안 도착한 이벤트도 즉시 소비한다(보이지 않는 화면이 네비게이션되는 등).
 * 본 헬퍼는 STARTED 동안만 수집하고 STOPPED 시 중단 → STARTED 재개 시 재시작하므로, ViewModel 의
 * `Channel(BUFFERED)` 이 버퍼링한 이벤트가 누락 없이 화면이 보일 때 전달된다.
 * (state 의 `collectAsStateWithLifecycle` 에 대응하는 '이벤트' 버전 — 룰 11 lifecycle-aware 정합.)
 */
@Composable
fun <T> ObserveAsEvents(flow: Flow<T>, onEvent: suspend (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(flow, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect(onEvent)
        }
    }
}
