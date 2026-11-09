package com.mengyalife.app.data.care

import com.mengyalife.app.data.restore.RestoreWriteBarrier

/**
 * 通知投递的顺序门：
 * 1) 投递前 ready + generation 未变；
 * 2) 原子 claim；
 * 3) claim 后再次 ready + generation；
 * 返回 true 才允许 NotificationManager.notify。
 * 跨系统无法事务化：claim 后恢复可能导致本次通知被丢弃（宁可少发）。
 */
class CareDeliveryCoordinator(
    private val barrier: RestoreWriteBarrier,
    private val claim: suspend (Long) -> Boolean,
) {
    suspend fun approve(decisionId: Long, startGeneration: Long = barrier.generation()): Boolean {
        if (!CareDeliveryGate.canDeliver(barrier.isReady(), startGeneration, barrier.generation())) return false
        if (!claim(decisionId)) return false
        return CareDeliveryGate.canDeliver(barrier.isReady(), startGeneration, barrier.generation())
    }
}
