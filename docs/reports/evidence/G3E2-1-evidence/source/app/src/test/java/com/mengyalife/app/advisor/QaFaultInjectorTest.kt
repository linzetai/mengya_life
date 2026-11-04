package com.mengyalife.app.advisor

import com.mengyalife.app.data.advisor.QaFaultInjector
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test

class QaFaultInjectorTest {
    @After fun tearDown() = QaFaultInjector.reset()

    @Test
    fun `只注入指定索引`() {
        QaFaultInjector.failCreateAt = 2
        QaFaultInjector.maybeFailCreate(1)
        assertThrows(IllegalStateException::class.java) { QaFaultInjector.maybeFailCreate(2) }
    }

    @Test
    fun `撤销注入`() {
        QaFaultInjector.failUndoAt = 1
        assertThrows(IllegalStateException::class.java) { QaFaultInjector.maybeFailUndo(1) }
    }
}
