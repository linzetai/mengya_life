package com.mengyalife.app.domain.growth

import java.security.MessageDigest

/** 日记内容版本哈希：title + NUL + body；编辑后必定变化。 */
object DiaryContentHash {
    fun of(title: String?, body: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((title.orEmpty() + "\u0000" + body).trim().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
