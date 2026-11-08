# MengYa 0.24 · Personal Rhythm（实现与验收）

## 0.23 风险关闭

- 11:28/11:33 连续主动消息：来自 QA `qa_care_direct`/`qa_republish_care` 与 QA 构建曾硬编码 dailySendLimit=3；生产构建始终 1 次/天、同话题 72h。0.24 已移除 QA 的日上限硬编码，QA 触发同样走 Policy Gate。
- 日发送上限：0.24/0.23 真机验证，模型 SEND 被策略改 DEFER“今日主动关心已达上限”，机会不投递。
- 预算耗尽：预置 20 条决策后 Worker 不调用模型。
- SENDING 卡死：`reconcileSending()` 在 Worker/启动时把残留 SENDING 保守置 UNKNOWN；真机验证 SENDING→UNKNOWN。
- Key 边界：备份不导出 Key；`restoreEndpointAndModel` 保留本机 encryptedKey，新增单测通过；恢复不再撤销授权。

## 关心偏好模型

`care_preferences`：kind(TOPIC/TIME/BUSY/FEEDBACK)、topic_key(plan:/goal:/topic:)、value(PAUSED/SILENCED/BUSY/WELCOME/PREFER_WINDOW)、source(USER_EXPLICIT/USER_FEEDBACK)、source_excerpt、window_note、effective_at、expires_at、revoked_at。
暂停默认到本周日 23:59；永久关闭无到期；时间偏好无到期；偏好可在设置 →“已记住的关心偏好”查看来源并恢复。

## 真机场景（真实 deepseek-flash）

1. “这周比较忙，先别催我整理一次作品集。” → BUSY(plan:5)，到期本周日；回复“这周先少提…”。见 01。
2. “我其实更喜欢周日晚上回顾这一周。” → TIME，SUN 20:00；回复“记住了…”。见 02。
3. “以后别提醒我整理一次简历了。” → TOPIC SILENCED(plan:4)；回复说明范围与恢复方式。见 03。
4. Care 决策在 BUSY 生效时模型选择 DROP，未发送；候选列表不包含被静音计划。
5. SENDING 重启收敛为 UNKNOWN。

## 测试
349 项通过，0 失败（偏好解析、72h/日上限、预算、恢复保留 Key 等）。

## 未完成
- 暂停到期后自动恢复候选：仅解析/到期字段单测，未真机跨周。
- 正向反馈“刚才那个提醒挺有用”未真机；代码已接最近一次 SEND。
- Care Preference 已纳入备份/恢复（17 表）但未做设备往返。
- 时间偏好对决策的影响只通过 prompt；模型本轮 DROP，未取得“按周日晚发送”的真机结果。
- 设置偏好管理页未截图。
