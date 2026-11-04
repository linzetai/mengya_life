package com.mengyalife.app.domain.advisor

import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.LocalDate

/** AI 目标草案。只读，不写 goals / goal_checkins。 */
data class GoalProposalDraft(
    val schemaVersion: Int,
    val summary: String,
    val assumptions: List<String>,
    val risks: List<String>,
    val nodes: List<GoalProposalNode>,
)

data class GoalProposalNode(
    val tempId: String,
    val level: GoalLevel,
    val parentTempId: String?,
    val parentGoalId: Long?,
    val title: String,
    val description: String?,
    val domain: GoalDomain,
    val startDate: String?,
    val dueDate: String?,
    val basis: String?,
)

sealed interface GoalProposalParseResult {
    data class Success(val draft: GoalProposalDraft) : GoalProposalParseResult
    data class Failure(val message: String) : GoalProposalParseResult
}

/**
 * 严格解析 AI 输出的目标草案 JSON。
 *
 * - 允许代码围栏/前后说明，但只截取第一个 `{` 到最后一个 `}`；
 * - 校验层级、临时父引用、已有父目标 ID、重复 ID、循环依赖；
 * - 任意非法输入返回 Failure，不抛异常。
 */
object GoalProposalParser {

    const val SCHEMA_VERSION = 1
    const val MAX_NODES = 30
    const val MAX_TITLE = 60
    const val MAX_DESCRIPTION = 1000

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        raw: String,
        existingGoals: Map<Long, GoalLevel>,
    ): GoalProposalParseResult {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return GoalProposalParseResult.Failure("没有找到 JSON 对象")
        val objectText = raw.substring(start, end + 1)
        val root = runCatching { json.parseToJsonElement(objectText).jsonObject }
            .getOrElse { return GoalProposalParseResult.Failure("JSON 解析失败") }

        if (root["schemaVersion"]?.jsonPrimitive?.intOrNull != SCHEMA_VERSION) {
            return GoalProposalParseResult.Failure("schemaVersion 必须为 $SCHEMA_VERSION")
        }
        val summary = root["summary"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (summary.isEmpty()) return GoalProposalParseResult.Failure("缺少 summary")
        val assumptions = stringList(root["assumptions"])
        val risks = stringList(root["risks"])
        val goalArray = root["goals"] as? JsonArray
            ?: return GoalProposalParseResult.Failure("缺少 goals 数组")
        if (goalArray.isEmpty()) return GoalProposalParseResult.Failure("goals 不能为空")
        if (goalArray.size > MAX_NODES) return GoalProposalParseResult.Failure("草案节点过多")

        val nodes = mutableListOf<GoalProposalNode>()
        val tempIds = mutableSetOf<String>()
        for ((index, element) in goalArray.withIndex()) {
            val obj = element as? JsonObject
                ?: return GoalProposalParseResult.Failure("goals[$index] 不是对象")
            val tempId = obj["tempId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (tempId.isEmpty()) return GoalProposalParseResult.Failure("goals[$index] 缺少 tempId")
            if (!GoalProposalIds.isValidTempId(tempId)) {
                return GoalProposalParseResult.Failure("tempId 格式非法：$tempId")
            }
            if (!tempIds.add(tempId)) return GoalProposalParseResult.Failure("tempId 重复：$tempId")
            val level = when (obj["level"]?.jsonPrimitive?.contentOrNull?.uppercase()) {
                "LONG", "LONG_TERM" -> GoalLevel.LONG_TERM
                "STAGE" -> GoalLevel.STAGE
                "ACTION" -> GoalLevel.ACTION
                else -> return GoalProposalParseResult.Failure("$tempId 的 level 非法")
            }
            val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (title.isEmpty()) return GoalProposalParseResult.Failure("$tempId 缺少标题")
            if (title.length > MAX_TITLE) return GoalProposalParseResult.Failure("$tempId 标题过长")
            val description = obj["description"]?.jsonPrimitive?.contentOrNull?.trim()
            if ((description?.length ?: 0) > MAX_DESCRIPTION) {
                return GoalProposalParseResult.Failure("$tempId 描述过长")
            }
            val domain = GoalDomain.entries.firstOrNull {
                it.name == obj["domain"]?.jsonPrimitive?.contentOrNull?.uppercase()
            } ?: GoalDomain.LIFE
            val parentTempId = obj["parentTempId"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            if (parentTempId != null && !GoalProposalIds.isValidTempId(parentTempId)) {
                return GoalProposalParseResult.Failure("parentTempId 格式非法：$parentTempId")
            }
            val parentGoalId = obj["parentGoalId"]?.jsonPrimitive?.longOrNull
            if (parentGoalId != null && parentGoalId <= 0) {
                return GoalProposalParseResult.Failure("parentGoalId 必须为正数")
            }
            val startDate = obj["startDate"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            val dueDate = obj["dueDate"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            validateDate(startDate)?.let { return GoalProposalParseResult.Failure("$tempId：$it") }
            validateDate(dueDate)?.let { return GoalProposalParseResult.Failure("$tempId：$it") }
            if (startDate != null && dueDate != null && dueDate < startDate) {
                return GoalProposalParseResult.Failure("$tempId 截止日期早于开始日期")
            }
            nodes.add(
                GoalProposalNode(
                    tempId = tempId,
                    level = level,
                    parentTempId = parentTempId,
                    parentGoalId = parentGoalId,
                    title = title,
                    description = description,
                    domain = domain,
                    startDate = startDate,
                    dueDate = dueDate,
                    basis = obj["basis"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }

        val byTemp = nodes.associateBy { it.tempId }
        for (node in nodes) {
            when (node.level) {
                GoalLevel.LONG_TERM -> {
                    if (node.parentTempId != null || node.parentGoalId != null) {
                        return GoalProposalParseResult.Failure("${node.tempId}：长期目标不能有父级")
                    }
                }
                GoalLevel.STAGE -> {
                    val resolved = resolveParentLevel(node, byTemp, existingGoals)
                        ?: return GoalProposalParseResult.Failure("${node.tempId}：父级不存在")
                    if (resolved != GoalLevel.LONG_TERM) {
                        return GoalProposalParseResult.Failure("${node.tempId}：阶段目标只能挂在长期目标下")
                    }
                }
                GoalLevel.ACTION -> {
                    val resolved = resolveParentLevel(node, byTemp, existingGoals)
                        ?: return GoalProposalParseResult.Failure("${node.tempId}：父级不存在")
                    if (resolved != GoalLevel.STAGE) {
                        return GoalProposalParseResult.Failure("${node.tempId}：短期行动只能挂在阶段目标下")
                    }
                }
            }
            if (hasTempCycle(node, byTemp)) {
                return GoalProposalParseResult.Failure("${node.tempId}：存在循环依赖")
            }
            if (node.parentGoalId != null && node.parentGoalId !in existingGoals) {
                return GoalProposalParseResult.Failure("${node.tempId}：引用的已有目标 ${node.parentGoalId} 不存在")
            }
        }

        return GoalProposalParseResult.Success(
            GoalProposalDraft(
                schemaVersion = SCHEMA_VERSION,
                summary = summary,
                assumptions = assumptions,
                risks = risks,
                nodes = nodes,
            ),
        )
    }

    fun encode(draft: GoalProposalDraft): EncodedProposal = EncodedProposal(
        summary = draft.summary,
        assumptionsJson = buildJsonArray { draft.assumptions.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }.toString(),
        risksJson = buildJsonArray { draft.risks.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }.toString(),
        nodesJson = buildJsonArray {
            draft.nodes.forEach { node ->
                add(
                    buildJsonObject {
                        put("tempId", node.tempId)
                        put("level", when (node.level) {
                            GoalLevel.LONG_TERM -> "LONG"
                            GoalLevel.STAGE -> "STAGE"
                            GoalLevel.ACTION -> "ACTION"
                        })
                        put("parentTempId", node.parentTempId)
                        put("parentGoalId", node.parentGoalId)
                        put("title", node.title)
                        put("description", node.description)
                        put("domain", node.domain.name)
                        put("startDate", node.startDate)
                        put("dueDate", node.dueDate)
                        put("basis", node.basis)
                    },
                )
            }
        }.toString(),
    )

    fun decode(
        schemaVersion: Int,
        summary: String,
        assumptionsJson: String,
        risksJson: String,
        nodesJson: String,
    ): GoalProposalDraft? = runCatching {
        val nodes = Json.parseToJsonElement(nodesJson).jsonArray.map { element ->
            val obj = element.jsonObject
            val tempId = obj["tempId"]!!.jsonPrimitive.content
            if (!GoalProposalIds.isValidTempId(tempId)) return@runCatching null
            GoalProposalNode(
                tempId = tempId,
                level = when (obj["level"]?.jsonPrimitive?.contentOrNull) {
                    "LONG", "LONG_TERM" -> GoalLevel.LONG_TERM
                    "STAGE" -> GoalLevel.STAGE
                    else -> GoalLevel.ACTION
                },
                parentTempId = obj["parentTempId"]?.jsonPrimitive?.contentOrNull,
                parentGoalId = obj["parentGoalId"]?.jsonPrimitive?.longOrNull,
                title = obj["title"]!!.jsonPrimitive.content,
                description = obj["description"]?.jsonPrimitive?.contentOrNull,
                domain = GoalDomain.entries.firstOrNull {
                    it.name == obj["domain"]?.jsonPrimitive?.contentOrNull
                } ?: GoalDomain.LIFE,
                startDate = obj["startDate"]?.jsonPrimitive?.contentOrNull,
                dueDate = obj["dueDate"]?.jsonPrimitive?.contentOrNull,
                basis = obj["basis"]?.jsonPrimitive?.contentOrNull,
            )
        }
        if (nodes.isEmpty()) return@runCatching null
        GoalProposalDraft(
            schemaVersion = schemaVersion,
            summary = summary,
            assumptions = Json.parseToJsonElement(assumptionsJson).jsonArray.map {
                it.jsonPrimitive.content
            },
            risks = Json.parseToJsonElement(risksJson).jsonArray.map {
                it.jsonPrimitive.content
            },
            nodes = nodes,
        )
    }.getOrNull()

    data class EncodedProposal(
        val summary: String,
        val assumptionsJson: String,
        val risksJson: String,
        val nodesJson: String,
    )

    private fun resolveParentLevel(
        node: GoalProposalNode,
        byTemp: Map<String, GoalProposalNode>,
        existingGoals: Map<Long, GoalLevel>,
    ): GoalLevel? {
        if (node.parentTempId != null) return byTemp[node.parentTempId]?.level
        if (node.parentGoalId != null) return existingGoals[node.parentGoalId]
        return null
    }

    private fun hasTempCycle(
        node: GoalProposalNode,
        byTemp: Map<String, GoalProposalNode>,
    ): Boolean {
        var cursor = node.parentTempId
        val seen = mutableSetOf(node.tempId)
        var guard = 0
        while (cursor != null && guard < 64) {
            if (!seen.add(cursor)) return true
            cursor = byTemp[cursor]?.parentTempId
            guard++
        }
        return false
    }

    private fun stringList(element: kotlinx.serialization.json.JsonElement?): List<String> =
        runCatching {
            (element as? JsonArray)?.mapNotNull {
                it.jsonPrimitive.contentOrNull?.trim()?.takeIf { value -> value.isNotEmpty() }
            }.orEmpty()
        }.getOrDefault(emptyList())

    private fun validateDate(raw: String?): String? {
        if (raw == null) return null
        return runCatching { LocalDate.parse(raw) }.fold(
            onSuccess = { null },
            onFailure = { "日期必须是 yyyy-MM-dd" },
        )
    }
}
