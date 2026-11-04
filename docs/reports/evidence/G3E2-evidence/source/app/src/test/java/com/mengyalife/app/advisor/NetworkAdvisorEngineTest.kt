package com.mengyalife.app.advisor

import com.mengyalife.app.data.advisor.HttpResponse
import com.mengyalife.app.data.advisor.HttpTransport
import com.mengyalife.app.data.advisor.NetworkAdvisorEngine
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorError
import com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private class FakeTransport : HttpTransport {
    var response: HttpResponse = HttpResponse(200, "{}")
    var error: Throwable? = null
    var calls = 0
    var lastUrl: String? = null
    var lastHeaders: Map<String, String> = emptyMap()
    var lastBody: String = ""

    override suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): HttpResponse {
        calls++
        lastUrl = url
        lastHeaders = headers
        lastBody = body
        error?.let { throw it }
        return response
    }
}

class NetworkAdvisorEngineTest {

    private val config = AdvisorRuntimeConfig(
        endpoint = "https://api.example.com/v1",
        model = "deepseek-chat",
        apiKey = "sk-secret-123",
    )

    private val messages = listOf(AdvisorChatMessage("user", "你好"))

    private fun successBody(content: String = "你好，我是顾问") =
        """{"choices":[{"message":{"role":"assistant","content":"$content"}}]}"""

    @Test
    fun `成功响应解析为助手内容`() = runTest {
        val transport = FakeTransport().apply { response = HttpResponse(200, successBody()) }
        val result = NetworkAdvisorEngine(transport).chat(config, messages)
        assertTrue(result is AdvisorChatResult.Success)
        assertEquals("你好，我是顾问", (result as AdvisorChatResult.Success).content)
        assertEquals("https://api.example.com/v1/chat/completions", transport.lastUrl)
    }

    @Test
    fun `请求体包含模型与消息，Authorization 使用 Bearer`() = runTest {
        val transport = FakeTransport().apply { response = HttpResponse(200, successBody()) }
        NetworkAdvisorEngine(transport).chat(config, messages, maxTokens = 123)
        assertTrue(transport.lastHeaders["Authorization"]!!.startsWith("Bearer sk-"))
        val body = Json.parseToJsonElement(transport.lastBody).jsonObject
        assertEquals("deepseek-chat", body["model"]!!.jsonPrimitive.content)
        assertEquals(123, body["max_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals("user", body["messages"]!!.jsonArray[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("你好", body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertFalse(transport.lastBody.contains("sk-secret-123"))
    }

    @Test
    fun `401 映射为无效密钥`() = runTest {
        val transport = FakeTransport().apply { response = HttpResponse(401, "{}") }
        val result = NetworkAdvisorEngine(transport).chat(config, messages)
        assertEquals(AdvisorError.UNAUTHORIZED, (result as AdvisorChatResult.Failure).error.code)
        assertFalse(result.error.userMessage.contains("sk-secret-123"))
    }

    @Test
    fun `429 映射为限流`() = runTest {
        val transport = FakeTransport().apply { response = HttpResponse(429, "{}") }
        val result = NetworkAdvisorEngine(transport).chat(config, messages)
        assertEquals(AdvisorError.RATE_LIMITED, (result as AdvisorChatResult.Failure).error.code)
    }

    @Test
    fun `5xx 映射为服务端错误`() = runTest {
        val transport = FakeTransport().apply { response = HttpResponse(503, "{}") }
        val result = NetworkAdvisorEngine(transport).chat(config, messages)
        assertEquals(AdvisorError.SERVER, (result as AdvisorChatResult.Failure).error.code)
    }

    @Test
    fun `400 带服务端消息`() = runTest {
        val transport = FakeTransport().apply {
            response = HttpResponse(400, """{"error":{"message":"model not found"}}""")
        }
        val result = NetworkAdvisorEngine(transport).chat(config, messages)
        val failure = result as AdvisorChatResult.Failure
        assertEquals(AdvisorError.HTTP, failure.error.code)
        assertTrue(failure.error.userMessage.contains("model not found"))
    }

    @Test
    fun `格式错误不会伪造回复`() = runTest {
        val transport = FakeTransport().apply { response = HttpResponse(200, "not-json") }
        val result = NetworkAdvisorEngine(transport).chat(config, messages)
        assertEquals(AdvisorError.INVALID_RESPONSE, (result as AdvisorChatResult.Failure).error.code)
    }

    @Test
    fun `空 content 视为格式错误`() = runTest {
        val transport = FakeTransport().apply { response = HttpResponse(200, successBody("")) }
        val result = NetworkAdvisorEngine(transport).chat(config, messages)
        assertEquals(AdvisorError.INVALID_RESPONSE, (result as AdvisorChatResult.Failure).error.code)
    }

    @Test
    fun `超时与离线分别映射`() = runTest {
        val timeout = FakeTransport().apply { error = SocketTimeoutException("timeout") }
        val timeoutResult = NetworkAdvisorEngine(timeout).chat(config, messages)
        assertEquals(AdvisorError.TIMEOUT, (timeoutResult as AdvisorChatResult.Failure).error.code)

        val offline = FakeTransport().apply { error = UnknownHostException("dns") }
        val offlineResult = NetworkAdvisorEngine(offline).chat(config, messages)
        assertEquals(AdvisorError.OFFLINE, (offlineResult as AdvisorChatResult.Failure).error.code)
    }

    @Test
    fun `非 HTTPS 地址在发请求前就被拒绝`() = runTest {
        val transport = FakeTransport()
        val result = NetworkAdvisorEngine(transport).chat(
            config.copy(endpoint = "http://api.example.com/v1"),
            messages,
        )
        assertEquals(AdvisorError.INVALID_ENDPOINT, (result as AdvisorChatResult.Failure).error.code)
        assertEquals(0, transport.calls)
    }

    @Test
    fun `测试连接成功与失败`() = runTest {
        val success = FakeTransport().apply { response = HttpResponse(200, successBody("OK")) }
        assertTrue(NetworkAdvisorEngine(success).testConnection(config).success)

        val failure = FakeTransport().apply { response = HttpResponse(401, "{}") }
        val failed = NetworkAdvisorEngine(failure).testConnection(config)
        assertFalse(failed.success)
        assertTrue(failed.message.contains("401"))
    }
}
