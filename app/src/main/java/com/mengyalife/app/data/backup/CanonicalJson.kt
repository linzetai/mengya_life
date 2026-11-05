package com.mengyalife.app.data.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * G4A-0.1 定义的 Canonical JSON（CJ）。
 *
 * 只用于哈希与 AAD，不用于展示；规则见 G4A0-BACKUP-FORMAT-V2.md §4.5.1。
 * 任何输出都必须是 UTF-8、键按 UTF-8 字节序、无多余空白、数字规范化。
 */
object CanonicalJson {

    fun encode(element: JsonElement): String = buildString(estimateSize(element)) {
        writeElement(element, this)
    }

    fun bytes(element: JsonElement): ByteArray = encode(element).toByteArray(StandardCharsets.UTF_8)

    fun sha256Hex(element: JsonElement): String = sha256Hex(bytes(element))

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun estimateSize(element: JsonElement): Int = when (element) {
        is JsonObject -> 2 + element.size * 16
        is JsonArray -> 2 + element.size * 8
        else -> 16
    }

    private fun writeElement(element: JsonElement, out: StringBuilder) {
        when (element) {
            is JsonNull -> out.append("null")
            is JsonObject -> writeObject(element, out)
            is JsonArray -> writeArray(element, out)
            is JsonPrimitive -> writePrimitive(element, out)
        }
    }

    private fun writeObject(obj: JsonObject, out: StringBuilder) {
        out.append('{')
        val keys = obj.keys.sortedWith(::compareUtf8)
        keys.forEachIndexed { index, key ->
            if (index > 0) out.append(',')
            writeString(key, out)
            out.append(':')
            writeElement(obj.getValue(key), out)
        }
        out.append('}')
    }

    private fun writeArray(array: JsonArray, out: StringBuilder) {
        out.append('[')
        array.forEachIndexed { index, element ->
            if (index > 0) out.append(',')
            writeElement(element, out)
        }
        out.append(']')
    }

    private fun writePrimitive(primitive: JsonPrimitive, out: StringBuilder) {
        if (primitive.isString) {
            writeString(primitive.content, out)
            return
        }
        val raw = primitive.content
        when (raw) {
            "true", "false", "null" -> out.append(raw)
            else -> out.append(canonicalNumber(raw))
        }
    }

    private fun canonicalNumber(raw: String): String {
        val normalized = runCatching { BigDecimal(raw).stripTrailingZeros() }
            .getOrElse { throw BackupException("CJ_NUMBER", "非法数字：$raw") }
        if (normalized.compareTo(BigDecimal.ZERO) == 0) return "0"
        return if (normalized.scale() <= 0) {
            normalized.toBigIntegerExact().toString()
        } else {
            normalized.toPlainString()
        }
    }

    private fun writeString(value: String, out: StringBuilder) {
        out.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (ch.code < 0x20) {
                    out.append("\\u")
                    out.append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(ch)
                }
            }
        }
        out.append('"')
    }

    /** UTF-8 字节序（无符号），用于对象键排序。 */
    private fun compareUtf8(a: String, b: String): Int {
        val x = a.toByteArray(StandardCharsets.UTF_8)
        val y = b.toByteArray(StandardCharsets.UTF_8)
        val n = minOf(x.size, y.size)
        for (i in 0 until n) {
            val xi = x[i].toInt() and 0xFF
            val yi = y[i].toInt() and 0xFF
            if (xi != yi) return xi - yi
        }
        return x.size - y.size
    }
}
