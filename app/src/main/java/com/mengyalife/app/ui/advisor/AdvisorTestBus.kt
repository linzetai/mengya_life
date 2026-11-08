package com.mengyalife.app.ui.advisor

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 仅 QA 构建使用的测试入口。
 *
 * Android 的 `adb shell input text` 不支持中文，真机验收无法用命令行输入自然语言；
 * 因此 QA 包允许通过 intent extra 注入一句顾问输入。正式包不会读取它，
 * 也不会改变任何业务语义。
 */
object AdvisorTestBus {
    val prompts = MutableStateFlow<String?>(null)

    fun offer(prompt: String) {
        prompts.value = prompt.trim().takeIf { it.isNotEmpty() }
    }

    fun clear() {
        prompts.value = null
    }
}
