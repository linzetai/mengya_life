package com.mengyalife.app.domain.advisor

/**
 * 顾问的纯规则：URL 校验、上下文预算、提示词与授权摘要。
 *
 * 这些函数不接触数据库、网络或 Android API，便于 JVM 单测覆盖。
 */
object AdvisorRules {

    /** 只带最近 N 条对话，防止每次请求把全部历史发出去。 */
    const val MAX_HISTORY_MESSAGES = 12

    /** 历史 + 当前问题 + 目标上下文的字符预算（粗略、保守）。 */
    const val MAX_CONTEXT_CHARS = 6_000

    /** 用户单条输入上限。 */
    const val MAX_INPUT_CHARS = 4_000

    /** 模型输出上限。 */
    const val MAX_OUTPUT_TOKENS = 800

    const val MAX_ENDPOINT_CHARS = 500
    const val MAX_MODEL_CHARS = 120
    const val MAX_KEY_CHARS = 512

    const val ERROR_ENDPOINT_EMPTY = "服务地址不能为空"
    const val ERROR_ENDPOINT_HTTPS = "服务地址必须以 https:// 开头（本轮只允许 HTTPS）"
    const val ERROR_ENDPOINT_INVALID = "服务地址格式不正确"
    const val ERROR_ENDPOINT_QUERY = "服务地址不能包含查询参数或锚点"
    const val ERROR_MODEL_EMPTY = "模型名称不能为空"
    const val ERROR_MODEL_TOO_LONG = "模型名称过长"
    const val ERROR_KEY_EMPTY = "API Key 不能为空"
    const val ERROR_KEY_TOO_LONG = "API Key 过长"
    const val ERROR_INPUT_TOO_LONG = "问题过长，请控制在 $MAX_INPUT_CHARS 字以内"

    /**
     * 校验服务地址。只接受 HTTPS，不允许 userinfo / query / fragment，
     * 避免把密钥或参数意外拼进 URL。
     */
    fun validateEndpoint(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return ERROR_ENDPOINT_EMPTY
        if (value.length > MAX_ENDPOINT_CHARS) return ERROR_ENDPOINT_INVALID
        if (!value.startsWith("https://", ignoreCase = true)) return ERROR_ENDPOINT_HTTPS
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return ERROR_ENDPOINT_INVALID
        if (!uri.scheme.equals("https", ignoreCase = true)) return ERROR_ENDPOINT_HTTPS
        if (uri.host.isNullOrBlank()) return ERROR_ENDPOINT_INVALID
        if (uri.userInfo != null) return ERROR_ENDPOINT_INVALID
        if (uri.rawQuery != null || uri.rawFragment != null) return ERROR_ENDPOINT_QUERY
        return null
    }

    fun validateModel(raw: String): String? = when {
        raw.trim().isEmpty() -> ERROR_MODEL_EMPTY
        raw.trim().length > MAX_MODEL_CHARS -> ERROR_MODEL_TOO_LONG
        else -> null
    }

    fun validateApiKey(raw: String): String? = when {
        raw.isBlank() -> ERROR_KEY_EMPTY
        raw.length > MAX_KEY_CHARS -> ERROR_KEY_TOO_LONG
        else -> null
    }

    fun validateInput(raw: String): String? =
        if (raw.trim().length > MAX_INPUT_CHARS) ERROR_INPUT_TOO_LONG else null

    /**
     * 把用户填写的「基础地址」规范成 Chat Completions URL。
     *
     * 兼容三种写法：
     * - `https://host/v1` → `https://host/v1/chat/completions`
     * - `https://host/openai/v1/` → `.../v1/chat/completions`
     * - 已经写了 `.../chat/completions` → 原样使用
     */
    fun chatCompletionsUrl(endpoint: String): String {
        val base = endpoint.trim().trimEnd('/')
        return when {
            base.endsWith("/chat/completions", ignoreCase = true) -> base
            base.endsWith("/v1", ignoreCase = true) -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }

    /** 系统提示词：只给建议，不执行任何数据修改，不给人生综合评分。 */
    fun systemPrompt(): String =
        """
        你是「萌芽人生」的长期个人成长顾问。你可以理解用户的问题、分析用户主动提供的目标、
        提出长期方向/阶段目标/短期行动建议、解释决策影响，并在信息不足时用中文追问。

        必须遵守：
        1. 你只有建议权，不能声称已经创建、修改或删除任何目标、日记、进展；
           这些操作必须由用户在应用内确认后自行完成。
        2. 不要编造用户没有提供的个人事实；不确定时先问清楚。
        3. 不要给出「人生综合评分」「成长总分」或类似的未经定义的整体量化分数。
        4. 回答使用中文，具体、克制、可执行，避免空泛口号。
        5. 如果用户附带了目标摘要，只基于摘要分析，不要假设未提供的日记或其它目标。
        """.trimIndent()

    /** 目标上下文块（只有用户明确授权后才拼进请求）。 */
    fun goalContextBlock(context: AdvisorGoalContext): String = buildString {
        appendLine("【用户本次明确授权发送的目标摘要】")
        appendLine("长期目标：${context.title}")
        context.description?.takeIf { it.isNotBlank() }?.let { appendLine("描述：$it") }
        if (context.stageTitles.isNotEmpty()) {
            appendLine("阶段目标：" + context.stageTitles.joinToString("；"))
        }
        if (context.actionTitles.isNotEmpty()) {
            appendLine("短期行动：" + context.actionTitles.joinToString("；"))
        }
        if (context.recentCheckinLines.isNotEmpty()) {
            appendLine("最近进展：")
            context.recentCheckinLines.forEach { appendLine("- $it") }
        }
        appendLine("（未包含日记正文，也未包含其它目标。）")
    }.trim()

    /**
     * 构造发送给模型的消息：
     * 系统提示 → 可选目标上下文 → 最近历史（预算内、优先新消息）→ 当前问题。
     *
     * @param history 本地已保存且状态为 SENT 的消息（不含当前问题）
     */
    fun buildRequestMessages(
        history: List<AdvisorMessage>,
        currentInput: String,
        goalContext: AdvisorGoalContext? = null,
        extraSystemContext: String? = null,
        extraSystemPrompt: String? = null,
    ): List<AdvisorChatMessage> {
        val systemText = buildString {
            append(systemPrompt())
            extraSystemPrompt?.takeIf { it.isNotBlank() }?.let {
                append("\n\n")
                append(it)
            }
        }
        val system = AdvisorChatMessage(role = "system", content = systemText)
        val contextBlocks = buildList {
            goalContext?.let { add(goalContextBlock(it)) }
            extraSystemContext?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        val current = AdvisorChatMessage(role = "user", content = currentInput.trim())

        var budget = MAX_CONTEXT_CHARS - system.content.length - current.content.length -
            contextBlocks.sumOf { it.length }
        val selected = mutableListOf<AdvisorMessage>()
        val sentHistory = history
            .filter { it.status == AdvisorMessageStatus.SENT && it.content.isNotBlank() }
            .takeLast(MAX_HISTORY_MESSAGES)
        for (message in sentHistory.asReversed()) {
            if (budget <= 0) break
            val cost = message.content.length
            if (cost > budget) break
            selected.add(message)
            budget -= cost
        }

        return buildList {
            add(system)
            contextBlocks.forEach { block ->
                add(AdvisorChatMessage(role = "system", content = block))
            }
            selected.asReversed().forEach { message ->
                add(
                    AdvisorChatMessage(
                        role = when (message.role) {
                            AdvisorRole.USER -> "user"
                            AdvisorRole.ASSISTANT -> "assistant"
                        },
                        content = message.content,
                    ),
                )
            }
            add(current)
        }
    }

    /** 目标草案生成的严格 JSON 协议（不依赖 tool calling）。 */
    fun proposalSystemPrompt(): String =
        """
        现在请把对话讨论整理为「目标草案」，只输出一个 JSON 对象，不要 Markdown 代码块，不要额外解释。
        schemaVersion 必须为 1。字段：
        {
          "schemaVersion": 1,
          "summary": "一句话总结",
          "assumptions": ["需要用户确认的假设"],
          "risks": ["可能的风险"],
          "goals": [
            {
              "tempId": "g1",
              "level": "LONG" | "STAGE" | "ACTION",
              "parentTempId": "g1" | null,
              "parentGoalId": 123 | null,
              "title": "标题",
              "description": "描述",
              "domain": "HEALTH|CAREER|WEALTH|LIFE|LEARNING",
              "startDate": "yyyy-MM-dd" | null,
              "dueDate": "yyyy-MM-dd" | null,
              "basis": "建议依据"
            }
          ]
        }
        规则：
        - 长期目标 level=LONG，parentTempId/parentGoalId 必须为 null；
        - 阶段目标必须挂在长期目标下；短期行动必须挂在阶段目标下；
        - 新节点内部用 tempId/parentTempId 连接；给已有目标补充时可用 parentGoalId；
        - tempId 必须唯一，不能形成循环；
        - 不要编造收入、资产、完成概率或用户未提供的个人事实；
        - 信息不足时在 assumptions 中写清楚，而不是假设成事实；
        - 草案最多 12 个节点，每个 description 不超过 120 字，确保 JSON 完整输出。
        """.trimIndent()

    /** 给 UI 的授权摘要（不是发给模型的内容本身）。 */
    fun consentLines(context: AdvisorGoalContext): List<String> {
        val stageSuffix = if (context.stageTitles.isEmpty()) {
            ""
        } else {
            "（" + context.stageTitles.take(3).joinToString("、") +
                (if (context.stageTitles.size > 3) "…" else "") + "）"
        }
        val actionSuffix = if (context.actionTitles.isEmpty()) {
            ""
        } else {
            "（" + context.actionTitles.take(5).joinToString("、") +
                (if (context.actionTitles.size > 5) "…" else "") + "）"
        }
        val progressSuffix = if (context.recentCheckinLines.isEmpty()) {
            ""
        } else {
            "，例如 " + context.recentCheckinLines.take(2).joinToString("；")
        }
        return buildList {
            add("目标：${context.title}")
            context.description?.takeIf { it.isNotBlank() }?.let {
                add("描述：" + it.take(80) + if (it.length > 80) "…" else "")
            }
            add("阶段目标：${context.stageCount} 个$stageSuffix")
            add("短期行动：${context.actionCount} 个$actionSuffix")
            add("最近进展：${context.totalCheckins} 条$progressSuffix")
            add("不发送：日记正文、其它目标、完整数据库")
        }
    }

}
