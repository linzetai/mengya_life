package com.mengyalife.app.data.advisor

/**
 * 仅用于 QA 构建的故障注入；正式包不读取任何注入参数。
 *
 * 通过启动 Intent extra 设置：
 *   adb shell am start ... --ei qa_fail_create_at 2
 *   adb shell am start ... --ei qa_fail_undo_at 1
 * 索引从 1 开始，表示第 N 个目标创建/删除前抛错。
 */
object QaFaultInjector {
    @Volatile var failCreateAt: Int? = null
    @Volatile var failUndoAt: Int? = null

    fun reset() {
        failCreateAt = null
        failUndoAt = null
    }

    fun maybeFailCreate(index: Int) {
        if (failCreateAt == index) {
            throw IllegalStateException("QA injected failure at create #$index")
        }
    }

    fun maybeFailUndo(index: Int) {
        if (failUndoAt == index) {
            throw IllegalStateException("QA injected failure at undo #$index")
        }
    }
}
