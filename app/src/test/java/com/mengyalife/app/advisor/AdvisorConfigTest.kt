package com.mengyalife.app.advisor

import com.mengyalife.app.data.advisor.DataStoreAdvisorConfigRepository
import com.mengyalife.app.data.advisor.StoredAiConfig
import com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig
import com.mengyalife.app.domain.advisor.AiConfigState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorConfigTest {

    private fun repo(
        storage: FakeAiConfigStorage = FakeAiConfigStorage(),
        cipher: FakeSecretCipher = FakeSecretCipher(),
    ) = Triple(
        DataStoreAdvisorConfigRepository(storage, cipher, allowFakeEngine = true),
        storage,
        cipher,
    )

    @Test
    fun `保存配置后状态只有 hasKey，不含明文`() = runTest {
        val (repository, storage, _) = repo()
        val result = repository.save("https://api.example.com/v1", "deepseek-chat", "sk-secret-123")
        assertTrue(result.ok)

        val state = repository.state.first()
        assertTrue(state.configured)
        assertTrue(state.hasKey)
        assertFalse(state.toString().contains("sk-secret-123"))
        assertFalse(storage.state.value.encryptedKey!!.contains("sk-secret-123"))
    }

    @Test
    fun `恢复设置只更新端点并保留本机密钥`() = runTest {
        val (repository, _, _) = repo()
        repository.save("https://old.example.com/v1", "old-model", "sk-keep-me")
        repository.restoreEndpointAndModel("https://api.deepseek.com", "deepseek-flash")
        val state = repository.state.first()
        assertEquals("https://api.deepseek.com", state.endpoint)
        assertEquals("deepseek-flash", state.model)
        assertTrue(state.hasKey)
        assertEquals("sk-keep-me", repository.runtimeConfig()!!.apiKey)
    }

    @Test
    fun `运行时配置能解密，删除密钥后不可用`() = runTest {
        val (repository, storage, cipher) = repo()
        repository.save("https://api.example.com/v1", "deepseek-chat", "sk-secret-123")

        val runtime = repository.runtimeConfig()!!
        assertEquals("sk-secret-123", runtime.apiKey)
        assertEquals("https://api.example.com/v1", runtime.endpoint)
        assertFalse(runtime.toString().contains("sk-secret-123"))

        repository.deleteKey()
        assertNull(storage.state.value.encryptedKey)
        assertNull(repository.runtimeConfig())
        assertEquals(1, cipher.deleted)
        assertFalse(repository.state.first().hasKey)
    }

    @Test
    fun `留空 key 保存时保留旧密钥`() = runTest {
        val (repository, storage, _) = repo()
        repository.save("https://api.example.com/v1", "m1", "sk-old")
        val oldBlob = storage.state.value.encryptedKey
        repository.save("https://api.example.com/v2", "m2", null)
        assertEquals(oldBlob, storage.state.value.encryptedKey)
        assertEquals("https://api.example.com/v2", repository.runtimeConfig()!!.endpoint)
    }

    @Test
    fun `第一次保存必须提供 key`() = runTest {
        val (repository, _, _) = repo()
        val result = repository.save("https://api.example.com/v1", "m", null)
        assertFalse(result.ok)
        assertEquals("API Key 不能为空", result.error)
    }

    @Test
    fun `http 地址与空模型被拒绝`() = runTest {
        val (repository, _, _) = repo()
        assertFalse(repository.save("http://api.example.com", "m", "sk").ok)
        assertFalse(repository.save("https://api.example.com", " ", "sk").ok)
        assertFalse(repository.save("https://api.example.com", "m", " ").ok)
    }

    @Test
    fun `QA 假引擎开关可持久化，生产构造时被忽略`() = runTest {
        val (repository, storage, _) = repo()
        repository.setUseFakeEngine(true)
        assertTrue(storage.state.value.useFakeEngine)
        assertTrue(repository.state.first().useFakeEngine)

        val prod = DataStoreAdvisorConfigRepository(storage, FakeSecretCipher(), allowFakeEngine = false)
        assertFalse(prod.state.first().useFakeEngine)
        prod.setUseFakeEngine(false) // 不应抛异常
    }

    @Test
    fun `解密失败时 runtimeConfig 返回 null`() = runTest {
        val storage = FakeAiConfigStorage(
            StoredAiConfig(
                endpoint = "https://api.example.com/v1",
                model = "m",
                encryptedKey = "corrupted",
            ),
        )
        val repository = DataStoreAdvisorConfigRepository(storage, FakeSecretCipher(), allowFakeEngine = true)
        assertNull(repository.runtimeConfig())
    }

    @Test
    fun `状态对象不含 key 字段`() {
        val state = AiConfigState("https://x/v1", "m", hasKey = true)
        assertFalse(state.toString().contains("sk-"))
    }
}
