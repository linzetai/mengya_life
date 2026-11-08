# Gate 4 备份与发布安全（已补齐）

- record_proposals 已加入备份（18 表），恢复 deleteAll 清理并还原；旧备份缺表按空表兼容。
- CareDecision 改为 allDecisions() 全量；UI 最近 50 条不变。
- 敏感 expRow/actRow 日志已删除；仅保留状态/表名级日志。
- qaDailySendLimit 仅 BuildConfig.QA_BUILD 生效。
- DB v22 连续 Migration（notified_at）；schema 由 Room 构建生成。
- 验收：B01/B02/B03/B05/B08/B09 代码完成；B04 旧备份兼容有单测；B06/B07 故障注入未新增；设备往返未跑。
