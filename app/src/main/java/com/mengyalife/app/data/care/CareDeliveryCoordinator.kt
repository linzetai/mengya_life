package com.mengyalife.app.data.care

import com.mengyalife.app.data.restore.RestoreWriteBarrier

/**
 * 通知投递边界：在同一恢复栅栏临界区内原子 claim 并调用实际 post，
 * 避免“批准后、post 前”恢复进入维护态。跨 Room/系统通知仍无法事务化。
 */
class CareDeliveryCoordinator(
    private val barrier: RestoreWriteBarrier,
    private val claim: suspend (Long) -> Boolean,
) {
    /** Claim and perform the external post under the same restore-exclusive boundary. */
    suspend fun deliver(
        decisionId: Long,
        startGeneration: Long = barrier.generation(),
        post: suspend () -> Unit,
    ): Boolean = try {
        barrier.withWrite(startGeneration) {
            if (!claim(decisionId)) return@withWrite false
            post()
            true
        }
    } catch (_: com.mengyalife.app.data.restore.RestoreException) {
        false
    }

}
