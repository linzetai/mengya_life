package com.mengyalife.app.restore

import com.mengyalife.app.data.restore.RestoreException
import com.mengyalife.app.data.restore.RestoreWriteBarrier

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreWriteBarrierTest {
    @Test
    fun `unknown blocks writes without side effects`() = runTest {
        val barrier = RestoreWriteBarrier(initiallyReady = false)
        var sideEffects = 0
        val failure = runCatching { barrier.withWrite { sideEffects++; 1 } }.exceptionOrNull()
        assertTrue(failure is RestoreException)
        assertEquals(0, sideEffects)
    }

    @Test
    fun `worker critical section cannot produce effects during maintenance`() = runTest {
        val barrier = RestoreWriteBarrier(initiallyReady = false)
        var generatedReview = 0
        var sentAi = 0
        var notified = 0
        val failure = runCatching {
            barrier.withWrite {
                generatedReview++
                sentAi++
                notified++
            }
        }.exceptionOrNull()
        assertTrue(failure is RestoreException)
        assertEquals(0, generatedReview)
        assertEquals(0, sentAi)
        assertEquals(0, notified)
    }

    @Test
    fun `exclusive restore waits existing write and invalidates stale work`() = runTest {
        val barrier = RestoreWriteBarrier(initiallyReady = true)
        val generation = barrier.generation()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var written = 0
        val writer = launch {
            barrier.withWrite(generation) {
                entered.complete(Unit)
                release.await()
                written++
            }
        }
        entered.await()
        var convergedWrite = 0
        val restore = async {
            barrier.withExclusive {
                assertFalse(barrier.isReady())
                barrier.withWrite { convergedWrite++ }
            }
        }
        assertFalse(restore.isCompleted)
        release.complete(Unit)
        writer.join()
        restore.await()
        assertEquals(1, convergedWrite)
        assertEquals(1, written)
        assertFalse(barrier.isReady())
        val failure = runCatching { barrier.withWrite(generation) { written++ } }.exceptionOrNull()
        assertTrue(failure is RestoreException)
        assertEquals("stale side effect must not run", 1, written)
        barrier.setReady(true)
        barrier.withWrite(barrier.generation()) { written++ }
        assertEquals(2, written)
    }
}
