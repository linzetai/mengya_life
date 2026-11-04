package com.mengyalife.app.domain.growth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull

data class ExtractedSignal(
    val category: GrowthCategory,
    val content: String,
    val evidence: String,
    val isInference: Boolean,
    val confidence: Double?,
)

sealed interface DiaryExtractionParseResult {
    data class Success(val signals: List<ExtractedSignal>) : DiaryExtractionParseResult
    data class Failure(val message: String) : DiaryExtractionParseResult
}

/**
 * 日记提取协议 v1。
 *
 * 事实/推测必须由模型显式标记；evidence 必须能在日记原文中找到，
 * 从解析层阻止「编造日记没有记录的事件」。
 */
object DiaryExtractionProtocol {
    const val SCHEMA_VERSION = 1
    const val MAX_SIGNALS = 6
    const val MAX_CONTENT = 500
    const val MAX_EVIDENCE = 500
    const val MAX_DIARY_CHARS = 4_000

    fun prompt(diaryTitle: String?, diaryBody: String): String = """
        你是一个谨慎的成长记录整理器。请只根据下面这篇日记提取 1–6 条成长线索。
        只输出一个 JSON 对象，不要 Markdown，不要解释。schemaVersion 必须为 1：
        {
          "schemaVersion": 1,
          "signals": [
            {
              "category": "EVENT|PROGRESS|BLOCKER|HABIT|EMOTION|ACTION_CANDIDATE",
              "content": "一句话线索",
              "evidence": "日记中的原文摘录（必须逐字出现）",
              "isInference": false,
              "confidence": 0.0
            }
          ]
        }
        规则：
        - 只能使用日记里实际出现的信息，不能编造事件、人物、时间或结果；
        - evidence 必须逐字来自日记，不得改写；
        - 猜测、推断、建议必须 isInference=true，不能写成确定事实；
        - 不做心理疾病或医学诊断，不建议停药/治疗；
        - 不修改、不创建任何目标、进展或打卡；
        - 没有值得提取的内容时返回 {"schemaVersion":1,"signals":[]}。
        日记标题：${diaryTitle ?: "（无标题）"}
        日记正文：
        ${diaryBody.take(MAX_DIARY_CHARS)}
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String, diaryTitle: String?, diaryBody: String): DiaryExtractionParseResult {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return DiaryExtractionParseResult.Failure("没有找到 JSON 对象")
        val root = runCatching { json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject }
            .getOrElse { return DiaryExtractionParseResult.Failure("JSON 解析失败") }
        if (root["schemaVersion"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() != SCHEMA_VERSION) {
            return DiaryExtractionParseResult.Failure("schemaVersion 必须为 $SCHEMA_VERSION")
        }
        val array = root["signals"] as? JsonArray
            ?: return DiaryExtractionParseResult.Failure("缺少 signals 数组")
        if (array.size > MAX_SIGNALS) return DiaryExtractionParseResult.Failure("线索数量超过上限")
        val source = normalize((diaryTitle.orEmpty() + "\n" + diaryBody))
        val seen = mutableSetOf<String>()
        val signals = mutableListOf<ExtractedSignal>()
        for ((index, element) in array.withIndex()) {
            val obj = element as? JsonObject
                ?: return DiaryExtractionParseResult.Failure("signals[$index] 不是对象")
            val category = GrowthCategory.entries.firstOrNull {
                it.name == obj["category"]?.jsonPrimitive?.contentOrNull?.uppercase()
            } ?: return DiaryExtractionParseResult.Failure("signals[$index] category 非法")
            val content = obj["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val evidence = obj["evidence"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (content.isEmpty()) return DiaryExtractionParseResult.Failure("signals[$index] content 为空")
            if (evidence.isEmpty()) return DiaryExtractionParseResult.Failure("signals[$index] evidence 为空")
            if (content.length > MAX_CONTENT) return DiaryExtractionParseResult.Failure("signals[$index] content 过长")
            if (evidence.length > MAX_EVIDENCE) return DiaryExtractionParseResult.Failure("signals[$index] evidence 过长")
            if (!source.contains(normalize(evidence))) {
                return DiaryExtractionParseResult.Failure("signals[$index] evidence 不在日记原文中")
            }
            if (content.contains("诊断为") || content.contains("确诊") || content.contains("建议停药")) {
                return DiaryExtractionParseResult.Failure("signals[$index] 包含医学诊断，已拒绝")
            }
            val inference = obj["isInference"]?.jsonPrimitive?.booleanOrNull
                ?: return DiaryExtractionParseResult.Failure("signals[$index] 缺少 isInference")
            val confidence = obj["confidence"]?.jsonPrimitive?.doubleOrNull?.also {
                if (!it.isFinite() || it < 0 || it > 1) {
                    return DiaryExtractionParseResult.Failure("signals[$index] confidence 非法")
                }
            }
            val key = category.name + "|" + content
            if (!seen.add(key)) continue
            signals.add(ExtractedSignal(category, content, evidence, inference, confidence))
        }
        return DiaryExtractionParseResult.Success(signals)
    }

    private fun normalize(text: String): String =
        text.replace('\r', ' ').replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
}
