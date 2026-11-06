package com.gunnys.eundunhealth.wear.contract

import java.time.ZoneId

/**
 * 모바일(`:app`)과 워치(`:wear`)가 공통으로 사용하는 한국 표준시(KST, Asia/Seoul) 단일 진실 공급원(SSoT).
 */
val KST_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
