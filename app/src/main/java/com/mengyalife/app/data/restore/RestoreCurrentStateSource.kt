package com.mengyalife.app.data.restore

import androidx.room.withTransaction
import com.mengyalife.app.data.local.MengyaDatabase

/** 只读当前计数；不读取正文，不写任何表。 */
interface RestoreCurrentStateSource {
    suspend fun counts(): Map<String, Int>
}

class RoomRestoreCurrentStateSource(private val database: MengyaDatabase) : RestoreCurrentStateSource {
    override suspend fun counts(): Map<String, Int> = database.withTransaction {
        val db = database.openHelper.readableDatabase
        RestoreTables.SQL_TABLES.mapValues { (_, table) ->
            db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
    }
}
