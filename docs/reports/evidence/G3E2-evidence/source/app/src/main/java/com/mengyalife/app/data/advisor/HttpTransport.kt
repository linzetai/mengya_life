package com.mengyalife.app.data.advisor

import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class HttpResponse(val code: Int, val body: String)

/** HTTP 传输抽象：生产用 HttpURLConnection，测试用 Fake。 */
interface HttpTransport {
    suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String,
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 60_000,
    ): HttpResponse
}

/**
 * 最小 HttpURLConnection 实现。
 *
 * - 关闭重定向（避免 Authorization 被带到其它主机）；
 * - 支持协程取消：取消时断开连接；
 * - 不打印 URL/请求头/响应体，避免密钥进入日志。
 */
class UrlConnectionHttpTransport : HttpTransport {

    override suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): HttpResponse = suspendCancellableCoroutine { continuation ->
        val connection: HttpURLConnection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                useCaches = false
                instanceFollowRedirects = false
                this.connectTimeout = connectTimeoutMs
                this.readTimeout = readTimeoutMs
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
        } catch (error: Throwable) {
            continuation.resumeWithException(error)
            return@suspendCancellableCoroutine
        }

        continuation.invokeOnCancellation {
            runCatching { connection.disconnect() }
        }

        val worker = Thread {
            try {
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(bytes) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                if (continuation.isActive) {
                    continuation.resume(HttpResponse(code = code, body = text))
                }
            } catch (error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } finally {
                runCatching { connection.disconnect() }
            }
        }
        worker.isDaemon = true
        worker.name = "mengya-ai-http"
        worker.start()
    }
}
