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

    override suspend fun chat(
        config: AdvisorRuntimeConfig,
        messages: List<AdvisorChatMessage>,
        maxTokens: Int,
    ): AdvisorChatResult {
        delay(delayMs)
        val last = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        if (last.contains("[slow]")) delay(10_000)
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

    override suspend fun testConnection(config: AdvisorRuntimeConfig): AdvisorTestResult =
        AdvisorTestResult(success = true, message = "QA 离线测试引擎连接成功（未发起网络请求）。")
}
