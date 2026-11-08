package com.mengyalife.app.data.care

import kotlinx.coroutines.flow.MutableStateFlow

/** 通知点击 → 打开唯一顾问对话；只传 CareOpportunity ID。 */
object CareOpenBus {
    val pending = MutableStateFlow<Long?>(null)
    fun offer(id: Long) { pending.value = id }
    fun clear() { pending.value = null }
}
