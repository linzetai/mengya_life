package com.mengyalife.app.backup

import com.mengyalife.app.data.backup.BackupFormatV2
import com.mengyalife.app.data.backup.BackupValidator
import com.mengyalife.app.data.local.plan.PlanEventEntity
import com.mengyalife.app.data.local.plan.SmallPlanEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.21：小计划与状态历史必须进入备份；旧备份缺表时仍可校验。 */
class PlanBackupTest {

    private val plan = SmallPlanEntity(
        id = 1, title = "这周练三次吉他", timeScope = "这周", goalId = 12,
        status = "COMPLETED", sourceMessageId = "m1", createdAt = 1000, updatedAt = 2000,
    )
    private val event = PlanEventEntity(id = 1, planId = 1, status = "COMPLETED", note = "练完了", createdAt = 2000)

    @Test
    fun `plans and events are exported with counts and hashes`() {
        val progress = com.mengyalife.app.data.local.plan.PlanProgressEntity(
            id = 1, planId = 1, note = "练了一次", recordedAt = 1500, sourceMessageId = "m2",
        )
        val snapshot = BackupTestFixtures.snapshot().copy(
            smallPlans = listOf(plan), planEvents = listOf(event), planProgress = listOf(progress),
        )
        val built = BackupFormatV2.encodePayload(
            snapshot, BackupTestFixtures.settings(), BackupTestFixtures.app(), "b-plan", BackupTestFixtures.T,
        )
        val result = BackupValidator.validatePayload(built.payload, built.payloadSha256, built.payloadBytes)
        assertTrue(result.errors.joinToString(), result.ok)
        val data = built.payload["data"]!!.jsonObject
        assertEquals(1, data["smallPlans"]!!.jsonArray.size)
        val row = data["smallPlans"]!!.jsonArray[0].jsonObject
        assertEquals("这周练三次吉他", row["title"]!!.jsonPrimitive.content)
        assertEquals("COMPLETED", row["status"]!!.jsonPrimitive.content)
        assertEquals(1, data["planEvents"]!!.jsonArray.size)
        assertEquals("练完了", data["planEvents"]!!.jsonArray[0].jsonObject["note"]!!.jsonPrimitive.content)
        assertEquals(1, data["planProgress"]!!.jsonArray.size)
        assertEquals("练了一次", data["planProgress"]!!.jsonArray[0].jsonObject["note"]!!.jsonPrimitive.content)
    }

    @Test
    fun `old backup without plan tables still validates`() {
        val built = BackupFormatV2.encodePayload(
            BackupTestFixtures.snapshot(), BackupTestFixtures.settings(), BackupTestFixtures.app(),
            "b-old", BackupTestFixtures.T,
        )
        val data = built.payload["data"]!!.jsonObject.toMutableMap()
        data.remove("smallPlans")
        data.remove("planEvents")
        data.remove("planProgress")
        val counts = built.payload["counts"]!!.jsonObject.toMutableMap()
        counts.remove("smallPlans")
        counts.remove("planEvents")
        counts.remove("planProgress")
        val stripped = JsonObject(
            built.payload.toMutableMap() + ("data" to JsonObject(data)) + ("counts" to JsonObject(counts)),
        )
        assertTrue(BackupValidator.validatePayload(stripped, null, null).ok)
    }
}
