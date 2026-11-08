package com.mengyalife.app.backup

import com.mengyalife.app.data.backup.BackupFormatV2
import com.mengyalife.app.data.backup.BackupValidator
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 备份必须保留进展语义（类型/原话/归一值），否则恢复后趋势会混线。 */
class RecordingBackupSemanticsTest {

    @Test
    fun `goal checkins export keeps amount kind and normalization fields`() {
        val built = BackupFormatV2.encodePayload(
            BackupTestFixtures.snapshot(),
            BackupTestFixtures.settings(),
            BackupTestFixtures.app(),
            "b-semantics",
            BackupTestFixtures.T,
        )
        val result = BackupValidator.validatePayload(built.payload, built.payloadSha256, built.payloadBytes)
        assertTrue(result.errors.joinToString(), result.ok)

        val rows = built.payload["data"]!!.jsonObject["goalCheckins"]!!.jsonArray
        assertTrue(rows.isNotEmpty())
        val row = rows[0].jsonObject
        assertTrue(row.containsKey("amountKind"))
        assertTrue(row.containsKey("originalText"))
        assertTrue(row.containsKey("normalizedValue"))
        assertTrue(row.containsKey("normalizedUnit"))
        assertEquals("snapshot", row["amountKind"]!!.jsonPrimitive.content)
    }
}
