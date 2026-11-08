package com.mengyalife.app.data.restore

import android.content.ContentValues
import androidx.room.withTransaction
import com.mengyalife.app.data.backup.CanonicalJson
import com.mengyalife.app.data.local.AppMetaEntity
import com.mengyalife.app.data.local.MengyaDatabase
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 9 表全量替换 + app_meta + 事务内摘要校验。
 * 任何异常让 Room 回滚；不触碰 Key/DataStore。
 */
class RestoreTransaction(private val database: MengyaDatabase) {

    suspend fun apply(plan: RestorePlan, completedAt: Long): Unit = database.withTransaction {
        val db = database.openHelper.writableDatabase
        deleteAll(db)
        if (com.mengyalife.app.BuildConfig.QA_BUILD && com.mengyalife.app.data.restore.QaRestoreFaultInjector.failTransaction) {
            throw RestoreException("QA_TX_FAIL", "QA injected transaction failure")
        }
        insertJournals(db, plan.rows.getValue("journalEntries"))
        insertGoals(db, plan.rows.getValue("goals"))
        insertCheckins(db, plan.rows.getValue("goalCheckins"))
        insertMessages(db, plan.rows.getValue("advisorMessages"))
        insertProposals(db, plan.rows.getValue("goalProposals"))
        insertSignals(db, plan.rows.getValue("growthSignals"))
        insertMemories(db, plan.rows.getValue("growthMemories"))
        insertInsights(db, plan.rows.getValue("insightReports"))
        insertWeeklies(db, plan.rows.getValue("weeklyReviews"))
        insertPlans(db, plan.rows["smallPlans"].orEmpty())
        insertPlanEvents(db, plan.rows["planEvents"].orEmpty())
        insertPlanProgress(db, plan.rows["planProgress"].orEmpty())
        insertCare(db, plan.rows["careOpportunities"].orEmpty(), "care_opportunities")
        insertCare(db, plan.rows["careDecisions"].orEmpty(), "care_decisions")
        insertCare(db, plan.rows["careFeedback"].orEmpty(), "care_feedback")
        insertCare(db, plan.rows["carePreferences"].orEmpty(), "care_preferences")

        database.appMetaDao().upsert(
            AppMetaEntity(
                restoreAttemptId = plan.attemptId,
                restoreBackupId = plan.backupId,
                restoreGeneration = plan.generation,
                restoreSettingsJson = plan.settingsJson,
                restoreCompletedAt = completedAt,
                restoreExpectedSha256 = plan.expectedRestoredSha256,
                restorePayloadSha256 = plan.payloadSha256,
            ),
        )
        verify(db, plan)
    }

    private fun deleteAll(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        listOf(
            "weekly_ai_reviews", "weekly_reviews", "insight_reports", "growth_memories",
            "growth_signals", "goal_proposals", "advisor_messages", "goal_checkins",
            "goals", "journal_entries",
            "small_plans", "plan_events", "plan_progress",
            "care_opportunities", "care_decisions", "care_feedback", "care_preferences",
        ).forEach { db.execSQL("DELETE FROM `$it`") }
    }

    private fun insertJournals(db: androidx.sqlite.db.SupportSQLiteDatabase, rows: List<Map<String, JsonElement>>) =
        rows.forEach { insert(db, "journal_entries", it) }
    private fun insertGoals(db: androidx.sqlite.db.SupportSQLiteDatabase, rows: List<Map<String, JsonElement>>) =
        rows.sortedBy { when (it["level"]?.jsonPrimitive?.content) {
            "LONG_TERM" -> 0; "STAGE" -> 1; "ACTION" -> 2; else -> 3
        } }.forEach { insert(db, "goals", it) }
    private fun insertCheckins(db: androidx.sqlite.db.SupportSQLiteDatabase, rows: List<Map<String, JsonElement>>) =
        rows.forEach { insert(db, "goal_checkins", it) }
    private fun insertMessages(db: androidx.sqlite.db.SupportSQLiteDatabase, rows: List<Map<String, JsonElement>>) =
        rows.forEach { insert(db, "advisor_messages", it) }
    private fun insertProposals(db: androidx.sqlite.db.SupportSQLiteDatabase, rows: List<Map<String, JsonElement>>) =
        rows.forEach { insert(db, "goal_proposals", it) }
    private fun insertSignals(db: androidx.sqlite.db.SupportSQLiteDatabase, rows: List<Map<String, JsonElement>>) =
        rows.forEach { insert(db, "growth_signals", it) }
    private fun insertMemories(db: androidx.sqlite.db.SupportSQLiteDatabase, rows: List<Map<String, JsonElement>>) =
        rows.forEach { insert(db, "growth_memories", it) }
    private fun insertInsights(db: androidx.sqlite.db.SupportSQLiteDatabase, rows: List<Map<String, JsonElement>>) =
        rows.forEach { insert(db, "insight_reports", it) }
    private fun insertWeeklies(db: androidx.sqlite.db.SupportSQLiteDatabase, rows: List<Map<String, JsonElement>>) =
        rows.forEach { insert(db, "weekly_reviews", it) }
    private fun insertPlans(db: androidx.sqlite.db.SupportSQLiteDatabase, rows: List<Map<String, JsonElement>>) =
        rows.forEach { insert(db, "small_plans", it) }
    private fun insertPlanEvents(db: androidx.sqlite.db.SupportSQLiteDatabase, rows: List<Map<String, JsonElement>>) =
        rows.forEach { insert(db, "plan_events", it) }
    private fun insertPlanProgress(db: androidx.sqlite.db.SupportSQLiteDatabase, rows: List<Map<String, JsonElement>>) =
        rows.forEach { insert(db, "plan_progress", it) }
    private fun insertCare(db: androidx.sqlite.db.SupportSQLiteDatabase, rows: List<Map<String, JsonElement>>, table: String) =
        rows.forEach { insert(db, table, it) }

    private fun insert(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String, row: Map<String, JsonElement>) {
        val values = ContentValues()
        row.forEach { (key, value) ->
            val column = camelToSnake(key)
            when (value) {
                is JsonNull -> values.putNull(column)
                is JsonPrimitive -> when {
                    value.isString -> values.put(column, value.content)
                    value.content == "true" -> values.put(column, 1)
                    value.content == "false" -> values.put(column, 0)
                    value.longOrNull != null -> values.put(column, value.longOrNull)
                    else -> values.put(column, value.content.toDoubleOrNull()
                        ?: throw RestoreException("VALUE_TYPE", "$table.$key 非法值"))
                }
                else -> throw RestoreException("VALUE_TYPE", "$table.$key 非法结构")
            }
        }
        if (db.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values) == -1L) {
            throw RestoreException("INSERT_FAILED", "$table 插入失败")
        }
    }

    private fun verify(db: androidx.sqlite.db.SupportSQLiteDatabase, plan: RestorePlan) {
        val tables = RestoreTables.RESTORED
        val actual = linkedMapOf<String, JsonArray>()
        tables.forEach { key ->
            val sql = RestoreTables.SQL_TABLES.getValue(key)
            val rows = mutableListOf<JsonObject>()
            db.query("SELECT * FROM `$sql` ORDER BY `id`").use { c ->
                while (c.moveToNext()) {
                    val row = buildJsonObject {
                        for (i in 0 until c.columnCount) {
                            val name = c.getColumnName(i)
                            val jsonKey = snakeToCamel(name)
                            when {
                                c.isNull(i) -> put(jsonKey, JsonNull)
                                jsonKey in BOOLEAN_FIELDS -> put(jsonKey, c.getInt(i) == 1)
                                c.getType(i) == android.database.Cursor.FIELD_TYPE_INTEGER -> put(jsonKey, c.getLong(i))
                                c.getType(i) == android.database.Cursor.FIELD_TYPE_FLOAT -> put(jsonKey, c.getDouble(i))
                                else -> put(jsonKey, c.getString(i))
                            }
                        }
                    }
                    rows += row
                }
            }
            actual[key] = JsonArray(rows)
            if (key == "smallPlans") {
                val exp = plan.rows[key]?.firstOrNull()
                val act = rows.firstOrNull()
                android.util.Log.i("RestoreVerify", "exp=${exp?.keys} act=${act?.keys} expRow=$exp actRow=$act")
            }
            val optional = key in setOf(
                "smallPlans", "planEvents", "planProgress",
                "careOpportunities", "careDecisions", "careFeedback", "carePreferences",
            )
            val expected = plan.perTableSha256[key]
                ?: if (optional) {
                    // 旧备份没有这两张表：按空表恢复，同样按空数组摘要校验。
                    CanonicalJson.sha256Hex(JsonArray(emptyList()))
                } else {
                    throw RestoreException("RESTORE_VERIFY", "缺少预期表摘要 $key")
                }
            if (CanonicalJson.sha256Hex(actual.getValue(key)) != expected) {
                throw RestoreException("RESTORE_VERIFY", "恢复后表摘要不匹配：$key")
            }
        }
        val dataObject = buildJsonObject { actual.forEach { (k, v) -> put(k, v) } }
        val expectedObject = buildJsonObject {
            put("data", dataObject)
            put("settings", kotlinx.serialization.json.Json.parseToJsonElement(plan.settingsJson))
        }
        if (CanonicalJson.sha256Hex(expectedObject) != plan.expectedRestoredSha256) {
            throw RestoreException("RESTORE_VERIFY", "恢复后规范化摘要不匹配")
        }
    }

    private fun camelToSnake(value: String): String = buildString {
        value.forEachIndexed { index, ch ->
            if (ch.isUpperCase() && index > 0) append('_')
            append(ch.lowercaseChar())
        }
    }

    private fun snakeToCamel(value: String): String {
        val parts = value.split('_')
        return parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    }

    private companion object {
        val BOOLEAN_FIELDS = setOf("isInference", "userCorrected", "insufficient", "proposalMode", "modelOk")
    }
}
