package com.mengyalife.app.data.backup

import com.mengyalife.app.data.local.DB_SCHEMA_VERSION
import com.mengyalife.app.data.local.weekly.WeeklyAiEntity
import com.mengyalife.app.domain.weekly.WeeklyAiParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * v2 格式的纯 JVM 实现。
 *
 * 分层：payload 编码 / 每表 hash / payload hash / 外层 envelope / AAD。
 * 不接触 Android Context、文件系统或数据库。
 */
object BackupFormatV2 {
    const val FORMAT = "mengyalife-backup"
    const val VERSION = 2
    const val MIN_READER_VERSION = 2

    val TABLE_KEYS = listOf(
        "journalEntries", "goals", "goalCheckins", "advisorMessages", "goalProposals",
        "growthSignals", "growthMemories", "insightReports", "weeklyReviews", "weeklyAiReviews",
        "smallPlans", "planEvents", "planProgress",
        "careOpportunities", "careDecisions", "careFeedback", "carePreferences",
    )
    val REQUIRED_FEATURES = listOf("core", "derived-insight", "derived-weekly", "weekly-ai-archive")

    private val json = Json { ignoreUnknownKeys = true }

    data class BuiltPayload(
        val payload: JsonObject,
        val payloadBytes: ByteArray,
        val payloadSha256: String,
        val perTableSha256: Map<String, String>,
    )

    fun newBackupId(): String = UUID.randomUUID().toString()

    fun encodePayload(snapshot: BackupSnapshot, settings: BackupSettingsSnapshot,
                      app: BackupAppInfo, backupId: String, createdAt: Long): BuiltPayload {
        val tables = linkedMapOf<String, JsonArray>()
        val hashes = linkedMapOf<String, String>()
        fun addTable(key: String, rows: JsonArray) {
            tables[key] = rows
            hashes[key] = CanonicalJson.sha256Hex(rows)
        }
        addTable("journalEntries", buildJsonArray {
            snapshot.journalEntries.sortedBy { it.id }.forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); putNullable("title", e.title); put("body", e.body)
                    put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
                    put("analysisStatus", e.analysisStatus); putNullable("analysisHash", e.analysisHash)
                    putNullable("analysisError", e.analysisError); put("analysisAttempts", e.analysisAttempts)
                    putNullable("analyzedAt", e.analyzedAt); putNullable("analysisLastAttemptAt", e.analysisLastAttemptAt)
                })
            }
        })
        addTable("goals", buildJsonArray {
            snapshot.goals.sortedWith(compareBy({ it.level.ordinal }, { it.id })).forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("title", e.title); putNullable("description", e.description)
                    put("level", e.level.name); putNullable("parentId", e.parentId)
                    put("domain", e.domain.name); put("status", e.status.name)
                    putNullable("startDate", e.startDate); putNullable("dueDate", e.dueDate)
                    put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
                })
            }
        })
        addTable("goalCheckins", buildJsonArray {
            snapshot.goalCheckins.sortedBy { it.id }.forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("goalId", e.goalId); put("recordedAt", e.recordedAt)
                    put("note", e.note); putNullable("metricValue", e.metricValue)
                    putNullable("metricUnit", e.metricUnit); put("source", e.source)
                    put("amountKind", e.amountKind)
                    putNullable("originalText", e.originalText)
                    putNullable("normalizedValue", e.normalizedValue)
                    putNullable("normalizedUnit", e.normalizedUnit)
                })
            }
        })
        addTable("advisorMessages", buildJsonArray {
            snapshot.advisorMessages.sortedWith(compareBy({ it.createdAt }, { it.id })).forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("role", e.role); put("content", e.content)
                    put("createdAt", e.createdAt); put("status", e.status)
                    putNullable("errorCode", e.errorCode); putNullable("errorMessage", e.errorMessage)
                    putNullable("contextGoalId", e.contextGoalId); put("contextEpoch", e.contextEpoch)
                    putNullable("sourcesJson", e.sourcesJson); putNullable("proposalError", e.proposalError)
                    put("proposalMode", e.proposalMode)
                })
            }
        })
        addTable("goalProposals", buildJsonArray {
            snapshot.goalProposals.sortedWith(compareBy({ it.createdAt }, { it.id })).forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("messageId", e.messageId); put("createdAt", e.createdAt)
                    put("schemaVersion", e.schemaVersion); put("status", e.status)
                    put("summary", e.summary); put("assumptionsJson", e.assumptionsJson)
                    put("risksJson", e.risksJson); put("nodesJson", e.nodesJson)
                    putNullable("selectedTempIdsJson", e.selectedTempIdsJson)
                    putNullable("appliedAt", e.appliedAt)
                    putNullable("appliedGoalIdsJson", e.appliedGoalIdsJson)
                    putNullable("undoneAt", e.undoneAt)
                })
            }
        })
        addTable("growthSignals", buildJsonArray {
            snapshot.growthSignals.sortedWith(compareBy({ it.extractedAt }, { it.id })).forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("sourceType", e.sourceType); put("sourceId", e.sourceId)
                    put("sourceVersion", e.sourceVersion); put("category", e.category)
                    put("content", e.content); put("evidence", e.evidence)
                    put("isInference", e.isInference); putNullable("confidence", e.confidence)
                    put("extractedAt", e.extractedAt); put("status", e.status)
                    put("origin", e.origin); putNullable("sourceTitle", e.sourceTitle)
                    put("contentHash", e.contentHash); put("updatedAt", e.updatedAt)
                })
            }
        })
        addTable("growthMemories", buildJsonArray {
            snapshot.growthMemories.sortedWith(compareBy({ it.updatedAt }, { it.id })).forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("theme", e.theme); put("content", e.content)
                    put("isInference", e.isInference); put("status", e.status)
                    put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
                    put("signalIdsJson", e.signalIdsJson); put("sourceDiaryIdsJson", e.sourceDiaryIdsJson)
                    put("userCorrected", e.userCorrected); put("version", e.version)
                    putNullable("confidence", e.confidence); put("insufficient", e.insufficient)
                    putNullable("recentChange", e.recentChange); put("fingerprint", e.fingerprint)
                    putNullable("deletedAt", e.deletedAt)
                })
            }
        })
        addTable("insightReports", buildJsonArray {
            snapshot.insightReports.sortedWith(compareBy({ it.generatedAt }, { it.id })).forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("dataVersion", e.dataVersion); put("status", e.status)
                    put("summary", e.summary); put("factsJson", e.factsJson); put("insightsJson", e.insightsJson)
                    put("periodStart", e.periodStart); put("periodEnd", e.periodEnd)
                    put("generatedAt", e.generatedAt); putNullable("error", e.error); putNullable("model", e.model)
                })
            }
        })
        addTable("weeklyReviews", buildJsonArray {
            snapshot.weeklyReviews.sortedWith(compareBy({ it.periodStart }, { it.id })).forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("periodKey", e.periodKey)
                    put("periodStart", e.periodStart); put("periodEnd", e.periodEnd)
                    put("generatedAt", e.generatedAt); put("version", e.version)
                    put("dataVersion", e.dataVersion); put("aiConsentVersion", e.aiConsentVersion)
                    put("factsJson", e.factsJson); put("sectionsJson", e.sectionsJson)
                    putNullable("notifiedAt", e.notifiedAt)
                })
            }
        })
        addTable("weeklyAiReviews", buildJsonArray {
            snapshot.weeklyAiReviews.sortedBy { it.periodKey }.forEach { add(weeklyAiJson(it)) }
        })

        val counts = snapshot.counts()
        addTable("smallPlans", buildJsonArray {
            snapshot.smallPlans.sortedBy { it.id }.forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("title", e.title); putNullable("timeScope", e.timeScope)
                    putNullable("goalId", e.goalId); put("status", e.status)
                    putNullable("sourceMessageId", e.sourceMessageId)
                    put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
                })
            }
        })
        addTable("planEvents", buildJsonArray {
            snapshot.planEvents.sortedBy { it.id }.forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("planId", e.planId); put("status", e.status)
                    putNullable("note", e.note); put("createdAt", e.createdAt)
                })
            }
        })
        addTable("planProgress", buildJsonArray {
            snapshot.planProgress.sortedBy { it.id }.forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("planId", e.planId); put("note", e.note)
                    put("recordedAt", e.recordedAt); putNullable("sourceMessageId", e.sourceMessageId)
                })
            }
        })
        addTable("careOpportunities", buildJsonArray {
            snapshot.careOpportunities.sortedBy { it.id }.forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("type", e.type); put("sourceId", e.sourceId)
                    putNullable("planId", e.planId); putNullable("goalId", e.goalId); put("title", e.title)
                    put("discoveredAt", e.discoveredAt); put("windowStart", e.windowStart)
                    put("windowEnd", e.windowEnd); put("expiresAt", e.expiresAt)
                    put("sensitivity", e.sensitivity); put("status", e.status)
                    put("dedupeKey", e.dedupeKey); put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
                })
            }
        })
        addTable("careDecisions", buildJsonArray {
            snapshot.careDecisions.sortedBy { it.id }.forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("opportunityId", e.opportunityId); put("action", e.action)
                    put("reason", e.reason); put("sourceRefs", e.sourceRefs)
                    putNullable("suggestedText", e.suggestedText); putNullable("avoidJson", e.avoidJson)
                    putNullable("nextWindowStart", e.nextWindowStart); putNullable("nextWindowEnd", e.nextWindowEnd)
                    put("modelOk", e.modelOk); put("createdAt", e.createdAt)
                    putNullable("messageId", e.messageId); putNullable("deliveredAt", e.deliveredAt)
                })
            }
        })
        addTable("carePreferences", buildJsonArray {
            snapshot.carePreferences.sortedBy { it.id }.forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("kind", e.kind); putNullable("topicKey", e.topicKey)
                    putNullable("topicLabel", e.topicLabel); put("value", e.value); put("source", e.source)
                    putNullable("sourceMessageId", e.sourceMessageId); putNullable("sourceExcerpt", e.sourceExcerpt)
                    putNullable("windowNote", e.windowNote); put("effectiveAt", e.effectiveAt)
                    putNullable("expiresAt", e.expiresAt); putNullable("revokedAt", e.revokedAt)
                    put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
                })
            }
        })
        addTable("careFeedback", buildJsonArray {
            snapshot.careFeedback.sortedBy { it.id }.forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("opportunityId", e.opportunityId)
                    put("kind", e.kind); put("createdAt", e.createdAt)
                })
            }
        })
        val payload = buildJsonObject {
            put("format", FORMAT)
            put("backupFormatVersion", VERSION)
            put("backupId", backupId)
            put("createdAt", createdAt)
            put("createdAtIso", Instant.ofEpochMilli(createdAt).toString())
            put("generator", buildJsonObject {
                put("appPackage", app.appPackage); put("qaBuild", app.qaBuild)
                put("versionName", app.versionName); put("versionCode", app.versionCode)
                put("androidSdk", app.androidSdk)
            })
            put("compatibility", buildJsonObject {
                put("dbSchemaVersion", DB_SCHEMA_VERSION)
                put("minReaderBackupFormatVersion", MIN_READER_VERSION)
                put("requiredFeatures", buildJsonArray { REQUIRED_FEATURES.forEach { add(JsonPrimitive(it)) } })
            })
            put("counts", buildJsonObject { counts.forEach { (k, v) -> put(k, v) } })
            put("data", buildJsonObject { tables.forEach { (k, v) -> put(k, v) } })
            put("settings", buildJsonObject {
                put("themeMode", settings.themeMode)
                put("weeklyReview", buildJsonObject {
                    put("enabled", settings.weeklyReviewEnabled)
                    put("aiEnabled", settings.weeklyAiEnabled)
                    put("notificationAsked", settings.notificationAsked)
                })
                put("ai", buildJsonObject {
                    put("endpoint", settings.aiEndpoint); put("model", settings.aiModel)
                })
            })
            put("integrity", buildJsonObject {
                put("perTableSha256", buildJsonObject { hashes.forEach { (k, v) -> put(k, v) } })
            })
        }
        val bytes = CanonicalJson.bytes(payload)
        return BuiltPayload(payload, bytes, CanonicalJson.sha256Hex(bytes), hashes)
    }

    fun aad(payloadSha256: String, params: CryptoParams): ByteArray {
        val obj = buildJsonObject {
            put("backupFormatVersion", VERSION)
            put("encryption", buildJsonObject {
                put("cipher", "AES-256-GCM"); put("kdf", "PBKDF2-HMAC-SHA256")
                put("iterations", params.iterations)
                put("salt", base64(params.salt)); put("nonce", base64(params.nonce))
                put("tagBits", 128)
            })
            put("format", FORMAT)
            put("payloadSha256", payloadSha256)
        }
        return CanonicalJson.bytes(obj)
    }

    fun envelope(payloadSha256: String, encrypted: EncryptedPayload, params: CryptoParams): ByteArray {
        val obj = buildJsonObject {
            put("format", FORMAT)
            put("backupFormatVersion", VERSION)
            put("payloadSha256", payloadSha256)
            put("encryption", buildJsonObject {
                put("cipher", "AES-256-GCM"); put("kdf", "PBKDF2-HMAC-SHA256")
                put("iterations", params.iterations)
                put("salt", base64(params.salt)); put("nonce", base64(params.nonce))
                put("tagBits", 128)
            })
            put("ciphertext", base64(encrypted.ciphertextWithTag))
        }
        return CanonicalJson.bytes(obj)
    }

    data class ParsedEnvelope(
        val payloadSha256: String,
        val params: CryptoParams,
        val ciphertext: ByteArray,
        val raw: JsonObject,
    )

    fun parseEnvelope(bytes: ByteArray): ParsedEnvelope {
        val root = runCatching { json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject }
            .getOrElse { throw BackupException("ENVELOPE_JSON", "备份文件不是合法 JSON") }
        if (root["format"]?.jsonPrimitive?.contentOrNull != FORMAT) {
            throw BackupException("ENVELOPE_FORMAT", "不是萌芽人生备份文件")
        }
        if (root["backupFormatVersion"]?.jsonPrimitive?.intOrNull != VERSION) {
            throw BackupException("ENVELOPE_VERSION", "不支持的备份格式版本")
        }
        val enc = root["encryption"] as? JsonObject
            ?: throw BackupException("ENVELOPE_ENCRYPTION", "缺少加密信息")
        if (enc["cipher"]?.jsonPrimitive?.contentOrNull != "AES-256-GCM" ||
            enc["kdf"]?.jsonPrimitive?.contentOrNull != "PBKDF2-HMAC-SHA256" ||
            enc["tagBits"]?.jsonPrimitive?.intOrNull != 128
        ) {
            throw BackupException("ENVELOPE_ALGORITHM", "不支持的加密算法或参数")
        }
        val params = CryptoParams(
            iterations = enc["iterations"]?.jsonPrimitive?.intOrNull
                ?: throw BackupException("ENVELOPE_KDF", "缺少迭代次数"),
            salt = decodeBase64(enc["salt"]?.jsonPrimitive?.contentOrNull, "salt"),
            nonce = decodeBase64(enc["nonce"]?.jsonPrimitive?.contentOrNull, "nonce"),
        )
        BackupCrypto.validateParams(params, enforceProduction = false)
        val payloadSha = root["payloadSha256"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (!payloadSha.matches(Regex("[0-9a-f]{64}"))) {
            throw BackupException("ENVELOPE_HASH", "payloadSha256 非法")
        }
        val ciphertext = decodeBase64(root["ciphertext"]?.jsonPrimitive?.contentOrNull, "ciphertext")
        return ParsedEnvelope(payloadSha, params, ciphertext, root)
    }

    fun decodePayload(bytes: ByteArray): JsonObject =
        runCatching { json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject }
            .getOrElse { throw BackupException("PAYLOAD_JSON", "解密后的载荷不是合法 JSON") }

    fun detect(bytes: ByteArray): BackupFileType {
        val root = runCatching { json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject }
            .getOrNull() ?: return BackupFileType.UNKNOWN
        val format = root["format"]?.jsonPrimitive?.contentOrNull
        if (format != FORMAT) return BackupFileType.UNKNOWN
        if (root["backupFormatVersion"]?.jsonPrimitive?.intOrNull == VERSION &&
            root["encryption"] is JsonObject
        ) return BackupFileType.V2_ENCRYPTED
        if (root["schemaVersion"]?.jsonPrimitive?.intOrNull == 1) return BackupFileType.V1_LEGACY
        return BackupFileType.UNKNOWN
    }

    fun suggestedFileName(createdAt: Long): String =
        "mengya-life-backup-v2-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(java.time.ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(createdAt)) + ".mengyalife"

    private fun weeklyAiJson(e: WeeklyAiEntity): JsonObject {
        val factIds = WeeklyAiParser.decodeFactIds(e.factIdsJson)
        return buildJsonObject {
            put("periodKey", e.periodKey); put("localDataVersion", e.localDataVersion)
            put("aiConsentVersion", e.aiConsentVersion); put("status", e.status)
            put("generatedAt", e.generatedAt); put("summary", e.summary)
            put("highlightsJson", e.highlightsJson); put("suggestion", e.suggestion)
            put("factIds", buildJsonArray { factIds.forEach { add(JsonPrimitive(it)) } })
            put("legacyFactIdsRaw", e.factIdsJson)
            put("categories", e.categories); putNullable("error", e.error)
            putNullable("model", e.model); put("configHash", e.configHash)
        }
    }

    private fun JsonObjectBuilder.putNullable(key: String, value: String?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun JsonObjectBuilder.putNullable(key: String, value: Long?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun JsonObjectBuilder.putNullable(key: String, value: Double?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun JsonObjectBuilder.putNullable(key: String, value: Boolean?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decodeBase64(value: String?, field: String): ByteArray {
        if (value.isNullOrBlank()) throw BackupException("ENVELOPE_FIELD", "缺少 $field")
        return runCatching { Base64.getDecoder().decode(value) }
            .getOrElse { throw BackupException("ENVELOPE_FIELD", "$field 不是合法 base64") }
    }
}
