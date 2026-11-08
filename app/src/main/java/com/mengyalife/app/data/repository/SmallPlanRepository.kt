package com.mengyalife.app.data.repository

import com.mengyalife.app.data.local.plan.PlanEventEntity
import com.mengyalife.app.data.local.plan.SmallPlanDao
import com.mengyalife.app.data.local.plan.SmallPlanEntity
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object SmallPlanStatus {
    const val ACTIVE = "ACTIVE"
    const val COMPLETED = "COMPLETED"
    const val PAUSED = "PAUSED"
    const val CANCELLED = "CANCELLED"

    fun label(status: String): String = when (status) {
        COMPLETED -> "已完成"
        PAUSED -> "已暂停"
        CANCELLED -> "已取消"
        else -> "进行中"
    }
}

/** 小计划唯一写入口；状态变更追加事件，保留历史。DB v17。 */
class SmallPlanRepository(
    private val dao: SmallPlanDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val canWrite: () -> Boolean = { true },
    private val writeBarrier: RestoreWriteBarrier? = null,
) {
    fun observeAll(): Flow<List<SmallPlanEntity>> = dao.observeAll()

    fun observeProgressCounts(): Flow<Map<Long, Int>> =
        dao.observeProgressCounts().map { rows -> rows.associate { it.planId to it.total } }

    suspend fun active(): List<SmallPlanEntity> = dao.loadActive()

    suspend fun all(): List<SmallPlanEntity> = dao.loadAll()

    suspend fun find(id: Long): SmallPlanEntity? = dao.findById(id)

    suspend fun create(
        title: String,
        timeScope: String?,
        goalId: Long?,
        sourceMessageId: String?,
    ): SmallPlanEntity {
        val clean = title.trim()
        require(clean.isNotEmpty()) { "计划内容不能为空" }
        val ts = now()
        val write = suspend {
            val id = dao.insert(
                SmallPlanEntity(
                    title = clean,
                    timeScope = timeScope?.trim()?.takeIf { it.isNotEmpty() },
                    goalId = goalId,
                    status = SmallPlanStatus.ACTIVE,
                    sourceMessageId = sourceMessageId,
                    createdAt = ts,
                    updatedAt = ts,
                ),
            )
            dao.insertEvent(PlanEventEntity(planId = id, status = SmallPlanStatus.ACTIVE, note = "创建", createdAt = ts))
            dao.findById(id) ?: error("计划创建失败")
        }
        return writeBarrier?.withWrite(writeBarrier.generation()) { write() } ?: write()
    }

    /** 记录一次执行；同一来源消息只计一次（幂等）。 */
    suspend fun addProgress(planId: Long, note: String, sourceMessageId: String?): Boolean {
        val clean = note.trim().ifEmpty { "完成一次" }
        val ts = now()
        val write = suspend {
            dao.insertProgress(
                com.mengyalife.app.data.local.plan.PlanProgressEntity(
                    planId = planId,
                    note = clean,
                    recordedAt = ts,
                    sourceMessageId = sourceMessageId,
                ),
            ) > 0
        }
        return writeBarrier?.withWrite(writeBarrier.generation()) { write() } ?: write()
    }

    suspend fun changeStatus(id: Long, status: String, note: String?): SmallPlanEntity? {
        val ts = now()
        val write = suspend {
            if (dao.updateStatus(id, status, ts) == 0) {
                null
            } else {
                dao.insertEvent(PlanEventEntity(planId = id, status = status, note = note, createdAt = ts))
                dao.findById(id)
            }
        }
        return writeBarrier?.withWrite(writeBarrier.generation()) { write() } ?: write()
    }
}
