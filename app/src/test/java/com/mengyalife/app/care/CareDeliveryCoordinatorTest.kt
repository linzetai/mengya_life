package com.mengyalife.app.care

import com.mengyalife.app.data.care.CareDeliveryCoordinator
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareDeliveryCoordinatorTest {
    @Test
    fun `delivers when ready and generation stable`() = runTest {
        val barrier = RestoreWriteBarrier(true)
        var claims = 0
        var posts = 0
        val c = CareDeliveryCoordinator(barrier) { claims++; true }
        assertTrue(c.deliver(1) { posts++ })
        assertEquals(1, claims)
        assertEquals(1, posts)
    }

    @Test
    fun `does not claim or post during maintenance`() = runTest {
        val barrier = RestoreWriteBarrier(false)
        var claims = 0
        var posts = 0
        val c = CareDeliveryCoordinator(barrier) { claims++; true }
        assertFalse(c.deliver(1) { posts++ })
        assertEquals(0, claims)
        assertEquals(0, posts)
    }

    @Test
    fun `restore cannot enter between notification claim and post`() = runTest {
        val barrier = RestoreWriteBarrier(true)
        val posting = CompletableDeferred<Unit>()
        val releasePost = CompletableDeferred<Unit>()
        var claims = 0
        var posts = 0
        val c = CareDeliveryCoordinator(barrier) { claims++; true }
        val delivery = async {
            c.deliver(1) {
                posts++
                posting.complete(Unit)
                releasePost.await()
            }
        }
        posting.await()
        var maintenanceEntered = false
        val restore = async {
            barrier.withExclusive { maintenanceEntered = true }
        }
        testScheduler.runCurrent()
        assertFalse("restore must wait for the external post call", maintenanceEntered)
        releasePost.complete(Unit)
        assertTrue(delivery.await())
        restore.await()
        assertTrue(maintenanceEntered)
        assertEquals(1, claims)
        assertEquals(1, posts)
    }
}
