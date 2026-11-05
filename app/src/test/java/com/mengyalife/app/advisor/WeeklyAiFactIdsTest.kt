package com.mengyalife.app.advisor

import com.mengyalife.app.domain.weekly.WeeklyAiParser
import org.junit.Assert.assertEquals
import org.junit.Test

/** G4A-1：旧 U+001F 与新 JSON 两种 fact_ids_json 必须都能读，新写入必须是 JSON。 */
class WeeklyAiFactIdsTest {

    @Test
    fun `legacy unit separator is still readable`() {
        assertEquals(
            listOf("week-counts", "wins-progress"),
            WeeklyAiParser.decodeFactIds("week-counts\u001fwins-progress"),
        )
    }

    @Test
    fun `new encoding is a real json array and round trips`() {
        val encoded = WeeklyAiParser.encodeFactIds(listOf("week-counts", "wins-progress"))
        assertEquals("[\"week-counts\",\"wins-progress\"]", encoded)
        // 必须是合法 JSON，而不是 U+001F 串。
        assertEquals(listOf("week-counts", "wins-progress"), WeeklyAiParser.decodeFactIds(encoded))
    }

    @Test
    fun `empty and malformed values do not crash`() {
        assertEquals(emptyList<String>(), WeeklyAiParser.decodeFactIds(null))
        assertEquals(emptyList<String>(), WeeklyAiParser.decodeFactIds(""))
        assertEquals(listOf("[bad]", "a", "b"), WeeklyAiParser.decodeFactIds("[bad]\u001fa\u001fb"))
    }
}
