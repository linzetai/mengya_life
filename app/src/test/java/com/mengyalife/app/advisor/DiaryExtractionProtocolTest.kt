package com.mengyalife.app.advisor

import com.mengyalife.app.domain.growth.DiaryExtractionParseResult
import com.mengyalife.app.domain.growth.DiaryExtractionProtocol
import com.mengyalife.app.domain.growth.GrowthCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryExtractionProtocolTest {

    private val body = "今天完成了一次重要汇报，感到很紧张。晚上散步后好一些。"

    private fun signal(
        category: String = "EVENT",
        content: String = "完成汇报",
        evidence: String = "完成了一次重要汇报",
        inference: Boolean = false,
        confidence: String? = "0.8",
    ) = buildString {
        append("{\"category\":\"$category\",\"content\":\"$content\",\"evidence\":\"$evidence\",\"isInference\":$inference")
        confidence?.let { append(",\"confidence\":$it") }
        append('}')
    }

    private fun payload(vararg signals: String) =
        "{\"schemaVersion\":1,\"signals\":[${signals.joinToString(",")}]}"

    @Test
    fun `合法事实与推测均可解析`() {
        val result = DiaryExtractionProtocol.parse(
            payload(
                signal(),
                signal(category = "EMOTION", content = "感到紧张", evidence = "感到很紧张", inference = true, confidence = "0.5"),
            ),
            "标题", body,
        )
        assertTrue(result is DiaryExtractionParseResult.Success)
        val signals = (result as DiaryExtractionParseResult.Success).signals
        assertEquals(2, signals.size)
        assertEquals(GrowthCategory.EMOTION, signals[1].category)
        assertTrue(signals[1].isInference)
    }

    @Test
    fun `evidence 不在原文中则拒绝`() {
        val result = DiaryExtractionProtocol.parse(payload(signal(evidence = "我升职了")), null, body)
        assertTrue(result is DiaryExtractionParseResult.Failure)
    }

    @Test
    fun `医学诊断内容被拒绝`() {
        val result = DiaryExtractionProtocol.parse(
            payload(signal(content = "诊断为焦虑症", evidence = "感到紧张")), null, body,
        )
        assertTrue(result is DiaryExtractionParseResult.Failure)
    }

    @Test
    fun `非法 schema 类别或 confidence 被拒绝`() {
        assertTrue(DiaryExtractionProtocol.parse("not json", null, body) is DiaryExtractionParseResult.Failure)
        assertTrue(DiaryExtractionProtocol.parse("{\"schemaVersion\":2,\"signals\":[]}", null, body) is DiaryExtractionParseResult.Failure)
        assertTrue(DiaryExtractionProtocol.parse(payload(signal(category = "UNKNOWN")), null, body) is DiaryExtractionParseResult.Failure)
        assertTrue(DiaryExtractionProtocol.parse(payload(signal(confidence = "1.5")), null, body) is DiaryExtractionParseResult.Failure)
        assertTrue(DiaryExtractionProtocol.parse(payload(signal(inference = false)).replace("\"isInference\":false", ""), null, body) is DiaryExtractionParseResult.Failure)
    }
}
