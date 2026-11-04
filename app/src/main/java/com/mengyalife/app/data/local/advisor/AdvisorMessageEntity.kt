package com.mengyalife.app.data.local.advisor

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 顾问对话消息（数据库 v3 新增）。
 *
 * 只保存聊天本身，不保存 API Key、目标快照或请求头。
 * `status = FAILED / CANCELLED` 的行不是 AI 回复，界面上必须显示为失败/取消 + 重试，
 * 不能当作正常回答。
 */
@Entity(
    tableName = "advisor_messages",
    indices = [Index(value = ["created_at"])],
)
data class AdvisorMessageEntity(
    /** 稳定消息 ID（UUID），不依赖自增，重试/删除都不会串。 */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** user / assistant */
    @ColumnInfo(name = "role")
    val role: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** SENT / FAILED / CANCELLED */
    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    /** 用户消息发送时附带的目标 id（仅用于回看，不保存目标快照）。 */
    @ColumnInfo(name = "context_goal_id")
    val contextGoalId: Long? = null,

    /** 产生这条消息时的授权 epoch；状态变化后旧 epoch 不会进入新请求。 */
    @ColumnInfo(name = "context_epoch")
    val contextEpoch: Long = 0L,

    /** 助手回复实际引用的来源（JSON 数组，不含正文/密钥）。 */
    @ColumnInfo(name = "sources_json")
    val sourcesJson: String? = null,

    /** 草案解析失败时的错误（不伪造草案）。 */
    @ColumnInfo(name = "proposal_error")
    val proposalError: String? = null,

    /** 该助手消息是否由「整理为目标草案」请求产生；用于失败重试保持 proposalMode。 */
    @ColumnInfo(name = "proposal_mode")
    val proposalMode: Boolean = false,
)
