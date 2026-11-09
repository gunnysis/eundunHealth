package com.gunnys.eundunhealth.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * [Context] 체인을 따라 올라가 호스트 [Activity]를 찾는다.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
