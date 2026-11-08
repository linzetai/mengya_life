# Gate 4 备份与发布安全

- record_proposals：未纳入备份/恢复，deleteAll 未清理：FAIL（B01/B02/B03）。
- CareDecision 仍 recentDecisions() 限 50 条：FAIL（B05）。
- RestoreTransaction.verify 的 expRow/actRow 日志已删除：PASS（B08 代码检查）。
- qaDailySendLimit 仅在 BuildConfig.QA_BUILD 生效：PASS（B09 代码检查）。
- DB v22 连续 Migration 已加；schema JSON 需构建生成（B migrations 未跑 instrumented）。
- 备份 17 表含 carePreferences；恢复保留 Key：PASS（单测）。
- 旧备份兼容：PASS（已有 optional 表测试）。
