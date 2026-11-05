package com.mengyalife.app.restore

import com.mengyalife.app.backup.BackupTestFixtures
import com.mengyalife.app.data.restore.RestoreException
import com.mengyalife.app.data.restore.RestoreValidator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreValidatorTest {
    private val reader = com.mengyalife.app.data.restore.BackupRestoreReader(enforceCryptoPolicy = false)
    private val pass = RestoreTestSupport.PASS.toCharArray()
    private val validator = RestoreValidator(BackupTestFixtures.app())

    private fun error(payload: kotlinx.serialization.json.JsonObject) =
        (runCatching { validator.validate(payload) }.exceptionOrNull() as RestoreException)

    @Test
    fun `historical dangling refs are warnings plus explicit repairs`() = runTest {
        val opened = reader.open(RestoreTestSupport.artifact().bytes, pass)
        val payload = RestoreTestSupport.mutateTable(opened.payload, "advisorMessages") { rows ->
            rows[0]["contextGoalId"] = JsonPrimitive(999999)
        }
        val result = validator.validate(payload)
        assertTrue(result.warnings.any { it.code == "HISTORICAL_CONTEXT_GOAL" })
        assertTrue(result.repairs.any { it.code == "CONTEXT_GOAL_NULL" })
    }

    @Test
    fun `strong goal parent missing is rejected`() = runTest {
        val opened = reader.open(RestoreTestSupport.artifact().bytes, pass)
        val payload = RestoreTestSupport.mutateTable(opened.payload, "goals") { rows ->
            rows.first { it["id"] == JsonPrimitive(2) }["parentId"] = JsonPrimitive(999999)
        }
        assertEquals("PAYLOAD_INVALID", error(payload).code)
    }

    @Test
    fun `invalid enum duplicate id and invalid double are rejected`() = runTest {
        val opened = reader.open(RestoreTestSupport.artifact().bytes, pass)
        val badEnum = RestoreTestSupport.mutateTable(opened.payload, "goals") { rows ->
            rows.first { it["id"] == JsonPrimitive(2) }["level"] = JsonPrimitive("BOGUS")
        }
        assertEquals("INVALID_ENUM", error(badEnum).code)

        val duplicate = RestoreTestSupport.mutateTable(opened.payload, "goals") { rows ->
            rows.add(rows[0])
        }
        assertEquals("PAYLOAD_INVALID", error(duplicate).code)

        val badDouble = RestoreTestSupport.mutateTable(opened.payload, "goalCheckins") { rows ->
            rows[0]["metricValue"] = JsonPrimitive("not-a-number")
        }
        assertEquals("INVALID_TYPE", error(badDouble).code)
    }

    @Test
    fun `old schema is a warning and legacy fact ids are accepted`() = runTest {
        val opened = reader.open(RestoreTestSupport.artifact().bytes, pass)
        val old = RestoreTestSupport.mutate(opened.payload) { map ->
            val compatibility = (map["compatibility"] as kotlinx.serialization.json.JsonObject).toMutableMap()
            compatibility["dbSchemaVersion"] = JsonPrimitive(10)
            map["compatibility"] = kotlinx.serialization.json.JsonObject(compatibility)
        }
        val result = validator.validate(old)
        assertTrue(result.warnings.any { it.code == "OLD_SCHEMA" })
        val weekly = opened.payload["data"]!!.jsonObject["weeklyAiReviews"]!!.jsonArray[0].jsonObject
        assertEquals(2, weekly["factIds"]!!.jsonArray.size)
        assertTrue(weekly["legacyFactIdsRaw"]!!.jsonPrimitive.content.contains("\u001f"))
    }

    @Test
    fun `abnormally nested JSON is rejected`() = runTest {
        val opened = reader.open(RestoreTestSupport.artifact().bytes, pass)
        val nested = "[".repeat(80) + "0" + "]".repeat(80)
        val payload = RestoreTestSupport.mutateTable(opened.payload, "insightReports") { rows ->
            rows[0]["factsJson"] = JsonPrimitive(nested)
        }
        assertEquals("JSON_DEPTH", error(payload).code)
    }

    @Test
    fun `unknown table is rejected`() = runTest {
        val opened = reader.open(RestoreTestSupport.artifact().bytes, pass)
        val payload = RestoreTestSupport.mutate(opened.payload) { map ->
            val data = (map["data"] as kotlinx.serialization.json.JsonObject).toMutableMap()
            data["futureTable"] = kotlinx.serialization.json.JsonArray(emptyList())
            map["data"] = kotlinx.serialization.json.JsonObject(data)
        }
        assertEquals("PAYLOAD_INVALID", error(payload).code)
    }
}
