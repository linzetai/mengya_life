package com.mengyalife.app.data.advisor

import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorError
import com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig
import com.mengyalife.app.domain.advisor.AdvisorRules
import com.mengyalife.app.domain.advisor.AdvisorTestResult
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException

/** AI 顾问引擎。只接收已经构造好的消息，不接触数据库。 */
interface AdvisorEngine {
    suspend fun chat(
        config: AdvisorRuntimeConfig,
        messages: List<AdvisorChatMessage>,
        maxTokens: Int = AdvisorRules.MAX_OUTPUT_TOKENS,
    ): AdvisorChatResult

    suspend fun testConnection(config: AdvisorRuntimeConfig): AdvisorTestResult
}

/**
 * OpenAI-compatible Chat Completions 适配器。
 *
 * 只实现一个端点：`POST {base}/v1/chat/completions`。
 * 不实现流式、工具调用、多模型路由——Gate 3A 先保证链路可靠。
 */
class NetworkAdvisorEngine(
    private val transport: HttpTransport = UrlConnectionHttpTransport(),
) : AdvisorEngine {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun chat(
        config: AdvisorRuntimeConfig,
        messages: List<AdvisorChatMessage>,
        maxTokens: Int,
    ): AdvisorChatResult {
        AdvisorRules.validateEndpoint(config.endpoint)?.let {
            return AdvisorChatResult.Failure(AdvisorError(AdvisorError.INVALID_ENDPOINT, it))
        }
        if (config.model.isBlank() || config.apiKey.isBlank()) {
            return AdvisorChatResult.Failure(AdvisorError.notConfigured())
        }

        val requestBody = buildJsonObject {
            put("model", config.model)
            put(
                "messages",
                buildJsonArray {
                    messages.forEach { message ->
                        add(
                            buildJsonObject {
                                put("role", message.role)
                                put("content", message.content)
                            },
                        )
                    }
                },
            )
            put("max_tokens", maxTokens)
            put("temperature", 0.6)
            put("stream", false)
        }.toString()

        val response = try {
            transport.postJson(
                url = AdvisorRules.chatCompletionsUrl(config.endpoint),
                headers = mapOf(
                    "Authorization" to "Bearer ${config.apiKey}",
                    "Content-Type" to "application/json",
                    "Accept" to "application/json",
                    "User-Agent" to "MengyaLife/0.6",
                ),
                body = requestBody,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (timeout: SocketTimeoutException) {
            return AdvisorChatResult.Failure(AdvisorError.timeout())
        } catch (offline: UnknownHostException) {
            return AdvisorChatResult.Failure(AdvisorError.offline())
        } catch (refused: ConnectException) {
            return AdvisorChatResult.Failure(AdvisorError.offline())
        } catch (tls: SSLException) {
            return AdvisorChatResult.Failure(
                AdvisorError(AdvisorError.OFFLINE, "无法安全连接到模型服务，请检查地址与证书。"),
            )
        } catch (io: IOException) {
            return AdvisorChatResult.Failure(AdvisorError.offline())
        } catch (other: Throwable) {
            return AdvisorChatResult.Failure(AdvisorError.unknown())
        }

        return when (response.code) {
            200 -> parseSuccess(response.body)
            401, 403 -> AdvisorChatResult.Failure(AdvisorError.unauthorized())
            429 -> AdvisorChatResult.Failure(AdvisorError.rateLimited())
            in 500..599 -> AdvisorChatResult.Failure(AdvisorError.server())
            301, 302, 303, 307, 308 -> AdvisorChatResult.Failure(
                AdvisorError(AdvisorError.HTTP, "服务地址发生了重定向（HTTP ${response.code}），请直接填写最终地址。"),
            )
            else -> AdvisorChatResult.Failure(
                AdvisorError(
                    code = AdvisorError.HTTP,
                    userMessage = buildString {
                        append("模型服务返回错误（HTTP ${response.code}）")
                        serverErrorMessage(response.body)?.let { append("：$it") }
                    },
                ),
            )
        }
    }

    override suspend fun testConnection(config: AdvisorRuntimeConfig): AdvisorTestResult {
        val result = chat(
            config = config,
            messages = listOf(
                AdvisorChatMessage(role = "user", content = "请只回复：OK"),
            ),
            maxTokens = 8,
        )
        return when (result) {
            is AdvisorChatResult.Success -> AdvisorTestResult(
                success = true,
                message = "连接成功：模型返回了 ${result.content.length} 字。",
            )
            is AdvisorChatResult.Failure -> AdvisorTestResult(
                success = false,
                message = result.error.userMessage,
            )
        }
    }

    private fun parseSuccess(body: String): AdvisorChatResult = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val content = root["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
        if (content.isNullOrEmpty()) {
            AdvisorChatResult.Failure(AdvisorError.invalidResponse())
        } else {
            AdvisorChatResult.Success(content)
        }
    }.getOrElse {
        AdvisorChatResult.Failure(AdvisorError.invalidResponse())
    }

    private fun serverErrorMessage(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.replace('\n', ' ')
            ?.take(160)
    }.getOrNull()
}

/**
 * 仅用于 QA 构建/离线自动化的假引擎。
 *
 * - 明确在回复里写「QA 离线测试引擎」，不会伪装成真实模型；
 * - 通过输入里的标记可以模拟 401 / 429 / 超时 / 离线 / 格式错误；
 * - 正式包不会创建它。
 */
class FakeAdvisorEngine(
    private val delayMs: Long = 250,
) : AdvisorEngine {
    private var growthInsightRequests = 0

    override suspend fun chat(
        config: AdvisorRuntimeConfig,
        messages: List<AdvisorChatMessage>,
        maxTokens: Int,
    ): AdvisorChatResult {
        delay(delayMs)
        val last = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        if (last.contains("[slow]")) delay(10_000)
        val planPrompt = messages.firstOrNull {
            it.role == "system" && it.content.contains("你是小计划整理器")
        }?.content
        if (planPrompt != null) {
            return AdvisorChatResult.Success(fakePlanExtraction(last))
        }
        val extractPrompt = messages.firstOrNull {
            it.role == "system" && it.content.contains("记录提取器")
        }?.content
        if (extractPrompt != null) {
            return AdvisorChatResult.Success(fakeRecordExtraction(extractPrompt, last))
        }
        val diaryPrompt = messages.lastOrNull { it.content.contains("日记正文：") }?.content
        if (diaryPrompt != null) {
            val body = diaryPrompt.substringAfter("日记正文：").trim()
            val evidence = body.take(24).ifBlank { "日记" }
            val json = kotlinx.serialization.json.buildJsonObject {
                put("schemaVersion", 1)
                put(
                    "signals",
                    kotlinx.serialization.json.buildJsonArray {
                        add(
                            kotlinx.serialization.json.buildJsonObject {
                                put("category", "EVENT")
                                put("content", "记录了一次具体经历")
                                put("evidence", evidence)
                                put("isInference", false)
                                put("confidence", 0.9)
                            },
                        )
                        add(
                            kotlinx.serialization.json.buildJsonObject {
                                put("category", "EMOTION")
                                put("content", "可能伴随情绪波动")
                                put("evidence", evidence)
                                put("isInference", true)
                                put("confidence", 0.4)
                            },
                        )
                    },
                )
            }
            return AdvisorChatResult.Success(json.toString())
        }
        val growthPrompt = messages.firstOrNull {
            it.role == "system" && it.content.contains("GROWTH_INSIGHT_V1")
        }?.let { messages.lastOrNull { m -> m.role == "user" }?.content.orEmpty() }
        if (growthPrompt != null) {
            if (Regex("(?m)^dataSufficient=false$").containsMatchIn(growthPrompt)) {
                return AdvisorChatResult.Failure(AdvisorError.invalidResponse())
            }
            val factIds = Regex("(?m)^可用 factIds：([^\\n]+)$")
                .find(growthPrompt)?.groupValues?.get(1)?.split(',').orEmpty()
            val staleFact = factIds.firstOrNull { it.startsWith("no-recent-progress-") }
            val progressFact = factIds.firstOrNull { it.startsWith("trend-") }
                ?: factIds.firstOrNull { it == "progress-count" }
                ?: factIds.firstOrNull { it != "insufficient" }
            val factId = if (growthInsightRequests++ % 2 == 0) {
                staleFact ?: progressFact
            } else {
                progressFact ?: staleFact
            } ?: return AdvisorChatResult.Failure(AdvisorError.invalidResponse())
            val factBlock = Regex(
                "(?m)^- \\[${Regex.escape(factId)}] ([^\\n]*)\\n\\s+factSources: ([^\\n]*)",
            ).find(growthPrompt) ?: return AdvisorChatResult.Failure(AdvisorError.invalidResponse())
            val factText = factBlock.groupValues[1].substringAfter('：').trim()
            val sourceRefs = factBlock.groupValues[2].split(',').filter { it.contains(':') }
            if (sourceRefs.isEmpty()) return AdvisorChatResult.Failure(AdvisorError.invalidResponse())
            val allowedSources = sourceRefs.map { sourceRef ->
                val sourceLabel = Regex("(?m)^- ${Regex.escape(sourceRef)} \\(([^)]*)\\)$")
                    .find(growthPrompt)?.groupValues?.get(1).orEmpty()
                Triple(sourceRef.substringBefore(':'), sourceRef.substringAfter(':'), sourceLabel)
            }
            val noRecentProgress = factId.startsWith("no-recent-progress-")
            val json = kotlinx.serialization.json.buildJsonObject {
                put("schemaVersion", 1)
                put("summary", "QA 离线建议（只基于当前数据事实）")
                put(
                    "insights",
                    buildJsonArray {
                        add(
                            kotlinx.serialization.json.buildJsonObject {
                                put("type", "SUGGESTION")
                                put("title", if (noRecentProgress) "是否愿意为这个目标安排一个小步骤？" else "可以怎样延续已有变化？")
                                put(
                                    "body",
                                    if (noRecentProgress) {
                                        "$factText 你可以考虑是否安排一个小步骤，也可以不同意或先观察。"
                                    } else {
                                        "$factText 这是本地 QA 引擎的建议，不会写入任何业务数据。"
                                    },
                                )
                                put("isInference", true)
                                put("confidence", 0.6)
                                put("factIds", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(factId)) })
                                put(
                                    "sources",
                                    buildJsonArray {
                                        allowedSources.forEach { (type, id, label) ->
                                            add(
                                                kotlinx.serialization.json.buildJsonObject {
                                                    put("type", type)
                                                    put("id", id)
                                                    put("label", label)
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    },
                )
            }
            return AdvisorChatResult.Success(json.toString())
        }
        val weeklyPrompt = messages.firstOrNull { it.content.contains("你是长期成长顾问") }?.content
        if (weeklyPrompt != null) {
            // QA 专用：用模型名确定性模拟周回顾 AI 的成功/失败/超时；正式包不会创建本引擎。
            when {
                config.model.contains("timeout") -> return AdvisorChatResult.Failure(AdvisorError.timeout())
                config.model.contains("401") -> return AdvisorChatResult.Failure(AdvisorError.unauthorized())
                config.model.contains("429") -> return AdvisorChatResult.Failure(AdvisorError.rateLimited())
                config.model.contains("offline") -> return AdvisorChatResult.Failure(AdvisorError.offline())
                config.model.contains("invalid") -> return AdvisorChatResult.Success("这不是 JSON")
                config.model.contains("slow") -> delay(12_000)
            }
            val factIds = Regex("- \\[([^\\]]+)]").findAll(weeklyPrompt)
                .map { it.groupValues[1] }.take(2).toList()
            val highlight = if (factIds.isEmpty()) "[]" else
                """[{"type":"POSITIVE","title":"QA 周回顾","body":"基于本周事实的本地测试输出。","isInference":true,"factIds":["${factIds.first()}"]}]"""
            return AdvisorChatResult.Success(
                """{"schemaVersion":1,"summary":"（QA 离线测试引擎）本周保持了一个可核验的小节奏。","highlights":$highlight,"suggestion":"下周继续记录一次具体进展。"}""",
            )
        }
        if (messages.any { it.content.contains("整理为目标草案") }) {
            val all = messages.joinToString("\n") { it.content }
            val stageId = Regex("\\[阶段#(\\d+)]").find(all)?.groupValues?.get(1)?.toLongOrNull()
            val goalId = Regex("\\[目标#(\\d+)]").find(all)?.groupValues?.get(1)?.toLongOrNull()
            if (stageId != null) {
                return AdvisorChatResult.Success(
                    """{"schemaVersion":1,"summary":"给已有阶段补充行动","assumptions":[],"risks":[],"goals":[
                    {"tempId":"a1","level":"ACTION","title":"QA 给已有阶段补充行动","description":null,"domain":"HEALTH",
                     "parentTempId":null,"parentGoalId":$stageId,"startDate":null,"dueDate":null,"basis":"QA fake"}]}""",
                )
            }
            if (goalId != null) {
                return AdvisorChatResult.Success(
                    """{"schemaVersion":1,"summary":"给已有长期目标补充阶段","assumptions":[],"risks":[],"goals":[
                    {"tempId":"s1","level":"STAGE","title":"QA 给已有长期目标补充阶段","description":null,"domain":"HEALTH",
                     "parentTempId":null,"parentGoalId":$goalId,"startDate":null,"dueDate":null,"basis":"QA fake"}]}""",
                )
            }
            return AdvisorChatResult.Success(
                """
                {"schemaVersion":1,"summary":"从对话整理的健康目标草案","assumptions":["每周可投入三次"],
                 "risks":["工作忙时容易中断"],"goals":[
                 {"tempId":"g1","level":"LONG","title":"把健康变成长期复利","description":"稳定睡眠与运动","domain":"HEALTH","parentTempId":null,"parentGoalId":null,"startDate":null,"dueDate":null,"basis":"对话讨论"},
                 {"tempId":"s1","level":"STAGE","title":"建立每周三次运动节律","description":null,"domain":"HEALTH","parentTempId":"g1","parentGoalId":null,"startDate":null,"dueDate":null,"basis":"近期进展"},
                 {"tempId":"a1","level":"ACTION","title":"本周安排三次晨跑","description":null,"domain":"HEALTH","parentTempId":"s1","parentGoalId":null,"startDate":null,"dueDate":null,"basis":"可执行下一步"}]}
                """.trimIndent(),
            )
        }
        return when {
            last.contains("[401]") -> AdvisorChatResult.Failure(AdvisorError.unauthorized())
            last.contains("[429]") -> AdvisorChatResult.Failure(AdvisorError.rateLimited())
            last.contains("[timeout]") -> AdvisorChatResult.Failure(AdvisorError.timeout())
            last.contains("[offline]") -> AdvisorChatResult.Failure(AdvisorError.offline())
            last.contains("[invalid]") -> AdvisorChatResult.Failure(AdvisorError.invalidResponse())
            else -> AdvisorChatResult.Success(
                "（QA 离线测试引擎）已收到你的问题：${last.take(200)}\n\n" +
                    "这是本地生成的测试回复，用于验证对话保存、失败重试与重启恢复，" +
                    "不代表真实模型输出。",
            )
        }
    }


    /** QA-only plan parser fixture; never used by release builds or described as real-model quality. */
    private fun fakePlanExtraction(userText: String): String {
        if (!com.mengyalife.app.domain.advisor.PlanIntentGate.looksLikeCreate(userText)) {
            return """{"schemaVersion":1,"record":null}"""
        }
        val scope = listOf("今天", "明天", "这周", "本周", "周末", "下周", "每天")
            .firstOrNull(userText::contains).orEmpty()
        val intent = if (listOf("记成小计划", "记一条计划", "记下来").any(userText::contains)) "EXPLICIT" else "SUGGESTED"
        val record = kotlinx.serialization.json.buildJsonObject {
            put("type", "PLAN")
            put("change", "CREATE")
            put("title", userText.trim().take(100))
            put("timeScope", scope)
            put("goalId", kotlinx.serialization.json.JsonNull)
            put("intent", intent)
        }
        return kotlinx.serialization.json.buildJsonObject {
            put("schemaVersion", 1)
            put("record", record)
        }.toString()
    }

    /**
     * QA 专用：确定性的记录候选提取。只覆盖验收需要的几条自然表达，
     * 真实语义由真实模型完成；这里不会伪装成模型能力。
     */
    private fun fakeRecordExtraction(prompt: String, userText: String): String {
        val goals = Regex("""\[目标#(\d+)]\s*([^（\n]+)（([^）]*)）""").findAll(prompt)
            .map {
                val rawTitle = it.groupValues[2].trim()
                val title = listOf("长期目标：", "阶段目标：", "短期行动：")
                    .firstOrNull { prefix -> rawTitle.startsWith(prefix) }
                    ?.let { prefix -> rawTitle.removePrefix(prefix) }
                    ?: rawTitle
                Triple(it.groupValues[1].toLong(), title, it.groupValues[3])
            }
            .toList()
        fun jsonEscape(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

        val explicit = listOf("记录", "记一下", "记一笔", "帮我记").any { userText.contains(it) }
        val numberMatch = Regex("""(\d+(?:\.\d+)?)\s*(万)?\s*(元|块|小时|次|公里|天|页|本)?""")
            .find(userText)
        val rawValue = numberMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val manSuffix = numberMatch?.groupValues?.getOrNull(2).orEmpty()
        val explicitUnit = numberMatch?.groupValues?.getOrNull(3).orEmpty()
        val unit = when {
            explicitUnit.isNotEmpty() -> explicitUnit
            // 「14.7 万」换算为 147000 元，避免再叠加一个「万元」变成双重单位。
            manSuffix.isNotEmpty() -> "元"
            else -> ""
        }
        val value = when {
            rawValue == null -> null
            manSuffix.isNotEmpty() -> rawValue * 10_000
            else -> rawValue
        }
        val snapshot = listOf("现在", "已经攒到", "累计", "目前有").any { userText.contains(it) } ||
            userText.contains("攒到")
        val amountKind = when {
            value == null && userText.any { it == '完' } -> "NONE"
            value == null -> return """{"schemaVersion":1,"record":null}"""
            snapshot -> "SNAPSHOT"
            else -> "DELTA"
        }
        // 目标匹配：按标题/领域关键词；多个命中时转为澄清候选。
        val wealthWords = listOf("存", "攒", "储蓄", "万", "元")
        val careerWords = listOf("学习", "读", "学", "工作", "职业", "课")
        val matched = goals.filter { (_, title, _) ->
            when {
                wealthWords.any { userText.contains(it) } ->
                    title.contains("存") || title.contains("攒") || title.contains("万") || title.contains("财富")
                careerWords.any { userText.contains(it) } ->
                    title.contains("学习") || title.contains("发展") || title.contains("职业") || title.contains("工作")
                else -> true
            }
        }
        val chosen = matched.singleOrNull()
        val forceAmbiguous = goals.size > 1 && userText.contains("记录一下进展")
        if (forceAmbiguous || chosen == null) {
            val candidates = goals.take(3).joinToString(",") { (id, title, _) ->
                """{"goalId":$id,"title":"${jsonEscape(title)}"}"""
            }
            return """{"schemaVersion":1,"record":{"type":"PROGRESS","goalId":null,"goalTitle":null,""" +
                """"goalCandidates":[$candidates],"amountKind":"$amountKind","metricValue":${value ?: "null"},""" +
                """"metricUnit":"${jsonEscape(unit)}","note":"${jsonEscape(userText)}","occurredAt":null,""" +
                """"pendingFields":["goal"],"intent":"SUGGESTED"}}"""
        }
        val goalId = chosen?.first
        val goalTitle = chosen?.second
        val intent = if (explicit) "EXPLICIT" else "SUGGESTED"
        val metric = if (value == null) "null" else value.toBigDecimal().stripTrailingZeros().toPlainString()
        return """{"schemaVersion":1,"record":{"type":"PROGRESS","goalId":${goalId ?: "null"},""" +
            """"goalTitle":${if (goalTitle == null) "null" else "\"" + jsonEscape(goalTitle) + "\""},""" +
            """"goalCandidates":[],"amountKind":"$amountKind","metricValue":$metric,""" +
            """"metricUnit":"${jsonEscape(unit)}","note":"${jsonEscape(userText)}","occurredAt":null,""" +
            """"pendingFields":${if (goalId == null) "[\"goal\"]" else "[]"},"intent":"$intent"}}"""
    }

    override suspend fun testConnection(config: AdvisorRuntimeConfig): AdvisorTestResult =
        AdvisorTestResult(success = true, message = "QA 离线测试引擎连接成功（未发起网络请求）。")
}
