package com.gunnys.eundunhealth.ui.auth

import com.gunnys.eundunhealth.data.auth.AppErrorException
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError

/**
 * Auth 흐름 onFailure 공통 처리: AppErrorException 이면 내부 AppError 를 꺼내고,
 * 아니면 toAppError() 변환하며 Unknown 만 Sentry 보고. (Login/Signup/Auth VM 중복 단일화)
 */
fun Throwable.toAppErrorReporting(): AppError = (this as? AppErrorException)?.appError ?: toAppError().also { it.reportToSentry() }
