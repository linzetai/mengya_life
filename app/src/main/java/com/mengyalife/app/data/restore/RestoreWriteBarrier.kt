package com.mengyalife.app.data.restore

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Shared single-process serialization boundary for business writes vs restore. */
class RestoreWriteBarrier(initiallyReady: Boolean = true) {
    private val mutex = Mutex()
    @Volatile private var ready = initiallyReady
    @Volatile private var restoreGeneration = 0L

    private class Permit(val owner: RestoreWriteBarrier, val exclusive: Boolean) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Permit>
    }

    fun setReady(value: Boolean) { ready = value }
    fun isReady(): Boolean = ready
    fun generation(): Long = restoreGeneration

    suspend fun <T> withWrite(block: suspend () -> T): T = withWrite(null, block)

    suspend fun <T> withWrite(expectedGeneration: Long?, block: suspend () -> T): T {
        val inherited = currentCoroutineContext()[Permit]
        // Authorized convergence work inherits the exclusive permit. It must be
        // allowed to write while the barrier is intentionally not READY.
        if (inherited?.owner === this && inherited.exclusive) return block()
        // Fail immediately when called during UNKNOWN/RECONCILING/MAINTENANCE;
        // do not queue a stale UI/Worker write until after Gate release.
        if (!ready) throw RestoreException("RESTORE_BLOCKED", "恢复维护中，禁止业务写入")
        val captured = expectedGeneration ?: restoreGeneration
        if (inherited?.owner === this) {
            checkWritable(captured)
            return block()
        }
        return mutex.withLock {
            checkWritable(captured)
            withContext(Permit(this, exclusive = false)) { block() }
        }
    }

    /** Exclusive restore/reconciliation; increments token to invalidate requests in flight. */
    suspend fun <T> withExclusive(block: suspend () -> T): T {
        if (currentCoroutineContext()[Permit]?.owner === this) return block()
        return mutex.withLock {
            ready = false
            restoreGeneration += 1
            withContext(Permit(this, exclusive = true)) { block() }
        }
    }

    private fun checkWritable(expectedGeneration: Long?) {
        if (!ready) throw RestoreException("RESTORE_BLOCKED", "恢复维护中，禁止业务写入")
        if (expectedGeneration != null && expectedGeneration != restoreGeneration) {
            throw RestoreException("RESTORE_STALE_RESULT", "恢复后旧请求已过期")
        }
    }
}
