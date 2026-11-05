package com.mengyalife.app.backup

import com.mengyalife.app.data.backup.CanonicalJson
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalJsonTest {
    private fun canon(text: String): String =
        CanonicalJson.encode(Json.parseToJsonElement(text))

    @Test
    fun `keys are sorted by utf8 bytes and strings are escaped`() {
        assertEquals(
            "{\"a\":{\"y\":[true,\"x\"],\"z\":null},\"b\":2}",
            canon("{\"b\":2,\"a\":{\"z\":null,\"y\":[true,\"x\"]}}"),
        )
        assertEquals(
            "8c7c049c86bae2410bbd0a21e751a14e68e57edfc270d1862be217c402e9e203",
            CanonicalJson.sha256Hex(Json.parseToJsonElement("{\"a\":{\"y\":[true,\"x\"],\"z\":null},\"b\":2}")),
        )
    }

    @Test
    fun `numbers are normalized without exponents`() {
        assertEquals("{\"a\":1,\"b\":1.5,\"c\":0}", canon("{\"a\":1.0,\"b\":1.50,\"c\":-0.0}"))
        assertEquals(
            "20aa7fa8b4765bc5cfa908d31348ba1be7ebcbaaa3d9a5917e6aced9e16613a6",
            CanonicalJson.sha256Hex(Json.parseToJsonElement("{\"a\":1,\"b\":1.5,\"c\":0}")),
        )
        assertEquals("1000", canon("1e3"))
    }

    @Test
    fun `empty array vector matches format contract`() {
        assertEquals(
            "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945",
            CanonicalJson.sha256Hex(Json.parseToJsonElement("[]")),
        )
    }
}
