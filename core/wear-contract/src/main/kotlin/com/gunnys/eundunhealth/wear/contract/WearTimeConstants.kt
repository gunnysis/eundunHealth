package com.gunnys.eundunhealth.wear.contract

import java.time.ZoneId

/**
 * 모바일(`:app`)과 워치(`:wear`)가 공통으로 사용하는 한국 표준시(KST, Asia/Seoul) 단일 진실 공급원(SSoT).
 */
val KST_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

/**
 * Wear OS 6 상시 생체·마음 상태 계약 버전 및 Wearable Data Layer 경로 상수.
 */
object WearContractConstants {
    const val BIOMETRIC_SCHEMA_VERSION: Int = 1

    const val DAILY_SUMMARY_CHANNEL_PATH: String = "/eundunhealth/v1/biometric/daily_summary"
    const val SYNC_REQUEST_MESSAGE_PATH: String = "/eundunhealth/v1/biometric/sync_request"
    const val SYNC_ACK_MESSAGE_PATH: String = "/eundunhealth/v1/biometric/sync_ack"

    const val PASSIVE_HEART_RATE_GOAL_BPM: Double = 100.0
    const val RAW_BUCKET_RETENTION_DAYS: Long = 7L
    const val DAILY_SUMMARY_RETENTION_DAYS: Long = 30L
}
