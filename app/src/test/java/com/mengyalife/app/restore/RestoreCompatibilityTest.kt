package com.mengyalife.app.restore

import com.mengyalife.app.data.backup.BackupFormatV2
import com.mengyalife.app.data.restore.RestoreCountResolver
import com.mengyalife.app.data.restore.RestoreRowNormalizer
import com.mengyalife.app.data.restore.RestoreTables
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreCompatibilityTest {
    @Test
    fun `old backup without optional counts previews zero`() {
        val counts = buildJsonObject {
            BackupFormatV2.TABLE_KEYS.filter { it !in RestoreTables.OPTIONAL }.forEach { put(it, JsonPrimitive(1)) }
        }
        val resolved = RestoreCountResolver.resolve(counts)
        RestoreTables.OPTIONAL.forEach { assertEquals(0, resolved[it]) }
    }

    @Test(expected = Exception::class)
    fun `required count missing still rejected`() {
        RestoreCountResolver.resolve(JsonObject(emptyMap()))
    }

    @Test
    fun `old care decision row gets missing columns as null`() {
        val row = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "id" to JsonPrimitive(1), "action" to JsonPrimitive("SEND"),
        )
        val normalized = RestoreRowNormalizer.normalize("careDecisions", row)
        assertEquals(JsonNull, normalized["notifiedAt"])
        assertEquals(JsonNull, normalized["messageId"])
        assertEquals(JsonNull, normalized["deliveredAt"])
    }

    @Test
    fun `present column is not overwritten`() {
        val row = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "notifiedAt" to JsonPrimitive(123L),
        )
        val normalized = RestoreRowNormalizer.normalize("careDecisions", row)
        assertEquals(JsonPrimitive(123L), normalized["notifiedAt"])
    }
}
