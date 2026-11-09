package com.mengyalife.app.care

import com.mengyalife.app.data.care.CareDeliveryCoordinator
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareDeliveryCoordinatorTest {
    @Test
    fun `approves when ready and generation stable`() = runTest {
        val barrier = RestoreWriteBarrier(true)
        var claims = 0
        val c = CareDeliveryCoordinator(barrier) { claims++; true }
        assertTrue(c.approve(1))
        assertEquals(1, claims)
    }

    @Test
    fun `does not claim when maintenance`() = runTest {
        val barrier = RestoreWriteBarrier(false)
        var claims = 0
        val c = CareDeliveryCoordinator(barrier) { claims++; true }
        assertFalse(c.approve(1))
        assertEquals(0, claims)
    }

    @Test
    fun `restore between claim and post drops delivery`() = runTest {
        val barrier = RestoreWriteBarrier(true)
        val start = barrier.generation()
        var claims = 0
        val c = CareDeliveryCoordinator(barrier) {
            claims++
            barrier.withExclusive { }
            barrier.setReady(true)
            true
        }
        assertFalse(c.approve(1, start))
        assertEquals(1, claims)
        assertTrue(barrier.generation() > start)
    }
}
