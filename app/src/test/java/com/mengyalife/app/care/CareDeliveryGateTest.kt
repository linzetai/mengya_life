package com.mengyalife.app.care

import com.mengyalife.app.data.care.CareDeliveryGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareDeliveryGateTest {
    @Test
    fun `delivers only when ready and same generation`() {
        assertTrue(CareDeliveryGate.canDeliver(true, 3, 3))
        assertFalse(CareDeliveryGate.canDeliver(false, 3, 3))
        assertFalse(CareDeliveryGate.canDeliver(true, 3, 4))
        assertFalse(CareDeliveryGate.canDeliver(false, 3, 4))
    }
}
