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
        1. 你本身不能直接改数据库；但应用具备「对话式记录进展」能力：用户明确说
           「帮我记录…」且信息完整时，应用会自动生成进展记录并由用户在卡片上确认或撤销。
           不要否认这个能力，也不要声称是你本人直接写入了数据；信息不全时先追问。
        1.1 在用户消息的回复里，**不要提前宣称“已保存/已记录”**：正式写入状态以应用卡片为准；
           需要时可说“我帮你整理成一条待确认的记录”。
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
    data class BuiltRequest(
        val messages: List<AdvisorChatMessage>,
        val usedSources: List<AdvisorSource>,
        val contextChars: Int,
        val historyMessages: Int,
        val droppedContextEntries: Int,
        val truncated: Boolean,
    )

    /**
     * 统一构建请求：system、extra prompt、自动上下文、历史、当前问题共同受
     * [MAX_CONTEXT_CHARS] 约束。只有真正进入请求的上下文条目才返回来源。
     */
    fun buildRequest(
        history: List<AdvisorMessage>,
        currentInput: String,
        contextEntries: List<AdvisorContextEntry>,
        extraSystemPrompt: String? = null,
        maxChars: Int = MAX_CONTEXT_CHARS,
    ): BuiltRequest {
        val systemText = buildString {
            append(systemPrompt())
            extraSystemPrompt?.takeIf { it.isNotBlank() }?.let {
                append("\n\n")
                append(it)
            }
        }
        val currentText = currentInput.trim()
        val fixed = systemText.length + currentText.length
        var remaining = (maxChars - fixed).coerceAtLeast(0)

        val includedEntries = mutableListOf<AdvisorContextEntry>()
        var dropped = 0
        for (entry in contextEntries) {
            val cost = entry.text.length + 1
            if (cost <= remaining) {
                includedEntries.add(entry)
                remaining -= cost
            } else {
                dropped++
            }
        }

        val historyCandidates = history
            .filter { it.status == AdvisorMessageStatus.SENT && it.content.isNotBlank() }
            .takeLast(MAX_HISTORY_MESSAGES)
        val selectedHistory = mutableListOf<AdvisorMessage>()
        for (message in historyCandidates.asReversed()) {
            val cost = message.content.length + 1
            if (cost <= remaining) {
                selectedHistory.add(message)
                remaining -= cost
            }
        }

        val messages = buildList {
            add(AdvisorChatMessage(role = "system", content = systemText))
            includedEntries.forEach { entry ->
                add(AdvisorChatMessage(role = "system", content = entry.text))
            }
            selectedHistory.asReversed().forEach { message ->
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
            add(AdvisorChatMessage(role = "user", content = currentText))
        }
        return BuiltRequest(
            messages = messages,
            usedSources = includedEntries.mapNotNull { it.source },
            contextChars = includedEntries.sumOf { it.text.length },
            historyMessages = selectedHistory.size,
            droppedContextEntries = dropped,
            truncated = fixed > maxChars,
        )
    }

    /** 兼容旧测试/调用：把上下文当成一个 block，来源为空。 */
    fun buildRequestMessages(
        history: List<AdvisorMessage>,
        currentInput: String,
        goalContext: AdvisorGoalContext? = null,
        extraSystemContext: String? = null,
        extraSystemPrompt: String? = null,
    ): List<AdvisorChatMessage> {
        val blocks = buildList {
            goalContext?.let { add(AdvisorContextEntry(goalContextBlock(it))) }
            extraSystemContext?.takeIf { it.isNotBlank() }?.let {
                add(AdvisorContextEntry(it))
            }
        }
        return buildRequest(
            history = history,
            currentInput = currentInput,
            contextEntries = blocks,
            extraSystemPrompt = extraSystemPrompt,
        ).messages
    }

    /** 用户是否在要求把整段讨论整理成多节点目标草案（原按钮能力）。 */
    fun looksLikePlanDraftRequest(text: String): Boolean {
        val value = text.trim()
        if (value.isEmpty()) return false
        return value.contains("目标草案") ||
            (value.contains("草案") && value.contains("目标")) ||
            value.contains("整理成目标") ||
            value.contains("整理为目标") ||
            value.contains("生成一个目标") ||
            value.contains("生成目标")
    }

    /** 是否值得在对话里轻量提示“可以整理成目标”（不弹固定工具栏）。 */
    fun shouldSuggestPlanDraft(text: String): Boolean {
        val value = text.trim()
        if (value.length < 30) return false
        val planWords = listOf("计划", "打算", "准备", "方案", "步骤", "先", "然后", "第一步")
        return planWords.any { value.contains(it) } && !looksLikePlanDraftRequest(value)
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

    /**
     * 记录候选提取协议（不依赖 tool calling）。
     *
     * 只发送：本次用户原话 + 已授权的目标清单 + 少量近期进展；
     * 模型只输出 JSON 候选，写入由应用领域接口执行。
     */
    fun recordExtractionPrompt(
        goalLines: List<String>,
        recentCheckinLines: List<String>,
        today: String,
    ): String = buildString {
        appendLine("你是记录提取器，只做结构化提取，不聊天、不解释、不输出 Markdown。")
        appendLine("今天的日期：$today。只输出一个 JSON 对象。")
        appendLine()
        appendLine("可用目标（只能引用这些 id）：")
        if (goalLines.isEmpty()) appendLine("（无）") else goalLines.forEach { appendLine("- $it") }
        if (recentCheckinLines.isNotEmpty()) {
            appendLine()
            appendLine("最近的进展记录（用于判断增量/累计）：")
            recentCheckinLines.forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine("输出 schema：")
        appendLine(
            """{"schemaVersion":1,"record":{"type":"PROGRESS","goalId":123,"goalTitle":"目标标题",""" +
                """"goalCandidates":[{"goalId":1,"title":"目标"}],"amountKind":"DELTA",""" +
                """"metricValue":2000,"metricUnit":"元","note":"今天存了 2000 元",""" +
                """"occurredAt":"yyyy-MM-dd","pendingFields":[],"intent":"EXPLICIT"}}""",
        )
        appendLine("没有记录意图时输出：{\"schemaVersion\":1,\"record\":null}")
        appendLine()
        appendLine("规则：")
        appendLine("1. 只有用户明确在陈述自己的经历/进展/数值时才返回 record；普通讨论、提问、计划返回 null。")
        appendLine("2. amountKind 必须是 DELTA、SNAPSHOT 或 NONE：")
        appendLine("   - DELTA = 本轮新增量（如「又存了 2000」「今天学了 2 小时」）；")
        appendLine("   - SNAPSHOT = 当前累计状态（如「现在有 14.7 万了」），不能被当成新增量；")
        appendLine("   - NONE = 没有数值的进展（note 必须补全描述）。")
        appendLine("3. 不确定目标时 goalId 填 null，并给出 1-3 个 goalCandidates；不要把候选当事实。")
        appendLine("4. 有歧义/缺少数值时，把字段名放进 pendingFields（goal / metricValue / occurredAt）。")
        appendLine("5. 用户是在纠正上一条记录（如「说错了，实际是 1500」）时，仍按 SNAPSHOT 或 DELTA 提取并给出正确的数值。")
        appendLine("6. intent：用户明确要求记录（记录一下/帮我记/记一笔）用 EXPLICIT；只是聊天中提到用 SUGGESTED。")
        appendLine("7. 不要编造用户没有说过的数字、单位、日期或目标；note 用用户原话或极简改写，不超过 200 字。")
    }.trim()

    /**
     * 日记提取：把最近相关对话整理成一篇第一人称日记草稿。
     * 只允许使用对话里出现的事实；不编造情绪和事件。
     */
    fun diaryExtractionPrompt(
        transcriptLines: List<String>,
        today: String,
    ): String = buildString {
        appendLine("你是日记整理器，只做结构化提取，不聊天、不解释、不输出 Markdown。")
        appendLine("今天的日期：$today。只输出一个 JSON 对象。")
        appendLine()
        appendLine("最近的相关对话（每条带角色）：")
        if (transcriptLines.isEmpty()) appendLine("（无）") else transcriptLines.forEach { appendLine("- $it") }
        appendLine()
        appendLine("输出 schema：")
        appendLine(
            """{"schemaVersion":1,"record":{"type":"DIARY","title":"可选标题",""" +
                """"body":"第一人称日记正文","occurredAt":"yyyy-MM-dd","intent":"EXPLICIT"}}""",
        )
        appendLine("没有值得整理的日记内容时输出：{\"schemaVersion\":1,\"record\":null}")
        appendLine()
        appendLine("规则：")
        appendLine("1. 只使用对话中**明确出现**的事实（做了什么、见了谁、发生了什么、结果如何）。")
        appendLine("2. 不编造事件、人物、数字、日期，也不替用户添加没有表达过的情绪。")
        appendLine("2.1 只整理用户最近明确要求整理的这一段对话；更早的无关话题（例如之前聊过的存款细节）不要写进来。")
        appendLine("2.2 只写用户自己说过的事实与感受；不要把你（顾问）的建议、分析、追问改写成用户的反思。")
        appendLine("3. 用户只是表达情绪（如「今天有点难过」）时，不生成日记，返回 null。")
        appendLine("4. 正文用用户第一人称，按时间或主题自然组织，保留具体细节，200-800 字以内。")
        appendLine("5. 标题可选，不超过 20 字；不确定就省略 title。")
        appendLine("6. occurredAt 只在对话明确提到日期时填写，否则省略。")
        appendLine("7. intent：用户明确要求整理/写日记时 EXPLICIT，否则 SUGGESTED。")
    }.trim()

    /**
     * 目标提取：创建 / 修改 / 拆解。
     * 只提出候选，不允许自动写入；修改必须引用已有目标 id。
     */
    fun goalExtractionPrompt(
        goalLines: List<String>,
        today: String,
        dismissedBreakdownGoalIds: List<Long> = emptyList(),
    ): String = buildString {
        appendLine("你是目标整理器，只做结构化提取，不聊天、不解释、不输出 Markdown。")
        appendLine("今天的日期：$today。只输出一个 JSON 对象。")
        appendLine()
        appendLine("用户已有目标（只能引用这些 id）：")
        if (goalLines.isEmpty()) appendLine("（无）") else goalLines.forEach { appendLine("- $it") }
        appendLine()
        appendLine("输出 schema：")
        appendLine(
            """{"schemaVersion":1,"record":{"type":"GOAL","change":"CREATE","goalId":null,""" +
                """"title":"目标标题","description":null,"level":"LONG_TERM","domain":"CAREER",""" +
                """"dueAt":"yyyy-MM-dd","parentId":null,"breakdown":[{"title":"阶段","level":"STAGE"}],""" +
                """"uncertain":["截止日期"],"intent":"EXPLICIT"}}""",
        )
        appendLine("不需要生成目标时输出：{\"schemaVersion\":1,\"record\":null}")
        appendLine()
        appendLine("规则：")
        appendLine("1. 只有用户明确要求创建/修改/拆解目标时才返回 record；普通愿望、计划、行动、进展都返回 null。")
        appendLine("2. 模糊愿望（如「最近有点想学吉他」）不建目标；用户说「设成目标」才建。")
        appendLine("2.1 但用户带明确时间跨度与具体方向的自述（如「我想在三年内找到一份更有成长空间的工作」）可以生成 CREATE 草稿。")
        appendLine("3. change：CREATE 新建；UPDATE 修改已有目标（必须给 goalId）；BREAKDOWN 只拆解已有目标（必须给 goalId）。")
        appendLine("4. 修改只填写用户明确要改的字段；没提到的字段留空，不要顺手改状态、期限或层级。")
        appendLine("5. level 只能是 LONG_TERM / STAGE / ACTION；STAGE/ACTION 需要 parentId，否则先不要建。")
        appendLine("6. domain 只能是 CAREER / HEALTH / WEALTH / LIFE / LEARNING。")
        appendLine("7. 不要编造薪资、期限、阶段目标；用户没说的写进 uncertain。")
        appendLine("8. breakdown 最多 4 条，仅在用户要求拆解或明显不知从何开始时给；每条只写标题与 level。")
        appendLine("9. 先检查已有目标，避免重复创建；如果只是同类目标，优先建议 UPDATE。")
        appendLine("10. dueAt 只在用户明确给出时间范围时填写（如「三年内」），否则留空。")
        if (dismissedBreakdownGoalIds.isNotEmpty()) {
            appendLine(
                "11. 用户刚刚拒绝过这些目标的拆解建议：${dismissedBreakdownGoalIds.joinToString()}；" +
                    "除非用户再次明确要求，不要重复建议拆解它们。",
            )
        }
    }.trim()

    /**
     * 客户端只按纯文本渲染；把模型常用的 Markdown 标记清掉，避免出现原始 ** / #。
     */
    fun stripMarkdown(raw: String): String {
        var text = raw
        text = text.replace(Regex("```[a-zA-Z]*\\n?"), "").replace("```", "")
        text = text.replace(Regex("\\*\\*(.+?)\\*\\*", RegexOption.DOT_MATCHES_ALL), "$1")
        text = text.replace(Regex("(?m)^#{1,6}\\s+"), "")
        text = text.replace(Regex("(?m)^\\s*[*+]\\s+"), "· ")
        text = text.replace("`", "")
        text = text.replace(Regex("\\n{3,}"), "\n\n")
        return text.trim()
    }

    /**
     * 小计划提取：明确未来行动 → CREATE；引用既有计划并说完成/暂停/取消 → STATUS。
     * “练了一次/昨天练了”属于进展事实，不是计划完成。
     */
    fun planExtractionPrompt(
        planLines: List<String>,
        goalLines: List<String>,
        today: String,
    ): String = buildString {
        appendLine("你是小计划整理器，只做结构化提取，不聊天、不解释、不输出 Markdown。")
        appendLine("今天：$today。只输出一个 JSON 对象。")
        appendLine("已有小计划（STATUS 只能引用这些 id）：")
        if (planLines.isEmpty()) appendLine("（无）") else planLines.forEach { appendLine("- $it") }
        appendLine("可选关联目标：")
        if (goalLines.isEmpty()) appendLine("（无）") else goalLines.forEach { appendLine("- $it") }
        appendLine()
        appendLine(
            """CREATE: {"schemaVersion":1,"record":{"type":"PLAN","change":"CREATE","title":"行动内容",""" +
                """"timeScope":"这周/明天/周末","goalId":null,"intent":"EXPLICIT"}}""",
        )
        appendLine(
            """STATUS: {"schemaVersion":1,"record":{"type":"PLAN","change":"STATUS","planId":3,""" +
                """"status":"COMPLETED|PAUSED|CANCELLED|ACTIVE","note":"用户原话","intent":"EXPLICIT"}}""",
        )
        appendLine("不需要时输出：{\"schemaVersion\":1,\"record\":null}")
        appendLine("规则：")
        appendLine("1. 明确的未来行动意向（准备/打算/计划+时间/范围）就 CREATE；随口想法、已完成事实、长期方向都不要。")
        appendLine("1.1 用户明确说“记成小计划/记一条计划”时，即使没说具体哪天、每次多久，也要用已有信息生成 CREATE 草稿（时间范围可留用户原话），不要反问。")
        appendLine("2. 只有用户明确说某计划完成/暂停/取消/恢复时才 STATUS；一次练习不等于完成。")
        appendLine("3. 时间范围只写用户给出的（这周/明天/周末/每天）；没给就留空，不编造频率和截止日期。")
        appendLine("4. 关联目标不确定就留空；不要为了凑关联而猜。")
        appendLine("5. 有多个相似计划且无法确定时，把 status 留空并返回 record:null。")
    }.trim()

    /**
     * Care Planner：在有依据的候选里自主决定是否打扰；只输出 JSON。
     */
    fun carePlannerPrompt(
        candidateLines: List<String>,
        recentDecisionLines: List<String>,
        nowText: String,
        userZone: String,
    ): String = buildString {
        appendLine("你是 MengYa 的主动关心规划器。只在有可靠记录依据时考虑关心用户。")
        appendLine("现在是 $nowText（用户时区 $userZone）。只输出一个 JSON 对象。")
        appendLine("候选机会（只能从中选择一个 id，不能编造）：")
        candidateLines.forEach { appendLine("- $it") }
        if (recentDecisionLines.isNotEmpty()) {
            appendLine("最近的决策（避免重复打扰）：")
            recentDecisionLines.forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine(
            """{"action":"SEND|SCHEDULE|DEFER|DROP","opportunityId":1,"reason":"依据，引用记录",""" +
                """"suggestedText":"一句简短、自然的中文关心，不超过 40 字",""" +
                """"avoid":["不要提的内容"],"nextWindowStart":0,"nextWindowEnd":0}""",
        )
        appendLine("规则：")
        appendLine("1. 只有候选里有真实进展/计划状态时才 SEND；没有可靠依据就 DROP。")
        appendLine("2. 用户刚被问过、最近明确忽略、或计划已完成/已取消，不要 SEND。")
        appendLine("3. 用户可能忙/免打扰时用 SCHEDULE 或 DEFER，不要硬发；但若候选窗口覆盖当前时段、今天尚未打扰过，" +
            "且计划有真实依据，优先 SEND 而不是无限延后。")
        appendLine("4. 不能把推测写成用户事实；不能修改任何目标、计划或进展。")
        appendLine("5. suggestedText 要像熟人问候，不质问、不说教、不编造细节；不超过 40 字。")
        appendLine("6. 若候选之间难以判断，选择更有依据、更贴近当前时间的一个；都不值得就 DROP。")
        appendLine("7. reason 不超过 60 字，suggestedText 不超过 40 字；确保 JSON 在 200 token 内完整结束。")
    }.trim()

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
